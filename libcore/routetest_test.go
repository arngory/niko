package libcore

import (
	"encoding/json"
	"testing"
)

// The route tester against a core with rules as the app writes them: a sniff first, a domain, an address range by
// port, an app by uid and package, the final outbound for the rest; and a resolve that is skipped unless asked.
func TestRouteTest(t *testing.T) {
	config := `{
		"log": {"level": "error"},
		"dns": {"servers": [{"type": "udp", "tag": "dns-direct", "server": "127.0.0.1", "server_port": 9}]},
		"outbounds": [
			{"type": "direct", "tag": "proxy"},
			{"type": "direct", "tag": "bypass"},
			{"type": "block", "tag": "block"}
		],
		"route": {
			"rules": [
				{"action": "sniff"},
				{"domain_suffix": ["example.ru"], "outbound": "bypass"},
				{"ip_cidr": ["10.0.0.0/8"], "port": [443], "outbound": "block"},
				{"package_name": ["org.example.app"], "outbound": "bypass"},
				{"domain": ["resolve.example"], "action": "resolve"},
				{"ip_cidr": ["192.0.2.0/24"], "action": "reject"}
			],
			"final": "proxy"
		}
	}`
	instance := startTestBox(t, config)
	defer instance.Close()
	ask := func(q routeTestQuery) routeTestResult {
		t.Helper()
		raw, _ := json.Marshal(q)
		answer, err := RouteTest(instance, string(raw))
		if err != nil {
			t.Fatalf("%+v: %v", q, err)
		}
		var result routeTestResult
		if err = json.Unmarshal([]byte(answer), &result); err != nil {
			t.Fatal(err)
		}
		return result
	}
	if r := ask(routeTestQuery{Host: "www.example.ru", Port: 443}); r.Index != 1 || r.Outbound != "bypass" || len(r.Steps) != 2 || r.Steps[0].Note == "" {
		t.Fatalf("a domain: %+v", r)
	}
	if r := ask(routeTestQuery{Host: "10.1.2.3", Port: 443}); r.Index != 2 || r.Outbound != "block" {
		t.Fatalf("an address and a port: %+v", r)
	}
	if r := ask(routeTestQuery{Host: "10.1.2.3", Port: 80}); r.Index != -1 || r.Outbound != "proxy" || r.Action != "final" {
		t.Fatalf("the address on another port: %+v", r)
	}
	if r := ask(routeTestQuery{Host: "example.com", Port: 443, UID: 10123, Packages: []string{"org.example.app"}}); r.Index != 3 || r.Outbound != "bypass" {
		t.Fatalf("an app: %+v", r)
	}
	if r := ask(routeTestQuery{Host: "192.0.2.7", Port: 53, Network: "udp"}); r.Index != 5 || r.Action == "" || r.Outbound != "" {
		t.Fatalf("a reject: %+v", r)
	}
	// resolve not asked: noted and skipped, the final outbound takes it
	r := ask(routeTestQuery{Host: "resolve.example", Port: 443})
	if r.Index != -1 || len(r.Steps) != 2 || r.Steps[1].Note != "resolve: skipped" {
		t.Fatalf("a resolve not asked for: %+v", r)
	}
	for _, bad := range []string{`{"host": ""}`, `{"host": "x", "network": "icmp"}`, `not json`} {
		if _, err := RouteTest(instance, bad); err == nil {
			t.Errorf("%s: no error", bad)
		}
	}
	if _, err := RouteTest(nil, `{"host": "x"}`); err == nil {
		t.Error("no core: no error")
	}
}
