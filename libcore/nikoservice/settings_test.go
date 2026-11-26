package nikoservice

import (
	"context"
	"os"
	"path/filepath"
	"reflect"
	"runtime"
	"slices"
	"strings"
	"testing"
	"time"

	"libcore/nikoconf"
	"libcore/nikopb"
	"libcore/nikostore"

	"github.com/sagernet/sing-box/experimental/boxdd"
	"google.golang.org/grpc/codes"
	"google.golang.org/protobuf/proto"
)

// Settings' fields are the computer's settings the store keeps, named as the app names them (their JSON names).
func TestSettingsFields(t *testing.T) {
	var names []string
	fields := (&nikopb.Settings{}).ProtoReflect().Descriptor().Fields()
	for i := range fields.Len() {
		names = append(names, fields.Get(i).JSONName())
	}
	slices.Sort(names)
	if want := nikostore.SettingNames(); !slices.Equal(names, want) {
		t.Fatalf("Settings: %v\nthe store's: %v", names, want)
	}
	// a computer's defaults give themselves back
	defaults := nikoconf.DesktopSettings()
	if back := settingsFrom(settingsProto(defaults)); back.MixedPort != 2080 || back.ServiceMode != "proxy" ||
		back.DomainStrategyForRemote != nil || back.RemoteDNS != defaults.RemoteDNS {
		t.Fatalf("%+v", back)
	}
}

// GetSettings and UpdateSettings: the defaults, the fields named alone changed, what is refused, SETTINGS in Watch.
func TestSettingsCalls(t *testing.T) {
	client := serve(t, storeServer(t.TempDir()))
	ctx := context.Background()
	watch := changes(t, client)
	if c := <-watch; !c.Reset_ {
		t.Fatalf("first: %v", c)
	}

	got, err := client.GetSettings(ctx, &nikopb.Empty{})
	if err != nil {
		t.Fatal(err)
	}
	// the system proxy on by default on Windows alone
	want := &nikopb.Settings{ServiceMode: "proxy", Mtu: 9000,
		RemoteDns: "https://cloudflare-dns.com/dns-query\nhttps://dns.quad9.net/dns-query", DirectDns: "local",
		EnableFakeDns: true, MixedPort: 2080, TrafficSniffing: 1, SystemProxy: runtime.GOOS == "windows",
		EnableDnsRouting: true, RuleSetUpdateHours: 24, DpiDirectPreset: "tlsrec_sni", RaycontrolAutoSwitch: true}
	if !proto.Equal(got, want) {
		t.Fatalf("the defaults: %v", got)
	}

	values := &nikopb.Settings{MixedPort: 2081, MixedPassword: "aaaa1111", Mtu: 1, LogLevel: 3,
		DomainStrategyForServer: "ipv4_only"}
	got, err = client.UpdateSettings(ctx, &nikopb.SettingsUpdate{Settings: values,
		Fields: []string{"mixed_port", "mixed_password", "log_level", "domain_strategy_for_server"}})
	if err != nil {
		t.Fatal(err)
	}
	want.MixedPort, want.MixedPassword, want.LogLevel, want.DomainStrategyForServer = 2081, "aaaa1111", 3, "ipv4_only"
	if !proto.Equal(got, want) {
		t.Fatalf("updated: %v", got)
	}
	select {
	case c := <-watch:
		if c.Kind != nikopb.Change_SETTINGS || len(c.Ids) != 0 {
			t.Fatalf("the change: %v", c)
		}
	case <-time.After(5 * time.Second):
		t.Fatal("no SETTINGS in Watch")
	}
	if got, _ = client.GetSettings(ctx, &nikopb.Empty{}); !proto.Equal(got, want) {
		t.Fatalf("read again: %v", got)
	}

	for _, c := range []*nikopb.SettingsUpdate{
		{Settings: values, Fields: []string{"mtu"}},
		{Settings: values, Fields: []string{"mixedPort"}},
		{Settings: values, Fields: []string{"no_such"}},
		{Settings: &nikopb.Settings{ServiceMode: "tun"}, Fields: []string{"service_mode"}},
		{Settings: &nikopb.Settings{RemoteDns: "dns.example/x"}, Fields: []string{"remote_dns"}},
		{Fields: []string{"mixed_port"}},
	} {
		if _, err = client.UpdateSettings(ctx, c); code(err) != codes.InvalidArgument {
			t.Errorf("%v: %v", c, err)
		}
	}
	if got, _ = client.GetSettings(ctx, &nikopb.Empty{}); !proto.Equal(got, want) {
		t.Fatalf("changed by what was refused: %v", got)
	}
}

