package libcore

import (
	"net"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

func TestURLTest(t *testing.T) {
	var requests, connections atomic.Int32
	server := httptest.NewUnstartedServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// the second answer slower: the result is the second's
		if requests.Add(1) == 2 {
			time.Sleep(120 * time.Millisecond)
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	server.Config.ConnState = func(_ net.Conn, state http.ConnState) {
		if state == http.StateNew {
			connections.Add(1)
		}
	}
	server.Start()
	defer server.Close()
	ms, err := urlTest(server.Client(), server.URL+"/generate_204", 5000)
	if err != nil || ms < 100 || ms > 2000 {
		t.Fatalf("%d ms, %v", ms, err)
	}
	if requests.Load() != 2 || connections.Load() != 1 {
		t.Fatalf("%d requests on %d connections, want 2 on 1", requests.Load(), connections.Load())
	}
}

func TestURLTestRedirectIsAnAnswer(t *testing.T) {
	var followed atomic.Bool
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/elsewhere" {
			followed.Store(true)
		}
		http.Redirect(w, r, "/elsewhere", http.StatusFound)
	}))
	defer server.Close()
	if _, err := urlTest(server.Client(), server.URL+"/", 5000); err != nil || followed.Load() {
		t.Fatalf("%v, followed %v", err, followed.Load())
	}
}

func TestURLTestTimeout(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		select {
		case <-r.Context().Done():
		case <-time.After(2 * time.Second):
		}
	}))
	defer server.Close()
	start := time.Now()
	if _, err := urlTest(server.Client(), server.URL+"/", 300); err == nil || time.Since(start) > 1500*time.Millisecond {
		t.Fatalf("%v after %v", err, time.Since(start))
	}
	if _, err := urlTest(nil, server.URL, 300); err == nil {
		t.Fatal("no client: no error")
	}
}
