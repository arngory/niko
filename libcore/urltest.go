package libcore

import (
	"context"
	"net/http"
	"time"

	"libcore/urltest"
)

// urlTest is a profile's latency as the app shows it: delegates to libcore/urltest.Measure.
func urlTest(client *http.Client, link string, timeout int32) (int32, error) {
	return urltest.Measure(context.Background(), client, link, time.Duration(timeout)*time.Millisecond)
}
