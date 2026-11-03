// Package rayapi talks to the remote API of a raycontrol node
// (arngory/raycontrol docs/spec/api-v1.md): SPA v2 opens the port, then
// ConnectRPC unary calls in JSON over HTTP/2, TLS 1.3 with X25519MLKEM768
// only, the node's certificate checked by its pin and the device's client
// certificate on every connection.
package rayapi

import (
	"bytes"
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net"
	"net/http"
	"net/http/httptrace"
	"strconv"
	"strings"
	"sync/atomic"
	"syscall"
	"time"

	"libcore/knock"
)

const (
	registerLabel = "raycontrol-register-v1"
	rotateLabel   = "raycontrol-rotate-v1"
	maxBody       = 8 << 20

	DeviceService = "raycontrol.device.v1.DeviceService/"
	NodeService   = "raycontrol.node.v1.NodeService/"
)

// Identity is the device's client certificate: the node knows the device by
// its key (Pin).
type Identity struct {
	Cert string `json:"cert"` // base64 DER
	Key  string `json:"key"`  // base64 PKCS #8
	Pin  string `json:"pin"`  // base64url SHA-256 of the SubjectPublicKeyInfo
}

// Endpoint is the API of one node as a device reaches it.
type Endpoint struct {
	Host     string             `json:"host"`
	Port     int                `json:"port"` // api.port
	Pin      string             `json:"pin"`  // api.pin
	Identity Identity           `json:"identity"`
	SPA      knock.SPAv2Options `json:"spa"` // device_key set
	// a socket control that keeps the sockets out of the app's own VPN (nil in tests)
	Control func(network, address string, conn syscall.RawConn) error `json:"-"`
	Timeout time.Duration                                             `json:"-"`
	// the TLS handshake on its own, at most Timeout (0: 10 s)
	HandshakeTimeout time.Duration `json:"-"`
	// no SPA (tests)
	NoSPA bool `json:"-"`
}

// Error is a ConnectRPC error of the node: Code is not_found,
// unauthenticated, permission_denied, invalid_argument, ...
type Error struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

func (e *Error) Error() string {
	if e.Message == "" {
		return "raycontrol api: " + e.Code
	}
	return "raycontrol api: " + e.Code + ": " + e.Message
}

// ErrNoAnswer: the API port stayed closed after SPA over UDP and TCP.
var ErrNoAnswer = errors.New("raycontrol api: the node did not answer")

// ErrAnswerLost: the request went out but no answer came. The call may have
// taken effect on the node (an invite used, keys switched), so it is not sent
// again.
var ErrAnswerLost = errors.New("raycontrol api: the request was sent but its answer was lost; it may have taken effect")

// connectError is a failure before the request was written (TCP, the TLS
// handshake, a timeout meanwhile). Only these are tried again after another
// SPA packet.
type connectError struct{ err error }

func (e *connectError) Error() string { return e.err.Error() }
func (e *connectError) Unwrap() error { return e.err }

var errPin = errors.New("raycontrol api: the server key does not match the pin")

// NewIdentity makes a client key (ECDSA P-256) and a self-signed certificate
// for it; the node checks the key only, not names or dates (api-v1.md §1).
func NewIdentity() (Identity, error) {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return Identity{}, err
	}
	serial, err := rand.Int(rand.Reader, new(big.Int).Lsh(big.NewInt(1), 127))
	if err != nil {
		return Identity{}, err
	}
	now := time.Now()
	template := &x509.Certificate{
		SerialNumber: serial,
		Subject:      pkix.Name{CommonName: "raycontrol device"},
		NotBefore:    now.Add(-time.Hour),
		NotAfter:     now.AddDate(10, 0, 0),
		KeyUsage:     x509.KeyUsageDigitalSignature,
		ExtKeyUsage:  []x509.ExtKeyUsage{x509.ExtKeyUsageClientAuth},
	}
	der, err := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	if err != nil {
		return Identity{}, err
	}
	pkcs8, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		return Identity{}, err
	}
	cert, err := x509.ParseCertificate(der)
	if err != nil {
		return Identity{}, err
	}
	return Identity{
		Cert: base64.StdEncoding.EncodeToString(der),
		Key:  base64.StdEncoding.EncodeToString(pkcs8),
		Pin:  certPin(cert),
	}, nil
}

func certPin(cert *x509.Certificate) string {
	sum := sha256.Sum256(cert.RawSubjectPublicKeyInfo)
	return base64.RawURLEncoding.EncodeToString(sum[:])
}

func (id Identity) tlsCertificate() (tls.Certificate, *x509.Certificate, error) {
	der, err := base64.StdEncoding.DecodeString(id.Cert)
	if err != nil {
		return tls.Certificate{}, nil, errors.New("raycontrol api: bad client certificate")
	}
	pkcs8, err := base64.StdEncoding.DecodeString(id.Key)
	if err != nil {
		return tls.Certificate{}, nil, errors.New("raycontrol api: bad client key")
	}
	key, err := x509.ParsePKCS8PrivateKey(pkcs8)
	if err != nil {
		return tls.Certificate{}, nil, errors.New("raycontrol api: bad client key")
	}
	cert, err := x509.ParseCertificate(der)
	if err != nil {
		return tls.Certificate{}, nil, errors.New("raycontrol api: bad client certificate")
	}
	return tls.Certificate{Certificate: [][]byte{der}, PrivateKey: key, Leaf: cert}, cert, nil
}

