package raysub

import (
	"context"
	"crypto/ecdsa"
	"crypto/ed25519"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"math/big"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"libcore/knock"

	"github.com/cloudflare/circl/sign/mldsa/mldsa65"
)

type node struct {
	server   *httptest.Server
	request  Request
	edKey    ed25519.PrivateKey
	mlKey    *mldsa65.PrivateKey
	payload  Payload
	sawCurve tls.CurveID
}

const nodeID = "33333333333333333333333333333333"

func newNode(t *testing.T) *node {
	n := &node{}
	edPub, edKey, _ := ed25519.GenerateKey(rand.Reader)
	mlPub, mlKey, _ := mldsa65.GenerateKey(rand.Reader)
	n.edKey, n.mlKey = edKey, mlKey
	n.payload = Payload{V: 1, NodeID: nodeID, Seq: 5, Time: time.Now().Unix(), Issued: time.Now().Unix(),
		Expires:  time.Now().Add(72 * time.Hour).Unix(),
		Profiles: []Profile{{Svc: 3, Name: "dns", Link: "vless://u@inner:1?type=raw#dns"}}}
	deviceKey := strings.Repeat("11", 32)
	key, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	template := &x509.Certificate{SerialNumber: big.NewInt(1), NotBefore: time.Now().Add(-time.Hour), NotAfter: time.Now().Add(time.Hour)}
	der, _ := x509.CreateCertificate(rand.Reader, template, template, &key.PublicKey, key)
	cert, _ := x509.ParseCertificate(der)
	pin := sha256.Sum256(cert.RawSubjectPublicKeyInfo)

	n.server = httptest.NewUnstartedServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		key, _ := hex.DecodeString(deviceKey)
		minute := time.Now().Unix() / 60
		if r.URL.Path != "/sub/v1" || r.Header.Get("X-Raycontrol-Device") != "0123456789abcdef" ||
			r.Header.Get("Authorization") != authToken(key, minute) {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		body, _ := json.Marshal(n.payload)
		message := append([]byte(signLabel), body...)
		mlSig := make([]byte, mldsa65.SignatureSize)
		mldsa65.SignTo(n.mlKey, message, nil, true, mlSig)
		enc := base64.RawURLEncoding
		json.NewEncoder(w).Encode(map[string]any{"payload": enc.EncodeToString(body),
			"sig": map[string]string{"ed25519": enc.EncodeToString(ed25519.Sign(n.edKey, message)), "mldsa65": enc.EncodeToString(mlSig)}})
	}))
	n.server.TLS = &tls.Config{
		MinVersion: tls.VersionTLS13, CurvePreferences: []tls.CurveID{tls.X25519MLKEM768},
		Certificates: []tls.Certificate{{Certificate: [][]byte{der}, PrivateKey: key}},
	}
	n.server.Config.ConnState = func(c net.Conn, s http.ConnState) {
		if tc, ok := c.(*tls.Conn); ok && s == http.StateActive {
			n.sawCurve = tc.ConnectionState().CurveID
		}
	}
	n.server.StartTLS()
	t.Cleanup(n.server.Close)
	address := n.server.Listener.Addr().(*net.TCPAddr)
	mlRaw, _ := mlPub.MarshalBinary()
	n.request = Request{
		Host: "127.0.0.1", Port: address.Port, Pin: base64.RawURLEncoding.EncodeToString(pin[:]),
		DeviceID: "0123456789abcdef", NodeID: nodeID,
		Ed25519: hex.EncodeToString(edPub), MLDSA65: base64.RawURLEncoding.EncodeToString(mlRaw),
		SPA: knock.SPAv2Options{NodeID: nodeID, EpochLen: 86400, SlotLen: 600,
			PortKey: strings.Repeat("44", 32), PortBase: 40000, PortRange: 1, LenMin: 96, LenMax: 160, DeviceKey: deviceKey},
		Timeout: 5 * time.Second,
	}
	return n
}

func TestNekoSubscriptionFetch(t *testing.T) {
	n := newNode(t)
	result, err := Fetch(context.Background(), n.request)
	if err != nil {
		t.Fatal(err)
	}
	if result.Payload.Seq != 5 || len(result.Payload.Profiles) != 1 || result.Payload.Profiles[0].Name != "dns" {
		t.Fatalf("%+v", result.Payload)
	}
	if result.ClockSkew > 2 || result.ClockSkew < -2 {
		t.Fatalf("skew %d", result.ClockSkew)
	}
	if n.sawCurve != tls.X25519MLKEM768 {
		t.Fatalf("TLS used %v, want X25519MLKEM768", n.sawCurve)
	}
}

func TestNekoSubscriptionRejects(t *testing.T) {
	for name, mutate := range map[string]func(n *node){
		"wrong pin": func(n *node) { n.request.Pin = base64.RawURLEncoding.EncodeToString(make([]byte, 32)) },
		"wrong ed25519": func(n *node) {
			pub, _, _ := ed25519.GenerateKey(rand.Reader)
			n.request.Ed25519 = hex.EncodeToString(pub)
		},
		"wrong ML-DSA-65": func(n *node) { _, key, _ := mldsa65.GenerateKey(rand.Reader); n.mlKey = key },
		"other node":      func(n *node) { n.payload.NodeID = strings.Repeat("ab", 16) },
		"rollback":        func(n *node) { n.request.LastSeq = 6 },
		"expired":         func(n *node) { n.payload.Expires = time.Now().Add(-time.Minute).Unix() },
		"unknown device":  func(n *node) { n.request.DeviceID = "ffffffffffffffff" },
	} {
		t.Run(name, func(t *testing.T) {
			n := newNode(t)
			mutate(n)
			if _, err := Fetch(context.Background(), n.request); err == nil {
				t.Fatal("accepted")
			}
		})
	}
}

// The auth token matches raycontrol's (internal/sub AuthToken): HMAC-SHA256(device key, label || BE64(minute)).
func TestNekoSubscriptionAuthToken(t *testing.T) {
	key := make([]byte, 32)
	for i := range key {
		key[i] = 0x11
	}
	got := authToken(key, 29453760)
	if !strings.HasPrefix(got, "RC1 29453760.") || len(strings.TrimPrefix(got, "RC1 29453760.")) != 43 {
		t.Fatal(got)
	}
}
