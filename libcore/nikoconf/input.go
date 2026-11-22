package nikoconf

import (
	"encoding/json"
	"errors"
	"strings"

	"libcore/nikofmt"
)

// What Build reads (app/src/main/java/io/niko/fmt/ConfigInput.kt): the profile, the settings, the stored profiles,
// groups and rules, and what only the device knows. No raycontrol node: the app's ConfigRay.None. As JSON it is the
// golden fixtures' input.json (shared/golden/config), but for the profiles' beans, which the fixtures give as links.

// Input is one build's input, a ConfigInput with ConfigRay.None, and buildConfig's flags.
type Input struct {
	// Profile is the id of the profile built
	Profile  int64     `json:"profile"`
	Profiles []Profile `json:"profiles"`
	Groups   []Group   `json:"groups"`
	Rules    []Rule    `json:"rules"`
	Settings Settings  `json:"settings"`
	// Apps (package name → uid) and PrivateDNS (the strict Private DNS host) are what the default device answers
	Apps       map[string]int `json:"apps"`
	PrivateDNS *string        `json:"private_dns"`
	ForTest    bool           `json:"for_test"`
	ForExport  bool           `json:"for_export"`
	// Platform is the system the config is for: PlatformAndroid ("" too), PlatformWindows or PlatformLinux. The app
	// builds Android's alone; a computer's parts (desktop.go) are Go's, held by the golden cases' windows.json and
	// linux.json
	Platform string `json:"platform"`
	// Device: nil for a FixedDevice of Apps and PrivateDNS
	Device Device `json:"-"`
}

// The platforms a config is built for.
const (
	PlatformAndroid = "android"
	PlatformWindows = "windows"
	PlatformLinux   = "linux"
)

// UnmarshalJSON reads an input.json: settings over the app's defaults, also where it has none.
func (in *Input) UnmarshalJSON(data []byte) error {
	type plain Input
	p := plain{Settings: DefaultSettings()}
	if err := json.Unmarshal(data, &p); err != nil {
		return err
	}
	*in = Input(p)
	return nil
}

// Profile is a stored profile (ProxyEntity): its place and its bean.
type Profile struct {
	ID    int64 `json:"id"`
	Group int64 `json:"group"`
	Order int64 `json:"order"`
	// Type is TypeMap's name of the bean's type, as nikostore keeps it ("" where only Bean is given)
	Type string       `json:"type,omitempty"`
	Bean nikofmt.Bean `json:"-"`
}

// UnmarshalJSON reads a profile, its bean from "bean" as nikostore keeps it (nikofmt.DecodeBean of "type"); a
// golden input has none there, its test makes the bean of the link.
func (p *Profile) UnmarshalJSON(data []byte) error {
	var v struct {
		ID    int64           `json:"id"`
		Group int64           `json:"group"`
		Order int64           `json:"order"`
		Type  string          `json:"type"`
		Bean  json.RawMessage `json:"bean"`
	}
	if err := json.Unmarshal(data, &v); err != nil {
		return err
	}
	*p = Profile{ID: v.ID, Group: v.Group, Order: v.Order, Type: v.Type}
	if len(v.Bean) > 0 && string(v.Bean) != "null" {
		bean, err := nikofmt.DecodeBean(v.Type, v.Bean)
		if err != nil {
			return err
		}
		p.Bean = bean
	}
	return nil
}

// Group is a ProxyGroup, its fields the build reads and their names.
type Group struct {
	ID        int64   `json:"id"`
	UserOrder int64   `json:"userOrder"`
	Ungrouped bool    `json:"ungrouped"`
	Name      *string `json:"name"`
	Type      int     `json:"type"`
	// Order is how the group sorts its profiles (GroupOrder), not their order
	Order      int  `json:"order"`
	IsSelector bool `json:"isSelector"`
	// FrontProxy and LandingProxy: a profile's id, -1 for none
	FrontProxy   int64 `json:"frontProxy"`
	LandingProxy int64 `json:"landingProxy"`
	// AutoSelect: urltest instead of the selector
	AutoSelect bool `json:"autoSelect"`
}

// UnmarshalJSON reads a group over ProxyGroup's defaults.
func (g *Group) UnmarshalJSON(data []byte) error {
	type plain Group
	p := plain{FrontProxy: -1, LandingProxy: -1}
	if err := json.Unmarshal(data, &p); err != nil {
		return err
	}
	*g = Group(p)
	return nil
}

// Rule is a route rule (RuleEntity). Outbound: 0 the proxy, -1 bypass, -2 block, else a profile's id.
type Rule struct {
	ID         int64    `json:"id"`
	Name       string   `json:"name"`
	Config     string   `json:"config"`
	UserOrder  int64    `json:"userOrder"`
	Enabled    bool     `json:"enabled"`
	Domains    string   `json:"domains"`
	IP         string   `json:"ip"`
	Port       string   `json:"port"`
	SourcePort string   `json:"sourcePort"`
	Network    string   `json:"network"`
	Source     string   `json:"source"`
	Protocol   string   `json:"protocol"`
	Outbound   int64    `json:"outbound"`
	Packages   []string `json:"packages"` // a set: the first of each name counts
	// Programs are a computer's apps (D-RULES-02): a process's name ("firefox.exe") or its full path, as the
	// phone's rules have Packages; a computer's own, the app has no such field and Android's build reads none
	Programs []string `json:"programs"`
}

