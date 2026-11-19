package libcore

import (
	"bufio"
	"bytes"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"testing"
	"time"

	M "github.com/sagernet/sing/common/metadata"

	"libcore/raysub"
)

// naiveRow is a naive line of the subscription, with the next one the node published for it, if any; published is
// false for a naive+quic line made here from the naive+https one by its scheme.
type naiveRow struct {
	link      string
	next      *raysub.Next
	published bool
}

// naiveRows are a device's naive lines: over HTTP/2 (naive+https) and over QUIC (naive+quic).
type naiveRows struct {
	h2, quic *naiveRow
}

func naiveQuery(t *testing.T, link string) url.Values {
	t.Helper()
	u, err := url.Parse(link)
	if err != nil {
		t.Fatal(err)
	}
	return u.Query()
}

func naiveSNI(t *testing.T, link string) string { return naiveQuery(t, link).Get("sni") }

// naiveSameBut: two lines of one naive profile that differ, if at all, in their name and password only: the address,
// the port and every other parameter (niko-pin, uot, niko-ack, the node and service ids) are the same
func naiveSameBut(t *testing.T, a, b string) error {
	t.Helper()
	ua, err := url.Parse(a)
	if err != nil {
		return err
	}
	ub, err := url.Parse(b)
	if err != nil {
		return err
	}
	if ua.Scheme != ub.Scheme || ua.Host != ub.Host || ua.User.Username() != ub.User.Username() {
		return fmt.Errorf("%s://%s@%s and %s://%s@%s", ua.Scheme, ua.User.Username(), ua.Host, ub.Scheme, ub.User.Username(), ub.Host)
	}
	qa, qb := ua.Query(), ub.Query()
	qa.Del("sni")
	qb.Del("sni")
	if qa.Encode() != qb.Encode() {
		return fmt.Errorf("parameters %q and %q", qa.Encode(), qb.Encode())
	}
	return nil
}

