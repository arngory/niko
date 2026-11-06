package rayapi

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"math/big"
	"net"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"libcore/knock"
)

// node is a fake remote API with the checks of api-v1.md §1, §4 and §6.
type node struct {
	server    *httptest.Server
	pin       string
	inviteKey []byte
	devices   map[string]string // client pin -> role
	pinTries  int               // the PIN invite "inv-pin" (code 123456): tries left
	sawProto  string
	sawCurve  tls.CurveID
	slowHits  atomic.Int32
}

// writePinTries answers as connect does: permission_denied with a PinTries{left} detail, its value the protobuf
// message (field 1, a varint; proto3 leaves out 0) and the debug JSON.
func writePinTries(w http.ResponseWriter, left int) {
	var message []byte
	if left != 0 {
		message = append([]byte{0x08}, byte(left))
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusForbidden)
	json.NewEncoder(w).Encode(map[string]any{
		"code": "permission_denied", "message": "the invite needs its PIN",
		"details": []any{map[string]any{
			"type": "raycontrol.device.v1.PinTries", "value": base64.RawStdEncoding.EncodeToString(message),
			"debug": map[string]any{"left": left},
		}},
	})
}

func writeError(w http.ResponseWriter, status int, code string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(map[string]string{"code": code})
}

func newNode(t *testing.T) *node {
	n := &node{inviteKey: make([]byte, 32), devices: map[string]string{}, pinTries: 5}
	rand.Read(n.inviteKey)
	key, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	template := &x509.Certificate{SerialNumber: big.NewInt(1), NotBefore: time.Now().Add(-time.Hour), NotAfter: time.Now().Add(time.Hour)}
	der, _ := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	cert, _ := x509.ParseCertificate(der)
	n.pin = certPin(cert)

	mux := http.NewServeMux()
	mux.HandleFunc("/raycontrol.device.v1.DeviceService/Register", func(w http.ResponseWriter, r *http.Request) {
		var req struct {
			InviteID string `json:"inviteId"`
			Minute   string `json:"minute"`
			Mac      []byte `json:"mac"`
			PinProof []byte `json:"pinProof"`
		}
		json.NewDecoder(r.Body).Decode(&req)
		minute, _ := strconv.ParseInt(req.Minute, 10, 64)
		clientPin := certPin(r.TLS.PeerCertificates[0])
		now := time.Now().Unix() / 60
		known := req.InviteID == "inv1" || (req.InviteID == "inv-pin" && n.pinTries > 0)
		if !known || minute < now-2 || minute > now+2 ||
			!hmacEqual(req.Mac, RegisterMAC(n.inviteKey, minute, clientPin)) {
			writeError(w, http.StatusNotFound, "not_found")
			return
		}
		// api-v1.md §4: the PIN after the mac; none costs no try, a wrong one does, the last withdraws the invite
		if req.InviteID == "inv-pin" && !hmacEqual(req.PinProof, PinProof(n.inviteKey, "123456")) {
			if len(req.PinProof) > 0 {
				n.pinTries--
			}
			writePinTries(w, n.pinTries)
			return
		}
		n.devices[clientPin] = "admin"
		json.NewEncoder(w).Encode(map[string]string{"bundle": "rcdev1:abc", "deviceId": "dev1", "role": "admin"})
	})
	mux.HandleFunc("/raycontrol.device.v1.DeviceService/RotateCertificate", func(w http.ResponseWriter, r *http.Request) {
		old := certPin(r.TLS.PeerCertificates[0])
		if n.devices[old] == "" {
			writeError(w, http.StatusUnauthorized, "unauthenticated")
			return
		}
		var req struct {
			Certificate []byte `json:"certificate"`
			Signature   []byte `json:"signature"`
		}
		json.NewDecoder(r.Body).Decode(&req)
		next, err := x509.ParseCertificate(req.Certificate)
		if err != nil {
			writeError(w, http.StatusBadRequest, "invalid_argument")
			return
		}
		digest := sha256.Sum256([]byte(rotateLabel + old + certPin(next)))
		if !ecdsa.VerifyASN1(next.PublicKey.(*ecdsa.PublicKey), digest[:], req.Signature) {
			writeError(w, http.StatusBadRequest, "invalid_argument")
			return
		}
		delete(n.devices, old)
		n.devices[certPin(next)] = "admin"
		w.Write([]byte("{}"))
	})
	mux.HandleFunc("/raycontrol.node.v1.NodeService/ListUsers", func(w http.ResponseWriter, r *http.Request) {
		if n.devices[certPin(r.TLS.PeerCertificates[0])] != "admin" {
			writeError(w, http.StatusUnauthorized, "unauthenticated")
			return
		}
		n.sawProto = r.Proto
		w.Write([]byte(`{"users":[{"id":"1","name":"alice","enabled":true,"services":["reality"]}]}`))
	})
	mux.HandleFunc("/raycontrol.node.v1.NodeService/RotateService", func(w http.ResponseWriter, r *http.Request) {
		n.slowHits.Add(1)
		// processed, but the answer comes too late for the client
		time.Sleep(1500 * time.Millisecond)
		w.Write([]byte(`{"switchAt":"1"}`))
	})
	n.server = httptest.NewUnstartedServer(mux)
	n.server.EnableHTTP2 = true
	n.server.TLS = &tls.Config{
		MinVersion: tls.VersionTLS13, CurvePreferences: []tls.CurveID{tls.X25519MLKEM768},
		Certificates: []tls.Certificate{{Certificate: [][]byte{der}, PrivateKey: key}},
		ClientAuth:   tls.RequireAnyClientCert,
	}
	n.server.Config.ConnState = func(c net.Conn, s http.ConnState) {
		if tc, ok := c.(*tls.Conn); ok && s == http.StateActive {
			n.sawCurve = tc.ConnectionState().CurveID
		}
	}
	n.server.StartTLS()
	t.Cleanup(n.server.Close)
	return n
}

