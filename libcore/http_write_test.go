package libcore

import (
	"bytes"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// WriteTo saves a body up to maxWriteSize and refuses, and removes, one past it.
func TestWriteToBounded(t *testing.T) {
	saved := maxWriteSize
	maxWriteSize = 1000
	defer func() { maxWriteSize = saved }()
	dir := t.TempDir()
	for _, size := range []int{0, 1000, 1001, 5000} {
		path := filepath.Join(dir, "body")
		response := &httpResponse{Response: &http.Response{Body: io.NopCloser(bytes.NewReader(make([]byte, size)))}}
		err := response.WriteTo(path)
		info, statErr := os.Stat(path)
		if size <= 1000 {
			if err != nil || statErr != nil || info.Size() != int64(size) {
				t.Fatalf("%d bytes: %v, %v", size, err, statErr)
			}
			continue
		}
		if err == nil || !strings.Contains(err.Error(), "larger than") || !os.IsNotExist(statErr) {
			t.Fatalf("%d bytes: %v, file %v", size, err, statErr)
		}
	}
}

// An error names the refusals of a panel that limits devices, in brackets after the status; other headers stay out.
func TestErrorNamesPanelRefusal(t *testing.T) {
	for _, c := range []struct {
		header http.Header
		want   string
	}{
		{http.Header{"X-Hwid-Not-Supported": {"true"}, "X-Hwid-Active": {"true"}}, "HTTP 404 Not Found [x-hwid-not-supported]: gone"},
		{http.Header{"X-Hwid-Max-Devices-Reached": {" TRUE "}, "X-Hwid-Limit": {"true"}},
			"HTTP 404 Not Found [x-hwid-max-devices-reached] [x-hwid-limit]: gone"},
		{http.Header{"X-Hwid-Not-Supported": {"false"}}, "HTTP 404 Not Found: gone"},
		{nil, "HTTP 404 Not Found: gone"},
	} {
		response := &httpResponse{Response: &http.Response{Status: "404 Not Found", Header: c.header,
			Body: io.NopCloser(strings.NewReader("gone"))}}
		if got := response.errorString(); got != c.want {
			t.Errorf("%v: %q, want %q", c.header, got, c.want)
		}
	}
}