// TestRaycontrolNaiveSNI (raycontrol v0.29.0, R4-42, subscription-v1 §3 and §4), run by raycontrol-interop.yml on
// the node with certificates from Pebble: the node's naive changes its name (sni), and the new one comes in the
// profile's next, the same line with another sni. Before its time the line and its next carry the same address, port
// and niko-pin, and both names carry (the front serves the new one ahead of its publication); after rotate -now the
// line has the new name and carries, and the line the device had before (the rollback to link of a device that has
// not fetched again) carries still. Through the core's naive outbound (cronet), each line whole as the app takes it,
// over HTTP/2; and each over QUIC as naive+quic, which the node publishes or this test makes from the line by its
// scheme: the app makes no QUIC profile of a naive+https line, so that part checks the node's front on UDP 443 for a
// client that does (raycontrol R3-5), not the app.
//
// RAYCONTROL_RAYCTL runs rayctl on that node, RAYCONTROL_NAIVE_BUNDLE is the package of a device whose user has the
// naive service, RAYCONTROL_SING_BOX a sing-box built with naive, RAYCONTROL_CA the root of the node's certificates;
// RAYCONTROL_ADDRESS and RAYCONTROL_LIMIT_TARGET as for TestRaycontrolSitePins.
func TestRaycontrolNaiveSNI(t *testing.T) {
	rayctlCommand := strings.Fields(os.Getenv("RAYCONTROL_RAYCTL"))
	bundlePath, singBox, caPath := os.Getenv("RAYCONTROL_NAIVE_BUNDLE"), os.Getenv("RAYCONTROL_SING_BOX"), os.Getenv("RAYCONTROL_CA")
	if len(rayctlCommand) == 0 || bundlePath == "" || singBox == "" || caPath == "" {
		t.Skip("RAYCONTROL_RAYCTL, RAYCONTROL_NAIVE_BUNDLE, RAYCONTROL_SING_BOX and RAYCONTROL_CA not set (raycontrol-interop.yml sets them)")
	}
	_, _, request := interopDeviceAt(t, bundlePath)
	rayctl := func(args ...string) string {
		t.Helper()
		out, err := exec.Command(rayctlCommand[0], append(slices.Clone(rayctlCommand[1:]), args...)...).CombinedOutput()
		if err != nil {
			t.Fatalf("rayctl %s: %v: %s", strings.Join(args, " "), err, out)
		}
		t.Logf("rayctl %s: %s", strings.Join(args, " "), strings.TrimSpace(string(out)))
		return string(out)
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

	// the naive+quic lines made here from the published naive+https ones, not the node's
	synthesized := map[string]bool{}
	kind := func(link string) string {
		if synthesized[link] {
			return "naive+quic made from the line (the front over QUIC)"
		}
		return strings.SplitN(link, "://", 2)[0]
	}
	fetch := func() naiveRows {
		t.Helper()
		appJSON, err := RaycontrolFetchSubscription(string(request))
		if err != nil {
			t.Fatalf("subscription: %v", err)
		}
		var result raysub.Result
		if err = json.Unmarshal([]byte(appJSON), &result); err != nil {
			t.Fatal(err)
		}
		var rows naiveRows
		for _, p := range result.Payload.Profiles {
			u, err := url.Parse(p.Link)
			if err != nil {
				t.Fatal(err)
			}
			row := &naiveRow{link: p.Link, next: p.Next, published: true}
			switch u.Scheme {
			case "naive+https":
				rows.h2 = row
			case "naive+quic":
				rows.quic = row
			}
		}
		if rows.h2 == nil {
			t.Fatalf("the subscription has no naive line: %d profiles", len(result.Payload.Profiles))
		}
		// the node publishes naive's one line, naive+https (R3-5), and its front serves every name of naive on UDP
		// 443 too, for a client that takes the same line over QUIC. Niko does not make such a profile from the line
		// (RayRows takes the schemes published); the line is made here by its scheme, to check the front over QUIC
		if rows.quic == nil {
			rows.quic = &naiveRow{link: "naive+quic" + strings.TrimPrefix(rows.h2.link, "naive+https")}
			synthesized[rows.quic.link] = true
			if rows.h2.next != nil {
				next := *rows.h2.next
				next.Link = "naive+quic" + strings.TrimPrefix(next.Link, "naive+https")
				rows.quic.next = &next
				synthesized[next.Link] = true
			}
		}
		return rows
	}

	// carry: a stream through the line (the core's naive outbound, a sing-box of its own on a local mixed port) to
	// the echo, and back
	carry := func(link string) error {
		t.Helper()
		u, err := url.Parse(link)
		if err != nil {
			return err
		}
		q := u.Query()
		port, _ := strconv.Atoi(u.Port())
		password, _ := u.User.Password()
		tlsOptions := map[string]any{"enabled": true, "server_name": q.Get("sni"), "certificate_path": caPath}
		if pin := q.Get("niko-pin"); pin != "" {
			var pins []string
			for _, one := range strings.Split(pin, ",") {
				sum, err := base64.RawURLEncoding.DecodeString(strings.TrimRight(one, "="))
				if err != nil || len(sum) != 32 {
					t.Fatalf("niko-pin %q: %v", pin, err)
				}
				pins = append(pins, base64.StdEncoding.EncodeToString(sum))
			}
			tlsOptions["certificate_public_key_sha256"] = pins
		}
		local, err := net.Listen("tcp", "127.0.0.1:0")
		if err != nil {
			return err
		}
		localPort := local.Addr().(*net.TCPAddr).Port
		local.Close()
		config, _ := json.Marshal(map[string]any{
			"log":      map[string]any{"level": "warn"},
			"inbounds": []any{map[string]any{"type": "mixed", "tag": "in", "listen": "127.0.0.1", "listen_port": localPort}},
			"outbounds": []any{map[string]any{
				"type": "naive", "tag": "naive", "server": linkServer(t, u), "server_port": port,
				"username": u.User.Username(), "password": password, "quic": u.Scheme == "naive+quic", "tls": tlsOptions,
			}},
			"route": map[string]any{"final": "naive"},
		})
		path := filepath.Join(t.TempDir(), "naive.json")
		if err := os.WriteFile(path, config, 0o600); err != nil {
			return err
		}
		var logs bytes.Buffer
		process := exec.Command(singBox, "run", "-c", path)
		process.Stdout, process.Stderr = &logs, &logs
		if err := process.Start(); err != nil {
			return err
		}
		defer func() {
			process.Process.Kill()
			process.Wait()
			if logs.Len() > 0 {
				t.Logf("sing-box for %s: %s", q.Get("sni"), strings.TrimSpace(logs.String()))
			}
		}()
		var conn net.Conn
		for i := 0; i < 50; i++ {
			if conn, err = net.DialTimeout("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(localPort)), time.Second); err == nil {
				break
			}
			time.Sleep(100 * time.Millisecond)
		}
		if err != nil {
			return fmt.Errorf("the local port: %w", err)
		}
		defer conn.Close()
		conn.SetDeadline(time.Now().Add(25 * time.Second))
		if _, err := fmt.Fprintf(conn, "CONNECT %s HTTP/1.1\r\nHost: %s\r\n\r\n", destination, destination); err != nil {
			return err
		}
		reader := bufio.NewReader(conn)
		response, err := http.ReadResponse(reader, nil)
		if err != nil {
			return fmt.Errorf("CONNECT: %w", err)
		}
		if response.StatusCode != http.StatusOK {
			return fmt.Errorf("CONNECT: %s", response.Status)
		}
		payload := []byte("niko naive name rotation")
		if _, err := conn.Write(payload); err != nil {
			return err
		}
		back := make([]byte, len(payload))
		if _, err := io.ReadFull(reader, back); err != nil {
			return fmt.Errorf("the echo: %w", err)
		}
		if !bytes.Equal(back, payload) {
			return fmt.Errorf("the echo %q, want %q", back, payload)
		}
		return nil
	}
	// carriesBefore: each line carries within a minute, the time it took logged: the node applies a new user's
	// password, a new name's route and a switch a little after it says so (its apply runs on its own), and answers as
	// the site (an empty 404) until then. With a time [before], a line that carries only at it or after fails: next
	// ahead of its from is to carry ahead of it, not once the switch has come
	carriesBefore := func(what string, before time.Time, rows ...string) {
		t.Helper()
		for _, link := range rows {
			if link == "" {
				continue
			}
			start := time.Now()
			var err error
			for {
				if err = carry(link); err == nil {
					break
				}
				if time.Since(start) > time.Minute {
					t.Fatalf("%s, %s (%s), for a minute: %v", what, kind(link), naiveSNI(t, link), err)
				}
				time.Sleep(3 * time.Second)
			}
			if !before.IsZero() && !time.Now().Before(before) {
				t.Fatalf("%s, %s (%s): carried only at or after %s, not ahead of it", what, kind(link),
					naiveSNI(t, link), before.UTC().Format(time.RFC3339))
			}
			t.Logf("%s, %s: carries (%s after the first try)", what, kind(link),
				time.Since(start).Round(time.Second))
		}
	}
	carries := func(what string, rows ...string) {
		t.Helper()
		carriesBefore(what, time.Time{}, rows...)
	}
	quicLink := func(row *naiveRow) string {
		if row == nil {
			return ""
		}
		return row.link
	}
	quicNext := func(row *naiveRow) string {
		if row == nil || row.next == nil {
			return ""
		}
		return row.next.Link
	}

	before := fetch()
	oldName := naiveSNI(t, before.h2.link)
	if oldName == "" {
		t.Fatal("the naive line has no sni")
	}
	carries("the line before the rotation ("+oldName+")", before.h2.link, quicLink(before.quic))

	// the rotation the operator asks for: the new name in next, from its time on
	rayctl("service", "rotate", "naive")
	var planned naiveRows
	deadline := time.Now().Add(5 * time.Minute)
	for {
		planned = fetch()
		if planned.h2.next != nil && naiveSNI(t, planned.h2.next.Link) != oldName {
			break
		}
		if time.Now().After(deadline) {
			t.Fatalf("no next with a new name 5 minutes after rotate (link %s, next %v)", naiveSNI(t, planned.h2.link), planned.h2.next)
		}
		time.Sleep(3 * time.Second)
	}
	newName := naiveSNI(t, planned.h2.next.Link)
	from := time.Unix(planned.h2.next.From, 0)
	t.Logf("next: %s from %s (in %s)", newName, from.UTC().Format(time.RFC3339), time.Until(from).Round(time.Second))
	if naiveSNI(t, planned.h2.link) != oldName {
		t.Fatalf("the line's name went %s → %s with the rotation planned, want it kept until from", oldName, naiveSNI(t, planned.h2.link))
	}
	if !strings.HasSuffix(newName, ".example.test") {
		t.Fatalf("the new name %q is not under the node's domain", newName)
	}
	if !time.Now().Before(from) {
		t.Fatalf("next's from %s is not ahead", from)
	}
	if err := naiveSameBut(t, planned.h2.link, planned.h2.next.Link); err != nil {
		t.Fatalf("the line and its next differ in more than the name and password: %v", err)
	}
	if naiveQuery(t, planned.h2.link).Get("niko-pin") != naiveQuery(t, planned.h2.next.Link).Get("niko-pin") {
		t.Fatal("niko-pin of the line and of its next differ: the site's key changed with the name")
	}
	// a naive+quic line the node publishes has its own next: the same new name
	if planned.quic.published {
		if planned.quic.next == nil || naiveSNI(t, planned.quic.next.Link) != newName {
			t.Fatalf("the naive+quic line's next %v, want the new name %s as over HTTP/2", planned.quic.next, newName)
		}
		if err := naiveSameBut(t, planned.quic.link, planned.quic.next.Link); err != nil {
			t.Fatalf("naive+quic: the line and its next: %v", err)
		}
	}
	// the new name ahead of its time (next may be tried 120 s before from and earlier), the old one still
	carriesBefore("next ahead of its time ("+newName+")", from, planned.h2.next.Link, quicNext(planned.quic))
	carries("the line until from ("+oldName+")", planned.h2.link, quicLink(planned.quic))

	// the switch at once: the line takes the new name, the old one is still served (the rollback to link)
	out := rayctl("service", "rotate", "naive", "-now")
	if !strings.Contains(out, oldName) {
		t.Logf("rotate -now does not name the previous name %s it keeps serving", oldName)
	}
	var switched naiveRows
	deadline = time.Now().Add(2 * time.Minute)
	for {
		switched = fetch()
		if naiveSNI(t, switched.h2.link) != oldName {
			break
		}
		if time.Now().After(deadline) {
			t.Fatalf("the line keeps the old name %s 2 minutes after rotate -now", oldName)
		}
		time.Sleep(3 * time.Second)
	}
	t.Logf("after rotate -now the line has %s (next was %s)", naiveSNI(t, switched.h2.link), newName)
	if naiveQuery(t, switched.h2.link).Get("niko-pin") != naiveQuery(t, planned.h2.link).Get("niko-pin") {
		t.Fatal("niko-pin changed with rotate -now")
	}
	carries("the line after rotate -now ("+naiveSNI(t, switched.h2.link)+")", switched.h2.link, quicLink(switched.quic))
	carries("the line the device had before ("+oldName+", the rollback)", planned.h2.link, quicLink(planned.quic))
}
