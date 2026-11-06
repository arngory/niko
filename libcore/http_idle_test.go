package libcore

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

// The review: a whole-request bound cut a download that was slow but moving (an APK through a DNS tunnel). The
// idle bound lets one that keeps moving finish, longer than the bound, and cuts one that stops.
func TestIdleTimeout(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		flusher := w.(http.Flusher)
		for i := 0; i < 8; i++ {
			// the stalled one stops after its third piece for longer than the bound
			if r.URL.Path == "/stalls" && i == 3 {
				time.Sleep(time.Second)
			}
			w.Write([]byte("piece "))
			flusher.Flush()
			time.Sleep(100 * time.Millisecond)
		}
	}))
	defer server.Close()
	client := NewHttpClient()
	client.SetIdleTimeout(400)
	dir := t.TempDir()
	path := filepath.Join(dir, "body")

	// 8 pieces 100 ms apart: 800 ms in all, twice the idle bound, never 400 ms without one
	request := client.NewRequest()
	request.SetURL(server.URL + "/moves")
	response, err := request.Execute()
	if err != nil {
		t.Fatal(err)
	}
	if err = response.WriteTo(path); err != nil {
		t.Fatalf("a slow transfer that keeps moving: %v", err)
	}
	if body, _ := os.ReadFile(path); strings.Count(string(body), "piece") != 8 {
		t.Fatalf("got %q", body)
	}

	request = client.NewRequest()
	request.SetURL(server.URL + "/stalls")
	response, err = request.Execute()
	if err != nil {
		t.Fatal(err)
	}
	if err = response.WriteTo(path); err == nil {
		t.Fatal("a transfer stalled past the idle bound finished")
	}
	if _, statErr := os.Stat(path); !os.IsNotExist(statErr) {
		t.Fatalf("the cut download left its file: %v", statErr)
	}
}