func hmacEqual(a, b []byte) bool { return len(a) == len(b) && string(a) == string(b) }

func (n *node) endpoint(id Identity) Endpoint {
	address := n.server.Listener.Addr().(*net.TCPAddr)
	return Endpoint{Host: "127.0.0.1", Port: address.Port, Pin: n.pin, Identity: id, NoSPA: true, Timeout: 5 * time.Second}
}

func TestNekoAPIRegisterRotateCall(t *testing.T) {
	n := newNode(t)
	id, err := NewIdentity()
	if err != nil {
		t.Fatal(err)
	}
	e := n.endpoint(id)
	r, err := register(context.Background(), e, "inv1", n.inviteKey, "", time.Now())
	if err != nil {
		t.Fatal(err)
	}
	if r.Bundle != "rcdev1:abc" || r.Role != "admin" || r.DeviceID != "dev1" || r.Identity.Pin != id.Pin {
		t.Fatalf("%+v", r)
	}
	if n.sawCurve != tls.X25519MLKEM768 {
		t.Fatalf("TLS used %v, want X25519MLKEM768", n.sawCurve)
	}

	answer, err := Call(context.Background(), e, NodeService+"ListUsers", nil)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(answer), `"alice"`) || n.sawProto != "HTTP/2.0" {
		t.Fatalf("%s over %s", answer, n.sawProto)
	}

	next, err := NewIdentity()
	if err != nil {
		t.Fatal(err)
	}
	if err = RotateCertificate(context.Background(), e, next); err != nil {
		t.Fatal(err)
	}
	if next.Pin == id.Pin {
		t.Fatal("the key did not change")
	}
	// the old key is gone, the new one works
	if _, err = Call(context.Background(), e, NodeService+"ListUsers", nil); !isCode(err, "unauthenticated") {
		t.Fatalf("old key: %v", err)
	}
	if _, err = Call(context.Background(), n.endpoint(next), NodeService+"ListUsers", nil); err != nil {
		t.Fatal(err)
	}
}

func isCode(err error, code string) bool {
	var apiErr *Error
	return errors.As(err, &apiErr) && apiErr.Code == code
}

func TestNekoAPIRejects(t *testing.T) {
	n := newNode(t)
	id, _ := NewIdentity()
	e := n.endpoint(id)
	wrongKey := make([]byte, 32)
	if _, err := register(context.Background(), e, "inv1", wrongKey, "", time.Now()); !isCode(err, "not_found") {
		t.Fatalf("wrong invite key: %v", err)
	}
	if _, err := register(context.Background(), e, "inv1", n.inviteKey, "", time.Now().Add(-10*time.Minute)); !isCode(err, "not_found") {
		t.Fatalf("stale minute: %v", err)
	}
	bad := e
	bad.Pin = base64.RawURLEncoding.EncodeToString(make([]byte, 32))
	if _, err := Call(context.Background(), bad, NodeService+"ListUsers", nil); !errors.Is(err, errPin) {
		t.Fatalf("wrong pin: %v", err)
	}
	if _, err := Call(context.Background(), e, NodeService+"ListUsers", nil); !isCode(err, "unauthenticated") {
		t.Fatalf("unregistered device: %v", err)
	}
}