func (e *Endpoint) dialer() *net.Dialer {
	return &net.Dialer{Timeout: 10 * time.Second, Control: e.Control}
}

// client makes the HTTP client of one Call; callCtx ends with the Call, and
// with it any dial still under way (net/http keeps dialing for the pool after
// a request gave up).
func (e *Endpoint) client(callCtx context.Context) (*http.Client, error) {
	cert, _, err := e.Identity.tlsCertificate()
	if err != nil {
		return nil, err
	}
	pin := e.Pin
	config := &tls.Config{
		MinVersion:       tls.VersionTLS13,
		CurvePreferences: []tls.CurveID{tls.X25519MLKEM768},
		NextProtos:       []string{"h2"},
		Certificates:     []tls.Certificate{cert},
		// the node's certificate is self-signed: its key pin replaces the chain check
		InsecureSkipVerify: true,
		VerifyPeerCertificate: func(raw [][]byte, _ [][]*x509.Certificate) error {
			if len(raw) == 0 {
				return errPin
			}
			c, err := x509.ParseCertificate(raw[0])
			if err != nil {
				return err
			}
			if !hmac.Equal([]byte(certPin(c)), []byte(pin)) {
				return errPin
			}
			return nil
		},
	}
	timeout := e.Timeout
	if timeout == 0 {
		timeout = 20 * time.Second
	}
	handshakeTimeout := e.HandshakeTimeout
	if handshakeTimeout == 0 {
		handshakeTimeout = 10 * time.Second
	}
	handshakeTimeout = min(handshakeTimeout, timeout)
	dialer := e.dialer()
	return &http.Client{
		Timeout: timeout,
		Transport: &http.Transport{
			// the connection is made here, so that a failure before the request is told apart (connectError)
			DialTLSContext: func(_ context.Context, network, address string) (net.Conn, error) {
				// the transport's context outlives the request; the Call's does not
				raw, err := dialer.DialContext(callCtx, network, address)
				if err != nil {
					return nil, &connectError{err}
				}
				conn := tls.Client(raw, config)
				// a handshake that hangs (a middlebox) fails here, as a failure to connect
				handshakeCtx, cancel := context.WithTimeout(callCtx, handshakeTimeout)
				err = conn.HandshakeContext(handshakeCtx)
				cancel()
				if err != nil {
					raw.Close()
					if errors.Is(err, errPin) {
						return nil, errPin
					}
					return nil, &connectError{err}
				}
				return conn, nil
			},
			ForceAttemptHTTP2: true,
			// a connection that completes after its request gave up still goes to the pool: do not keep it
			IdleConnTimeout: 5 * time.Second,
			Proxy:           nil,
		},
	}, nil
}

// Call makes one unary call (method: "<package>.<Service>/<Method>") with a
// JSON body (protobuf JSON mapping) and returns the JSON answer. SPA goes
// over UDP, once more, then over TCP while no connection can be made; once
// the request went out it is never sent again (ErrAnswerLost), since calls
// such as Register or RotateService must not run twice.
func Call(ctx context.Context, e Endpoint, method string, body []byte) ([]byte, error) {
	callCtx, cancel := context.WithCancel(ctx)
	defer cancel()
	client, err := e.client(callCtx)
	if err != nil {
		return nil, err
	}
	defer client.CloseIdleConnections()
	if e.NoSPA {
		return e.post(ctx, client, method, body)
	}
	var lastErr error
	for _, tcp := range []bool{false, false, true} {
		if err = knock.SendSPAv2(ctx, e.dialer(), e.Host, e.SPA, tcp, time.Now()); err != nil {
			return nil, err
		}
		answer, err := e.post(ctx, client, method, body)
		var connErr *connectError
		if err == nil || !errors.As(err, &connErr) {
			return answer, err
		}
		lastErr = err
	}
	return nil, fmt.Errorf("%w: %v", ErrNoAnswer, lastErr)
}

