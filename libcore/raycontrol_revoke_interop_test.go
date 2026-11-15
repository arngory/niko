package libcore

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"errors"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"slices"
	"strings"
	"testing"
	"time"

	"github.com/miekg/dns"
	M "github.com/sagernet/sing/common/metadata"

	"libcore/raysub"
)

// revokeRows are what a device of the revocation test takes from its subscription: its hy2-masq row (pinned) and its
// DoH line, whose token is the device's own (raycontrol v0.27.0).
type revokeRows struct {
	hy2 string
	doh *url.URL
}

// TestRaycontrolRevoke (raycontrol v0.27.0, api-v1 §5a, subscription-v1), run by raycontrol-interop.yml on the node
// with certificates from Pebble after the pins check: two devices of one user; one is revoked. The revoked device's
// DoH token gets an empty 404 from the first query after the revocation, and its subscription is refused. The other
// device keeps its DoH token, its old credentials stop working (a new connection is refused) and the subscription it
// fetches again gives new ones that carry. RenewUser does the same to the other device without a revocation. The
// times are logged: the client side of "the user's other devices reconnect after their next fetch".
//
// RAYCONTROL_RAYCTL runs rayctl on that node, RAYCONTROL_REVOKE_ONE and RAYCONTROL_REVOKE_TWO are the two devices'
// packages, RAYCONTROL_REVOKE_USER their user; RAYCONTROL_ADDRESS, RAYCONTROL_CA and RAYCONTROL_LIMIT_TARGET as for
// TestRaycontrolSitePins.
func TestRaycontrolRevoke(t *testing.T) {
	rayctlCommand := strings.Fields(os.Getenv("RAYCONTROL_RAYCTL"))
	onePath, twoPath, user := os.Getenv("RAYCONTROL_REVOKE_ONE"), os.Getenv("RAYCONTROL_REVOKE_TWO"), os.Getenv("RAYCONTROL_REVOKE_USER")
	if len(rayctlCommand) == 0 || onePath == "" || twoPath == "" || user == "" {
		t.Skip("RAYCONTROL_RAYCTL, RAYCONTROL_REVOKE_ONE, RAYCONTROL_REVOKE_TWO and RAYCONTROL_REVOKE_USER not set (raycontrol-interop.yml sets them)")
	}
	one, node, oneRequest := interopDeviceAt(t, onePath)
	two, _, twoRequest := interopDeviceAt(t, twoPath)
	rootsPEM, err := os.ReadFile(os.Getenv("RAYCONTROL_CA"))
	if err != nil {
		t.Fatal("RAYCONTROL_CA: ", err)
	}
	roots := x509.NewCertPool()
	if !roots.AppendCertsFromPEM(rootsPEM) {
		t.Fatal("RAYCONTROL_CA: no certificate")
	}
	rayctl := func(args ...string) string {
		t.Helper()
		out, err := exec.Command(rayctlCommand[0], append(slices.Clone(rayctlCommand[1:]), args...)...).CombinedOutput()
		if err != nil {
			t.Fatalf("rayctl %s: %v: %s", strings.Join(args, " "), err, out)
		}
		t.Logf("rayctl %s: %s", strings.Join(args, " "), strings.TrimSpace(string(out)))
		return string(out)
	}

	// the destination through the tunnel: an echo on every address of the runner
	listener, err := net.Listen("tcp", ":0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			go func() {
				defer conn.Close()
				io.Copy(conn, conn)
			}()
		}
	}()
	destination := M.ParseSocksaddrHostPort(os.Getenv("RAYCONTROL_LIMIT_TARGET"), uint16(listener.Addr().(*net.TCPAddr).Port))

	fetch := func(request []byte) (revokeRows, error) {
		appJSON, err := RaycontrolFetchSubscription(string(request))
		if err != nil {
			return revokeRows{}, err
		}
		var result raysub.Result
		if err = json.Unmarshal([]byte(appJSON), &result); err != nil {
			return revokeRows{}, err
		}
		var rows revokeRows
		for _, p := range result.Payload.Profiles {
			u, err := url.Parse(p.Link)
			if err != nil {
				return revokeRows{}, err
			}
			switch {
			case u.Scheme == "hysteria2" && u.Query().Get("obfs") == "":
				rows.hy2 = p.Link
			case u.Scheme == "https" && strings.HasPrefix(u.Path, "/dns-query"):
				rows.doh = u
			}
		}
		if rows.hy2 == "" || rows.doh == nil {
			return rows, errors.New("the subscription has not both hy2-masq and DoH")
		}
		return rows, nil
	}
	rowsOf := func(what string, request []byte) revokeRows {
		t.Helper()
		rows, err := fetch(request)
		if err != nil {
			t.Fatalf("%s: %v", what, err)
		}
		return rows
	}
	// throughHysteria: a new connection through the row (its own pins), the echo back
	throughHysteria := func(link, deviceKey string) error {
		kind, config := profileConfig(t, link, node, "", "", deviceKey, 50)
		if kind != "hysteria2-pin" {
			t.Fatalf("the hy2-masq row built as %s, want it pinned", kind)
		}
		instance := startTestBox(t, config)
		defer instance.Close()
		outbound, _ := instance.Box.Outbound().Outbound("proxy")
		ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
		defer cancel()
		conn, err := outbound.DialContext(ctx, "tcp", destination)
		if err != nil {
			return err
		}
		defer conn.Close()
		conn.SetDeadline(time.Now().Add(5 * time.Second))
		echo := make([]byte, 2)
		if _, err = conn.Write([]byte("ok")); err == nil {
			_, err = io.ReadFull(conn, echo)
		}
		if err == nil && string(echo) != "ok" {
			err = errors.New("not the echo")
		}
		return err
	}
	// within waits for check to come true, and says how long it took
	within := func(what string, limit time.Duration, check func() bool) time.Duration {
		t.Helper()
		start := time.Now()
		for !check() {
			if time.Since(start) > limit {
				t.Fatalf("%s: not in %v", what, limit)
			}
			time.Sleep(time.Second)
		}
		return time.Since(start).Round(time.Second)
	}
	// askDoH sends one query to the DoH line as a client does (GET, RFC 8484), with the CA of the stand
	askDoH := func(u *url.URL) (int, []byte, error) {
		query := new(dns.Msg)
		query.SetQuestion("example.com.", dns.TypeA)
		wire, err := query.Pack()
		if err != nil {
			return 0, nil, err
		}
		port := u.Port()
		if port == "" {
			port = "443"
		}
		client := &http.Client{Timeout: 10 * time.Second, Transport: &http.Transport{
			DialContext: func(ctx context.Context, network, _ string) (net.Conn, error) {
				return (&net.Dialer{}).DialContext(ctx, network, net.JoinHostPort(node, port))
			},
			TLSClientConfig: &tls.Config{
				ServerName: u.Hostname(), RootCAs: roots, CurvePreferences: []tls.CurveID{tls.X25519MLKEM768},
			},
			ForceAttemptHTTP2: true,
		}}
		defer client.CloseIdleConnections()
		// the line's path (the token in it) and the query alone: what the line carries for the client (its pins)
		// is not the server's
		ask := url.URL{Scheme: "https", Host: u.Host, Path: u.Path, RawPath: u.RawPath,
			RawQuery: "dns=" + base64.RawURLEncoding.EncodeToString(wire)}
		request, _ := http.NewRequest(http.MethodGet, ask.String(), nil)
		request.Header.Set("Accept", "application/dns-message")
		response, err := client.Do(request)
		if err != nil {
			return 0, nil, err
		}
		defer response.Body.Close()
		body, err := io.ReadAll(io.LimitReader(response.Body, 64<<10))
		return response.StatusCode, body, err
	}
	dohAnswers := func(what string, u *url.URL) {
		t.Helper()
		status, body, err := askDoH(u)
		answer := new(dns.Msg)
		if err != nil || status != http.StatusOK || answer.Unpack(body) != nil {
			t.Fatalf("%s: DoH answered %d (%d bytes), %v", what, status, len(body), err)
		}
	}
	// dohTaken waits for a new DoH token (a new device's, a renewed user's) to answer, asking again only while the
	// node answers 404 (its DoH takes a token after it reloads; raycontrol v0.27.0's notes have a token-shaped path
	// wait for that reload, run 43672859960 got a 404 0.6 s on). Logged: how long after fetched (when the token came
	// with the subscription) it answered
	dohTaken := func(what string, u *url.URL, fetched time.Time) {
		t.Helper()
		for {
			status, body, err := askDoH(u)
			if err != nil || (status != http.StatusOK && status != http.StatusNotFound) {
				t.Fatalf("%s: DoH answered %d (%d bytes), %v", what, status, len(body), err)
			}
			if status == http.StatusOK {
				t.Logf("%s: DoH answered %v after the subscription that gave the token", what,
					time.Since(fetched).Round(100*time.Millisecond))
				dohAnswers(what, u)
				return
			}
			if time.Since(fetched) > 60*time.Second {
				t.Fatalf("%s: DoH still answers 404 60 s after the subscription that gave the token", what)
			}
			time.Sleep(500 * time.Millisecond)
		}
	}

	// before: each device's DoH token answers, the other device's Hysteria2 carries. The devices were made just
	// before this test: how long the node's DoH takes to take a new device's token is logged (a token-shaped path
	// waits for DoH's reload after a change, raycontrol v0.27.0's notes say; run 43672859960 got a 404 0.6 s on)
	oneRows, oneFetched := rowsOf("device one", oneRequest), time.Now()
	twoRows, twoFetched := rowsOf("device two", twoRequest), time.Now()
	if oneRows.doh.Path == twoRows.doh.Path {
		t.Fatal("the two devices have the same DoH line: no token per device")
	}
	dohTaken("device one's new DoH token", oneRows.doh, oneFetched)
	dohTaken("device two's new DoH token", twoRows.doh, twoFetched)
	// a new user's credentials carry once the node's data plane has them (run 43678255945, with the DoH tokens taken
	// at once: Hysteria2 refused device two 0.6 s after its subscription): waited for, and logged
	for {
		err := throughHysteria(twoRows.hy2, two.DeviceKey)
		if err == nil {
			break
		}
		if time.Since(twoFetched) > 60*time.Second {
			t.Fatalf("device two before: Hysteria2 refuses its new credentials 60 s after its subscription: %v", err)
		}
		time.Sleep(time.Second)
	}
	t.Logf("device two carries %v after its subscription", time.Since(twoFetched).Round(100*time.Millisecond))

	// the revocation
	out := rayctl("device", "revoke", one.DeviceID)
	revoked := time.Now()
	if !strings.Contains(out, "credentials are new") {
		t.Fatalf("rayctl device revoke says nothing of the user's new credentials: %s", out)
	}
	// the revoked device's token: an empty 404 from the first query after the revocation returns
	status, body, err := askDoH(oneRows.doh)
	if err != nil || status != http.StatusNotFound || len(body) != 0 {
		t.Fatalf("the revoked device's DoH, the first query after the revocation: %d (%d bytes), %v; want an empty 404",
			status, len(body), err)
	}
	// the other device's token stays its own
	dohAnswers("device two after the revocation", twoRows.doh)
	// the other device's credentials as it had them: a new connection is refused within seconds
	refused := within("device two's old credentials refused", 60*time.Second, func() bool {
		return throughHysteria(twoRows.hy2, two.DeviceKey) != nil
	})
	t.Logf("device two's old credentials refused %v after the revocation", refused)
	// the subscription it fetches again has new ones, which carry
	var renewed revokeRows
	within("device two reconnects with its new subscription", 90*time.Second, func() bool {
		rows, err := fetch(twoRequest)
		if err != nil || rows.hy2 == twoRows.hy2 {
			return false
		}
		renewed = rows
		return throughHysteria(rows.hy2, two.DeviceKey) == nil
	})
	t.Logf("device two carries again %v after the revocation (its fetch and a new connection)",
		time.Since(revoked).Round(time.Second))
	if renewed.doh.Path != twoRows.doh.Path {
		t.Fatal("the revocation of another device changed this device's DoH token")
	}
	// the revoked device gets no subscription
	if _, err = fetch(oneRequest); err == nil {
		t.Fatal("the revoked device still gets its subscription")
	}

	// RenewUser: the same for the other device without a revocation, its DoH token included (a leak is what it is
	// for): the old token gets the empty 404, the new subscription's answers
	out = rayctl("user", "renew", user)
	if !strings.Contains(out, "credentials are new") {
		t.Fatalf("rayctl user renew says nothing of the new credentials: %s", out)
	}
	renewedAt := time.Now()
	if status, body, err = askDoH(renewed.doh); err != nil || status != http.StatusNotFound || len(body) != 0 {
		t.Fatalf("device two's DoH token after RenewUser: %d (%d bytes), %v; want an empty 404", status, len(body), err)
	}
	refused = within("device two's credentials refused after RenewUser", 60*time.Second, func() bool {
		return throughHysteria(renewed.hy2, two.DeviceKey) != nil
	})
	var (
		after        revokeRows
		afterFetched time.Time
	)
	within("device two reconnects after RenewUser", 90*time.Second, func() bool {
		rows, err := fetch(twoRequest)
		if err != nil || rows.hy2 == renewed.hy2 {
			return false
		}
		after, afterFetched = rows, time.Now()
		return throughHysteria(rows.hy2, two.DeviceKey) == nil
	})
	t.Logf("RenewUser: the old credentials refused %v after it, device two carries again %v after it", refused,
		time.Since(renewedAt).Round(time.Second))
	if after.doh.Path == renewed.doh.Path {
		t.Fatal("RenewUser left device two's DoH token as it was")
	}
	dohTaken("device two's DoH token after RenewUser", after.doh, afterFetched)
}