// With SPA: a packet over UDP before the call (to the slot port; nothing listens there in the test).
func TestNekoAPIWithSPA(t *testing.T) {
	n := newNode(t)
	id, _ := NewIdentity()
	e := n.endpoint(id)
	e.NoSPA = false
	e.SPA = knock.SPAv2Options{NodeID: strings.Repeat("33", 16), EpochLen: 86400, SlotLen: 600, PortKey: strings.Repeat("44", 32),
		PortBase: 40000, PortRange: 4096, LenMin: 96, LenMax: 160, DeviceKey: hex.EncodeToString(n.inviteKey)}
	start := time.Now()
	if _, err := register(context.Background(), e, "inv1", n.inviteKey, "", time.Now()); err != nil {
		t.Fatal(err)
	}
	if time.Since(start) < 300*time.Millisecond {
		t.Fatal("no wait after SPA")
	}
	// a closed port: SPA over UDP twice and over TCP, then ErrNoAnswer
	n.server.Close()
	if _, err := Call(context.Background(), e, NodeService+"ListUsers", nil); !errors.Is(err, ErrNoAnswer) {
		t.Fatalf("closed port: %v", err)
	}
}

// A request that reached the node is never sent again: Register or RotateService must not run twice.
func TestNekoAPIAnswerLostNoRetry(t *testing.T) {
	n := newNode(t)
	id, _ := NewIdentity()
	e := n.endpoint(id)
	e.NoSPA = false
	e.Timeout = 500 * time.Millisecond
	e.SPA = knock.SPAv2Options{NodeID: strings.Repeat("33", 16), EpochLen: 86400, SlotLen: 600, PortKey: strings.Repeat("44", 32),
		PortBase: 40000, PortRange: 4096, LenMin: 96, LenMax: 160, DeviceKey: hex.EncodeToString(n.inviteKey)}
	_, err := Call(context.Background(), e, NodeService+"RotateService", []byte(`{"name":"reality","now":true}`))
	if !errors.Is(err, ErrAnswerLost) {
		t.Fatalf("got %v, want ErrAnswerLost", err)
	}
	if hits := n.slowHits.Load(); hits != 1 {
		t.Fatalf("the request was sent %d times", hits)
	}
}

// A handshake that never completes is a failure to connect, tried again after SPA, never "answer lost".
func TestNekoAPIStalledHandshake(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	var accepted, closed atomic.Int32
	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			accepted.Add(1)
			// reads the ClientHello and never answers; counts the client closing
			go func() {
				io.Copy(io.Discard, conn)
				conn.Close()
				closed.Add(1)
			}()
		}
	}()
	for _, c := range []struct {
		name  string
		setup func(e *Endpoint)
	}{
		// the call's timeout runs out during the handshake (which min() caps at the same 400 ms)
		{"call timeout", func(e *Endpoint) { e.Timeout = 400 * time.Millisecond; e.HandshakeTimeout = 10 * time.Second }},
		// the handshake's own deadline, well inside the call's
		{"handshake timeout", func(e *Endpoint) { e.Timeout = 5 * time.Second; e.HandshakeTimeout = 300 * time.Millisecond }},
	} {
		t.Run(c.name, func(t *testing.T) { stalledHandshake(t, listener, &accepted, &closed, c.setup) })
	}
}

func stalledHandshake(t *testing.T, listener net.Listener, accepted, closed *atomic.Int32, setup func(e *Endpoint)) {
	accepted.Store(0)
	closed.Store(0)
	id, _ := NewIdentity()
	e := Endpoint{Host: "127.0.0.1", Port: listener.Addr().(*net.TCPAddr).Port, Pin: strings.Repeat("A", 43), Identity: id,
		SPA: knock.SPAv2Options{NodeID: strings.Repeat("33", 16), EpochLen: 86400, SlotLen: 600, PortKey: strings.Repeat("44", 32),
			PortBase: 40000, PortRange: 4096, LenMin: 96, LenMax: 160, DeviceKey: strings.Repeat("11", 32)}}
	setup(&e)
	_, err := Call(context.Background(), e, DeviceService+"Register", []byte(`{}`))
	if !errors.Is(err, ErrNoAnswer) || errors.Is(err, ErrAnswerLost) {
		t.Fatalf("got %v, want ErrNoAnswer", err)
	}
	if n := accepted.Load(); n != 4 {
		t.Fatalf("%d connections, want 4 (UDP, UDP again, ClientHello, bare TCP)", n)
	}
	// no dial outlives the Call (net/http would finish it for the pool)
	deadline := time.Now().Add(2 * time.Second)
	for closed.Load() < accepted.Load() && time.Now().Before(deadline) {
		time.Sleep(20 * time.Millisecond)
	}
	if c, n := closed.Load(), accepted.Load(); c != n {
		t.Fatalf("%d of %d connections still open after the call", n-c, n)
	}
}

