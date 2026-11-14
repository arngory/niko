package libcore

import (
	"bytes"
	"context"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"libcore/device"
	"libcore/ech"
	"log"
	"net"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"sync"
	"sync/atomic"
	"time"

	"github.com/sagernet/quic-go"
	"github.com/sagernet/quic-go/http3"
	"github.com/sagernet/sing-box/boxapi"
	"github.com/sagernet/sing/common/metadata"
	"github.com/sagernet/sing/protocol/socks"
	"github.com/sagernet/sing/protocol/socks/socks5"
)

var errFailConnectSocks5 = errors.New("fail connect socks5")

type HTTPClient interface {
	RestrictedTLS()
	ModernTLS()
	PinnedTLS12()
	PinnedSHA256(sumHex string)
	TrySocks5(port int32, username string, password string)
	RequireSocks5()
	TryH3Direct()
	// UseInstance sends every request through the default outbound of i, a test instance of a profile: the app's
	// update through a profile while the service does not run
	UseInstance(i *BoxInstance)
	KeepAlive()
	// SetTimeout bounds each request whole, its dial and the reading of its body included; 0 none (the default)
	SetTimeout(milliseconds int32)
	// SetIdleTimeout bounds each wait without progress: for the answer to begin, then between the reads of its body;
	// a slow transfer that keeps moving goes on (a download through a DNS tunnel); 0 none (the default)
	SetIdleTimeout(milliseconds int32)
	NewRequest() HTTPRequest
	Close()
}

type HTTPRequest interface {
	SetURL(link string) error
	SetMethod(method string)
	SetHeader(key string, value string)
	SetContent(content []byte)
	SetContentString(content string)
	SetUserAgent(userAgent string)
	AllowInsecure()
	Execute() (HTTPResponse, error)
}

type HTTPResponse interface {
	GetHeader(string) *StringBox
	GetContent() ([]byte, error)
	GetContentString() (*StringBox, error)
	WriteTo(path string) error
}

var (
	_ HTTPClient   = (*httpClient)(nil)
	_ HTTPRequest  = (*httpRequest)(nil)
	_ HTTPResponse = (*httpResponse)(nil)
)

type httpClient struct {
	tls           tls.Config
	h1h2Transport http.Transport
	h1h2Client    http.Client
	trySocks5     bool
	tryH3Direct   bool
	// requireSocks5: the service runs in proxy mode, a request never goes direct (it would show the device's
	// address); a port that does not answer is a core restarting, waited for a few seconds
	requireSocks5 bool
	idleTimeout   time.Duration
}

func NewHttpClient() HTTPClient {
	// its chains against the roots of the setting (Go's own, which applyRootStore keeps to the list)
	applyRootStore()
	client := new(httpClient)
	client.h1h2Client.Transport = &client.h1h2Transport
	client.h1h2Transport.TLSClientConfig = &client.tls
	client.h1h2Transport.DisableKeepAlives = true
	return client
}

func (c *httpClient) ModernTLS() {
	c.tls.MinVersion = tls.VersionTLS12
	// c.tls.CipherSuites = nekoutils.Map(tls.CipherSuites(), func(it *tls.CipherSuite) uint16 { return it.ID })
}

func (c *httpClient) RestrictedTLS() {
	c.tls.MinVersion = tls.VersionTLS13
	// c.tls.CipherSuites = nekoutils.Map(nekoutils.Filter(tls.CipherSuites(), func(it *tls.CipherSuite) bool {
	// 	return nekoutils.Contains(it.SupportedVersions, uint16(tls.VersionTLS13))
	// }), func(it *tls.CipherSuite) uint16 {
	// 	return it.ID
	// })
}

func (c *httpClient) PinnedTLS12() {
	c.tls.MinVersion = tls.VersionTLS12
	c.tls.MaxVersion = tls.VersionTLS12
}

func (c *httpClient) PinnedSHA256(sumHex string) {
	c.tls.VerifyPeerCertificate = func(rawCerts [][]byte, verifiedChains [][]*x509.Certificate) error {
		for _, rawCert := range rawCerts {
			certSum := sha256.Sum256(rawCert)
			if sumHex == hex.EncodeToString(certSum[:]) {
				return nil
			}
		}
		return errors.New("pinned sha256 sum mismatch")
	}
}

