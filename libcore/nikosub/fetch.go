package nikosub

import (
	"context"
	"crypto/tls"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"runtime"
	"strings"
	"time"

	"libcore/nikofmt"
)

// The fetch of a subscription, as RawUpdater.request makes it through libcore's HTTP client (libcore/http.go): a GET
// with the subscription's User-Agent or the app's, at most 120 s in all and 30 s without a byte, a 200 alone taken
// (an error names the status, a panel's refusal headers and the body's first 100 bytes), the body at most 16 MiB,
// the URL's user info as basic authentication. The app's local proxy and HTTP/3 (trySocks5, tryH3Direct) are the
// phone's: the daemon's requests go out as its own.

const (
	// MaxBody is libcore's maxContent: a body read whole into memory
	MaxBody = 16 << 20
	// FetchTimeout and IdleTimeout are the update's: a panel that answers and then goes quiet holds no update
	FetchTimeout = 120 * time.Second
	IdleTimeout  = 30 * time.Second
)

// UserAgent is the app's (ktx/Nets.kt USER_AGENT) for a Niko version: what providers know the app by, and what makes
// them answer with a ClashMeta config.
func UserAgent(version string) string {
	return "NekoBox/Android/" + version + " (Niko; Prefer ClashMeta Format)"
}

// Client fetches subscriptions.
type Client struct {
	// Transport is the HTTP transport; nil: one of its own, direct, TLS as Allow* say
	Transport http.RoundTripper
	// UserAgent is the default, where a subscription has no custom one
	UserAgent string
	// AllowInsecure is DataStore.allowInsecureOnRequest: certificates not checked
	AllowInsecure bool
	// TLS13 is appTLSVersion "1.3": restrictedTLS
	TLS13 bool
	// InstallSecret is DataStore.subscriptionHwid, the secret the device ids are of; "" sends none
	InstallSecret string
	// Timeout and Idle: zero for FetchTimeout and IdleTimeout
	Timeout, Idle time.Duration
}

// Response is a subscription's answer: its headers and its body as text.
type Response struct {
	Header http.Header
	Body   string
}

// header is GetHeader: the first value, "" for none.
func (r *Response) header(name string) string { return r.Header.Get(name) }

// panelRefusals are libcore's: the headers a panel refusing the device answers with, named in the error.
var panelRefusals = []string{"x-hwid-not-supported", "x-hwid-max-devices-reached", "x-hwid-limit"}

func (c *Client) transport() http.RoundTripper {
	if c.Transport != nil {
		return c.Transport
	}
	config := &tls.Config{InsecureSkipVerify: c.AllowInsecure}
	if c.TLS13 {
		config.MinVersion = tls.VersionTLS13
	}
	return &http.Transport{
		Proxy:             nil,
		DialContext:       (&net.Dialer{Timeout: 30 * time.Second}).DialContext,
		TLSClientConfig:   config,
		DisableKeepAlives: true,
	}
}

// Fetch is RawUpdater.request: one GET of url for the subscription.
func (c *Client) Fetch(ctx context.Context, sub *Subscription, address string) (*Response, error) {
	timeout, idle := c.Timeout, c.Idle
	if timeout == 0 {
		timeout = FetchTimeout
	}
	if idle == 0 {
		idle = IdleTimeout
	}
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	// the idle bound runs from the start: the dial and the wait for the answer count as waits without progress
	ctx, stop := context.WithCancel(ctx)
	defer stop()
	timer := time.AfterFunc(idle, stop)
	defer timer.Stop()
	u, err := url.Parse(address)
	if err != nil {
		return nil, err
	}
	if u.Scheme != "http" && u.Scheme != "https" {
		return nil, errors.New("a subscription's address is http or https")
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, address, nil)
	if err != nil {
		return nil, err
	}
	if u.User != nil {
		password, _ := u.User.Password()
		request.SetBasicAuth(u.User.Username(), password)
	}
	agent := c.UserAgent
	if !nikofmt.KtIsBlank(sub.CustomUserAgent) {
		agent = sub.CustomUserAgent
	}
	request.Header.Set("User-Agent", agent)
	if sub.SendHwid && c.InstallSecret != "" {
		// panels that limit devices (Remnawave, Marzban) identify them by these headers; the id is of the host the
		// panel first knew (hwidHost), so a move or the spare address is still the same device
		host := sub.HwidHost
		if nikofmt.KtIsBlank(host) {
			host = sub.Link
		}
		request.Header.Set("x-hwid", DeviceID(c.InstallSecret, host))
		request.Header.Set("x-device-os", deviceOS())
		request.Header.Set("x-device-model", "Niko desktop")
	}
	response, err := (&http.Client{Transport: c.transport()}).Do(request)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return nil, errors.New(statusError(response))
	}
	var body strings.Builder
	buffer := make([]byte, 32<<10)
	total := 0
	for {
		n, err := response.Body.Read(buffer)
		if n > 0 {
			timer.Reset(idle)
			total += n
			if total > MaxBody {
				return nil, errors.New("the answer is larger than 16 MiB")
			}
			body.Write(buffer[:n])
		}
		if errors.Is(err, io.EOF) {
			break
		}
		if err != nil {
			return nil, err
		}
	}
	return &Response{Header: response.Header, Body: nikofmt.DecodeUTF8([]byte(body.String()))}, nil
}

// statusError is libcore's errorString: the status, each refusal header set to true in brackets, the body's first
// bytes.
func statusError(response *http.Response) string {
	status := "HTTP " + response.Status
	for _, name := range panelRefusals {
		if strings.EqualFold(strings.TrimSpace(response.Header.Get(name)), "true") {
			status += " [" + name + "]"
		}
	}
	head, err := io.ReadAll(io.LimitReader(response.Body, 4096))
	if err != nil {
		return status
	}
	content := string(head)
	if len(content) > 100 {
		content = content[:100] + " ..."
	}
	return fmt.Sprint(status, ": ", content)
}

func deviceOS() string {
	switch runtime.GOOS {
	case "windows":
		return "Windows"
	case "darwin":
		return "macOS"
	}
	return "Linux"
}