// When the Call ends, a dial still in its handshake ends too, though both timeouts are far off (net/http keeps
// dialing for its pool after the request gave up). Two things end it: the Call's context and, since Go 1.23,
// CloseIdleConnections; this checks the behaviour, not which of them did it.
func TestNekoAPIDialEndsWithCall(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	var accepted, closed atomic.Int32
	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			accepted.Add(1)
			go func() {
				io.Copy(io.Discard, conn)
				conn.Close()
				closed.Add(1)
			}()
		}
	}()
	id, _ := NewIdentity()
	e := Endpoint{Host: "127.0.0.1", Port: listener.Addr().(*net.TCPAddr).Port, Pin: strings.Repeat("A", 43), Identity: id,
		Timeout: 5 * time.Second, HandshakeTimeout: 5 * time.Second,
		SPA: knock.SPAv2Options{NodeID: strings.Repeat("33", 16), EpochLen: 86400, SlotLen: 600, PortKey: strings.Repeat("44", 32),
			PortBase: 40000, PortRange: 4096, LenMin: 96, LenMax: 160, DeviceKey: strings.Repeat("11", 32)}}
	// after the first SPA wait (300 ms) the handshake starts and stalls; the call ends at 800 ms
	ctx, cancel := context.WithTimeout(context.Background(), 800*time.Millisecond)
	defer cancel()
	if _, err = Call(ctx, e, DeviceService+"Register", []byte(`{}`)); err == nil {
		t.Fatal("a stalled handshake answered")
	}
	if accepted.Load() == 0 {
		t.Fatal("no connection was made")
	}
	deadline := time.Now().Add(time.Second)
	for closed.Load() < accepted.Load() && time.Now().Before(deadline) {
		time.Sleep(20 * time.Millisecond)
	}
	if c, n := closed.Load(), accepted.Load(); c != n {
		t.Fatalf("%d of %d connections still open 1 s after the call (the handshake timeout is 5 s)", n-c, n)
	}
}

// An invite with a PIN (api-v1.md §4): asked without one costs no try, a wrong one costs one and says how many are
// left, the right one registers; the last wrong one withdraws the invite.
func TestNekoAPIRegisterPin(t *testing.T) {
	n := newNode(t)
	id, _ := NewIdentity()
	e := n.endpoint(id)
	left := func(err error) int {
		var apiErr *Error
		if !errors.As(err, &apiErr) || apiErr.Code != "permission_denied" || apiErr.PinLeft == nil {
			t.Fatalf("not a PIN refusal: %v", err)
		}
		return *apiErr.PinLeft
	}
	_, err := register(context.Background(), e, "inv-pin", n.inviteKey, "", time.Now())
	if left(err) != 5 {
		t.Fatalf("no PIN: %v", err)
	}
	if !strings.HasPrefix(err.Error(), "raycontrol api: permission_denied: pin tries left 5") {
		t.Fatalf("the app reads %q", err.Error())
	}
	_, err = register(context.Background(), e, "inv-pin", n.inviteKey, "000000", time.Now())
	if left(err) != 4 {
		t.Fatalf("wrong PIN: %v", err)
	}
	if r, err := register(context.Background(), e, "inv-pin", n.inviteKey, "123456", time.Now()); err != nil || r.Bundle != "rcdev1:abc" {
		t.Fatalf("right PIN: %+v %v", r, err)
	}
	// down to the last: 0 left (a message without its field), then the invite is gone
	for want := 3; want >= 0; want-- {
		if _, err = register(context.Background(), e, "inv-pin", n.inviteKey, "999999", time.Now()); left(err) != want {
			t.Fatalf("wrong PIN, want %d left: %v", want, err)
		}
	}
	if _, err = register(context.Background(), e, "inv-pin", n.inviteKey, "123456", time.Now()); !isCode(err, "not_found") {
		t.Fatalf("withdrawn: %v", err)
	}
	// a PIN that is not 6 digits never goes out
	if _, err = Register(context.Background(), Invite{SPA: knock.SPAv2Options{DeviceKey: hex.EncodeToString(n.inviteKey)}, PinCode: "12345"}, nil); !errors.Is(err, ErrPinFormat) {
		t.Fatalf("short PIN: %v", err)
	}
}

// The PIN proof as api-v1.md §4 defines it.
func TestNekoAPIPinProof(t *testing.T) {
	key := make([]byte, 32)
	for i := range key {
		key[i] = byte(i)
	}
	mac := hmac.New(sha256.New, key)
	mac.Write([]byte("raycontrol-invite-pin-v1123456"))
	if !hmac.Equal(PinProof(key, "123456"), mac.Sum(nil)) {
		t.Fatal("pin proof")
	}
	// the detail's value in either alphabet, padded or not; an empty message is 0
	for value, want := range map[string]int{"CAQ": 4, "CAQ=": 4, "": 0, "CP8B": 255} {
		e := &Error{Details: []struct {
			Type  string          `json:"type"`
			Value string          `json:"value"`
			Debug json.RawMessage `json:"debug"`
		}{{Type: "type.googleapis.com/raycontrol.device.v1.PinTries", Value: value}}}
		if got := e.pinTries(); got == nil || *got != want {
			t.Fatalf("%q: %v", value, got)
		}
	}
}