func (c *httpClient) TrySocks5(port int32, username string, password string) {
	dialer := new(net.Dialer)
	c.h1h2Transport.DialContext = func(ctx context.Context, network, addr string) (net.Conn, error) {
		socksConn, err := dialer.DialContext(ctx, "tcp", "127.0.0.1:"+strconv.Itoa(int(port)))
		for wait := 0; err != nil && c.requireSocks5 && wait < 10 && ctx.Err() == nil; wait++ {
			select {
			case <-ctx.Done():
			case <-time.After(500 * time.Millisecond):
				socksConn, err = dialer.DialContext(ctx, "tcp", "127.0.0.1:"+strconv.Itoa(int(port)))
			}
		}
		if err != nil {
			if c.requireSocks5 {
				return nil, fmt.Errorf("the local proxy does not answer: %w", err)
			}
			if c.tryH3Direct {
				return nil, errFailConnectSocks5
			}
			// the service is not running: nothing to go through, the request goes as any app's would
			return dialer.DialContext(ctx, network, addr)
		}
		_, err = socks.ClientHandshake5(socksConn, socks5.CommandConnect, metadata.ParseSocksaddr(addr), username, password)
		if err != nil {
			socksConn.Close()
			// the service runs but its proxy failed: going direct instead (h1/h2 or the H3 fallback, which
			// errFailConnectSocks5 starts) would show the device's own address where the user chose the proxy
			return nil, fmt.Errorf("through the local proxy: %w", err)
		}
		return socksConn, nil
	}
	c.trySocks5 = true
}

// RequireSocks5 keeps every request on the SOCKS port of TrySocks5: the service runs in proxy mode, and a port
// that does not answer is waited for (a core restarting), never gone around.
func (c *httpClient) RequireSocks5() {
	c.requireSocks5 = true
}

func (c *httpClient) TryH3Direct() {
	c.tryH3Direct = true
}

func (c *httpClient) UseInstance(i *BoxInstance) {
	c.h1h2Transport.DialContext = func(ctx context.Context, network, addr string) (net.Conn, error) {
		if i == nil || i.Box == nil {
			return nil, errors.New("no instance")
		}
		return boxapi.DialContext(ctx, i.Box, nil, network, addr)
	}
	// through the instance alone: neither the local SOCKS port nor a direct HTTP/3
	c.trySocks5 = false
	c.requireSocks5 = false
	c.tryH3Direct = false
}

func (c *httpClient) KeepAlive() {
	c.h1h2Transport.ForceAttemptHTTP2 = true
	c.h1h2Transport.DisableKeepAlives = false
}

func (c *httpClient) SetTimeout(milliseconds int32) {
	c.h1h2Client.Timeout = time.Duration(max(milliseconds, 0)) * time.Millisecond
}

func (c *httpClient) SetIdleTimeout(milliseconds int32) {
	c.idleTimeout = time.Duration(max(milliseconds, 0)) * time.Millisecond
}

// idleBody is a response body whose request is cancelled when no read brings anything for its idle bound
type idleBody struct {
	io.ReadCloser
	timer  *time.Timer
	idle   time.Duration
	cancel context.CancelFunc
}

func (b *idleBody) Read(p []byte) (int, error) {
	n, err := b.ReadCloser.Read(p)
	if n > 0 {
		b.timer.Reset(b.idle)
	}
	return n, err
}

func (b *idleBody) Close() error {
	b.timer.Stop()
	b.cancel()
	return b.ReadCloser.Close()
}

func (c *httpClient) NewRequest() HTTPRequest {
	req := &httpRequest{httpClient: c}
	req.request = http.Request{
		Method: "GET",
		Header: http.Header{},
	}
	return req
}

func (c *httpClient) Close() {
	c.h1h2Transport.CloseIdleConnections()
}

type httpRequest struct {
	*httpClient
	request http.Request
}

func (r *httpRequest) AllowInsecure() {
	r.tls.InsecureSkipVerify = true
}

func (r *httpRequest) SetURL(link string) (err error) {
	r.request.URL, err = url.Parse(link)
	if err != nil {
		return
	}
	if r.request.URL.User != nil {
		user := r.request.URL.User.Username()
		password, _ := r.request.URL.User.Password()
		r.request.SetBasicAuth(user, password)
	}
	return
}

