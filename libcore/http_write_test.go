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