// displayName is RuleEntity.displayName.
func (r *Rule) displayName() string {
	if !nikofmt.KtIsBlank(r.Name) {
		return r.Name
	}
	return "Rule " + itoa64(r.ID)
}

// Settings are the settings the build reads (ConfigSettings), named as the app names them.
type Settings struct {
	ServiceMode        string `json:"serviceMode"`
	AllowAccess        bool   `json:"allowAccess"`
	RemoteDNS          string `json:"remoteDns"`
	DirectDNS          string `json:"directDns"`
	EnableDNSRouting   bool   `json:"enableDnsRouting"`
	EnableFakeDNS      bool   `json:"enableFakeDns"`
	DNSOptimisticCache bool   `json:"dnsOptimisticCache"`
	TrafficSniffing    int    `json:"trafficSniffing"`
	ResolveDestination bool   `json:"resolveDestination"`
	IPv6Mode           int    `json:"ipv6Mode"`
	// the domain strategies as stored, nil for none (see domainStrategy)
	DomainStrategyForRemote *string `json:"domainStrategyForRemote"`
	DomainStrategyForDirect *string `json:"domainStrategyForDirect"`
	DomainStrategyForServer *string `json:"domainStrategyForServer"`
	EnableClashAPI          bool    `json:"enableClashAPI"`
	LogLevel                int     `json:"logLevel"`
	TunImplementation       int     `json:"tunImplementation"`
	MTU                     int     `json:"mtu"`
	MixedPort               int     `json:"mixedPort"`
	MixedUsername           string  `json:"mixedUsername"`
	LocalProxyNoAuthInVpn   bool    `json:"localProxyNoAuthInVpn"`
	ProxyApps               bool    `json:"proxyApps"`
	BypassLanInCore         bool    `json:"bypassLanInCore"`
	YggdrasilEnabled        bool    `json:"yggdrasilEnabled"`
	YggdrasilPeers          string  `json:"yggdrasilPeers"`
	YggdrasilTLSChrome      bool    `json:"yggdrasilTlsChrome"`
	YggdrasilAllowInbound   bool    `json:"yggdrasilAllowInbound"`
	YggdrasilInboundPorts   string  `json:"yggdrasilInboundPorts"`
	YggdrasilPeersViaProxy  bool    `json:"yggdrasilPeersViaProxy"`
	LanInboundPorts         string  `json:"lanInboundPorts"`
	HonestConnect           bool    `json:"honestConnect"`
	RootStore               string  `json:"rootStore"`
	NaiveQuicPhoneRoots     bool    `json:"naiveQuicPhoneRoots"`
	IcmpThroughNode         bool    `json:"icmpThroughNode"`
	IcmpNoLocalReply        bool    `json:"icmpNoLocalReply"`
	RefuseQuicOverStreams   bool    `json:"refuseQuicOverStreams"`
	TLSFragmentBypass       int     `json:"tlsFragmentBypass"`
	ConnectionTestURL       string  `json:"connectionTestURL"`
	RuleSetUpdateHours      int     `json:"ruleSetUpdateHours"`
	GlobalAllowInsecure     bool    `json:"globalAllowInsecure"`
	GlobalCustomConfig      string  `json:"globalCustomConfig"`
	Secrets                 Secrets `json:"secrets"`
	// SystemProxy, a computer's own (D-NAV-02): in proxy mode the local proxy is the system's while the core runs;
	// nil for the default, on Windows on (SystemProxyOn)
	SystemProxy *bool `json:"systemProxy"`
	// ProgramsMode and Programs, a computer's own (D-RULES-06), what the phone's per-app proxy is: which programs go
	// through Niko, ProgramsAll, ProgramsOnly or ProgramsExcept those of Programs, a name or a full path a line, as a
	// rule's programs are (desktop.go)
	ProgramsMode int    `json:"programsMode"`
	Programs     string `json:"programs"`
}

// Secrets are the install's secrets a config may carry (ConfigSecrets).
type Secrets struct {
	ClashAPI           string `json:"clashApi"`
	LocalProxyPassword string `json:"localProxyPassword"`
	// MixedPassword is the user's own password for the local proxy, "" for none
	MixedPassword string `json:"mixedPassword"`
	YggdrasilKey  string `json:"yggdrasilKey"`
}

// The settings' values the build tells apart (Key, IPv6Mode, TunImplementation).
const (
	ModeVPN   = "vpn"
	ModeProxy = "proxy"

	ipv6Disable = 0
	ipv6Enable  = 1
	ipv6Prefer  = 2
	ipv6Only    = 3

	tunGVisor = 0
	tunSystem = 1

	ProgramsAll    = 0
	ProgramsOnly   = 1
	ProgramsExcept = 2
)

