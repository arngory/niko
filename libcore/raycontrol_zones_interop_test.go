package libcore

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"net/url"
	"os"
	"slices"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"

	"libcore/dnsprobe"
	"libcore/knock"
	"libcore/rayprobe"
	"libcore/raysub"

	"github.com/miekg/dns"
)

// spareZoneStep is what spareZoneInterop needs of TestRaycontrolTunnelInterop's run: the dnstt profile measured, the
// device and the node's probe, a refusal check and a box through a resolver of the step's
type spareZoneStep struct {
	kind      string
	profile   raysub.Profile
	node      string
	host      string
	subPort   int
	subPin    string
	deviceID  string
	spa       knock.SPAv2Options
	deviceKey []byte
	probe     *raysub.ProbeInfo
	dnsProbe  *raysub.DNSProbe
	warm      []byte
	refused   func(what, link, resolver string)
	start     func(link, resolver string) *BoxInstance
}

// spareZoneInterop: the node's dnstt with a spare zone (rayctl service set <dnstt> spare_zones=1), its main zone cut
// by the network's resolver (RAYCONTROL_CUT_MAIN: drop, or servfail). dnsprobe-v1 fails in the main zone through it
// and passes in the spare one, which is when the app takes the spare (RayDnsResolvers.detect); the tunnel then goes
// through the spare zone and not through the main one; and the node takes the summary's obs zone of the spare
// (RAYCONTROL_ZONE_REPORT: a summary is once a day per device and AS).
func spareZoneInterop(t *testing.T, s spareZoneStep) {
	t.Helper()
	main, spare := dnsZones(t, s.profile.Link)
	if len(spare) == 0 {
		t.Fatalf("%s %s: no niko-dns-spare in the link: %s", s.kind, s.profile.Name, s.profile.Link)
	}
	if s.dnsProbe == nil || s.dnsProbe.V != 1 {
		t.Fatal("no dns_probe in the subscription")
	}
	if !slices.Contains(s.probe.Report, "dns_zone") {
		t.Fatalf("probe.report without dns_zone: %v", s.probe.Report)
	}
	mode := os.Getenv("RAYCONTROL_CUT_MAIN")
	resolver, cut := cuttingResolver(t, net.JoinHostPort(s.node, "53"), main, mode == "servfail")
	ask := func(zone string) dnsprobe.Result {
		request, _ := json.Marshal(map[string]any{"resolver": "udp://" + resolver, "zone": zone, "key": s.dnsProbe.Key,
			"large": false, "timeout_ms": 4000})
		out, err := RaycontrolDnsProbe(string(request))
		if err != nil {
			t.Fatal(err)
		}
		var r dnsprobe.Result
		if err = json.Unmarshal([]byte(out), &r); err != nil {
			t.Fatal(err)
		}
		return r
	}
	if r := ask(main); r.OK {
		t.Fatalf("dnsprobe in the main zone %s through a resolver that cuts it (%s): %+v", main, mode, r)
	}
	if r := ask(spare[0]); !r.OK {
		t.Fatalf("dnsprobe in the spare zone %s: %+v", spare[0], r)
	}
	t.Logf("dnsprobe through the resolver that cuts the main zone (%s): the main fails, the spare passes", mode)

	// the tunnel in the spare zone through that resolver, as the app runs it (DnsTunnel.withZone)
	instance := s.start(linkWithZone(t, s.profile.Link, spare[0]), resolver)
	defer instance.Close()
	for deadline := time.Now().Add(90 * time.Second); ; {
		out, err := RaycontrolProbeTunnel(instance, string(s.warm))
		var r probeResult
		if err == nil && json.Unmarshal([]byte(out), &r) == nil && r.Down != nil && r.Down.OK {
			break
		}
		if time.Now().After(deadline) {
			t.Fatalf("%s %s in the spare zone: no answer through the tunnel in 90 s: %s %v", s.kind, s.profile.Name, out, err)
		}
		time.Sleep(3 * time.Second)
	}
	probeThrough(t, s.kind+" "+s.profile.Name+" (spare zone)", instance, s.host, s.subPort, s.subPin, s.deviceID, s.spa,
		s.probe.Tunnel, s.profile.Svc, 64<<10)
	s.refused("in the main zone through the resolver that cuts it", s.profile.Link, resolver)
	if cut() == 0 {
		t.Fatal("the resolver cut no query")
	}
	// the spare zone's name for the step, which reads the node's count of it
	if out := os.Getenv("RAYCONTROL_SPARE_OUT"); out != "" {
		if err := os.WriteFile(out, []byte(spare[0]), 0o600); err != nil {
			t.Fatal(err)
		}
	}

	if os.Getenv("RAYCONTROL_ZONE_REPORT") == "" {
		return
	}
	// the network's summary: the tunnel works here in zone 1, the first spare (probe-v1 §6, dnszones-v1 §3)
	rc := rayprobe.NewClient(&rayprobe.Endpoint{
		Address: net.JoinHostPort(s.node, strconv.Itoa(s.subPort)), Pin: s.subPin,
		DeviceID: s.deviceID, DeviceKey: s.deviceKey, Dial: (&net.Dialer{}).DialContext,
	})
	defer rc.Close()
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	summary := fmt.Sprintf(`{"v":1,"net":"wifi","obs":[{"svc":%d,"result":"works","dns":"udp","zone":1}]}`, s.profile.Svc)
	if err := rc.Report(ctx, []byte(summary)); err != nil {
		t.Fatalf("the summary with the spare zone: %v", err)
	}
	t.Logf("the node took the summary: svc %d works in its zone 1", s.profile.Svc)
}

