package nikostore

import (
	"bytes"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"libcore/nikoconf"
)

// The computer's settings: a computer's defaults at first; what is set kept across an opening, and only that on the
// disk; the others as they were; each change told.
func TestSettingsRoundTrip(t *testing.T) {
	dir := t.TempDir()
	var changes []Change
	s := open(t, dir, NoSealer{}, &changes)
	got, err := s.Settings()
	if err != nil || !reflect.DeepEqual(got, nikoconf.DesktopSettings()) {
		t.Fatalf("a new store's settings: %+v %v", got, err)
	}
	if got.ServiceMode != nikoconf.ModeProxy || got.MixedPort != 2080 || got.SystemProxy != nil {
		t.Fatalf("not a computer's defaults: %+v", got)
	}
	if _, err = os.Stat(filepath.Join(dir, settingsFile)); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("a settings file before any setting: %v", err)
	}

	// only the settings named change: the others of from, its zero values, are not read
	from := nikoconf.Settings{MixedPort: 2081, ServiceMode: nikoconf.ModeVPN, MTU: 1500, RemoteDNS: "tls://1.1.1.1",
		Secrets: nikoconf.Secrets{MixedPassword: "aaaa1111"}}
	got, err = s.UpdateSettings(from, []string{"mixedPort", "serviceMode", "mixedPassword"})
	if err != nil {
		t.Fatal(err)
	}
	want := nikoconf.DesktopSettings()
	want.MixedPort, want.ServiceMode, want.Secrets.MixedPassword = 2081, nikoconf.ModeVPN, "aaaa1111"
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("updated: %+v\nwant %+v", got, want)
	}
	if !reflect.DeepEqual(changes, []Change{{Kind: KindSettings}}) {
		t.Fatalf("changes %+v", changes)
	}
	on := true
	got, err = s.UpdateSettings(nikoconf.Settings{SystemProxy: &on, MTU: 1500}, []string{"systemProxy", "mtu"})
	if err != nil || got.SystemProxy == nil || !*got.SystemProxy || got.MTU != 1500 || got.MixedPort != 2081 {
		t.Fatalf("a second change: %+v %v", got, err)
	}
	want.SystemProxy, want.MTU = &on, 1500

	again := open(t, dir, NoSealer{}, nil)
	if got, err = again.Settings(); err != nil || !reflect.DeepEqual(got, want) {
		t.Fatalf("opened again: %+v %v", got, err)
	}
	// the disk has what was set, no default of this build's
	var doc settingsDocument
	data, _ := os.ReadFile(filepath.Join(dir, settingsFile))
	if err = json.Unmarshal(data, &doc); err != nil {
		t.Fatal(err)
	}
	if doc.Schema != 1 || len(doc.Settings) != 4 || string(doc.Settings["mixedPort"]) != "2081" ||
		string(doc.Secrets["mixedPassword"]) != `"aaaa1111"` || doc.Sealed != "" {
		t.Fatalf("settings.json: %s", data)
	}

	// a password set empty is none: none on the disk
	if got, err = again.UpdateSettings(nikoconf.Settings{}, []string{"mixedPassword"}); err != nil ||
		got.Secrets.MixedPassword != "" {
		t.Fatalf("%+v %v", got, err)
	}
	data, _ = os.ReadFile(filepath.Join(dir, settingsFile))
	if bytes.Contains(data, []byte("mixedPassword")) || bytes.Contains(data, []byte("aaaa1111")) {
		t.Fatalf("an empty password kept: %s", data)
	}
	// nothing named: nothing written, nothing told
	changes = nil
	s = open(t, dir, NoSealer{}, &changes)
	if _, err = s.UpdateSettings(nikoconf.Settings{}, nil); err != nil || changes != nil {
		t.Fatalf("%v %v", changes, err)
	}
}

