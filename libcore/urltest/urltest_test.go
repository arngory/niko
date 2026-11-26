package urltest

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestMeasureAndRedirect(t *testing.T) {
	calls := 0
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls++
		http.Redirect(w, r, "http://127.0.0.1:1/never", http.StatusFound)
	}))
	defer srv.Close()

	ms, err := Measure(context.Background(), srv.Client(), srv.URL, 2*time.Second)
	if err != nil {
		t.Fatalf("Measure: %v", err)
	}
	if ms < 0 || calls != 2 {
		t.Fatalf("ms=%d calls=%d", ms, calls)
	}
}
