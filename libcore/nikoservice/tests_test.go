package nikoservice

import (
	"archive/zip"
	"bytes"
	"context"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"libcore/exitaddress"
	"libcore/nikoconf"
	"libcore/nikofmt"
	"libcore/nikopb"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

func TestConnectionAndExitAddressRPCs(t *testing.T) {
	ctx := context.Background()

	// Without a running core, both return FailedPrecondition.
	idleSrv := storeServer(t.TempDir())
	idleClient := serve(t, idleSrv)
	if _, err := idleClient.TestConnection(ctx, &nikopb.TestConnectionRequest{}); code(err) != codes.FailedPrecondition {
		t.Fatalf("TestConnection without core: %v", err)
	}
	if _, err := idleClient.ExitAddress(ctx, &nikopb.ExitAddressRequest{Provider: "cloudflare"}); code(err) != codes.FailedPrecondition {
		t.Fatalf("ExitAddress without core: %v", err)
	}
	if _, err := idleClient.ExitAddress(ctx, &nikopb.ExitAddressRequest{Provider: "bad-provider"}); code(err) != codes.InvalidArgument {
		t.Fatalf("ExitAddress unknown provider: %v", err)
	}

	// Local test HTTP server for TestConnection.
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	runSrv := storeServer(t.TempDir())
	runSrv.coreDial = func(dCtx context.Context, network, address string) (net.Conn, error) {
		var d net.Dialer
		return d.DialContext(dCtx, network, address)
	}
	client := serve(t, runSrv)

	res, err := client.TestConnection(ctx, &nikopb.TestConnectionRequest{Url: srv.URL, TimeoutMs: 3000})
	if err != nil {
		t.Fatalf("TestConnection: %v", err)
	}
	if res.Error != "" || res.LatencyMs < 0 {
		t.Fatalf("unexpected TestConnection result: %+v", res)
	}

	// Invalid URL refused cleanly.
	if _, err := client.TestConnection(ctx, &nikopb.TestConnectionRequest{Url: "ftp://example.com"}); code(err) != codes.InvalidArgument {
		t.Fatalf("expected InvalidArgument for ftp URL, got %v", err)
	}

	// Local HTTPS server for ExitAddress.
	exitSrv := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("ip=198.51.100.77\nloc=nl\n"))
	}))
	defer exitSrv.Close()

	// Temporarily add a test provider pointing at local HTTP server (TLS cert verified via plain HTTP test server).
	plainExit := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("ip=198.51.100.77\nloc=nl\n"))
	}))
	defer plainExit.Close()
	exitaddress.Providers["test-local"] = exitaddress.Provider{
		URL:   plainExit.URL,
		Parse: exitaddress.Providers["cloudflare"].Parse,
	}
	t.Cleanup(func() { delete(exitaddress.Providers, "test-local") })

	exitRes, err := client.ExitAddress(ctx, &nikopb.ExitAddressRequest{Provider: "test-local", TimeoutMs: 3000})
	if err != nil {
		t.Fatalf("ExitAddress: %v", err)
	}
	if exitRes.Provider != "test-local" || exitRes.Ip != "198.51.100.77" || exitRes.CountryCode != "NL" {
		t.Fatalf("unexpected ExitAddress result: %+v", exitRes)
	}
}

func TestTestProfilesStreamingAndZipImport(t *testing.T) {
	ctx := context.Background()
	s := storeServer(t.TempDir())
	s.testProfile = func(_ context.Context, _ *user, id int64, _ string, _ time.Duration) (int32, error) {
		return int32(id * 17), nil
	}
	client := serve(t, s)

	// Build a real .zip archive containing two profile links.
	var buf bytes.Buffer
	zw := zip.NewWriter(&buf)
	f, err := zw.Create("profiles.txt")
	if err != nil {
		t.Fatal(err)
	}
	_, _ = f.Write([]byte(socksLink + "\n" + naiveLink + "\n"))
	if err := zw.Close(); err != nil {
		t.Fatal(err)
	}

	preview, err := client.Recognize(ctx, &nikopb.ImportInput{File: buf.Bytes(), FileName: "bundle.zip"})
	if err != nil || preview.Kind != nikopb.ImportPreview_PROFILES || len(preview.Profiles) != 2 {
		t.Fatalf("Recognize zip: %+v %v", preview, err)
	}

	impStream, err := client.Import(ctx, &nikopb.ImportRequest{
		Input: &nikopb.ImportInput{File: buf.Bytes(), FileName: "bundle.zip"},
	})
	if err != nil {
		t.Fatal(err)
	}
	var ids []int64
	for {
		msg, err := impStream.Recv()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		if msg.Done {
			ids = msg.Ids
		}
	}
	if len(ids) != 2 {
		t.Fatalf("expected 2 imported profiles from zip, got %v", ids)
	}

	// Stream TestProfiles for all profiles in the default group.
	stream, err := client.TestProfiles(ctx, &nikopb.TestProfilesRequest{GroupId: 0})
	if err != nil {
		t.Fatal(err)
	}
	got := map[int64]int32{}
	for {
		msg, err := stream.Recv()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		if msg.Error != "" {
			t.Fatalf("unexpected profile error: %+v", msg)
		}
		got[msg.ProfileId] = msg.LatencyMs
	}
	if len(got) != 2 || got[ids[0]] != int32(ids[0]*17) || got[ids[1]] != int32(ids[1]*17) {
		t.Fatalf("unexpected TestProfiles results: %+v", got)
	}

	// Non-owner callers are refused by canStart before any ephemeral box is constructed.
	deniedSrv := storeServer(t.TempDir())
	deniedSrv.canStart = func(context.Context) error {
		return status.Error(codes.PermissionDenied, "not owner")
	}
	deniedClient := serve(t, deniedSrv)
	deniedStream, err := deniedClient.TestProfiles(ctx, &nikopb.TestProfilesRequest{ProfileIds: []int64{1}})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := deniedStream.Recv(); code(err) != codes.PermissionDenied {
		t.Fatalf("TestProfiles non-owner = %v, want PermissionDenied", err)
	}
}

func TestForTestRefusesCustomConfig(t *testing.T) {
	zero := 0
	cfg := `{"log":{"output":"/tmp/pwn.log"}}`
	in := nikoconf.Input{
		Profile: 1,
		ForTest: true,
		Profiles: []nikoconf.Profile{{
			ID:    1,
			Group: 1,
			Bean:  &nikofmt.ConfigBean{Type: &zero, Config: &cfg},
		}},
		Settings: nikoconf.DesktopSettings(),
	}
	if _, err := nikoconf.Build(in); err == nil {
		t.Fatal("expected ForTest build to refuse raw ConfigBean profile")
	}

	// CustomConfigJson on a regular profile must also be ignored in ForTest mode.
	host := "198.51.100.1"
	port := 1080
	custom := `{"log":{"output":"/tmp/should-not-appear.log"}}`
	in2 := nikoconf.Input{
		Profile: 2,
		ForTest: true,
		Profiles: []nikoconf.Profile{{
			ID:    2,
			Group: 1,
			Bean: &nikofmt.SOCKSBean{
				AbstractBean: nikofmt.AbstractBean{
					ServerAddress:    &host,
					ServerPort:       &port,
					CustomConfigJson: &custom,
				},
			},
		}},
		Settings: nikoconf.DesktopSettings(),
	}
	built, err := nikoconf.Build(in2)
	if err != nil {
		t.Fatalf("ForTest build regular profile: %v", err)
	}
	if bytes.Contains(built.Config, []byte("should-not-appear.log")) {
		t.Fatalf("ForTest build merged CustomConfigJson: %s", built.Config)
	}
}