func (r *httpRequest) SetMethod(method string) {
	r.request.Method = method
}

func (r *httpRequest) SetHeader(key string, value string) {
	r.request.Header.Set(key, value)
}

func (r *httpRequest) SetUserAgent(userAgent string) {
	r.request.Header.Set("User-Agent", userAgent)
}

func (r *httpRequest) SetContent(content []byte) {
	buffer := bytes.Buffer{}
	buffer.Write(content)
	r.request.Body = io.NopCloser(bytes.NewReader(buffer.Bytes()))
	r.request.ContentLength = int64(len(content))
}

func (r *httpRequest) SetContentString(content string) {
	r.SetContent([]byte(content))
}

func (r *httpRequest) Execute() (HTTPResponse, error) {
	defer device.DeferPanicToError("http execute", func(err error) { log.Println(err) })
	// full direct
	if r.tryH3Direct && !r.trySocks5 {
		return r.doH3Direct()
	}
	request := &r.request
	var idle *idleBody
	if r.idleTimeout > 0 {
		// the bound runs from the start: the dial and the wait for the answer count as waits without progress
		ctx, cancel := context.WithCancel(r.request.Context())
		idle = &idleBody{timer: time.AfterFunc(r.idleTimeout, cancel), idle: r.idleTimeout, cancel: cancel}
		request = r.request.WithContext(ctx)
	}
	response, err := r.h1h2Client.Do(request)
	if err != nil {
		if idle != nil {
			idle.timer.Stop()
			idle.cancel()
		}
		// trySocks5 && tryH3Direct
		if r.tryH3Direct && errors.Is(err, errFailConnectSocks5) {
			return r.doH3Direct()
		}
		return nil, err
	}
	if idle != nil {
		idle.ReadCloser = response.Body
		idle.timer.Reset(r.idleTimeout)
		response.Body = idle
	}
	httpResp := &httpResponse{Response: response}
	if response.StatusCode != http.StatusOK {
		return nil, errors.New(httpResp.errorString())
	}
	return httpResp, nil
}

type requestFunc func() (response *http.Response, err error)

func (r *httpRequest) doH3Direct() (HTTPResponse, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	// the requests themselves, their bodies included, end with the client's timeout (2 min unless set): the losers
	// and a body that goes quiet hold no goroutine and no socket for good (neko)
	overall := r.h1h2Client.Timeout
	if overall <= 0 {
		overall = 2 * time.Minute
	}
	requestCtx, requestCancel := context.WithTimeout(context.Background(), overall)
	won := false
	defer func() {
		if !won {
			requestCancel()
		}
	}()

	successCh := make(chan *http.Response, 1)
	var finalErr error
	var failedCount atomic.Uint32
	var successCount atomic.Uint32
	var mu sync.Mutex

	funcs := []requestFunc{
		// Http(s) With Ech
		func() (response *http.Response, err error) {
			request := r.request.Clone(requestCtx)
			echClient := &http.Client{
				Transport: &http.Transport{
					DialTLSContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
						var d net.Dialer
						c, err := d.DialContext(ctx, network, addr)
						if err != nil {
							return c, err
						}
						domain := addr
						if host, _, _ := net.SplitHostPort(addr); host != "" {
							domain = host
						}
						echTls := ech.NewECHClientConfig(domain, &r.tls, gLocalDNSTransport)
						return echTls.Client(ctx, c)
					},
					DisableKeepAlives: true,
				},
			}
			return echClient.Do(request)
		},
		// H3 HTTPS
		func() (response *http.Response, err error) {
			request := r.request.Clone(requestCtx)
			h3Client := &http.Client{
				Transport: &http3.Transport{
					TLSClientConfig: r.tls.Clone(),
					QUICConfig: &quic.Config{
						MaxIdleTimeout: time.Second,
					},
				},
			}
			return h3Client.Do(request)
		},
	}

	if r.request.URL.Scheme == "http" {
		funcs = funcs[:1]
	}

	for i, f := range funcs {
		go func(f requestFunc) {
			defer device.DeferPanicToError("http", func(err error) { log.Println(err) })
			defer func() {
				if successCount.Load() == 0 {
					if failedCount.Add(1) >= uint32(len(funcs)) {
						// 全部失败了
						cancel()
					}
				}
			}()

			var t string
			switch i {
			case 0:
				t = "http(s)"
			case 1:
				t = "h3"
			}

			// 执行HTTP请求
			rsp, err := f()
			if rsp == nil || err != nil {
				mu.Lock()
				finalErr = errors.Join(finalErr, fmt.Errorf("%s: %w", t, err))
				mu.Unlock()
				if rsp != nil && rsp.Body != nil {
					rsp.Body.Close()
				}
				return
			}

			// 处理 HTTP 状态码
			if rsp.StatusCode != http.StatusOK {
				hr := &httpResponse{Response: rsp}
				err = fmt.Errorf("%s: %s", t, hr.errorString())
				mu.Lock()
				finalErr = errors.Join(finalErr, err)
				mu.Unlock()
				return
			}

			select {
			case successCh <- rsp:
				// 第一个成功的请求，不要关闭 body
				successCount.Add(1)
			default:
				rsp.Body.Close()
			}
		}(f)
	}

	select {
	case result := <-successCh:
		won = true
		// the winner's body ends with requestCtx; the context is let go with it
		result.Body = &cancelBody{ReadCloser: result.Body, cancel: requestCancel}
		return &httpResponse{Response: result}, nil
	case <-ctx.Done():
		mu.Lock()
		defer mu.Unlock()
		return nil, finalErr
	}
}

