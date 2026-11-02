// Package raysub fetches a raycontrol subscription (arngory/raycontrol
// docs/spec/subscription-v1.md): SPA v2 to open the port, HTTPS with
// X25519MLKEM768 only and a pinned key, both node signatures checked.
package raysub

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/hmac"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"strconv"
	"syscall"
	"time"

	"libcore/knock"

	"github.com/cloudflare/circl/sign/mldsa/mldsa65"
)

const (
	signLabel = "raycontrol-sub-v1"
	authLabel = "raycontrol-sub-v1 auth"
	// spa-v2.md §7: the node keeps time within 30 s
	MaxClockSkew = 30 * time.Second
	maxBody      = 4 << 20
)

type Request struct {
	Host     string             `json:"host"`
	Port     int                `json:"port"`
	Pin      string             `json:"pin"` // base64url SHA-256 of the SubjectPublicKeyInfo
	DeviceID string             `json:"device_id"`
	NodeID   string             `json:"node_id"`
	Ed25519  string             `json:"ed25519"` // hex
	MLDSA65  string             `json:"mldsa65"` // base64url
	SPA      knock.SPAv2Options `json:"spa"`     // device_key set
	LastSeq  uint64             `json:"last_seq"`
	// a socket control that keeps the sockets out of the app's own VPN (nil in tests)
	Control func(network, address string, conn syscall.RawConn) error `json:"-"`
	// overrides for tests
	Now     func() time.Time `json:"-"`
	Timeout time.Duration    `json:"-"`
}

type Next struct {
	Link string `json:"link"`
	From int64  `json:"from"`
}

type Profile struct {
	Svc  uint16 `json:"svc"`
	Name string `json:"name"`
	Link string `json:"link"`
	Next *Next  `json:"next,omitempty"`
}

type Payload struct {
	V        int       `json:"v"`
	NodeID   string    `json:"node_id"`
	Seq      uint64    `json:"seq"`
	Time     int64     `json:"time"`
	Issued   int64     `json:"issued"`
	Expires  int64     `json:"expires"`
	Profiles []Profile `json:"profiles"`
}

type Result struct {
	Payload Payload `json:"payload"`
	// node clock minus ours, seconds; over 30 s SPA v2 fails
	ClockSkew int64 `json:"clock_skew_s"`
	// the TCP carrier was needed to open the port
	SPAOverTCP bool `json:"spa_over_tcp"`
}

type envelope struct {
	Payload string            `json:"payload"`
	Sig     map[string]string `json:"sig"`
}

var ErrNotFound = errors.New("raycontrol subscription: the node does not know this device (revoked, disabled or wrong key)")

// ErrNoAnswer: the port stayed closed after SPA v2 over UDP and TCP. A clock off by more than 30 s is a likely
// cause (the node drops the packet), so the app adds that hint to this error.
var ErrNoAnswer = errors.New("raycontrol subscription: the node did not answer")

type statusError int

func (e statusError) Error() string { return "HTTP " + strconv.Itoa(int(e)) }

// Fetch opens the port with SPA v2 (UDP, then the TCP carrier) and returns the verified payload.
func Fetch(ctx context.Context, r Request) (*Result, error) {
	now := time.Now
	if r.Now != nil {
		now = r.Now
	}
	timeout := r.Timeout
	if timeout == 0 {
		timeout = 15 * time.Second
	}
	deviceKey, err := hex.DecodeString(r.SPA.DeviceKey)
	if err != nil || len(deviceKey) != 32 {
		return nil, errors.New("raycontrol subscription: bad device key")
	}
	client := r.httpClient(timeout)
	// one Fetch per refresh: nothing to keep alive afterwards
	defer client.CloseIdleConnections()
	var lastErr error
	for _, tcp := range []bool{false, false, true} {
		if err = r.knock(ctx, tcp, now()); err != nil {
			return nil, err
		}
		var envelope *envelope
		envelope, lastErr = r.get(ctx, client, deviceKey, now())
		if lastErr == nil {
			result, err := r.verify(envelope, now())
			if result != nil {
				result.SPAOverTCP = tcp
			}
			return result, err
		}
		// the node answered: another knock changes nothing
		if errors.Is(lastErr, ErrNotFound) || errors.Is(lastErr, errPin) {
			return nil, lastErr
		}
		var status statusError
		if errors.As(lastErr, &status) {
			return nil, fmt.Errorf("raycontrol subscription: %w", lastErr)
		}
	}
	return nil, fmt.Errorf("%w: %v", ErrNoAnswer, lastErr)
}

func (r *Request) dialer() *net.Dialer {
	return &net.Dialer{Timeout: 10 * time.Second, Control: r.Control}
}

