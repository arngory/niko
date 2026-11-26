package exitaddress

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestQueryCloudflareAndValidation(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("fl=1\nip=203.0.113.42\nloc=de\n"))
	}))
	defer srv.Close()

	p := Providers["cloudflare"]
	p.URL = srv.URL
	ans, err := Query(context.Background(), srv.Client(), p)
	if err != nil {
		t.Fatalf("Query: %v", err)
	}
	if ans.IP != "203.0.113.42" || ans.CountryCode != "DE" {
		t.Fatalf("unexpected answer: %+v", ans)
	}
}
