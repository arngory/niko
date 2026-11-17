package libcore

import (
	"encoding/json"
	"strings"
	"testing"

	"libcore/routetest"
)

// The route tester against a core with rules as the app writes them (a sniff first, DNS hijacked on 53, then the
// user's), DNS answered from fixed hosts, a FakeDNS for one zone, a resolve action for the proxy's inbound.
func TestRouteTest(t *testing.T) {
	config := `{
		"log": {"level": "error"},
		"dns": {
			"servers": [
				{"type": "hosts", "tag": "dns-hosts", "path": [], "predefined": {
					"www.example.ru": ["203.0.113.5"], "resolve.example": ["10.9.8.7"], "mail.example": ["10.20.0.8"],
					"app.example": ["203.0.113.6"], "v6.example": ["2001:db8::6"]
				}},
				{"type": "fakeip", "tag": "dns-fake", "inet4_range": "198.18.0.0/15", "inet6_range": "fc00::/18"}
			],
			"rules": [
				{"domain": ["fail.example"], "action": "reject"},
				{"domain_suffix": ["fake.example"], "server": "dns-fake"}
			],
			"final": "dns-hosts"
		},
		"outbounds": [
			{"type": "direct", "tag": "proxy"},
			{"type": "direct", "tag": "bypass"},
			{"type": "block", "tag": "block"}
		],
		"route": {
			"rules": [
				{"inbound": ["tun-in", "mixed-in"], "action": "sniff"},
				{"protocol": ["quic"], "action": "reject"},
				{"port": [53], "action": "hijack-dns"},
				{"domain_suffix": ["example.ru"], "outbound": "bypass"},
				{"ip_cidr": ["10.0.0.0/8"], "port": [443], "outbound": "block"},
				{"package_name": ["org.example.app"], "outbound": "bypass"},
				{"domain": ["resolve.example", "fail.example"], "inbound": ["mixed-in"], "action": "resolve"},
				{"ip_cidr": ["10.9.8.0/24"], "outbound": "bypass"},
				{"ip_cidr": ["192.0.2.0/24"], "action": "reject"},
				{"ip_cidr": ["198.18.0.0/15"], "outbound": "block"},
				{"domain": ["mail.example"], "outbound": "bypass"},
				{"domain": ["override.example"], "action": "route-options", "override_port": 8443},
				{"port": [8443], "outbound": "block"},
				{"ip_version": 6, "action": "reject"}
			],
			"final": "proxy"
		}
	}`
	instance := startTestBox(t, config)
	defer instance.Close()
	ask := func(q routetest.Query) routetest.Result {
		t.Helper()
		raw, _ := json.Marshal(q)
		answer, err := RouteTest(instance, string(raw))
		if err != nil {
			t.Fatalf("%+v: %v", q, err)
		}
		var result routetest.Result
		if err = json.Unmarshal([]byte(answer), &result); err != nil {
			t.Fatal(err)
		}
		return result
	}
	vpn := func(host string, port uint16) routetest.Query {
		return routetest.Query{Host: host, Port: port, Inbound: "tun-in", Resolve: true}
	}
	proxy := func(host string, port uint16) routetest.Query {
		return routetest.Query{Host: host, Port: port, Inbound: "mixed-in", Resolve: true}
	}

	// through the VPN: the app's own query gives the address, the sniff the name, the domain rule takes it
	r := ask(vpn("www.example.ru", 443))
	if r.Index != 3 || r.Outbound != "bypass" || r.Destination != "203.0.113.5:443" || len(r.Resolved) != 1 ||
		len(r.Steps) != 2 || !strings.Contains(r.Steps[0].Note, "tls") {
		t.Fatalf("a domain through the VPN: %+v", r)
	}
	// the address the app's query gave is matched by an address rule
	if r = ask(vpn("resolve.example", 443)); r.Index != 4 || r.Outbound != "block" {
		t.Fatalf("an address rule on a domain's address: %+v", r)
	}
	if r = ask(vpn("10.1.2.3", 443)); r.Index != 4 || r.Outbound != "block" {
		t.Fatalf("an address and a port: %+v", r)
	}
	if r = ask(vpn("10.1.2.3", 80)); r.Index != -1 || r.Outbound != "proxy" || r.Action != "final" {
		t.Fatalf("the address on another port: %+v", r)
	}
	q := vpn("app.example", 443)
	q.UID, q.Packages = 10123, []string{"org.example.app"}
	if r = ask(q); r.Index != 5 || r.Outbound != "bypass" {
		t.Fatalf("an app: %+v", r)
	}
	// QUIC by its port, DNS by its port
	if r = ask(routetest.Query{Host: "www.example.ru", Port: 443, Network: "udp", Inbound: "tun-in", Resolve: true}); r.Index != 1 {
		t.Fatalf("QUIC: %+v", r)
	}
	if r = ask(routetest.Query{Host: "192.0.2.7", Port: 53, Network: "udp", Inbound: "tun-in"}); r.Index != 2 || r.Action != "hijack-dns" {
		t.Fatalf("DNS: %+v", r)
	}
	if r = ask(vpn("192.0.2.7", 8080)); r.Index != 8 || r.Outbound != "" || !strings.Contains(r.Steps[0].Note, "not known") {
		t.Fatalf("a reject: %+v", r)
	}
	// an unknown TCP port: the name taken as a TLS or HTTP would carry it; an unknown UDP port: no name (not QUIC)
	if r = ask(vpn("www.example.ru", 8443)); r.Index != 3 || !strings.Contains(r.Steps[0].Note, "TLS or HTTP") {
		t.Fatalf("an unknown TCP port: %+v", r)
	}
	if r = ask(routetest.Query{Host: "www.example.ru", Port: 3478, Network: "udp", Inbound: "tun-in", Resolve: true}); r.Index != -1 ||
		r.Outbound != "proxy" || !strings.Contains(r.Steps[0].Note, "not QUIC") {
		t.Fatalf("an unknown UDP port: %+v", r)
	}
	// SMTP is not sniffed: through the VPN its name is never seen
	if r = ask(vpn("mail.example", 25)); r.Index != -1 || !strings.Contains(r.Steps[0].Note, "skipped") {
		t.Fatalf("SMTP: %+v", r)
	}
	// a fake address stands for its domain, and the address rule for the fake range does not see it
	if r = ask(vpn("www.fake.example", 443)); r.Index != -1 || !r.FakeIP || r.Destination != "www.fake.example:443" ||
		len(r.Resolved) == 0 || !strings.HasPrefix(r.Resolved[0], "198.18.") {
		t.Fatalf("FakeDNS: %+v", r)
	}
	// a fake address the core never gave
	if r = ask(vpn("198.19.255.1", 443)); r.Action != "fakeip" || r.Error == "" {
		t.Fatalf("an unknown fake address: %+v", r)
	}
	// the app's query refused: no connection
	if r = ask(vpn("fail.example", 443)); r.Index != -1 || r.Action != "dns" || r.Error == "" || len(r.Steps) != 0 {
		t.Fatalf("a refused query: %+v", r)
	}
	if r = ask(vpn("v6.example", 443)); r.Index != 13 || r.Destination != "[2001:db8::6]:443" {
		t.Fatalf("IPv6: %+v", r)
	}
	// no DNS asked: the domain as it is, and said so
	if r = ask(routetest.Query{Host: "www.example.ru", Port: 443}); r.Index != 3 || len(r.Notes) != 1 {
		t.Fatalf("a domain without DNS: %+v", r)
	}

	// through the local proxy: the domain reaches the rules, an address rule needs the resolve action
	if r = ask(proxy("resolve.example", 443)); r.Index != 7 || r.Outbound != "bypass" || len(r.Steps) != 3 ||
		len(r.Steps[1].Resolved) != 1 || r.Steps[1].Resolved[0] != "10.9.8.7" {
		t.Fatalf("a resolve action: %+v", r)
	}
	if r = ask(proxy("fail.example", 443)); r.Index != 6 || r.Error == "" {
		t.Fatalf("a failed resolve action: %+v", r)
	}
	if r = ask(routetest.Query{Host: "resolve.example", Port: 443, Inbound: "mixed-in"}); r.Index != -1 ||
		r.Steps[1].Note != "resolve: skipped, no DNS queries asked" {
		t.Fatalf("a resolve not asked for: %+v", r)
	}
	if r = ask(proxy("override.example", 443)); r.Index != 12 || r.Outbound != "block" || !strings.Contains(r.Steps[1].Note, "8443") {
		t.Fatalf("a port override: %+v", r)
	}

	for _, bad := range []string{`{"host": ""}`, `{"host": "x", "network": "icmp"}`, `{"host": "a b"}`,
		`{"host": "x", "inbound": "nope"}`, `not json`} {
		if _, err := RouteTest(instance, bad); err == nil {
			t.Errorf("%s: no error", bad)
		}
	}
	if _, err := RouteTest(nil, `{"host": "x"}`); err == nil {
		t.Error("no core: no error")
	}
}

