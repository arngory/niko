package rayapi

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"math/big"
	"net"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
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
	sawProto  string
	sawCurve  tls.CurveID
}

func writeError(w http.ResponseWriter, status int, code string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(map[string]string{"code": code})
}

func newNode(t *testing.T) *node {
	n := &node{inviteKey: make([]byte, 32), devices: map[string]string{}}
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
		}
		json.NewDecoder(r.Body).Decode(&req)
		minute, _ := strconv.ParseInt(req.Minute, 10, 64)
		clientPin := certPin(r.TLS.PeerCertificates[0])
		now := time.Now().Unix() / 60
		if req.InviteID != "inv1" || minute < now-2 || minute > now+2 ||
			!hmacEqual(req.Mac, RegisterMAC(n.inviteKey, minute, clientPin)) {
			writeError(w, http.StatusNotFound, "not_found")
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
	r, err := register(context.Background(), e, "inv1", n.inviteKey, time.Now())
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
	if _, err := register(context.Background(), e, "inv1", wrongKey, time.Now()); !isCode(err, "not_found") {
		t.Fatalf("wrong invite key: %v", err)
	}
	if _, err := register(context.Background(), e, "inv1", n.inviteKey, time.Now().Add(-10*time.Minute)); !isCode(err, "not_found") {
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
	if _, err := register(context.Background(), e, "inv1", n.inviteKey, time.Now()); err != nil {
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