// What a setting may be: a value out of its range, a name no computer has or one given twice are refused, and nothing
// is written.
func TestSettingsRefused(t *testing.T) {
	dir := t.TempDir()
	var changes []Change
	s := open(t, dir, NoSealer{}, &changes)
	if _, err := s.UpdateSettings(nikoconf.Settings{MixedPort: 3000}, []string{"mixedPort"}); err != nil {
		t.Fatal(err)
	}
	changes = nil
	before, _ := os.ReadFile(filepath.Join(dir, settingsFile))
	str := func(s string) *string { return &s }
	for _, c := range []struct {
		name string
		from nikoconf.Settings
	}{
		{"mixedPort", nikoconf.Settings{MixedPort: 0}},
		{"mixedPort", nikoconf.Settings{MixedPort: 65536}},
		{"mtu", nikoconf.Settings{MTU: 999}},
		{"mtu", nikoconf.Settings{MTU: 10001}},
		{"serviceMode", nikoconf.Settings{ServiceMode: ""}},
		{"serviceMode", nikoconf.Settings{ServiceMode: "tproxy"}},
		{"tunImplementation", nikoconf.Settings{TunImplementation: 3}},
		{"ipv6Mode", nikoconf.Settings{IPv6Mode: -1}},
		{"logLevel", nikoconf.Settings{LogLevel: 5}},
		{"trafficSniffing", nikoconf.Settings{TrafficSniffing: 2}},
		{"remoteDns", nikoconf.Settings{RemoteDNS: ""}},
		{"remoteDns", nikoconf.Settings{RemoteDNS: "# only a comment"}},
		{"remoteDns", nikoconf.Settings{RemoteDNS: "https://dns.google/dns-query\nftp://dns.example"}},
		{"directDns", nikoconf.Settings{DirectDNS: "tls://dns.example/path"}},
		{"directDns", nikoconf.Settings{DirectDNS: "udp://1.1.1.1:70000"}},
		{"domainStrategyForRemote", nikoconf.Settings{DomainStrategyForRemote: str("AsIs")}},
		{"mixedUsername", nikoconf.Settings{MixedUsername: "a:b"}},
		{"mixedUsername", nikoconf.Settings{MixedUsername: strings.Repeat("a", 256)}},
		{"mixedPassword", nikoconf.Settings{Secrets: nikoconf.Secrets{MixedPassword: "aaaa\n1111"}}},
		{"yggdrasilEnabled", nikoconf.Settings{YggdrasilEnabled: true}},
		{"enableClashAPI", nikoconf.Settings{EnableClashAPI: true}},
		{"secrets", nikoconf.Settings{}},
		{"", nikoconf.Settings{}},
	} {
		if _, err := s.UpdateSettings(c.from, []string{c.name}); !errors.Is(err, ErrInvalid) {
			t.Errorf("%s %+v: %v", c.name, c.from, err)
		}
	}
	// twice, or a good one beside a bad one: neither
	if _, err := s.UpdateSettings(nikoconf.Settings{MTU: 1500}, []string{"mtu", "mtu"}); !errors.Is(err, ErrInvalid) {
		t.Errorf("twice: %v", err)
	}
	if _, err := s.UpdateSettings(nikoconf.Settings{MTU: 1500}, []string{"mtu", "mixedPort"}); !errors.Is(err, ErrInvalid) {
		t.Errorf("with a bad one: %v", err)
	}
	after, _ := os.ReadFile(filepath.Join(dir, settingsFile))
	if !bytes.Equal(before, after) || changes != nil {
		t.Fatalf("written: %s, told %v", after, changes)
	}
	if got, _ := s.Settings(); got.MTU != 9000 || got.MixedPort != 3000 {
		t.Fatalf("changed: %+v", got)
	}

	// what a computer may have: every DNS transport of the build, a domain strategy reset to none
	str2 := str("prefer_ipv6")
	good := nikoconf.Settings{RemoteDNS: "# mine\nquic://dns.adguard-dns.com\nhttps://1.1.1.1/dns-query",
		DirectDNS: "local", DomainStrategyForDirect: str2, MixedUsername: "niko user", MTU: 1000}
	got, err := s.UpdateSettings(good, []string{"remoteDns", "directDns", "domainStrategyForDirect", "mixedUsername", "mtu"})
	if err != nil || got.DomainStrategyForDirect == nil || *got.DomainStrategyForDirect != "prefer_ipv6" {
		t.Fatalf("%+v %v", got, err)
	}
	if got, err = s.UpdateSettings(nikoconf.Settings{}, []string{"domainStrategyForDirect"}); err != nil ||
		got.DomainStrategyForDirect != nil {
		t.Fatalf("reset: %+v %v", got, err)
	}
}