// With dns.reverse_mapping the address the app's query gave names the connection, sniffed or not.
func TestRouteTestReverseMapping(t *testing.T) {
	instance := startTestBox(t, `{
		"log": {"level": "error"},
		"dns": {
			"servers": [{"type": "hosts", "tag": "dns-hosts", "path": [], "predefined": {"mail.example": ["10.9.8.8"]}}],
			"reverse_mapping": true
		},
		"outbounds": [{"type": "direct", "tag": "proxy"}, {"type": "direct", "tag": "bypass"}],
		"route": {"rules": [{"domain": ["mail.example"], "outbound": "bypass"}], "final": "proxy"}
	}`)
	defer instance.Close()
	answer, err := RouteTest(instance, `{"host": "mail.example", "port": 25, "resolve": true}`)
	if err != nil {
		t.Fatal(err)
	}
	var r routetest.Result
	if err = json.Unmarshal([]byte(answer), &r); err != nil || r.Index != 0 || r.Outbound != "bypass" {
		t.Fatalf("%s: %v", answer, err)
	}
}

// A sniff action of one kind of sniffers leaves the other network unsniffed, as the router's does.
func TestRouteTestSniffers(t *testing.T) {
	instance := startTestBox(t, `{
		"log": {"level": "error"},
		"dns": {"servers": [{"type": "hosts", "tag": "dns-hosts", "path": [], "predefined": {"www.example.ru": ["203.0.113.5"]}}]},
		"outbounds": [{"type": "direct", "tag": "proxy"}, {"type": "direct", "tag": "bypass"}],
		"route": {
			"rules": [{"action": "sniff", "sniffer": ["quic"]}, {"domain_suffix": ["example.ru"], "outbound": "bypass"}],
			"final": "proxy"
		}
	}`)
	defer instance.Close()
	ask := func(query string) routetest.Result {
		t.Helper()
		answer, err := RouteTest(instance, query)
		if err != nil {
			t.Fatal(err)
		}
		var r routetest.Result
		if err = json.Unmarshal([]byte(answer), &r); err != nil {
			t.Fatal(err)
		}
		return r
	}
	if r := ask(`{"host": "www.example.ru", "port": 443, "resolve": true}`); r.Index != -1 || !strings.Contains(r.Steps[0].Note, "TCP is not sniffed") {
		t.Fatalf("TCP under QUIC's sniffer: %+v", r)
	}
	if r := ask(`{"host": "www.example.ru", "port": 443, "network": "udp", "resolve": true}`); r.Index != 1 || r.Outbound != "bypass" {
		t.Fatalf("QUIC: %+v", r)
	}
}