// The pieces of raycontrol-interop's DNS tunnel steps (raycontrol v0.14.0): a network's resolver that cuts the main
// zone of a dnstt service (dnszones-v1 §3), and the links changed as a step needs them.

// cuttingResolver is a UDP resolver in front of upstream that cuts zone as a network's resolver blocking it does:
// a query for a name under zone, its apex too and whatever the case of its letters, dropped (servfail false) or
// answered SERVFAIL at once (true); the others go on to upstream. Its address; closed with the test. It counts the
// queries it cut.
func cuttingResolver(t *testing.T, upstream, zone string, servfail bool) (string, func() int) {
	t.Helper()
	conn, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	var mu sync.Mutex
	cut := 0
	done := make(chan struct{})
	t.Cleanup(func() {
		close(done)
		conn.Close()
	})
	go func() {
		buf := make([]byte, 65535)
		for {
			n, client, err := conn.ReadFrom(buf)
			if err != nil {
				return
			}
			query := append([]byte(nil), buf[:n]...)
			var m dns.Msg
			if m.Unpack(query) == nil && len(m.Question) == 1 && underZone(m.Question[0].Name, zone) {
				mu.Lock()
				cut++
				mu.Unlock()
				if servfail {
					reply := new(dns.Msg)
					reply.SetRcode(&m, dns.RcodeServerFailure)
					if out, err := reply.Pack(); err == nil {
						conn.WriteTo(out, client)
					}
				}
				continue
			}
			go func() {
				select {
				case <-done:
					return
				default:
				}
				up, err := net.Dial("udp", upstream)
				if err != nil {
					return
				}
				defer up.Close()
				up.SetDeadline(time.Now().Add(5 * time.Second))
				if _, err := up.Write(query); err != nil {
					return
				}
				answer := make([]byte, 65535)
				if n, err := up.Read(answer); err == nil {
					conn.WriteTo(answer[:n], client)
				}
			}()
		}
	}()
	return conn.LocalAddr().String(), func() int {
		mu.Lock()
		defer mu.Unlock()
		return cut
	}
}

// underZone is whether name (a question's, its dot at the end or not) is zone or under it, the case aside
func underZone(name, zone string) bool {
	name = strings.TrimSuffix(strings.ToLower(name), ".")
	zone = strings.TrimSuffix(strings.ToLower(zone), ".")
	return name == zone || strings.HasSuffix(name, "."+zone)
}