// The secret settings on the disk: sealed where the store seals, never in the open there; plain ones of a file the
// platform did not seal are sealed at the opening; those of another machine are refused until set again.
func TestSettingsSealed(t *testing.T) {
	dir := t.TempDir()
	sealer := testSealer{"test"}
	s := open(t, dir, sealer, nil)
	from := nikoconf.Settings{MixedUsername: "niko", Secrets: nikoconf.Secrets{MixedPassword: "aaaa1111"}}
	if _, err := s.UpdateSettings(from, []string{"mixedUsername", "mixedPassword"}); err != nil {
		t.Fatal(err)
	}
	data, _ := os.ReadFile(filepath.Join(dir, settingsFile))
	if bytes.Contains(data, []byte("aaaa1111")) || !bytes.Contains(data, []byte(`"sealed": "test:`)) ||
		bytes.Contains(data, []byte("mixedPassword")) {
		t.Fatalf("settings.json: %s", data)
	}
	if got, err := open(t, dir, sealer, nil).Settings(); err != nil || got.Secrets.MixedPassword != "aaaa1111" {
		t.Fatalf("opened again: %+v %v", got, err)
	}

	// a file of a platform that seals nothing, opened where it seals: sealed then
	plain := t.TempDir()
	if _, err := open(t, plain, NoSealer{}, nil).UpdateSettings(from, []string{"mixedPassword"}); err != nil {
		t.Fatal(err)
	}
	if got, err := open(t, plain, sealer, nil).Settings(); err != nil || got.Secrets.MixedPassword != "aaaa1111" {
		t.Fatalf("%+v %v", got, err)
	}
	if data, _ = os.ReadFile(filepath.Join(plain, settingsFile)); bytes.Contains(data, []byte("aaaa1111")) {
		t.Fatalf("not sealed at the opening: %s", data)
	}

	// another machine's: refused, kept as it is, until the password is set again
	sealed, _ := os.ReadFile(filepath.Join(dir, settingsFile))
	other := open(t, dir, testSealer{"other"}, nil)
	if _, err := other.Settings(); !errors.Is(err, ErrRefused) {
		t.Fatalf("another machine's secrets: %v", err)
	}
	if _, err := other.UpdateSettings(nikoconf.Settings{MixedPort: 3000}, []string{"mixedPort"}); !errors.Is(err, ErrRefused) {
		t.Fatalf("a change beside them: %v", err)
	}
	if after, _ := os.ReadFile(filepath.Join(dir, settingsFile)); !bytes.Equal(after, sealed) {
		t.Fatalf("their seal changed: %s", after)
	}
	got, err := other.UpdateSettings(nikoconf.Settings{Secrets: nikoconf.Secrets{MixedPassword: "bbbb2222"}},
		[]string{"mixedPassword"})
	if err != nil || got.Secrets.MixedPassword != "bbbb2222" || got.MixedUsername != "niko" {
		t.Fatalf("set again: %+v %v", got, err)
	}
	if data, _ = os.ReadFile(filepath.Join(dir, settingsFile)); !bytes.Contains(data, []byte(`"sealed": "other:`)) {
		t.Fatalf("sealed again: %s", data)
	}
}

// settings.json of a newer Niko, or one broken: the store does not open, nor write over it; names of a newer build of
// the same schema are kept and not read.
func TestSettingsFile(t *testing.T) {
	dir := t.TempDir()
	open(t, dir, NoSealer{}, nil)
	path := filepath.Join(dir, settingsFile)
	newer := []byte(`{"schema": 2, "settings": {}}`)
	if err := os.WriteFile(path, newer, 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := Open(dir, Options{Sealer: NoSealer{}}); !errors.Is(err, ErrNewerSchema) {
		t.Fatalf("a newer one: %v", err)
	}
	if err := os.WriteFile(path, []byte(`{"schema": 1, "settings": [`), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := Open(dir, Options{Sealer: NoSealer{}}); err == nil {
		t.Fatal("a broken one opened")
	}
	if err := os.WriteFile(path, []byte(`{"schema": 1, "settings": {"mixedPort": 2090, "yggdrasilEnabled": true,
		"fromLater": "x"}}`), 0o600); err != nil {
		t.Fatal(err)
	}
	s := open(t, dir, NoSealer{}, nil)
	if got, err := s.Settings(); err != nil || got.MixedPort != 2090 || got.YggdrasilEnabled {
		t.Fatalf("%+v %v", got, err)
	}
	if _, err := s.UpdateSettings(nikoconf.Settings{MTU: 1500}, []string{"mtu"}); err != nil {
		t.Fatal(err)
	}
	if data, _ := os.ReadFile(path); !bytes.Contains(data, []byte(`"fromLater": "x"`)) {
		t.Fatalf("a newer build's setting lost: %s", data)
	}
}

// settingsNow are the store's settings, or the test fails.
func (s *Store) settingsNow(t *testing.T) nikoconf.Settings {
	t.Helper()
	got, err := s.Settings()
	if err != nil {
		t.Fatal(err)
	}
	return got
}
