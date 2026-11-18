package libcore

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptrace"
	"sync/atomic"
	"time"
)

// urlTest is a profile's latency as the app shows it: a GET of link through client twice on one connection, and the
// second one's time from its request sent to the answer's first byte; the first opens the connection (the proxy's
// handshake, TLS) and is not counted. A redirect is an answer: it is not followed. timeout bounds both, in ms.
func urlTest(client *http.Client, link string, timeout int32) (int32, error) {
	if client == nil {
		return 0, errors.New("url test: no client")
	}
	defer client.CloseIdleConnections()
	c := *client
	c.CheckRedirect = func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeout)*time.Millisecond)
	defer cancel()
	var latency time.Duration
	for i := 0; i < 2; i++ {
		// the transport calls these from its own goroutines (the write loop, the read loop), and Do can return
		// before the write loop's result is read: atomic, not plain variables. Times since base, on the
		// monotonic clock (a wall clock set meanwhile does not count)
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
		// what is left of a small body, so the second request goes on the same connection
		_, _ = io.Copy(io.Discard, io.LimitReader(response.Body, 64<<10))
		response.Body.Close()
		start, end := sent.Load(), answered.Load()
		if start == 0 || end == 0 {
			return 0, errors.New("url test: no answer timed")
		}
		latency = time.Duration(end - start)
	}
	return int32(latency.Milliseconds()), nil
}
