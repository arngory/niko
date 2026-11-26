// Package urltest measures HTTP latency through a proxy or sing-box outbound.
package urltest

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptrace"
	"sync/atomic"
	"time"
)

// DefaultURL is the lightweight 204 endpoint used when no custom test URL is configured.
const DefaultURL = "http://cp.cloudflare.com/"

// Measure sends two GET requests to link over client on a single keep-alive connection and returns
// the second request's time-to-first-byte in milliseconds (excluding the initial proxy/TLS handshake).
// Redirects count as an answer and are not followed.
func Measure(ctx context.Context, client *http.Client, link string, timeout time.Duration) (int32, error) {
	if client == nil {
		return 0, errors.New("url test: no client")
	}
	if link == "" {
		link = DefaultURL
	}
	if timeout <= 0 {
		timeout = 5 * time.Second
	}
	defer client.CloseIdleConnections()
	c := *client
	c.CheckRedirect = func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	var latency time.Duration
	for i := 0; i < 2; i++ {
		base := time.Now()
		var sent, answered atomic.Int64
		trace := &httptrace.ClientTrace{
			WroteHeaders:         func() { sent.Store(int64(time.Since(base)) + 1) },
			GotFirstResponseByte: func() { answered.Store(int64(time.Since(base)) + 1) },
		}
		request, err := http.NewRequestWithContext(httptrace.WithClientTrace(ctx, trace), http.MethodGet, link, nil)
		if err != nil {
			return 0, err
		}
		response, err := c.Do(request)
		if err != nil {
			return 0, err
		}
		_, _ = io.Copy(io.Discard, io.LimitReader(response.Body, 64<<10))
		response.Body.Close()
		start, end := sent.Load(), answered.Load()
		if start == 0 || end == 0 {
			return 0, errors.New("url test: no answer timed")
		}
		latency = time.Duration(end - start)
	}
	ms := int32(latency.Milliseconds())
	if ms < 1 {
		ms = 1
	}
	return ms, nil
}