func (e *Endpoint) post(ctx context.Context, client *http.Client, method string, body []byte) ([]byte, error) {
	if len(body) == 0 {
		body = []byte("{}")
	}
	url := "https://" + net.JoinHostPort(e.Host, strconv.Itoa(e.Port)) + "/" + strings.TrimPrefix(method, "/")
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("Connect-Protocol-Version", "1")
	// what decides a resend is whether the request went out, not the kind of error
	var wrote atomic.Bool
	request = request.WithContext(httptrace.WithClientTrace(request.Context(), &httptrace.ClientTrace{
		WroteHeaders: func() { wrote.Store(true) },
	}))
	response, err := client.Do(request)
	if err != nil {
		switch {
		case errors.Is(err, errPin):
			return nil, errPin
		case !wrote.Load():
			// nothing reached the node: a dial, a handshake or a timeout before the request
			var connErr *connectError
			if errors.As(err, &connErr) {
				return nil, err
			}
			return nil, &connectError{err}
		default:
			// the request may have reached the node
			return nil, fmt.Errorf("%w: %v", ErrAnswerLost, err)
		}
	}
	defer response.Body.Close()
	answer, err := io.ReadAll(io.LimitReader(response.Body, maxBody))
	if err != nil {
		return nil, fmt.Errorf("%w: %v", ErrAnswerLost, err)
	}
	if response.StatusCode != http.StatusOK {
		apiErr := &Error{}
		if json.Unmarshal(answer, apiErr) != nil || apiErr.Code == "" {
			apiErr = &Error{Code: "http_" + strconv.Itoa(response.StatusCode)}
		}
		return nil, apiErr
	}
	return answer, nil
}

// Invite is what Register needs of an rcinv1 package (api-v1.md §3).
type Invite struct {
	Host     string             `json:"host"`
	SPA      knock.SPAv2Options `json:"spa"` // device_key: the invite's one-time key
	InviteID string             `json:"device_id"`
	API      struct {
		Port int    `json:"port"`
		Pin  string `json:"pin"`
	} `json:"api"`
}

// Registered is the outcome of Register: the device's own rcdev1 package and
// its client certificate.
type Registered struct {
	Bundle   string   `json:"bundle"`
	DeviceID string   `json:"device_id"`
	Role     string   `json:"role"`
	Identity Identity `json:"identity"`
}

// RegisterMAC = HMAC-SHA256(invite key, label || BE64(minute) || pin) (api-v1.md §4).
func RegisterMAC(inviteKey []byte, minute int64, pin string) []byte {
	mac := hmac.New(sha256.New, inviteKey)
	mac.Write([]byte(registerLabel))
	var b [8]byte
	binary.BigEndian.PutUint64(b[:], uint64(minute))
	mac.Write(b[:])
	mac.Write([]byte(pin))
	return mac.Sum(nil)
}

// Register turns an invite into a device with a new client certificate
// (api-v1.md §4). The node refuses anything wrong with not_found.
func Register(ctx context.Context, invite Invite, control func(network, address string, conn syscall.RawConn) error) (*Registered, error) {
	inviteKey, err := hex.DecodeString(invite.SPA.DeviceKey)
	if err != nil || len(inviteKey) != 32 {
		return nil, errors.New("raycontrol api: bad invite key")
	}
	identity, err := NewIdentity()
	if err != nil {
		return nil, err
	}
	endpoint := Endpoint{Host: invite.Host, Port: invite.API.Port, Pin: invite.API.Pin, Identity: identity, SPA: invite.SPA, Control: control}
	return register(ctx, endpoint, invite.InviteID, inviteKey, time.Now())
}

func register(ctx context.Context, e Endpoint, inviteID string, inviteKey []byte, now time.Time) (*Registered, error) {
	minute := now.Unix() / 60
	body, _ := json.Marshal(map[string]any{
		"inviteId": inviteID,
		"minute":   strconv.FormatInt(minute, 10),
		"mac":      RegisterMAC(inviteKey, minute, e.Identity.Pin),
	})
	answer, err := Call(ctx, e, DeviceService+"Register", body)
	if err != nil {
		return nil, err
	}
	var r struct {
		Bundle   string `json:"bundle"`
		DeviceID string `json:"deviceId"`
		Role     string `json:"role"`
	}
	if err = json.Unmarshal(answer, &r); err != nil || !strings.HasPrefix(r.Bundle, "rcdev1:") {
		return nil, errors.New("raycontrol api: Register answered no device package")
	}
	return &Registered{Bundle: r.Bundle, DeviceID: r.DeviceID, Role: r.Role, Identity: e.Identity}, nil
}

// RotateCertificate moves the device to the client key of next over a
// connection with the current one (api-v1.md §6). The caller keeps next
// before the call: once the node answers, the old key no longer works.
func RotateCertificate(ctx context.Context, e Endpoint, next Identity) error {
	_, old, err := e.Identity.tlsCertificate()
	if err != nil {
		return err
	}
	nextCert, _, err := next.tlsCertificate()
	if err != nil {
		return err
	}
	if next.Pin != certPin(nextCert.Leaf) {
		return errors.New("raycontrol api: the new identity's pin does not match its certificate")
	}
	digest := sha256.Sum256([]byte(rotateLabel + certPin(old) + next.Pin))
	key, ok := nextCert.PrivateKey.(*ecdsa.PrivateKey)
	if !ok {
		return errors.New("raycontrol api: the new client key is not ECDSA")
	}
	signature, err := ecdsa.SignASN1(rand.Reader, key, digest[:])
	if err != nil {
		return err
	}
	body, _ := json.Marshal(map[string]any{"certificate": nextCert.Certificate[0], "signature": signature})
	_, err = Call(ctx, e, DeviceService+"RotateCertificate", body)
	return err
}