func TestUnderZone(t *testing.T) {
	for name, want := range map[string]bool{
		"t.example.test.": true, "T.Example.TEST.": true, "abc.t.example.test.": true, "ABC.T.EXAMPLE.TEST": true,
		"xt.example.test.": false, "example.test.": false, "t.example.test.evil.": false,
	} {
		if underZone(name, "t.example.test") != want {
			t.Fatalf("%s: want %v", name, want)
		}
	}
}

// dnsZones are the main zone and the spare ones of a DNS tunnel link (niko-dns, niko-dns-spare)
func dnsZones(t *testing.T, link string) (string, []string) {
	t.Helper()
	u, err := url.Parse(link)
	if err != nil {
		t.Fatal(err)
	}
	spec := strings.Split(u.Query().Get("niko-dns"), ";")
	if len(spec) != 4 {
		t.Fatalf("niko-dns %q", u.Query().Get("niko-dns"))
	}
	var spare []string
	for _, zone := range strings.Split(u.Query().Get("niko-dns-spare"), ",") {
		if zone = strings.TrimSpace(zone); zone != "" {
			spare = append(spare, zone)
		}
	}
	return strings.Split(spec[2], ",")[0], spare
}

// linkWithZone is a DNS tunnel link with zone in place of its main one, as the app runs the tunnel in a spare zone
// (DnsTunnel.withZone)
func linkWithZone(t *testing.T, link, zone string) string {
	t.Helper()
	u, err := url.Parse(link)
	if err != nil {
		t.Fatal(err)
	}
	q := u.Query()
	spec := strings.Split(q.Get("niko-dns"), ";")
	if len(spec) != 4 {
		t.Fatalf("niko-dns %q", q.Get("niko-dns"))
	}
	spec[2] = zone
	q.Set("niko-dns", strings.Join(spec, ";"))
	u.RawQuery = q.Encode()
	return u.String()
}

// linkWithout is link without its parameter param: a client that does not know it
func linkWithout(t *testing.T, link, param string) string {
	t.Helper()
	u, err := url.Parse(link)
	if err != nil {
		t.Fatal(err)
	}
	q := u.Query()
	if !q.Has(param) {
		t.Fatalf("the link has no %s: %s", param, link)
	}
	q.Del(param)
	u.RawQuery = q.Encode()
	return u.String()
}

func TestCuttingResolver(t *testing.T) {
	// an upstream that answers every name
	pc, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	server := &dns.Server{PacketConn: pc, Handler: dns.HandlerFunc(func(w dns.ResponseWriter, r *dns.Msg) {
		reply := new(dns.Msg)
		reply.SetReply(r)
		reply.Answer = append(reply.Answer, &dns.TXT{Hdr: dns.RR_Header{Name: r.Question[0].Name, Rrtype: dns.TypeTXT,
			Class: dns.ClassINET, Ttl: 1}, Txt: []string{"up"}})
		w.WriteMsg(reply)
	})}
	go server.ActivateAndServe()
	defer server.Shutdown()
	for _, servfail := range []bool{false, true} {
		resolver, cut := cuttingResolver(t, pc.LocalAddr().String(), "t.example.test", servfail)
		client := &dns.Client{Timeout: time.Second}
		ask := func(name string) (*dns.Msg, error) {
			m := new(dns.Msg)
			m.SetQuestion(name, dns.TypeTXT)
			r, _, err := client.Exchange(m, resolver)
			return r, err
		}
		if r, err := ask("other.example.test."); err != nil || len(r.Answer) != 1 {
			t.Fatalf("servfail %v: a name outside the zone: %v %v", servfail, r, err)
		}
		for _, name := range []string{"T.Example.Test.", "abc.t.example.test."} {
			r, err := ask(name)
			if servfail && (err != nil || r.Rcode != dns.RcodeServerFailure) {
				t.Fatalf("%s: want SERVFAIL: %v %v", name, r, err)
			}
			if !servfail && err == nil {
				t.Fatalf("%s: want no answer: %v", name, r)
			}
		}
		if cut() != 2 {
			t.Fatalf("servfail %v: %d cut", servfail, cut())
		}
	}
}
