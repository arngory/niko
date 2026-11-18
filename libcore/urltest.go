package libcore

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptrace"
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
		var sent, answered time.Time
		trace := &httptrace.ClientTrace{
			WroteHeaders:         func() { sent = time.Now() },
			GotFirstResponseByte: func() { answered = time.Now() },
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
		if sent.IsZero() || answered.IsZero() {
			return 0, errors.New("url test: no answer timed")
		}
		latency = answered.Sub(sent)
	}
	return int32(latency.Milliseconds()), nil
}
