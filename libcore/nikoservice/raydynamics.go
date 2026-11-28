package nikoservice

import (
	"context"
	"encoding/hex"
	"encoding/json"
	"errors"
	"net"
	"os"
	"path/filepath"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"sync"
	"time"

	"libcore/naiveroots"
	"libcore/nikoconf"
	"libcore/nikofmt"
	"libcore/nikopb"
	"libcore/nikoray"
	"libcore/probe"
	"libcore/rayapi"
	"libcore/raynames"
	"libcore/rayprobe"
	"libcore/redact"

	"github.com/sagernet/sing-box/protocol/knock"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

const (
	// rotateIdentityAfterSeconds matches RayApi.ROTATE_AFTER_S (90 days, api-v1.md §6).
	rotateIdentityAfterSeconds = 90 * 86400
	watchdogPeriod             = 10 * time.Minute
	watchdogFastPeriod         = 30 * time.Second
	watchdogInitialDelay       = 20 * time.Second
	maxAdminBodyBytes          = 256 << 10
)

// adminAllowedMethods is the explicit allowlist of raycontrol remote API methods that NodeAdminCall
// may proxy on behalf of an authenticated owner with an admin node identity (D-ADMIN).
// Method names match raycontrol NodeService / DeviceService (AdminModel.kt).
var adminAllowedMethods = map[string]string{
	"GetSelf":           rayapi.DeviceService + "GetSelf",
	"GetStatus":         rayapi.NodeService + "GetStatus",
	"GetAdminAccess":    rayapi.NodeService + "GetAdminAccess",
	"SetAdminAccess":    rayapi.NodeService + "SetAdminAccess",
	"SetNodeName":       rayapi.NodeService + "SetNodeName",
	"SetNodeNote":       rayapi.NodeService + "SetNodeNote",
	"GetAppMirror":      rayapi.NodeService + "GetAppMirror",
	"SetAppMirror":      rayapi.NodeService + "SetAppMirror",
	"GetSummary":        rayapi.NodeService + "GetSummary",
	"SetSummary":        rayapi.NodeService + "SetSummary",
	"GetUpdateStatus":   rayapi.NodeService + "GetUpdateStatus",
	"StartUpdate":       rayapi.NodeService + "StartUpdate",
	"GetPorts":          rayapi.NodeService + "GetPorts",
	"GetMetrics":        rayapi.NodeService + "GetMetrics",
	"ListAudit":         rayapi.NodeService + "ListAudit",
	"GetDNS":            rayapi.NodeService + "GetDNS",
	"ListRelays":        rayapi.NodeService + "ListRelays",
	"CheckDNSZones":     rayapi.NodeService + "CheckDNSZones",
	"ListUsers":         rayapi.NodeService + "ListUsers",
	"CreateUser":        rayapi.NodeService + "CreateUser",
	"DeleteUser":        rayapi.NodeService + "DeleteUser",
	"SetUserEnabled":    rayapi.NodeService + "SetUserEnabled",
	"SetUserServices":   rayapi.NodeService + "SetUserServices",
	"SetUserLimit":      rayapi.NodeService + "SetUserLimit",
	"RenewUser":         rayapi.NodeService + "RenewUser",
	"ExportUser":        rayapi.NodeService + "ExportUser",
	"ListDevices":       rayapi.NodeService + "ListDevices",
	"CreateInvite":      rayapi.NodeService + "CreateInvite",
	"RevokeDevice":      rayapi.NodeService + "RevokeDevice",
	"ListServices":      rayapi.NodeService + "ListServices",
	"SetServiceEnabled": rayapi.NodeService + "SetServiceEnabled",
	"DeleteService":     rayapi.NodeService + "DeleteService",
	"RotateService":     rayapi.NodeService + "RotateService",
	"SetServiceOption":  rayapi.NodeService + "SetServiceOption",
}

func (s *server) nodeEndpoint(node *nikoray.Node, identity *nikoray.Identity) (rayapi.Endpoint, error) {
	if node.API == nil {
		return rayapi.Endpoint{}, status.Error(codes.FailedPrecondition, "this device has no access to the node's admin API")
	}
	if identity == nil {
		return rayapi.Endpoint{}, status.Error(codes.FailedPrecondition, "no client certificate for this node: register through an invite")
	}
	spa, err := spaOptions(node.SPAJSON())
	if err != nil {
		return rayapi.Endpoint{}, status.Error(codes.Internal, "the node's SPA: "+err.Error())
	}
	control, lookup := s.bypass()
	ep := rayapi.Endpoint{
		Host: node.Host,
		Port: node.API.Port,
		Pin:  node.API.Pin,
		Identity: rayapi.Identity{
			Cert: identity.Cert,
			Key:  identity.Key,
			Pin:  identity.Pin,
		},
		SPA:     spa,
		Control: control,
		Lookup:  lookup,
	}
	if name := node.ReachName(); name != "" {
		ep.Host, ep.Addrs = name, node.KnownAddrs()
	}
	return ep, nil
}

func (s *server) callNodeAPI(ctx context.Context, u *user, nodeID, fullMethod string, body []byte) ([]byte, error) {
	node, err := u.store.Node(nodeID)
	if err != nil {
		return nil, storeError(err)
	}
	ep, err := s.nodeEndpoint(node, node.Identity)
	if err != nil {
		return nil, err
	}
	callFn := s.apiCall
	if callFn == nil {
		callFn = rayapi.Call
	}
	answer, err := callFn(ctx, ep, fullMethod, body)
	if err != nil && node.IdentityNext != nil && isRayUnauthenticated(err) {
		next := node.IdentityNext
		epNext, epErr := s.nodeEndpoint(node, next)
		if epErr != nil {
			return nil, err
		}
		answer, err = callFn(ctx, epNext, fullMethod, body)
		if err == nil {
			// The node had already switched to IdentityNext after a lost RotateCertificate response.
			_, _ = u.store.UpdateNode(nodeID, func(n *nikoray.Node) bool {
				if n.IdentityNext != nil && n.IdentityNext.Pin == next.Pin {
					n.Identity = n.IdentityNext
					n.IdentityNext = nil
					return true
				}
				return false
			})
		}
	}
	if err != nil {
		return nil, rayAPIError(err)
	}
	if len(answer) == 0 {
		return []byte("{}"), nil
	}
	return answer, nil
}

func isRayUnauthenticated(err error) bool {
	var apiErr *rayapi.Error
	if errors.As(err, &apiErr) {
		return apiErr.Code == "unauthenticated"
	}
	return strings.Contains(err.Error(), "raycontrol api: unauthenticated")
}

func rayAPIError(err error) error {
	var apiErr *rayapi.Error
	if errors.As(err, &apiErr) {
		switch apiErr.Code {
		case "unauthenticated":
			return status.Error(codes.Unauthenticated, apiErr.Error())
		case "permission_denied":
			return status.Error(codes.PermissionDenied, apiErr.Error())
		case "not_found":
			return status.Error(codes.NotFound, apiErr.Error())
		case "invalid_argument":
			return status.Error(codes.InvalidArgument, apiErr.Error())
		case "failed_precondition":
			return status.Error(codes.FailedPrecondition, apiErr.Error())
		case "unimplemented", "http_404":
			return status.Error(codes.Unimplemented, apiErr.Error())
		}
	}
	if errors.Is(err, context.Canceled) {
		return status.Error(codes.Canceled, err.Error())
	}
	if errors.Is(err, context.DeadlineExceeded) {
		return status.Error(codes.DeadlineExceeded, err.Error())
	}
	return status.Error(codes.Unavailable, err.Error())
}

// NodeAdminCall proxies an allowlisted admin API call to a raycontrol node using the daemon's sealed identity (D-ADMIN).
func (s *server) NodeAdminCall(ctx context.Context, req *nikopb.NodeAdminRequest) (*nikopb.NodeAdminResponse, error) {
	if s.canStart != nil {
		if err := s.canStart(ctx); err != nil {
			return nil, err
		}
	}
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	method := strings.TrimSpace(req.GetMethod())
	fullMethod, ok := adminAllowedMethods[method]
	if !ok {
		return nil, status.Errorf(codes.InvalidArgument, "method %q is not allowed", method)
	}
	rawBody := strings.TrimSpace(req.GetBodyJson())
	if rawBody == "" {
		rawBody = "{}"
	}
	if len(rawBody) > maxAdminBodyBytes {
		return nil, status.Error(codes.InvalidArgument, "admin request body too large")
	}
	var check map[string]any
	if err := json.Unmarshal([]byte(rawBody), &check); err != nil || check == nil {
		return nil, status.Error(codes.InvalidArgument, "admin request body must be a JSON object")
	}
	callCtx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()
	answer, err := s.callNodeAPI(callCtx, u, req.GetNodeId(), fullMethod, []byte(rawBody))
	if err != nil {
		return nil, err
	}
	return &nikopb.NodeAdminResponse{BodyJson: string(answer)}, nil
}

// rotateNodeIdentityIfDue rotates an admin device's client certificate every 90 days (api-v1.md §6, RayApi.rotateIfDue).
func (s *server) rotateNodeIdentityIfDue(ctx context.Context, u *user, nodeID string) (bool, error) {
	node, err := u.store.Node(nodeID)
	if err != nil {
		return false, err
	}
	nowTime := s.clock()
	now := nowTime.Unix()
	if node.API == nil || node.Identity == nil || now-node.Identity.CreatedAt < rotateIdentityAfterSeconds {
		return false, nil
	}
	s.dynMu.Lock()
	if s.rotating == nil {
		s.rotating = map[string]bool{}
	}
	if s.rotateRetryAt == nil {
		s.rotateRetryAt = map[string]time.Time{}
	}
	if s.rotating[nodeID] || nowTime.Before(s.rotateRetryAt[nodeID]) {
		s.dynMu.Unlock()
		return false, nil
	}
	s.rotating[nodeID] = true
	s.dynMu.Unlock()
	defer func() {
		s.dynMu.Lock()
		delete(s.rotating, nodeID)
		s.dynMu.Unlock()
	}()

	next := node.IdentityNext
	if next == nil {
		newID := s.newIdentity
		if newID == nil {
			newID = rayapi.NewIdentity
		}
		made, err := newID()
		if err != nil {
			return false, err
		}
		pending := &nikoray.Identity{Cert: made.Cert, Key: made.Key, Pin: made.Pin, CreatedAt: now}
		// Persist IdentityNext BEFORE calling RotateCertificate so a lost response never strands the key.
		updated, err := u.store.UpdateNode(nodeID, func(n *nikoray.Node) bool {
			if n.IdentityNext == nil {
				n.IdentityNext = pending
				return true
			}
			pending = n.IdentityNext
			return false
		})
		if err != nil {
			return false, err
		}
		node = updated
		next = pending
	}
	ep, err := s.nodeEndpoint(node, node.Identity)
	if err != nil {
		return false, err
	}
	rotFn := s.rotateCert
	if rotFn == nil {
		rotFn = rayapi.RotateCertificate
	}
	rotCtx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()
	err = rotFn(rotCtx, ep, rayapi.Identity{Cert: next.Cert, Key: next.Key, Pin: next.Pin})
	if err != nil {
		if isRayUnauthenticated(err) {
			// An earlier try may have already switched the node: GetSelf via callNodeAPI verifies and promotes IdentityNext.
			verifyCtx, vCancel := context.WithTimeout(ctx, 15*time.Second)
			defer vCancel()
			if _, verifyErr := s.callNodeAPI(verifyCtx, u, nodeID, rayapi.DeviceService+"GetSelf", []byte("{}")); verifyErr == nil {
				return true, nil
			}
		}
		s.dynMu.Lock()
		s.rotateRetryAt[nodeID] = s.clock().Add(time.Hour)
		s.dynMu.Unlock()
		return false, err
	}
	_, err = u.store.UpdateNode(nodeID, func(n *nikoray.Node) bool {
		if n.IdentityNext != nil && n.IdentityNext.Pin == next.Pin {
			n.Identity = n.IdentityNext
			n.IdentityNext = nil
			return true
		}
		return false
	})
	return err == nil, err
}

// ClearNodeSwitched clears the D-STATE-SWITCHED notice banner on a node.
func (s *server) ClearNodeSwitched(ctx context.Context, req *nikopb.NodeId) (*nikopb.Empty, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	_, err = u.store.UpdateNode(req.GetId(), func(n *nikoray.Node) bool {
		if n.SwitchedFrom == "" && n.SwitchedTo == "" && n.SwitchedAt == 0 {
			return false
		}
		n.SwitchedFrom, n.SwitchedTo, n.SwitchedAt = "", "", 0
		return true
	})
	if err != nil {
		return nil, storeError(err)
	}
	return &nikopb.Empty{}, nil
}

// SetNodeLan toggles lan-v1 visibility for this device on a raycontrol node.
func (s *server) SetNodeLan(ctx context.Context, req *nikopb.SetNodeLanRequest) (*nikopb.Empty, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	if err := u.store.SetNodeLanShow(req.GetNodeId(), req.GetShow()); err != nil {
		return nil, storeError(err)
	}
	// Trigger an immediate subscription refresh so the node receives X-Raycontrol-LAN: on|off.
	if s.fetch != nil && s.users != nil {
		if node, err := u.store.Node(req.GetNodeId()); err == nil && node.Subscription != nil {
			task := "node-" + strconv.FormatUint(s.users.tasks.Add(1), 10)
			go s.nodeTask(u, task, req.GetNodeId(), nil)
		}
	}
	return &nikopb.Empty{}, nil
}

// --- Diagnostic "Why isn't it working" (D-WHY / RunNodeTest) ---

var validRunIDPattern = regexp.MustCompile(`^[A-Za-z0-9._-]{1,64}$`)

type directProbeOutcome struct {
	SPACarrier  string
	UDPProbed   bool
	UDPMeasured bool
	UDPMTU      int
	UDPStatus   int
	Error       string
}

func (s *server) probeDirectNode(ctx context.Context, node *nikoray.Node) (*directProbeOutcome, error) {
	if s.directProbe != nil {
		return s.directProbe(ctx, node)
	}
	if node.Subscription == nil {
		return nil, nil
	}
	key, err := hex.DecodeString(node.DeviceKey)
	if err != nil || len(key) != 32 {
		return &directProbeOutcome{Error: "invalid device key"}, nil
	}
	spa, err := spaOptions(node.SPAJSON())
	if err != nil {
		return &directProbeOutcome{Error: err.Error()}, nil
	}
	control, lookup := s.bypass()
	dialer := &net.Dialer{Timeout: 8 * time.Second, Control: control}
	targets, _ := raynames.Targets(ctx, lookup, node.Host, node.KnownAddrs(), knock.TakesV3(spa))
	if len(targets) == 0 {
		return &directProbeOutcome{Error: "no node address"}, nil
	}
	host, serverName := targets[0].Address, targets[0].ServerName
	if serverName == "" && net.ParseIP(host) == nil {
		serverName = host
	}
	ep := &rayprobe.Endpoint{
		Address:    net.JoinHostPort(host, strconv.Itoa(node.Subscription.Port)),
		Pin:        node.Subscription.Pin,
		ServerName: serverName,
		DeviceID:   node.DeviceID,
		DeviceKey:  key,
		Dial:       dialer.DialContext,
	}
	out := &directProbeOutcome{}
	for _, carrier := range spa.TryCarriers() {
		if err := knock.SendSPAv2Named(ctx, dialer, host, serverName, spa, carrier, s.clock()); err != nil {
			out.Error = err.Error()
			continue
		}
		client := rayprobe.NewClient(ep)
		down := client.Down(ctx, 64<<10, probe.Options{WindowMs: 5000})
		answered := down.OK || down.Status != 0 || down.FirstByteMs > 0
		if answered {
			out.SPACarrier = carrier.String()
			out.Error = ""
			if node.Probe != nil && node.Probe.UDP {
				out.UDPProbed = true
				mtu, steps := client.PathMTU(ctx, []int{1472, 1350, 1200, 900}, 3, 4*time.Second)
				if ctx.Err() == nil && len(steps) > 0 {
					out.UDPMeasured = true
					out.UDPMTU = mtu
				}
			}
			client.Close()
			return out, nil
		}
		out.Error = down.Error
		client.Close()
	}
	if out.SPACarrier == "" && out.Error == "" {
		out.Error = "no answer on any SPA carrier"
	}
	return out, nil
}

func profileCarrier(typ string, bean nikofmt.Bean) string {
	if bean != nil {
		_, transport := nikofmt.Kind(bean)
		return transport
	}
	switch typ {
	case "hysteria", "hysteria2", "tuic", "wg", "awg", "wireguard", "naive-quic":
		return "udp"
	case "dnstt", "masterdns", "masterdnsvpn", "slipstream":
		return "dns"
	default:
		return "tcp"
	}
}

// RunNodeTest runs a diagnostic test suite against a raycontrol node, streaming steps and returning redacted findings (D-WHY).
func (s *server) RunNodeTest(req *nikopb.NodeTestRequest, stream grpc.ServerStreamingServer[nikopb.NodeTestEvent]) error {
	ctx := stream.Context()
	if s.canStart != nil {
		if err := s.canStart(ctx); err != nil {
			return err
		}
	}
	u, err := s.users.user(ctx)
	if err != nil {
		return err
	}
	node, err := u.store.Node(req.GetNodeId())
	if err != nil {
		return storeError(err)
	}
	runID := strings.TrimSpace(req.GetRunId())
	if runID == "" {
		runID = "r-" + s.clock().UTC().Format("20060102-1504")
	}
	if !validRunIDPattern.MatchString(runID) {
		return status.Error(codes.InvalidArgument, "run id: letters, digits, '.', '-', '_' only (1..64)")
	}

	// Ensure redactions are synced before streaming any live steps.
	s.syncLogRedactions()

	var stepMaps []map[string]any
	var reportBuf strings.Builder
	nowUTC := func() string { return s.clock().UTC().Truncate(time.Millisecond).Format(time.RFC3339Nano) }
	reportBuf.WriteString("# Niko test run " + runID + ", " + nowUTC() + "\n# steps (JSON lines)\n")

	emit := func(stepName string, fields map[string]any) error {
		m := map[string]any{"run": runID, "t": nowUTC(), "step": stepName}
		for k, v := range fields {
			m[k] = v
		}
		stepMaps = append(stepMaps, m)
		raw, _ := json.Marshal(m)
		line := redact.Log(string(raw))
		reportBuf.WriteString(line + "\n")
		return stream.Send(&nikopb.NodeTestEvent{RunId: runID, Step: stepName, Line: line})
	}

	if err := emit("start", map[string]any{
		"app":      Version,
		"node_id":  node.ID(),
		"net":      "desktop",
		"profiles": len(node.ProfileIDs),
	}); err != nil {
		return err
	}

	// Step 1: Direct SPA & UDP MTU probe.
	if dp, _ := s.probeDirectNode(ctx, node); dp != nil {
		fields := map[string]any{"udp_probed": dp.UDPProbed}
		if dp.SPACarrier != "" {
			fields["spa_carrier"] = dp.SPACarrier
		}
		if dp.UDPMeasured {
			fields["udp_mtu"] = dp.UDPMTU
		}
		if dp.UDPStatus != 0 {
			fields["udp_status"] = dp.UDPStatus
		}
		if dp.Error != "" {
			fields["error"] = dp.Error
		}
		if err := emit("direct", fields); err != nil {
			return err
		}
	}

	// Step 2: Subscription refresh & clock skew check.
	if node.Subscription != nil {
		_, _, _, subErr := s.updateNode(ctx, u, node.ID())
		if updated, err := u.store.Node(node.ID()); err == nil {
			node = updated
		}
		subFields := map[string]any{
			"profiles":     len(node.ProfileIDs),
			"clock_skew_s": node.ClockSkew,
		}
		if subErr != nil {
			subFields["error"] = status.Convert(subErr).Message()
		}
		if err := emit("subscription", subFields); err != nil {
			return err
		}
	}

	// Step 3: Probe each profile in deterministic key order.
	keys := make([]string, 0, len(node.ProfileIDs))
	for k := range node.ProfileIDs {
		if !strings.HasPrefix(k, "stale:") {
			keys = append(keys, k)
		}
	}
	slices.Sort(keys)

	s.testSlotsOnce.Do(func() {
		if s.testSlots == nil {
			s.testSlots = make(chan struct{}, testWorkers)
		}
	})

	profileDeadline := s.clock().Add(90 * time.Second)
	for _, key := range keys {
		if ctx.Err() != nil {
			return status.FromContextError(ctx.Err()).Err()
		}
		if s.clock().After(profileDeadline) {
			break
		}
		pid := node.ProfileIDs[key]
		stored, err := u.store.Profile(pid)
		if err != nil {
			continue
		}
		bean, _ := nikofmt.DecodeBean(stored.Type, stored.Bean)
		carrier := profileCarrier(stored.Type, bean)
		relayID := nikoray.RelayOfKey(key)
		startFields := map[string]any{
			"key":     key,
			"name":    stored.Name,
			"type":    stored.Type,
			"carrier": carrier,
		}
		if relayID != "" {
			startFields["relay"] = relayID
		}
		if err := emit("profile_start", startFields); err != nil {
			return err
		}

		select {
		case s.testSlots <- struct{}{}:
		case <-ctx.Done():
			return status.FromContextError(ctx.Err()).Err()
		}
		var ms int32
		var testErr error
		func() {
			defer func() { <-s.testSlots }()
			if s.testProfile != nil {
				ms, testErr = s.testProfile(ctx, u, pid, "", 5*time.Second)
			} else {
				ms, testErr = s.measureStoredProfile(ctx, u, pid, "", 5*time.Second)
			}
		}()

		pFields := map[string]any{"key": key}
		if testErr != nil {
			pFields["tcp_ok"] = false
			pFields["tcp_error"] = testErr.Error()
		} else {
			pFields["tcp_ok"] = true
			pFields["first_byte_ms"] = int64(ms)
		}
		if err := emit("profile", pFields); err != nil {
			return err
		}
	}

	if err := emit("end", nil); err != nil {
		return err
	}

	findings := nikoray.EvaluateTestSteps(stepMaps)
	summaryLine := redact.Log(findings.SummaryLine(runID))
	reportBuf.WriteString("# what Niko found on them\n" + summaryLine + "\n")
	// Redact node hosts, DNS names, past names, keys, and tokens before returning the shareable report.
	s.syncLogRedactions()
	redactedReport := redact.Log(reportBuf.String())

	pbFindings := &nikopb.NodeTestFindings{}
	for _, pf := range findings.Profiles {
		item := &nikopb.NodeTestProfile{
			Key:         pf.Key,
			Name:        pf.Name,
			Type:        pf.Type,
			Carrier:     pf.Carrier,
			Relay:       pf.Relay,
			Works:       pf.Works,
			Kbps:        pf.Kbps,
			FirstByteMs: pf.FirstByteMs,
			Cause:       pf.Cause,
			Error:       redact.Log(pf.Error),
			StalledAtKb: pf.StalledAtKB,
			UdpLossPct:  pf.UDPLossPct,
		}
		if pf.UDPOk != nil {
			v := *pf.UDPOk
			item.UdpOk = &v
		}
		pbFindings.Profiles = append(pbFindings.Profiles, item)
	}
	for _, n := range findings.Notes {
		pbFindings.Notes = append(pbFindings.Notes, &nikopb.NodeTestNote{
			Note:   n.Note,
			Number: n.Number,
			Detail: redact.Log(n.Detail),
		})
	}

	return stream.Send(&nikopb.NodeTestEvent{
		RunId:      runID,
		Step:       "findings",
		Line:       summaryLine,
		Done:       true,
		Findings:   pbFindings,
		ReportName: "niko-" + runID + ".log",
		ReportText: redactedReport,
	})
}

// --- Watchdog, Brutal, Datagrams, Anonymous Reports ---

type brutalEntry struct {
	At       int64
	DownKbps int64
	UpKbps   int64
	Lowered  int
}

type watchdogRun struct {
	cancel    context.CancelFunc
	nodeID    string
	profileID int64
	key       string
	stale     bool
	mu        sync.Mutex
}

func (s *server) currentBrutalRates() map[string]nikoconf.BrutalRate {
	s.dynMu.Lock()
	defer s.dynMu.Unlock()
	if len(s.brutalRates) == 0 {
		return nil
	}
	out := make(map[string]nikoconf.BrutalRate, len(s.brutalRates))
	now := s.clock().Unix()
	for id, e := range s.brutalRates {
		// Keep measured rates up to 7 days (BRUTAL_KEPT).
		if now-e.At <= 7*86400 && (e.DownKbps > 0 || e.UpKbps > 0) {
			out[id] = nikoconf.BrutalRate{DownKbps: e.DownKbps, UpKbps: e.UpKbps}
		}
	}
	return out
}

// recordBrutalRates saves measured Hysteria2 Brutal rates and optionally lowers download rate on poor ack rates.
func (s *server) recordBrutalRates(nodeID string, downKbps, upKbps int64) {
	s.dynMu.Lock()
	defer s.dynMu.Unlock()
	if s.brutalRates == nil {
		s.brutalRates = map[string]brutalEntry{}
	}
	s.brutalRates[nodeID] = brutalEntry{At: s.clock().Unix(), DownKbps: downKbps, UpKbps: upKbps}
}

// lowerBrutalOnPoorAck lowers a node's Hysteria2 Brutal download rate by 25%, or falls back to BBR (0) on 2nd strike.
func (s *server) lowerBrutalOnPoorAck(nodeID string) (newDown int64, changed bool) {
	s.dynMu.Lock()
	defer s.dynMu.Unlock()
	e, ok := s.brutalRates[nodeID]
	if !ok || e.DownKbps <= 0 {
		return 0, false
	}
	e.Lowered++
	if e.Lowered >= 2 {
		e.DownKbps = 0
	} else {
		next := (e.DownKbps * 3) / 4
		if next < 1000 {
			next = 0
		}
		e.DownKbps = next
	}
	s.brutalRates[nodeID] = e
	return e.DownKbps, true
}

func (s *server) markWatchdogStale(nodeID string, changedProfiles []int64) {
	s.dynMu.Lock()
	w := s.watchdog
	s.dynMu.Unlock()
	if w == nil || w.nodeID != nodeID {
		return
	}
	if slices.Contains(changedProfiles, w.profileID) {
		w.mu.Lock()
		w.stale = true
		w.mu.Unlock()
	}
}

func (s *server) startWatchdogForConnect(callerCtx context.Context, u *user, profileID int64, nodes []nikoray.Node) {
	var owner *nikoray.Node
	var key string
	for i := range nodes {
		if k := nodes[i].OwnerOf(profileID); k != "" {
			owner = &nodes[i]
			key = k
			break
		}
	}
	s.dynMu.Lock()
	if s.watchdog != nil {
		s.watchdog.cancel()
		s.watchdog = nil
	}
	if owner == nil || s.users == nil {
		s.dynMu.Unlock()
		return
	}
	// Preserve callerCtx values (e.g. gRPC peer credentials for s.start) without inheriting the RPC's deadline.
	baseCtx := context.WithoutCancel(callerCtx)
	ctx, cancel := context.WithCancel(baseCtx)
	stopOnShutdown := context.AfterFunc(s.users.context(), cancel)
	wrappedCancel := func() {
		stopOnShutdown()
		cancel()
	}
	w := &watchdogRun{cancel: wrappedCancel, nodeID: owner.ID(), profileID: profileID, key: key}
	s.watchdog = w
	s.dynMu.Unlock()

	go s.runWatchdogLoop(ctx, u, w)
}

func (s *server) runWatchdogLoop(ctx context.Context, u *user, w *watchdogRun) {
	timer := time.NewTimer(watchdogInitialDelay)
	defer timer.Stop()
	failures := 0
	for {
		select {
		case <-ctx.Done():
			return
		case <-timer.C:
		}
		// Only probe when a core is actually running.
		if s.core != nil && !s.connected() {
			return
		}
		if s.coreDial == nil && s.coreBox == nil {
			timer.Reset(watchdogPeriod)
			continue
		}
		settings, err := u.store.Settings()
		if err != nil {
			timer.Reset(watchdogPeriod)
			continue
		}
		res, err := s.TestConnection(ctx, &nikopb.TestConnectionRequest{TimeoutMs: 5000})
		if err != nil && status.Code(err) == codes.FailedPrecondition {
			// Core stopped: end this watchdog run cleanly.
			return
		}
		ok := err == nil && res != nil && res.Error == ""
		if ok {
			failures = 0
			w.mu.Lock()
			stale := w.stale
			w.mu.Unlock()
			if stale {
				_, _ = s.reconnectProfile(ctx, u, w.profileID, settings)
				return
			}
			if settings.RaycontrolReports {
				go s.maybeSendAnonymousReport(ctx, u, w.nodeID, w.key, true, int64(res.LatencyMs))
			}
			timer.Reset(watchdogPeriod)
			continue
		}
		failures++
		if failures < 2 {
			timer.Reset(watchdogFastPeriod)
			continue
		}
		// Two consecutive failures: recover (1. refresh subscription, 2. alternative credential, 3. failover).
		if recovered := s.recoverRayNode(ctx, u, w, settings); recovered {
			return
		}
		failures = 0
		timer.Reset(watchdogPeriod)
	}
}

// recoverRayNode executes the RayWatchdog recovery ladder (refresh -> useAlternative -> RayChannel failover).
func (s *server) recoverRayNode(ctx context.Context, u *user, w *watchdogRun, settings nikoconf.Settings) bool {
	if s.core != nil && !s.connected() {
		return false
	}
	// 1. Refresh subscription directly around TUN.
	if _, _, _, err := s.updateNode(ctx, u, w.nodeID); err == nil {
		if _, err := s.reconnectProfile(ctx, u, w.profileID, settings); err == nil {
			return true
		}
	}
	// 2. Try toggling alternative credential (link <-> next), rolling back if reconnect fails.
	if pid, switched, err := u.store.UseAlternativeLink(w.nodeID, w.key); err == nil && switched && pid > 0 {
		if _, err := s.reconnectProfile(ctx, u, pid, settings); err == nil {
			return true
		}
		_, _, _ = u.store.UseAlternativeLink(w.nodeID, w.key)
	}
	// 3. Auto-switch failover across other profiles of the node (RayChannel.failover + D-STATE-SWITCHED).
	if !settings.RaycontrolAutoSwitch {
		return false
	}
	if s.core != nil && !s.connected() {
		return false
	}
	node, err := u.store.Node(w.nodeID)
	if err != nil {
		return false
	}
	oldName := ""
	if cur, err := u.store.Profile(w.profileID); err == nil {
		oldName = cur.Name
	}
	var bestID int64
	var bestMs int32 = 1<<30 - 1
	var bestName string
	for k, pid := range node.ProfileIDs {
		if pid == w.profileID || strings.HasPrefix(k, "stale:") {
			continue
		}
		var ms int32
		var testErr error
		if s.testProfile != nil {
			ms, testErr = s.testProfile(ctx, u, pid, "", 5*time.Second)
		} else {
			ms, testErr = s.measureStoredProfile(ctx, u, pid, "", 5*time.Second)
		}
		if testErr == nil && ms < bestMs {
			bestMs = ms
			bestID = pid
			if p, err := u.store.Profile(pid); err == nil {
				bestName = p.Name
			}
		}
	}
	if bestID == 0 {
		return false
	}
	// Only persist selection and D-STATE-SWITCHED notice AFTER the replacement core starts successfully.
	if _, err := s.reconnectProfile(ctx, u, bestID, settings); err != nil {
		return false
	}
	_ = u.store.Select(bestID)
	now := s.clock().Unix()
	_, _ = u.store.UpdateNode(w.nodeID, func(n *nikoray.Node) bool {
		n.SwitchedFrom = oldName
		n.SwitchedTo = bestName
		n.SwitchedAt = now
		return true
	})
	return true
}

func (s *server) reconnectProfile(ctx context.Context, u *user, profileID int64, settings nikoconf.Settings) (bool, error) {
	if s.start == nil {
		return false, status.Error(codes.Unimplemented, "no daemon to start the core")
	}
	in, err := connectInput(u.store, profileID, connectSettings(settings))
	if err != nil {
		return false, err
	}
	in.UserMode = s.userMode
	if settings.RaycontrolBrutal {
		in.BrutalRates = s.currentBrutalRates()
	}
	in.DnsResolvers = s.currentDNSResolvers(in.Nodes)
	built, err := nikoconf.Build(in)
	if err != nil {
		return false, err
	}
	s.deviceKeys(in.Nodes)
	naiveroots.Sync(settings.RootStore)
	if err := s.slipstream.Start(ctx, built.SlipstreamLaunches); err != nil {
		return false, err
	}
	if err := s.start(ctx, string(built.Config), systemProxy(built.SystemProxy)); err != nil {
		s.slipstream.StopAll()
		return false, err
	}
	s.setActiveNodeID(ownerNodeIDOf(in.Profile, in.Nodes))
	s.startWatchdogForConnect(ctx, u, in.Profile, in.Nodes)
	s.probeNaiveDatagramSoon(u, in.Nodes, built.NaiveDatagramTags)
	return true, nil
}

// --- RayDatagrams verdict & per-network cache (naive-udp-v1 §9.4) ---

const (
	DatagramEchoCount = 30
	DatagramEchoSize  = 1100
	DatagramEchoPass  = 22
	datagramHoldPass  = 7 * 86400
	datagramHoldFail  = 6 * 3600
	datagramMaxNets   = 16
)

type DatagramVerdict string

const (
	DatagramPassed DatagramVerdict = "passed"
	DatagramFailed DatagramVerdict = "failed"
	DatagramAgain  DatagramVerdict = "again"
)

// EvaluateDatagramEcho computes the RayDatagrams verdict for a probe-v1 echo result.
func EvaluateDatagramEcho(statusCode, sent, received int) DatagramVerdict {
	if statusCode == 429 || sent == 0 {
		return DatagramAgain
	}
	if received >= DatagramEchoPass {
		return DatagramPassed
	}
	return DatagramFailed
}

type datagramEntry struct {
	At int64 `json:"at"`
	OK bool  `json:"ok"`
}

type datagramCache struct {
	mu   sync.Mutex
	path string
	data map[string]map[string]datagramEntry // networkKey -> serviceKey -> entry
}

func (c *datagramCache) Load(path string) {
	if path == "" {
		return
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.path == path && c.data != nil {
		return
	}
	// Switching cache path (e.g. another user): clear prior entries before reading so nothing leaks across users.
	c.path = path
	c.data = map[string]map[string]datagramEntry{}
	raw, err := os.ReadFile(path)
	if err != nil {
		return
	}
	var loaded map[string]map[string]datagramEntry
	if err := json.Unmarshal(raw, &loaded); err == nil && loaded != nil {
		c.data = loaded
	}
}

func (c *datagramCache) saveLocked() {
	if c.path == "" || c.data == nil {
		return
	}
	raw, err := json.Marshal(c.data)
	if err != nil {
		return
	}
	tmp, err := os.CreateTemp(filepath.Dir(c.path), "datagrams.*.tmp")
	if err != nil {
		return
	}
	_ = tmp.Chmod(0o600)
	if _, err := tmp.Write(raw); err != nil {
		_ = tmp.Close()
		_ = os.Remove(tmp.Name())
		return
	}
	if err := tmp.Close(); err != nil {
		_ = os.Remove(tmp.Name())
		return
	}
	if err := os.Rename(tmp.Name(), c.path); err != nil {
		_ = os.Remove(tmp.Name())
	}
}

func (c *datagramCache) Remember(networkKey, serviceID string, ok bool, now int64) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.data == nil {
		c.data = map[string]map[string]datagramEntry{}
	}
	if c.data[networkKey] == nil {
		c.data[networkKey] = map[string]datagramEntry{}
	}
	c.data[networkKey][serviceID] = datagramEntry{At: now, OK: ok}
	if len(c.data) > datagramMaxNets {
		var oldestNet string
		var oldestAt int64 = 1<<62 - 1
		for nk, m := range c.data {
			var latest int64
			for _, e := range m {
				if e.At > latest {
					latest = e.At
				}
			}
			if latest < oldestAt {
				oldestAt = latest
				oldestNet = nk
			}
		}
		if oldestNet != "" {
			delete(c.data, oldestNet)
		}
	}
	c.saveLocked()
}

func (c *datagramCache) Lookup(networkKey, serviceID string, now int64) (bool, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	e, ok := c.data[networkKey][serviceID]
	if !ok {
		return false, false
	}
	hold := int64(datagramHoldFail)
	if e.OK {
		hold = datagramHoldPass
	}
	if now-e.At < 0 || now-e.At >= hold {
		return false, false
	}
	return e.OK, true
}

// LookupNode returns the freshest non-expired verdict for nodeID (matching either exact nodeID
// or any service key prefixed with "nodeID#") on networkKey.
func (c *datagramCache) LookupNode(networkKey, nodeID string, now int64) (bool, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	entries := c.data[networkKey]
	if len(entries) == 0 {
		return false, false
	}
	prefix := nodeID + "#"
	var best datagramEntry
	found := false
	for key, e := range entries {
		if key != nodeID && !strings.HasPrefix(key, prefix) {
			continue
		}
		hold := int64(datagramHoldFail)
		if e.OK {
			hold = datagramHoldPass
		}
		if now-e.At < 0 || now-e.At >= hold {
			continue
		}
		if !found || e.At >= best.At {
			best = e
			found = true
		}
	}
	return best.OK, found
}

// --- Opt-in Anonymous Network Summary (RayReport.kt parity) ---

// BuildAnonymousReport builds the coarse bucketed JSON summary for POST /probe/v1/report (no identifiers/times).
func BuildAnonymousReport(svcID int, works bool, latencyMs int64, viaRelay bool, supportedFields []string) []byte {
	res := "works"
	if !works {
		res = "fails"
	} else if latencyMs > 2000 {
		res = "degraded"
	}
	obs := map[string]any{
		"svc":    svcID,
		"result": res,
	}
	if works {
		mbps := "20-100"
		switch {
		case latencyMs > 1500:
			mbps = "<1"
		case latencyMs > 500:
			mbps = "1-5"
		case latencyMs > 150:
			mbps = "5-20"
		case latencyMs <= 40:
			mbps = "100+"
		}
		obs["mbps"] = mbps
	}
	payload := map[string]any{
		"v":   1,
		"net": "desktop",
		"obs": []any{obs},
	}
	if slices.Contains(supportedFields, "path") {
		if viaRelay {
			payload["path"] = "relay"
		} else {
			payload["path"] = "direct"
		}
	}
	raw, _ := json.Marshal(payload)
	return raw
}

func (s *server) maybeSendAnonymousReport(ctx context.Context, u *user, nodeID, rowKey string, works bool, latencyMs int64) {
	today := s.clock().Unix() / 86400
	s.dynMu.Lock()
	if s.reportDays != nil && s.reportDays[nodeID] == today {
		s.dynMu.Unlock()
		return
	}
	s.dynMu.Unlock()

	node, err := u.store.Node(nodeID)
	if err != nil || node.Probe == nil || node.Subscription == nil {
		return
	}
	svcStr, _, _ := strings.Cut(rowKey, "#")
	svcID, err := strconv.Atoi(svcStr)
	if err != nil || svcID <= 0 {
		return
	}
	viaRelay := nikoray.RelayOfKey(rowKey) != ""
	body := BuildAnonymousReport(svcID, works, latencyMs, viaRelay, node.Probe.Report)
	key, err := hex.DecodeString(node.DeviceKey)
	if err != nil || len(key) != 32 {
		return
	}
	spa, err := spaOptions(node.SPAJSON())
	if err != nil {
		return
	}
	repCtx, cancel := context.WithTimeout(ctx, 12*time.Second)
	defer cancel()
	control, lookup := s.bypass()
	dialer := &net.Dialer{Timeout: 5 * time.Second, Control: control}
	reachHost := node.ReachName()
	if reachHost == "" {
		reachHost = node.Host
	}
	targets, _ := raynames.Targets(repCtx, lookup, reachHost, node.KnownAddrs(), knock.TakesV3(spa))
	if len(targets) == 0 {
		return
	}
	host, serverName := targets[0].Address, targets[0].ServerName
	if serverName == "" && net.ParseIP(host) == nil {
		serverName = host
	}
	ep := &rayprobe.Endpoint{
		Address:    net.JoinHostPort(host, strconv.Itoa(node.Subscription.Port)),
		Pin:        node.Subscription.Pin,
		ServerName: serverName,
		DeviceID:   node.DeviceID,
		DeviceKey:  key,
		Dial:       dialer.DialContext,
	}
	for _, carrier := range spa.TryCarriers() {
		if err := knock.SendSPAv2Named(repCtx, dialer, host, serverName, spa, carrier, s.clock()); err != nil {
			continue
		}
		client := rayprobe.NewClient(ep)
		err := client.Report(repCtx, body)
		client.Close()
		if err == nil {
			s.markReportDone(nodeID, today)
			return
		}
		var se *rayprobe.StatusError
		if errors.As(err, &se) && se.Status >= 400 && se.Status < 500 {
			// Final HTTP status from the node (404/409/429/400): do not retry until tomorrow.
			s.markReportDone(nodeID, today)
			return
		}
	}
}

func (s *server) markReportDone(nodeID string, day int64) {
	s.dynMu.Lock()
	defer s.dynMu.Unlock()
	if s.reportDays == nil {
		s.reportDays = map[string]int64{}
	}
	s.reportDays[nodeID] = day
}
