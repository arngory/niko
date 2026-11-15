package libcore

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

// The lookup services' answers as each sends them, and what is refused: another status, no address, the service's
// own failure, an answer too large.
func TestExitAddress(t *testing.T) {
	answers := map[string]string{
		"cloudflare": "fl=1\nh=www.cloudflare.com\nip=2001:db8::5\nts=1\nloc=de\ntls=TLSv1.3\n",
		"ipinfo":     `{"ip":"203.0.113.5","city":"Amsterdam","country":"NL","org":"AS64496"}`,
		"ipwho":      `{"ip":"203.0.113.6","success":true,"country":"Finland","country_code":"FI","city":"Helsinki"}`,
		"ipapi-co":   `{"ip":"::ffff:203.0.113.7","city":"Riga","country_code":"LV","country_name":"Latvia"}`,
	}
	want := map[string]exitAnswer{
		"cloudflare": {IP: "2001:db8::5", CountryCode: "DE"},
		"ipinfo":     {IP: "203.0.113.5", CountryCode: "NL", City: "Amsterdam"},
		"ipwho":      {IP: "203.0.113.6", CountryCode: "FI", Country: "Finland", City: "Helsinki"},
		"ipapi-co":   {IP: "203.0.113.7", CountryCode: "LV", Country: "Latvia", City: "Riga"},
	}
	var body string
	status := http.StatusOK
	server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(status)
		w.Write([]byte(body))
	}))
	defer server.Close()
	ask := func(id string) (exitAnswer, error) {
		p := exitProviders[id]
		p.url = server.URL
		return exitAddress(context.Background(), server.Client(), p)
	}
	for id, answer := range answers {
		body = answer
		got, err := ask(id)
		if err != nil || got != want[id] {
			t.Errorf("%s: %+v, %v; want %+v", id, got, err, want[id])
		}
	}
	for what, c := range map[string]struct {
		id, body string
		status   int
	}{
		"another status":      {"ipinfo", answers["ipinfo"], http.StatusTooManyRequests},
		"no address":          {"cloudflare", "fl=1\nloc=DE\n", http.StatusOK},
		"the service's no":    {"ipwho", `{"success":false,"message":"reserved range"}`, http.StatusOK},
		"the service's error": {"ipapi-co", `{"error":true,"reason":"RateLimited"}`, http.StatusOK},
		"cut at 64 KiB":       {"ipinfo", `{"ip":"203.0.113.5","pad":"` + strings.Repeat("x", 70<<10) + `"}`, http.StatusOK},
		"not JSON":            {"ipinfo", "<html>", http.StatusOK},
	} {
		body, status = c.body, c.status
		if got, err := ask(c.id); err == nil {
			t.Errorf("%s: %+v, no error", what, got)
		}
	}
	if _, err := ExitAddress(nil, "cloudflare", 1000); err == nil {
		t.Error("no core: no error")
	}
}