// defaultRemoteDNS is the remote DNS of the defaults: Cloudflare's DoH, Quad9's when it fails (a line each, the
// config's fallback server between them).
const defaultRemoteDNS = "https://cloudflare-dns.com/dns-query\nhttps://dns.quad9.net/dns-query"

// DefaultSettings are ConfigSettings' defaults, the app's.
func DefaultSettings() Settings {
	return Settings{
		ServiceMode:        ModeVPN,
		RemoteDNS:          defaultRemoteDNS,
		DirectDNS:          "local",
		EnableDNSRouting:   true,
		EnableFakeDNS:      true,
		TrafficSniffing:    1,
		IPv6Mode:           ipv6Disable,
		TunImplementation:  tunGVisor,
		MTU:                9000,
		MixedPort:          2080,
		YggdrasilTLSChrome: true,
		RootStore:          "chrome",
		ConnectionTestURL:  "http://cp.cloudflare.com/",
		RuleSetUpdateHours: 24,
	}
}

// UnmarshalJSON reads settings over the defaults.
func (s *Settings) UnmarshalJSON(data []byte) error {
	type plain Settings
	p := plain(DefaultSettings())
	if err := json.Unmarshal(data, &p); err != nil {
		return err
	}
	*s = Settings(p)
	return nil
}

func (s *Settings) isVPN() bool { return s.ServiceMode == ModeVPN }

// domainStrategy is ConfigSettings.domainStrategy: the strategy the user set for a DNS server's tag, "" for the
// core's own; "auto" is none, for the servers' addresses prefer_ipv4.
func (s *Settings) domainStrategy(tag string) string {
	switch tag {
	case "dns-remote":
		return strings.ReplaceAll(str(s.DomainStrategyForRemote), "auto", "")
	case "dns-direct":
		return strings.ReplaceAll(str(s.DomainStrategyForDirect), "auto", "")
	}
	return strings.ReplaceAll(str(s.DomainStrategyForServer), "auto", "prefer_ipv4")
}

// Device is what only the device knows (ConfigDevice), but the warnings, which Result holds.
type Device interface {
	// Port is a free local port; the plugins' and slipstream's paths take them, not ported yet
	Port() int
	// UID is the uid of an installed app
	UID(packageName string) (int, bool)
	// StrictPrivateDNSHost is the host of strict Private DNS, if set
	StrictPrivateDNSHost() (string, bool)
	// ProtectPathReady: libcore's protect server listens (it runs with the main instance)
	ProtectPathReady() bool
	// YggdrasilKey is a one-time Yggdrasil key
	YggdrasilKey() string
	// YggKeyRunning: the running core has a mesh node with this device key
	YggKeyRunning(seed string) bool
	// Now is the time, in seconds
	Now() int64
}

// FixedDevice is the golden test's device (ConfigGoldenTest.Device): ports from 20000 on in call order, the clock
// at 1800000000, the apps' uids as given, libcore's protect server not up, the one-time mesh key
// "golden-one-time-key", no mesh node running.
type FixedDevice struct {
	Apps       map[string]int
	PrivateDNS *string
	next       int
}

// NewFixedDevice is a FixedDevice of these apps and strict Private DNS host.
func NewFixedDevice(apps map[string]int, privateDNS *string) *FixedDevice {
	return &FixedDevice{Apps: apps, PrivateDNS: privateDNS, next: 20000}
}

func (d *FixedDevice) Port() int {
	d.next++
	return d.next - 1
}

func (d *FixedDevice) UID(packageName string) (int, bool) {
	uid, ok := d.Apps[packageName]
	return uid, ok
}

func (d *FixedDevice) StrictPrivateDNSHost() (string, bool) {
	if d.PrivateDNS == nil {
		return "", false
	}
	return *d.PrivateDNS, true
}

func (d *FixedDevice) ProtectPathReady() bool         { return false }
func (d *FixedDevice) YggdrasilKey() string           { return "golden-one-time-key" }
func (d *FixedDevice) YggKeyRunning(seed string) bool { return false }
func (d *FixedDevice) Now() int64                     { return 1_800_000_000 }

// Result is what a build gives: the config, and what it told the user.
type Result struct {
	// Config is the core's config: JSON as the app writes it (Gson's, indented; keys in another order), or a custom
	// config's own text (a ConfigBean of type 0), as it is
	Config []byte
	// Warnings, in the golden fixtures' words: "rule needs VPN: <rule>", "no such outbound: <rule>"; on a computer
	// "rule needs Android: <rule>" too
	Warnings []string
	// SystemProxy: the system's proxy while the core runs, a computer's in proxy mode where its settings say so
	// (desktop.go); the daemon sets it for its owner. Nil for none
	SystemProxy *SystemProxy
}

// SystemProxy is an HTTP proxy of the config's, its local proxy, for the system to use.
type SystemProxy struct {
	Server string `json:"server"`
	Port   int    `json:"port"`
}

var errNoProfile = errors.New("no such profile")