// Connect builds with the stored settings: the local proxy's port, login and address, the log, the DNS; VPN mode the
// settings' where the request asks none, with their TUN; a mode asked for over theirs.
func TestConnectSettings(t *testing.T) {
	var started []string
	var proxies []*boxdd.SystemProxy
	root := t.TempDir()
	s := storeServer(root)
	s.start = func(_ context.Context, config string, systemProxy *boxdd.SystemProxy) error {
		started = append(started, config)
		proxies = append(proxies, systemProxy)
		return nil
	}
	client := serve(t, s)
	ctx := context.Background()
	if ids := runImport(t, client, socksLink, 0).Ids; len(ids) != 1 {
		t.Fatalf("imported %v", ids)
	}
	if _, err := client.Select(ctx, &nikopb.ProfileId{Id: 1}); err != nil {
		t.Fatal(err)
	}
	update := func(values *nikopb.Settings, fields ...string) {
		t.Helper()
		if _, err := client.UpdateSettings(ctx, &nikopb.SettingsUpdate{Settings: values, Fields: fields}); err != nil {
			t.Fatal(err)
		}
	}
	update(&nikopb.Settings{MixedPort: 2081, MixedUsername: "niko", MixedPassword: "aaaa1111", AllowAccess: true,
		LogLevel: 1, RemoteDns: "tls://1.1.1.1", DnsOptimisticCache: true, BypassLanInCore: true},
		"mixed_port", "mixed_username", "mixed_password", "allow_access", "log_level", "remote_dns",
		"dns_optimistic_cache", "bypass_lan_in_core")

	if _, err := client.Connect(ctx, &nikopb.ConnectRequest{}); err != nil {
		t.Fatal(err)
	}
	config := parseConfig(t, started[len(started)-1])
	if len(config.Inbounds) != 1 {
		t.Fatalf("the inbounds: %v", config.Inbounds)
	}
	in := config.Inbounds[0]
	users, _ := in["users"].([]any)
	// the user's login after the app's own, as the app has them
	if in["type"] != "mixed" || in["listen"] != "0.0.0.0" || in["listen_port"] != 2081.0 || len(users) != 2 {
		t.Fatalf("the local proxy: %v", in)
	}
	if user := users[1].(map[string]any); user["username"] != "niko" || user["password"] != "aaaa1111" {
		t.Fatalf("its login: %v", user)
	}
	coreReads(t, started[len(started)-1])
	// the system proxy by the platform's default: on Windows the local proxy, for the daemon to set for its owner; the
	// config itself never sets it (the core would, for the user it runs as)
	var want *boxdd.SystemProxy
	if runtime.GOOS == "windows" {
		want = &boxdd.SystemProxy{Server: "127.0.0.1", Port: 2081}
	}
	if got := proxies[len(proxies)-1]; !reflect.DeepEqual(got, want) || in["set_system_proxy"] != nil {
		t.Fatalf("the system proxy %+v, want %+v; the inbound's %v", got, want, in["set_system_proxy"])
	}
	if config.Log["level"] != "warn" || config.DNS["optimistic"] != true {
		t.Fatalf("the log %v, the DNS %v", config.Log, config.DNS)
	}
	if !strings.Contains(started[len(started)-1], `"server": "1.1.1.1"`) ||
		!strings.Contains(started[len(started)-1], `"ip_is_private": true`) {
		t.Fatalf("the remote DNS or the LAN's rule missing: %s", started[len(started)-1])
	}

	// VPN mode by the settings, its TUN theirs; the local proxy beside it, for the user's login; the app's own login
	// never one of an empty password
	update(&nikopb.Settings{ServiceMode: "vpn", TunImplementation: 1, Mtu: 1500, Ipv6Mode: 1},
		"service_mode", "tun_implementation", "mtu", "ipv6_mode")
	if _, err := client.Connect(ctx, &nikopb.ConnectRequest{}); err != nil {
		t.Fatal(err)
	}
	config = parseConfig(t, started[len(started)-1])
	if len(config.Inbounds) != 2 {
		t.Fatalf("the inbounds: %v", config.Inbounds)
	}
	coreReads(t, started[len(started)-1])
	tun := config.Inbounds[0]
	if tun["type"] != "tun" || tun["stack"] != "system" || tun["mtu"] != 1500.0 || len(tun["address"].([]any)) != 2 {
		t.Fatalf("the TUN: %v", tun)
	}
	users, _ = config.Inbounds[1]["users"].([]any)
	if len(users) != 2 {
		t.Fatalf("the local proxy's logins: %v", config.Inbounds[1])
	}
	app := users[0].(map[string]any)
	if app["username"] != "nekobox-app" || len(app["password"].(string)) < 16 {
		t.Fatalf("the app's own login: %v", app)
	}

	// the mode asked for over the settings'
	if _, err := client.Connect(ctx, &nikopb.ConnectRequest{Mode: nikopb.ConnectRequest_PROXY.Enum()}); err != nil {
		t.Fatal(err)
	}
	if config = parseConfig(t, started[len(started)-1]); len(config.Inbounds) != 1 || config.Inbounds[0]["type"] != "mixed" {
		t.Fatalf("proxy mode asked for: %v", config.Inbounds)
	}
	// the system proxy as set, in proxy mode alone; GetSettings says it as it is
	update(&nikopb.Settings{SystemProxy: true}, "system_proxy")
	if _, err := client.Connect(ctx, &nikopb.ConnectRequest{Mode: nikopb.ConnectRequest_PROXY.Enum()}); err != nil {
		t.Fatal(err)
	}
	if got := proxies[len(proxies)-1]; got == nil || got.Server != "127.0.0.1" || got.Port != 2081 {
		t.Fatalf("the system proxy turned on: %+v", got)
	}
	if _, err := client.Connect(ctx, &nikopb.ConnectRequest{}); err != nil {
		t.Fatal(err)
	}
	if got := proxies[len(proxies)-1]; got != nil {
		t.Fatalf("a system proxy in VPN mode: %+v", got)
	}
	update(&nikopb.Settings{SystemProxy: false}, "system_proxy")
	if got, err := client.GetSettings(ctx, &nikopb.Empty{}); err != nil || got.SystemProxy {
		t.Fatalf("turned off: %v %v", got, err)
	}
	if _, err := client.Connect(ctx, &nikopb.ConnectRequest{Mode: nikopb.ConnectRequest_PROXY.Enum()}); err != nil {
		t.Fatal(err)
	}
	if got := proxies[len(proxies)-1]; got != nil {
		t.Fatalf("the system proxy turned off: %+v", got)
	}

	// only the programs chosen through Niko: their rule first, the rest direct
	update(&nikopb.Settings{ProgramsMode: 1, Programs: "curl\n"}, "programs_mode", "programs")
	if _, err := client.Connect(ctx, &nikopb.ConnectRequest{}); err != nil {
		t.Fatal(err)
	}
	config = parseConfig(t, started[len(started)-1])
	found := false
	for _, r := range config.Route["rules"].([]any) {
		rule := r.(map[string]any)
		if names, _ := rule["process_name"].([]any); len(names) == 1 && names[0] == "curl" {
			found = rule["outbound"] == "proxy"
			break
		}
	}
	if !found || config.Route["final"] != "bypass" || config.DNS["final"] != "dns-direct" {
		t.Fatalf("the programs' rule or the rest direct missing: %v", config.Route)
	}

	// the password on the disk is not in the open where the store seals
	sealed := storeServer(t.TempDir())
	sealed.users.sealer = testSealer{}
	other := serve(t, sealed)
	if _, err := other.UpdateSettings(ctx, &nikopb.SettingsUpdate{Settings: &nikopb.Settings{MixedPassword: "aaaa1111"},
		Fields: []string{"mixed_password"}}); err != nil {
		t.Fatal(err)
	}
	files, _ := filepath.Glob(filepath.Join(sealed.users.root, "users", "*", "niko", "settings.json"))
	if len(files) != 1 {
		t.Fatalf("settings.json: %v", files)
	}
	if data, _ := os.ReadFile(files[0]); strings.Contains(string(data), "aaaa1111") {
		t.Fatalf("the password in the open: %s", data)
	}
}

// testSealer seals by flipping bits: what DPAPI does, as far as the store can tell.
type testSealer struct{}

func (testSealer) Name() string { return "test" }

func (testSealer) Seal(plain []byte) ([]byte, error) {
	out := append([]byte(nil), plain...)
	for i := range out {
		out[i] ^= 0x5a
	}
	return out, nil
}

func (s testSealer) Open(sealed []byte) ([]byte, error) { return s.Seal(sealed) }