// knock sends one SPA v2 packet over UDP or TCP and waits for the rules to apply.
func (r *Request) knock(ctx context.Context, tcp bool, now time.Time) error {
	port, packet, err := knock.SPAv2Packet(r.SPA, now)
	if err != nil {
		return err
	}
	address := net.JoinHostPort(r.Host, strconv.Itoa(int(port)))
	network := "udp"
	if tcp {
		network = "tcp"
	}
	dialCtx, cancel := context.WithTimeout(ctx, 3*time.Second)
	conn, err := r.dialer().DialContext(dialCtx, network, address)
	cancel()
	if err == nil {
		conn.SetWriteDeadline(time.Now().Add(3 * time.Second))
		conn.Write(packet)
		conn.Close()
	}
	select {
	case <-time.After(300 * time.Millisecond):
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

var errPin = errors.New("raycontrol subscription: the server key does not match the pin")

func (r *Request) httpClient(timeout time.Duration) *http.Client {
	pin := r.Pin
	tlsConfig := &tls.Config{
		MinVersion:       tls.VersionTLS13,
		CurvePreferences: []tls.CurveID{tls.X25519MLKEM768},
		// the certificate is self-signed; it is checked by its key pin below
		InsecureSkipVerify: true,
		VerifyPeerCertificate: func(raw [][]byte, _ [][]*x509.Certificate) error {
			if len(raw) == 0 {
				return errPin
			}
			cert, err := x509.ParseCertificate(raw[0])
			if err != nil {
				return err
			}
			sum := sha256.Sum256(cert.RawSubjectPublicKeyInfo)
			if !hmac.Equal([]byte(base64.RawURLEncoding.EncodeToString(sum[:])), []byte(pin)) {
				return errPin
			}
			return nil
		},
	}
	if net.ParseIP(r.Host) == nil {
		tlsConfig.ServerName = r.Host
	}
	return &http.Client{
		Timeout: timeout,
		Transport: &http.Transport{
			DialContext:       r.dialer().DialContext,
			TLSClientConfig:   tlsConfig,
			ForceAttemptHTTP2: true,
			Proxy:             nil,
		},
	}
}

func authToken(deviceKey []byte, minute int64) string {
	mac := hmac.New(sha256.New, deviceKey)
	mac.Write([]byte(authLabel))
	var b [8]byte
	binary.BigEndian.PutUint64(b[:], uint64(minute))
	mac.Write(b[:])
	return "RC1 " + strconv.FormatInt(minute, 10) + "." + base64.RawURLEncoding.EncodeToString(mac.Sum(nil))
}

func (r *Request) get(ctx context.Context, client *http.Client, deviceKey []byte, now time.Time) (*envelope, error) {
	url := "https://" + net.JoinHostPort(r.Host, strconv.Itoa(r.Port)) + "/sub/v1"
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	request.Header.Set("X-Raycontrol-Device", r.DeviceID)
	request.Header.Set("Authorization", authToken(deviceKey, now.Unix()/60))
	response, err := client.Do(request)
	if err != nil {
		if errors.Is(err, errPin) {
			return nil, errPin
		}
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode == http.StatusNotFound {
		return nil, ErrNotFound
	}
	if response.StatusCode != http.StatusOK {
		return nil, statusError(response.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(response.Body, maxBody))
	if err != nil {
		return nil, err
	}
	var e envelope
	if err = json.Unmarshal(body, &e); err != nil {
		return nil, errors.New("raycontrol subscription: not an envelope")
	}
	return &e, nil
}

// verify checks the signatures and the payload (subscription-v1.md §5).
func (r *Request) verify(e *envelope, now time.Time) (*Result, error) {
	enc := base64.RawURLEncoding
	body, err := enc.DecodeString(e.Payload)
	if err != nil {
		return nil, errors.New("raycontrol subscription: bad payload encoding")
	}
	message := append([]byte(signLabel), body...)
	edPub, err := hex.DecodeString(r.Ed25519)
	if err != nil || len(edPub) != ed25519.PublicKeySize {
		return nil, errors.New("raycontrol subscription: the node has no valid ed25519 key (import the registration again)")
	}
	edSig, err := enc.DecodeString(e.Sig["ed25519"])
	if err != nil || !ed25519.Verify(edPub, message, edSig) {
		return nil, errors.New("raycontrol subscription: the ed25519 signature does not verify")
	}
	mlRaw, err := enc.DecodeString(r.MLDSA65)
	var mlPub mldsa65.PublicKey
	if err != nil || mlPub.UnmarshalBinary(mlRaw) != nil {
		return nil, errors.New("raycontrol subscription: the node has no valid ML-DSA-65 key (import the registration again)")
	}
	mlSig, err := enc.DecodeString(e.Sig["mldsa65"])
	if err != nil || !mldsa65.Verify(&mlPub, message, nil, mlSig) {
		return nil, errors.New("raycontrol subscription: the ML-DSA-65 signature does not verify")
	}
	var p Payload
	decoder := json.NewDecoder(bytes.NewReader(body))
	if err = decoder.Decode(&p); err != nil {
		return nil, errors.New("raycontrol subscription: bad payload")
	}
	switch {
	case p.V != 1:
		return nil, fmt.Errorf("raycontrol subscription: version %d", p.V)
	case p.NodeID != r.NodeID:
		return nil, errors.New("raycontrol subscription: signed for another node")
	case p.Seq < r.LastSeq:
		return nil, fmt.Errorf("raycontrol subscription: older than the one we have (seq %d < %d)", p.Seq, r.LastSeq)
	case p.Expires <= now.Unix():
		return nil, errors.New("raycontrol subscription: expired")
	}
	return &Result{Payload: p, ClockSkew: p.Time - now.Unix()}, nil
}
