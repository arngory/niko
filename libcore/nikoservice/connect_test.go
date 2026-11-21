package nikoservice

import (
	"context"
	"encoding/json"
	"strings"
	"testing"

	"libcore/nikopb"

	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/option"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// a Hysteria 1 profile over faketcp: the app runs a plugin for it
const pluginLink = "hysteria://hy.example.org:443?protocol=faketcp&auth=x#plugin"

// Connect builds the config of the selected profile, or of the one asked for, and gives it to the daemon's start; what
// it refuses, and how.
func TestConnect(t *testing.T) {
	var started []string
	var refusal error
	s := storeServer(t.TempDir())
	s.start = func(_ context.Context, config string) error {
		started = append(started, config)
		return refusal
	}
	client := serve(t, s)
	ctx := context.Background()
	connect := func(id int64) error {
		t.Helper()
		_, err := client.Connect(ctx, &nikopb.ConnectRequest{ProfileId: id})
		return err
	}

	if err := connect(0); code(err) != codes.FailedPrecondition {
		t.Fatalf("none selected: %v", err)
	}
	if ids := runImport(t, client, naiveLink+"\n"+socksLink+"\n"+pluginLink, 0).Ids; len(ids) != 3 {
		t.Fatalf("imported %v", ids)
	}
	if _, err := client.Select(ctx, &nikopb.ProfileId{Id: 2}); err != nil {
		t.Fatal(err)
	}

	// the selected profile: its outbound the proxy, the local proxy of proxy mode without a login, no TUN
	if err := connect(0); err != nil {
		t.Fatal(err)
	}
	config := parseConfig(t, started[len(started)-1])
	if out := config.Outbounds[0]; out["tag"] != "proxy" || out["type"] != "socks" || out["server"] != "198.51.100.40" ||
		out["server_port"] != 1080.0 || out["username"] != "user" {
		t.Fatalf("the outbound: %v", out)
	}
	if len(config.Inbounds) != 1 {
		t.Fatalf("the inbounds: %v", config.Inbounds)
	}
	if in := config.Inbounds[0]; in["type"] != "mixed" || in["tag"] != "mixed-in" || in["listen"] != "127.0.0.1" ||
		in["listen_port"] != 2080.0 || in["users"] != nil {
		t.Fatalf("the local proxy: %v", in)
	}
	if config.Route["final"] != "proxy" {
		t.Fatalf("the route: %v", config.Route)
	}
	// a config the core reads
	var options option.Options
	if err := options.UnmarshalJSONContext(include.Context(context.Background()), []byte(started[0])); err != nil {
		t.Fatalf("the core does not read it: %v", err)
	}

	// another profile by its id, the selection as it was
	if err := connect(1); err != nil {
		t.Fatal(err)
	}
	if out := parseConfig(t, started[len(started)-1]).Outbounds[0]; out["type"] != "naive" || out["server"] != "203.0.113.7" {
		t.Fatalf("the profile asked for: %v", out)
	}
	if got, _ := client.GetSelected(ctx, &nikopb.Empty{}); got.Id != 2 {
		t.Fatalf("selected: %v", got)
	}

	// what is refused before the start: no such profile, a plugin's, one the build refuses
	before := len(started)
	if err := connect(7); code(err) != codes.NotFound {
		t.Errorf("no such profile: %v", err)
	}
	if err := connect(3); code(err) != codes.Unimplemented {
		t.Errorf("a plugin's profile: %v", err)
	}
	p, err := client.GetProfile(ctx, &nikopb.ProfileId{Id: 2})
	if err != nil {
		t.Fatal(err)
	}
	knock := strings.Replace(p.BeanJson, `"knockSequence":""`, `"knockSequence":"tcp:7000"`, 1)
	if knock == p.BeanJson {
		t.Fatalf("no knockSequence in %s", p.BeanJson)
	}
	if _, err = client.SaveProfile(ctx, &nikopb.Profile{Id: 2, Type: "socks", BeanJson: knock}); err != nil {
		t.Fatal(err)
	}
	if err = connect(2); code(err) != codes.InvalidArgument || !strings.Contains(err.Error(), "knocking") {
		t.Errorf("a profile the build refuses: %v", err)
	}
	if len(started) != before {
		t.Fatalf("started %d times more", len(started)-before)
	}

	// the daemon's refusal as it gave it: another user's
	refusal = status.Error(codes.PermissionDenied, "the service is owned by another user")
	if err = connect(1); code(err) != codes.PermissionDenied {
		t.Errorf("the daemon's refusal: %v", err)
	}
	// no daemon behind the service: nothing to start with
	s.start = nil
	if err = connect(1); code(err) != codes.Unimplemented {
		t.Errorf("no daemon: %v", err)
	}
}

type builtConfig struct {
	Inbounds  []map[string]any `json:"inbounds"`
	Outbounds []map[string]any `json:"outbounds"`
	Route     map[string]any   `json:"route"`
}

func parseConfig(t *testing.T, config string) builtConfig {
	t.Helper()
	var c builtConfig
	if err := json.Unmarshal([]byte(config), &c); err != nil {
		t.Fatalf("%v: %s", err, config)
	}
	return c
}
