package chromecheck

import (
	"context"
	"encoding/binary"
	"encoding/hex"
	"io"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"testing"
	"time"

	"github.com/sagernet/sing-box/common/tls"
	"github.com/sagernet/sing-box/common/tls/chromehello"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
)

// captureName must resolve to 127.0.0.1 (/etc/hosts): Chrome takes the usual path for a name, not an address.
const captureName = "niko-capture.example"

// capture accepts one connection and returns the TLS record it starts with.
func capture(t *testing.T, l net.Listener) <-chan []byte {
	out := make(chan []byte, 1)
	go func() {
		defer close(out)
		c, err := l.Accept()
		if err != nil {
			return
		}
		defer c.Close()
		c.SetDeadline(time.Now().Add(20 * time.Second))
		header := make([]byte, 5)
		if _, err = io.ReadFull(c, header); err != nil {
			return
		}
		body := make([]byte, binary.BigEndian.Uint16(header[3:]))
		if _, err = io.ReadFull(c, body); err != nil {
			return
		}
		out <- append(header, body...)
	}()
	return out
}

func listen(t *testing.T) (net.Listener, int) {
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { l.Close() })
	return l, l.Addr().(*net.TCPAddr).Port
}

func shape(t *testing.T, name string, raw []byte) *chromehello.Hello {
	t.Helper()
	h, err := chromehello.Parse(raw)
	if err != nil {
		t.Fatalf("%s: %v", name, err)
	}
	return h
}

// TestChromeLive: the browser at CHROME_BIN sends what the embedded capture holds (else the capture is out of
// date: the new one is written to CHROME_HELLO_OUT), and the core's "chrome" client sends the same.
func TestChromeLive(t *testing.T) {
	chrome := os.Getenv("CHROME_BIN")
	if chrome == "" {
		t.Skip("CHROME_BIN not set")
	}
	l, port := listen(t)
	got := capture(t, l)
	profile := t.TempDir()
	cmd := exec.Command(chrome, "--no-sandbox", "--disable-gpu", "--no-first-run", "--no-default-browser-check",
		"--no-proxy-server", "--user-data-dir="+profile, "https://"+captureName+":"+strconv.Itoa(port)+"/")
	if err := cmd.Start(); err != nil {
		t.Fatal(err)
	}
	defer func() {
		cmd.Process.Kill()
		cmd.Wait()
	}()
	var live []byte
	select {
	case live = <-got:
	case <-time.After(60 * time.Second):
	}
	if live == nil {
		t.Fatal("no ClientHello from the browser")
	}
	version, _ := exec.Command(chrome, "--version").Output()
	if out := os.Getenv("CHROME_HELLO_OUT"); out != "" {
		os.MkdirAll(out, 0o755)
		os.WriteFile(filepath.Join(out, "hello.hex"), []byte(hex.EncodeToString(live)+"\n"), 0o644)
		os.WriteFile(filepath.Join(out, "SOURCE"), []byte(string(version)), 0o644)
	}
	browser := shape(t, "browser", live)
	embedded, err := chromehello.Raw()
	if err != nil {
		t.Fatal(err)
	}
	reference := shape(t, "embedded capture", embedded)
	t.Logf("browser %s: JA4 %s", version, browser.JA4())
	t.Logf("embedded (%s): JA4 %s", chromehello.Source, reference.JA4())

	// the core's client, as an outbound with utls fingerprint chrome dials
	l2, port2 := listen(t)
	got2 := capture(t, l2)
	config, err := tls.NewClient(context.Background(), log.NewNOPFactory().Logger(), captureName, option.OutboundTLSOptions{
		Enabled:    true,
		ServerName: captureName,
		UTLS:       &option.OutboundUTLSOptions{Enabled: true, Fingerprint: "chrome"},
	})
	if err != nil {
		t.Fatal(err)
	}
	conn, err := net.Dial("tcp", "127.0.0.1:"+strconv.Itoa(port2))
	if err != nil {
		t.Fatal(err)
	}
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		tls.ClientHandshake(ctx, conn, config)
	}()
	defer conn.Close()
	coreRaw := <-got2
	if coreRaw == nil {
		t.Fatal("no ClientHello from the core")
	}
	core := shape(t, "core", coreRaw)
	t.Logf("core: JA4 %s", core.JA4())

	if core.Shape() != reference.Shape() {
		t.Errorf("the core does not send the embedded capture\n--- embedded\n%s\n--- core\n%s", reference.Shape(), core.Shape())
	}
	if browser.Shape() != reference.Shape() {
		t.Errorf("Chrome changed: update common/tls/chromehello in the core from CHROME_HELLO_OUT\n--- embedded\n%s\n--- browser\n%s",
			reference.Shape(), browser.Shape())
	}
}