// cancelBody releases its request's context when the body is closed (neko).
type cancelBody struct {
	io.ReadCloser
	cancel context.CancelFunc
}

func (b *cancelBody) Close() error {
	err := b.ReadCloser.Close()
	b.cancel()
	return err
}

type httpResponse struct {
	*http.Response

	getContentOnce sync.Once
	content        []byte
	contentError   error
}

func (h *httpResponse) errorString() string {
	// an error's body: its first bytes are all it shows
	head, err := io.ReadAll(io.LimitReader(h.Body, 4096))
	h.Body.Close()
	if err != nil {
		return fmt.Sprint("HTTP ", h.Status)
	}
	content := string(head)
	if len(content) > 100 {
		content = content[:100] + " ..."
	}
	return fmt.Sprint("HTTP ", h.Status, ": ", content)
}

func (h *httpResponse) GetHeader(key string) *StringBox {
	return wrapString(h.Header.Get(key))
}

// maxContent bounds a body read whole into memory (subscriptions, the update's manifest): a server or a captive
// portal sending without end would take the process down (neko)
const maxContent = 16 << 20

var errContentTooLarge = errors.New("the answer is larger than 16 MiB")

func (h *httpResponse) GetContent() ([]byte, error) {
	h.getContentOnce.Do(func() {
		defer h.Body.Close()
		h.content, h.contentError = io.ReadAll(io.LimitReader(h.Body, maxContent+1))
		if h.contentError == nil && len(h.content) > maxContent {
			h.content, h.contentError = nil, errContentTooLarge
		}
	})
	return h.content, h.contentError
}

func (h *httpResponse) GetContentString() (*StringBox, error) {
	content, err := h.getContentString()
	if err != nil {
		return nil, err
	}
	return wrapString(content), nil
}

func (h *httpResponse) getContentString() (string, error) {
	content, err := h.GetContent()
	if err != nil {
		return "", err
	}
	return string(content), nil
}

// maxWriteSize bounds what WriteTo saves: a body that never ends (a broken mirror, a captive portal on a list's URL)
// fills no more of the storage than this, and the file is removed. The largest legitimate download (the route
// assets) is a few tens of MB.
var maxWriteSize int64 = 256 << 20

func (h *httpResponse) WriteTo(path string) error {
	defer h.Body.Close()
	file, err := os.Create(path)
	if err != nil {
		return err
	}
	n, err := io.Copy(file, io.LimitReader(h.Body, maxWriteSize+1))
	if closeErr := file.Close(); err == nil {
		err = closeErr
	}
	if err == nil && n > maxWriteSize {
		err = fmt.Errorf("response larger than %d bytes", maxWriteSize)
	}
	if err != nil {
		os.Remove(path)
	}
	return err
}
