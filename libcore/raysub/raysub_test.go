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
	"errors"
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
	// the payload's JSON as sent, in place of payload's
	rawPayload []byte
	// app-update-v1's files, behind the same auth (app_test.go)
	app http.Handler
	// the X-Raycontrol-LAN of the last request, "-" without one
	sawLAN string
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
		authorized := r.Header.Get("X-Raycontrol-Device") == "0123456789abcdef" &&
			r.Header.Get("Authorization") == authToken(key, minute)
		if n.app != nil && authorized && strings.HasPrefix(r.URL.Path, "/app/v1/") {
			n.app.ServeHTTP(w, r)
			return
		}
		if r.URL.Path != "/sub/v1" || !authorized {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		n.sawLAN = "-"
		if values, ok := r.Header["X-Raycontrol-Lan"]; ok {
			n.sawLAN = strings.Join(values, ",")
		}
		body, _ := json.Marshal(n.payload)
		if n.rawPayload != nil {
			body = n.rawPayload
		}
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

// the app reads the payload from the result's JSON: every field the node signed reaches it, those this package does
// not name too (a dns_probe or hints.via_relay dropped here once kept the app from them)
func TestSubscriptionKeepsThePayloadWhole(t *testing.T) {
	n := newNode(t)
	plain, _ := json.Marshal(n.payload)
	var fields map[string]any
	json.Unmarshal(plain, &fields)
	fields["dns_probe"] = map[string]any{"v": 1, "key": "AAEC"}
	fields["hints"] = map[string]any{"via_relay": true, "dns": map[string]any{"dnstt": "doh"}, "devices": 12, "days": 3}
	fields["x_later"] = map[string]any{"a": []any{1, "b"}}
	n.rawPayload, _ = json.Marshal(fields)
	result, err := Fetch(context.Background(), n.request)
	if err != nil {
		t.Fatal(err)
	}
	if p := result.Payload; p.DNSProbe == nil || p.DNSProbe.Key != "AAEC" || p.Hints == nil || !p.Hints.ViaRelay || p.Hints.DNS["dnstt"] != "doh" {
		t.Fatalf("%+v", p)
	}
	out, err := json.Marshal(result)
	if err != nil {
		t.Fatal(err)
	}
	var got struct {
		Payload map[string]any `json:"payload"`
	}
	if err = json.Unmarshal(out, &got); err != nil {
		t.Fatal(err)
	}
	want, _ := json.Marshal(fields)
	have, _ := json.Marshal(got.Payload)
	if string(have) != string(want) {
		t.Fatalf("the app gets %s, the node signed %s", have, want)
	}
}

// lan-v1 §1.1: the consent goes as X-Raycontrol-LAN: on|off; without one (an old setting, a node the device never
// chose for) the node keeps what it had, so no header at all
func TestNekoSubscriptionLANHeader(t *testing.T) {
	n := newNode(t)
	for lan, want := range map[string]string{"": "-", "on": "on", "off": "off", "maybe": "-"} {
		request := n.request
		request.LAN = lan
		if _, err := Fetch(context.Background(), request); err != nil {
			t.Fatal(err)
		}
		if n.sawLAN != want {
			t.Fatalf("lan %q: the node saw %q, want %q", lan, n.sawLAN, want)
		}
	}
	// the app passes it in the request's JSON
	var request Request
	if err := json.Unmarshal([]byte(`{"lan":"on"}`), &request); err != nil || request.LAN != "on" {
		t.Fatalf("%+v %v", request, err)
	}
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
			_, err := Fetch(context.Background(), n.request)
			if err == nil {
				t.Fatal("accepted")
			}
			if errors.Is(err, ErrNoAnswer) {
				t.Fatalf("the node answered, got %v", err)
			}
		})
	}
}

// A port that stays closed is ErrNoAnswer (the app adds the clock hint), after UDP, UDP and TCP.
func TestNekoSubscriptionNoAnswer(t *testing.T) {
	n := newNode(t)
	n.server.Close()
	start := time.Now()
	_, err := Fetch(context.Background(), n.request)
	if !errors.Is(err, ErrNoAnswer) {
		t.Fatalf("got %v, want ErrNoAnswer", err)
	}
	if time.Since(start) < 900*time.Millisecond {
		t.Fatal("gave up before the three knocks")
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
