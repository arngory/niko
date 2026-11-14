package libcore

import (
	"context"
	"crypto/sha256"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"errors"
	"io"
	"net"
	"net/url"
	"os"
	"os/exec"
	"strconv"
	"strings"
	"testing"
	"time"

	"libcore/raysub"

	M "github.com/sagernet/sing/common/metadata"

	"github.com/miekg/dns"
)

// sitePinRows are the rows of a subscription that carry the site's key pins (raycontrol cert-pin-v1 §2): naive,
// hy2-masq and DoH, and the list they share, as SHA-256 of a key's SubjectPublicKeyInfo.
type sitePinRows struct {
	naive, hy2, doh *url.URL
	hy2Link         string
	pins            [][]byte
}

func (r sitePinRows) list() string {
	var parts []string
	for _, pin := range r.pins {
		parts = append(parts, base64.RawURLEncoding.EncodeToString(pin))
	}
	return strings.Join(parts, ",")
}

// TestRaycontrolSitePins (raycontrol cert-pin-v1), run by raycontrol-interop.yml on a node whose site takes its
// certificate from an ACME CA (Pebble): the naive, hy2-masq and DoH rows carry one list of two pins; the first is the
// key of the leaf the node answers with, under the names of naive and of DoH, with a chain to the CA; Hysteria2 and
// the node's DoH as the remote DNS carry with the list and are refused with pins of other keys. Then the site's key
// is switched (rayctl service rotate): the list moves by one, the node answers with the key that was the next one,
// and a client that still holds the list from before the switch goes on working; or it is switched to a key made
// for that (the node's store leaked): no key of the old list stays, and that list is refused.
//
// RAYCONTROL_PINS_BUNDLE is the device's package on that node, RAYCONTROL_ADDRESS the node, RAYCONTROL_CA the CA's
// roots, RAYCONTROL_LIMIT_TARGET an address of the runner the node's exits dial; RAYCONTROL_PINS_ROTATE_NEXT and
// RAYCONTROL_PINS_ROTATE_FRESH the commands of the two switches, either or both.
func TestRaycontrolSitePins(t *testing.T) {
	path := os.Getenv("RAYCONTROL_PINS_BUNDLE")
	if path == "" {
		t.Skip("RAYCONTROL_PINS_BUNDLE not set (raycontrol-interop.yml sets it)")
	}
	b, node, request := interopDeviceAt(t, path)
	rootsPEM, err := os.ReadFile(os.Getenv("RAYCONTROL_CA"))
	if err != nil {
		t.Fatal("RAYCONTROL_CA: ", err)
	}
	roots := x509.NewCertPool()
	if !roots.AppendCertsFromPEM(rootsPEM) {
		t.Fatal("RAYCONTROL_CA: no certificate")
	}

	// the destination through the tunnel: an echo on every address of the runner
	listener, err := net.Listen("tcp", ":0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			go func() {
				defer conn.Close()
				io.Copy(conn, conn)
			}()
		}
	}()
	destination := M.ParseSocksaddrHostPort(os.Getenv("RAYCONTROL_LIMIT_TARGET"), uint16(listener.Addr().(*net.TCPAddr).Port))

	rows := func() sitePinRows {
		t.Helper()
		appJSON, err := RaycontrolFetchSubscription(string(request))
		if err != nil {
			t.Fatalf("subscription: %v", err)
		}
		var result raysub.Result
		if err = json.Unmarshal([]byte(appJSON), &result); err != nil {
			t.Fatal(err)
		}
		var found sitePinRows
		lists := map[string]string{}
		for _, p := range result.Payload.Profiles {
			u, err := url.Parse(p.Link)
			if err != nil {
				t.Fatal(err)
			}
			switch {
			case u.Scheme == "naive+https":
				found.naive = u
			case u.Scheme == "hysteria2" && u.Query().Get("obfs") == "":
				found.hy2, found.hy2Link = u, p.Link
			case u.Scheme == "https" && strings.HasPrefix(u.Path, "/dns-query"):
				found.doh = u
			default:
				continue
			}
			lists[u.Scheme] = u.Query().Get("niko-pin")
		}
		if found.naive == nil || found.hy2 == nil || found.doh == nil {
			t.Fatalf("the subscription has not all of naive, hy2-masq and DoH: %d profiles", len(result.Payload.Profiles))
		}
		list := lists["naive+https"]
		if list == "" || lists["hysteria2"] != list || lists["https"] != list {
			t.Fatalf("niko-pin of naive, hy2-masq and DoH: not one list in all three (%d, %d and %d characters)",
				len(lists["naive+https"]), len(lists["hysteria2"]), len(lists["https"]))
		}
		for _, one := range strings.Split(list, ",") {
			pin, err := base64.RawURLEncoding.DecodeString(one)
			if err != nil || len(pin) != sha256.Size {
				t.Fatalf("niko-pin %q: not a SHA-256 in base64url without padding", one)
			}
			found.pins = append(found.pins, pin)
		}
		// the node writes the key in use first, then the next one, and for an hour after an ordinary switch the key
		// before it (§2): the rest in any order, and a client takes any number
		seen := map[string]bool{}
		for _, pin := range found.pins {
			if seen[string(pin)] {
				t.Fatal("niko-pin: a key twice in the list")
			}
			seen[string(pin)] = true
		}
		if len(found.pins) < 2 {
			t.Fatalf("niko-pin: %d pins, want the key in use and the next one", len(found.pins))
		}
		return found
	}

	// leafKey is the key of the leaf the node answers name with on TCP 443, as a pin; with verify, its chain leads to
	// the CA under that name (what naive's Chromium checks besides the pin)
	leafKey := func(name string, verify bool) ([]byte, error) {
		dialer := &net.Dialer{Timeout: 5 * time.Second}
		conn, err := tls.DialWithDialer(dialer, "tcp", net.JoinHostPort(node, "443"), &tls.Config{
			ServerName: name, NextProtos: []string{"h2"}, InsecureSkipVerify: true,
			CurvePreferences: []tls.CurveID{tls.X25519MLKEM768},
		})
		if err != nil {
			return nil, err
		}
		defer conn.Close()
		chain := conn.ConnectionState().PeerCertificates
		if verify {
			intermediates := x509.NewCertPool()
			for _, c := range chain[1:] {
				intermediates.AddCert(c)
			}
			if _, err = chain[0].Verify(x509.VerifyOptions{Roots: roots, Intermediates: intermediates, DNSName: name}); err != nil {
				return nil, err
			}
		}
		sum := sha256.Sum256(chain[0].RawSubjectPublicKeyInfo)
		return sum[:], nil
	}
	// answers waits until the node answers name with the key want (a new key restarts its processes, §5)
	answers := func(name string, want []byte) {
		t.Helper()
		var got []byte
		var err error
		for deadline := time.Now().Add(60 * time.Second); time.Now().Before(deadline); time.Sleep(time.Second) {
			if got, err = leafKey(name, true); err == nil && string(got) == string(want) {
				return
			}
		}
		t.Fatalf("%s: the leaf's key is not the first pin of the subscription 60 s on (error %v, the same key %v)",
			name, err, string(got) == string(want))
	}

	// throughHysteria dials the echo through the hy2-masq row with pins in place of its own
	throughHysteria := func(r sitePinRows, pins string) error {
		link := linkWithParam(t, r.hy2Link, "niko-pin", pins)
		kind, config := profileConfig(t, link, node, "", "", b.DeviceKey, 50)
		if kind != "hysteria2-pin" {
			t.Fatalf("the hy2-masq row built as %s, want it pinned", kind)
		}
		instance := startTestBox(t, config)
		defer instance.Close()
		outbound, _ := instance.Box.Outbound().Outbound("proxy")
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		conn, err := outbound.DialContext(ctx, "tcp", destination)
		if err != nil {
			return err
		}
		defer conn.Close()
		conn.SetDeadline(time.Now().Add(5 * time.Second))
		echo := make([]byte, 2)
		if _, err = conn.Write([]byte("ok")); err == nil {
			_, err = io.ReadFull(conn, echo)
		}
		if err == nil && string(echo) != "ok" {
			err = errors.New("not the echo")
		}
		return err
	}
	// carries retries a dial that must go through: the backend restarts when the key changes
	carries := func(what string, dial func() error) {
		t.Helper()
		var err error
		for deadline := time.Now().Add(40 * time.Second); time.Now().Before(deadline); time.Sleep(2 * time.Second) {
			if err = dial(); err == nil {
				return
			}
		}
		t.Fatalf("%s: %v", what, err)
	}

	// throughDoH asks the node's DoH, as the app makes it its remote DNS (ConfigBuilder's nodeDohServer: the node's
	// address, the row's name and path, X25519MLKEM768 only, the pins in place of the chain), for a name
	throughDoH := func(r sitePinRows, pins string) error {
		var hashes []string
		for _, one := range strings.Split(pins, ",") {
			sum, err := base64.RawURLEncoding.DecodeString(one)
			if err != nil {
				t.Fatal(err)
			}
			hashes = append(hashes, base64.StdEncoding.EncodeToString(sum))
		}
		port := 443
		if r.doh.Port() != "" {
			port, _ = strconv.Atoi(r.doh.Port())
		}
		local, err := net.ListenPacket("udp", "127.0.0.1:0")
		if err != nil {
			t.Fatal(err)
		}
		localPort := local.LocalAddr().(*net.UDPAddr).Port
		local.Close()
		config, _ := json.Marshal(map[string]any{
			"log": map[string]any{"level": "warn"},
			"dns": map[string]any{"servers": []any{map[string]any{
				"type": "https", "tag": "dns-remote", "server": node, "server_port": port, "path": r.doh.EscapedPath(),
				"tls": map[string]any{
					"enabled": true, "server_name": r.doh.Hostname(), "curve_preferences": []string{"X25519MLKEM768"},
					"certificate_public_key_sha256": hashes,
				},
			}}},
			"inbounds": []any{map[string]any{
				"type": "direct", "tag": "dns-in", "listen": "127.0.0.1", "listen_port": localPort, "network": "udp",
				"override_address": "1.1.1.1", "override_port": 53,
			}},
			"outbounds": []any{map[string]any{"type": "direct", "tag": "direct"}},
			"route": map[string]any{
				"rules": []any{map[string]any{"inbound": []string{"dns-in"}, "action": "hijack-dns"}},
				"final": "direct",
			},
		})
		instance := startTestBox(t, string(config))
		defer instance.Close()
		query := new(dns.Msg)
		query.SetQuestion("example.com.", dns.TypeA)
		client := &dns.Client{Timeout: 8 * time.Second}
		answer, _, err := client.Exchange(query, net.JoinHostPort("127.0.0.1", strconv.Itoa(localPort)))
		if err != nil {
			return err
		}
		if answer.Rcode != dns.RcodeSuccess || len(answer.Answer) == 0 {
			return errors.New("the answer: " + dns.RcodeToString[answer.Rcode] + ", " + strconv.Itoa(len(answer.Answer)) + " records")
		}
		return nil
	}

	other := base64.RawURLEncoding.EncodeToString(make([]byte, sha256.Size))
	check := func(r sitePinRows, held string, heldWhat string) {
		t.Helper()
		// the leaf under naive's name and under DoH's: the key of the first pin, with a chain to the CA
		answers(r.naive.Query().Get("sni"), r.pins[0])
		answers(r.doh.Hostname(), r.pins[0])
		carries("Hysteria2 with "+heldWhat, func() error { return throughHysteria(r, held) })
		carries("DoH with "+heldWhat, func() error { return throughDoH(r, held) })
		// pins of other keys only: no connection, whatever the chain
		err := throughHysteria(r, other)
		if err == nil {
			t.Fatal("Hysteria2 with a pin of another key went through")
		}
		// the words the app's test run takes for a TLS failure (RayTestFindings)
		if !strings.Contains(err.Error(), "unrecognized remote public key") {
			t.Fatalf("Hysteria2 with a pin of another key: refused without naming the key: %v", err)
		}
		if err = throughDoH(r, other); err == nil {
			t.Fatal("DoH with a pin of another key answered")
		}
		t.Logf("with %s: naive's and DoH's leaf on the first pin, Hysteria2 and DoH carry; another key's pin is refused", heldWhat)
	}

	// the first certificate, and with it the next key: the rows carry the pins from then on (§4)
	for deadline := time.Now().Add(3 * time.Minute); ; time.Sleep(3 * time.Second) {
		appJSON, err := RaycontrolFetchSubscription(string(request))
		if err == nil && strings.Contains(appJSON, "niko-pin") {
			break
		}
		if time.Now().After(deadline) {
			t.Fatalf("no pins in the subscription 3 minutes on (its error: %v)", err)
		}
	}
	before := rows()
	if before.naive.Query().Get("sni") == "" {
		t.Fatal("the naive row with pins has no sni: Chromium pins a key to a name")
	}
	check(before, before.list(), "the subscription's list")
	// a list is a list: a pin alone is taken as it stands; only the first is the leaf's
	has := func(pins [][]byte, pin []byte) bool {
		for _, one := range pins {
			if string(one) == string(pin) {
				return true
			}
		}
		return false
	}
	var rest []string
	for _, pin := range before.pins[1:] {
		rest = append(rest, base64.RawURLEncoding.EncodeToString(pin))
	}
	if err := throughHysteria(before, strings.Join(rest, ",")); err == nil {
		t.Fatal("Hysteria2 with the pins of the keys not in use went through before the switch")
	}

	run := func(name string) bool {
		t.Helper()
		command := strings.Fields(os.Getenv(name))
		if len(command) == 0 {
			return false
		}
		if out, err := exec.Command(command[0], command[1:]...).CombinedOutput(); err != nil {
			t.Fatalf("the key switch (%s): %v: %s", name, err, out)
		}
		return true
	}
	switched := false

	// a switch to the next key: the list moves by one, the key that was next is in use, a new next one follows (§5)
	if run("RAYCONTROL_PINS_ROTATE_NEXT") {
		switched = true
		var after sitePinRows
		for deadline := time.Now().Add(120 * time.Second); ; time.Sleep(2 * time.Second) {
			after = rows()
			if has(before.pins[1:], after.pins[0]) {
				break
			}
			if string(after.pins[0]) != string(before.pins[0]) || time.Now().After(deadline) {
				t.Fatalf("after the switch the first pin is not a key the list had before it (still the old key: %v)",
					string(after.pins[0]) == string(before.pins[0]))
			}
		}
		fresh := false
		for _, pin := range after.pins[1:] {
			fresh = fresh || !has(before.pins, pin)
		}
		if !fresh {
			t.Fatal("after the switch the list has no new next key")
		}
		// a forced switch is for a leaked key: it leaves the clients' lists at once (§5). After an ordinary one the
		// key from before stays for an hour, for the clients that meet the old leaf with the new list
		forced := strings.Contains(os.Getenv("RAYCONTROL_PINS_ROTATE_NEXT"), "-force")
		if kept := has(after.pins, before.pins[0]); kept == forced {
			t.Fatalf("after the switch (forced: %v) the key from before it is in the list: %v", forced, kept)
		}
		// a client that has not fetched the subscription since holds the list from before: it goes on working
		check(after, before.list(), "the list from before the switch")
		check(after, after.list(), "the new list")
		// the key from before the switch alone is refused
		if err := throughHysteria(after, base64.RawURLEncoding.EncodeToString(before.pins[0])); err == nil {
			t.Fatal("Hysteria2 with only the key from before the switch went through")
		}
		before = after
	}

	// a switch to a key made for it (the node's store leaked, both of its keys with it): no key of the list from
	// before is in the new one, and a client that holds that list is refused until it fetches its subscription
	if run("RAYCONTROL_PINS_ROTATE_FRESH") {
		switched = true
		var after sitePinRows
		for deadline := time.Now().Add(120 * time.Second); ; time.Sleep(2 * time.Second) {
			after = rows()
			if !has(before.pins, after.pins[0]) {
				break
			}
			if time.Now().After(deadline) {
				t.Fatal("2 minutes after the switch to a fresh key the first pin is still a key of the list before it")
			}
		}
		for _, pin := range after.pins {
			if has(before.pins, pin) {
				t.Fatal("after the switch to a fresh key the list still has a key from before it")
			}
		}
		check(after, after.list(), "the new list")
		err := throughHysteria(after, before.list())
		if err == nil || !strings.Contains(err.Error(), "unrecognized remote public key") {
			t.Fatalf("Hysteria2 with the list from before a switch to a fresh key: %v, want the key refused", err)
		}
		if err = throughDoH(after, before.list()); err == nil {
			t.Fatal("DoH with the list from before a switch to a fresh key answered")
		}
		t.Log("the list from before the switch to a fresh key is refused; the subscription's new one carries")
	}
	if !switched {
		t.Fatal("neither RAYCONTROL_PINS_ROTATE_NEXT nor RAYCONTROL_PINS_ROTATE_FRESH set: the key was not switched")
	}
}
