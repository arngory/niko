package nikostore

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"unicode"

	"libcore/nikoconf"
)

// The computer's settings (docs/design/desktop-stage3.md §4): settings.json beside store.json, what the user changed
// over a computer's defaults (nikoconf.DesktopSettings), by the app's names. Only the settings a computer's build
// takes are kept: those of computerSettings.
//
//	{
//	  "schema": 1,
//	  "settings": {"mixedPort": 2081, "serviceMode": "vpn"},
//	  "sealed": "dpapi:…"                       the secret ones, {"mixedPassword": "…"}, as profiles' secrets are
//	}
//
// Where the platform seals nothing (Linux), "secrets" holds them as they are, the file the daemon's alone (0600). A
// name this build does not know (a newer Niko's of the same schema) stays on the disk and is not read. A setting
// written stays the user's: a later Niko's default does not change it.

const (
	settingsFile   = "settings.json"
	settingsSchema = 1
	// what a text setting may be: a DNS setting's servers, a line each, are the longest
	maxSetting = 64 << 10
	// a program's line: a full path of Windows' long ones
	maxProgram = 32 << 10
)

// computerSettings are the settings a computer has, by the app's names, and what each may be: nil where any value of
// its type is one.
var computerSettings = map[string]func(s *nikoconf.Settings) error{
	"serviceMode": func(s *nikoconf.Settings) error {
		return oneOf(s.ServiceMode, nikoconf.ModeProxy, nikoconf.ModeVPN)
	},
	"systemProxy":             nil,
	"tunImplementation":       func(s *nikoconf.Settings) error { return within(s.TunImplementation, 0, 2) },
	"mtu":                     func(s *nikoconf.Settings) error { return within(s.MTU, 1000, 10000) },
	"ipv6Mode":                func(s *nikoconf.Settings) error { return within(s.IPv6Mode, 0, 3) },
	"globalAllowInsecure":     nil,
	"remoteDns":               func(s *nikoconf.Settings) error { return dns(s.RemoteDNS) },
	"directDns":               func(s *nikoconf.Settings) error { return dns(s.DirectDNS) },
	"enableFakeDns":           nil,
	"domainStrategyForRemote": func(s *nikoconf.Settings) error { return strategy(s.DomainStrategyForRemote) },
	"domainStrategyForDirect": func(s *nikoconf.Settings) error { return strategy(s.DomainStrategyForDirect) },
	"domainStrategyForServer": func(s *nikoconf.Settings) error { return strategy(s.DomainStrategyForServer) },
	"dnsOptimisticCache":      nil,
	"bypassLanInCore":         nil,
	"mixedPort":               func(s *nikoconf.Settings) error { return within(s.MixedPort, 1, 65535) },
	// HTTP's Basic authorization ends the user's name at its first colon
	"mixedUsername":         func(s *nikoconf.Settings) error { return login(s.MixedUsername, ":") },
	"mixedPassword":         func(s *nikoconf.Settings) error { return login(s.Secrets.MixedPassword, "") },
	"allowAccess":           nil,
	"trafficSniffing":       func(s *nikoconf.Settings) error { return within(s.TrafficSniffing, 0, 1) },
	"resolveDestination":    nil,
	"refuseQuicOverStreams": nil,
	"icmpNoLocalReply":      nil,
	"logLevel":              func(s *nikoconf.Settings) error { return within(s.LogLevel, 0, 4) },
	"programsMode": func(s *nikoconf.Settings) error {
		return within(s.ProgramsMode, nikoconf.ProgramsAll, nikoconf.ProgramsExcept)
	},
	"programs": func(s *nikoconf.Settings) error { return programs(s.Programs) },
	// the rules (slice 10): their DNS rules, the rule-sets' update, «Обход DPI для прямых соединений» on the direct
	// rules with their flag
	"enableDnsRouting":   nil,
	"ruleSetUpdateHours": func(s *nikoconf.Settings) error { return within(s.RuleSetUpdateHours, 1, 24*30) },
	"dpiDirect":          func(s *nikoconf.Settings) error { return within(s.DpiDirect, nikoconf.DpiOff, nikoconf.DpiCustom) },
	"dpiDirectPreset":      func(s *nikoconf.Settings) error { return dpiPreset(s.DpiDirectPreset) },
	"dpiDirectCustom":      func(s *nikoconf.Settings) error { return dpiCustom(s.DpiDirectCustom) },
	"raycontrolAutoSwitch": nil,
	"raycontrolBrutal":     nil,
	"raycontrolReports":    nil,
}

// secretSettings are those of the settings that are nikoconf.Secrets', sealed on the disk.
var secretSettings = map[string]bool{"mixedPassword": true}

// SettingNames are the settings a computer has, by the app's names, sorted.
func SettingNames() []string {
	names := make([]string, 0, len(computerSettings))
	for name := range computerSettings {
		names = append(names, name)
	}
	slices.Sort(names)
	return names
}

func oneOf(value string, allowed ...string) error {
	if !slices.Contains(allowed, value) {
		return fmt.Errorf("%q is none of %s", value, strings.Join(allowed, ", "))
	}
	return nil
}

func within(value, low, high int) error {
	if value < low || value > high {
		return fmt.Errorf("%d is not from %d to %d", value, low, high)
	}
	return nil
}

func dns(text string) error {
	if len(text) > maxSetting {
		return errors.New("too long")
	}
	return nikoconf.CheckDNS(text)
}

// strategy: none (the core's own), or one the app offers (dns_network_select).
func strategy(value *string) error {
	if value == nil {
		return nil
	}
	return oneOf(*value, "auto", "prefer_ipv4", "prefer_ipv6", "ipv4_only", "ipv6_only")
}

// programs: the programs through Niko or not, a name or a full path a line, each printable characters; empty lines
// are none.
func programs(text string) error {
	if len(text) > maxSetting {
		return errors.New("too long")
	}
	for i, line := range strings.Split(text, "\n") {
		line = strings.TrimSuffix(line, "\r")
		if len(line) > maxProgram {
			return fmt.Errorf("line %d: over %d bytes", i+1, maxProgram)
		}
		if strings.IndexFunc(line, func(r rune) bool { return !unicode.IsPrint(r) && r != '\t' }) >= 0 {
			return fmt.Errorf("line %d: a character that is not printed", i+1)
		}
	}
	return nil
}

// dpiPreset: one of the app's ready-made strategies.
func dpiPreset(id string) error {
	var ids []string
	for _, p := range nikoconf.DpiPresets {
		ids = append(ids, p.ID)
	}
	return oneOf(id, ids...)
}

// dpiCustom: a ByeDPI string the build reads (nikoconf.ParseByeDpi), or none.
func dpiCustom(text string) error {
	if len(text) > 4096 {
		return errors.New("too long")
	}
	if strings.TrimSpace(text) == "" {
		return nil
	}
	_, err := nikoconf.ParseByeDpi(text)
	return err
}

// login: a user's name or password of the local proxy, a line of printable characters without those refused.
func login(value, refused string) error {
	if len(value) > 255 {
		return errors.New("over 255 bytes")
	}
	if i := strings.IndexFunc(value, func(r rune) bool { return !unicode.IsPrint(r) }); i >= 0 {
		return errors.New("a character that is not printed")
	}
	if refused != "" && strings.ContainsAny(value, refused) {
		return fmt.Errorf("no %q in it", refused)
	}
	return nil
}

// settings is settings.json as the store holds it: the settings changed (other names kept as they are) and the
// secret ones; once written, never changed in place.
type settings struct {
	public  map[string]json.RawMessage
	secrets map[string]json.RawMessage
	// unreadable: the secrets were sealed by another machine or account; sealed is what the disk has of them, kept
	// until they are set again
	unreadable bool
	sealed     string
}

type settingsDocument struct {
	Schema   int                        `json:"schema"`
	Settings map[string]json.RawMessage `json:"settings"`
	Secrets  map[string]json.RawMessage `json:"secrets,omitempty"`
	Sealed   string                     `json:"sealed,omitempty"`
}

// openSettings reads settings.json, none for none; plain secrets this store seals are sealed and written again.
func (s *Store) openSettings() error {
	s.settings = &settings{public: map[string]json.RawMessage{}, secrets: map[string]json.RawMessage{}}
	path := filepath.Join(s.dir, settingsFile)
	data, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		return nil
	}
	if err != nil {
		return err
	}
	if err = privateFile(path); err != nil {
		return err
	}
	var doc settingsDocument
	if err = json.Unmarshal(data, &doc); err != nil {
		return fmt.Errorf("%s: %w", path, err)
	}
	if doc.Schema > settingsSchema {
		return fmt.Errorf("%w: %s of schema %d, this one knows %d", ErrNewerSchema, settingsFile, doc.Schema,
			settingsSchema)
	}
	if doc.Settings != nil {
		s.settings.public = doc.Settings
	}
	if doc.Secrets != nil {
		s.settings.secrets = doc.Secrets
	}
	if doc.Sealed != "" {
		secrets, err := s.openSecrets(doc.Sealed)
		if err != nil {
			s.settings.unreadable, s.settings.sealed = true, doc.Sealed
			return nil
		}
		s.settings.secrets = secrets
		return nil
	}
	if len(doc.Secrets) > 0 && s.sealer.Name() != "" {
		return s.writeSettings(s.settings)
	}
	return nil
}

func (s *Store) openSecrets(sealed string) (map[string]json.RawMessage, error) {
	name, data, found := strings.Cut(sealed, ":")
	if !found || name != s.sealer.Name() {
		return nil, errSealed
	}
	raw, err := base64.StdEncoding.DecodeString(data)
	if err != nil {
		return nil, errSealed
	}
	plain, err := s.sealer.Open(raw)
	if err != nil {
		return nil, errSealed
	}
	var secrets map[string]json.RawMessage
	if json.Unmarshal(plain, &secrets) != nil || secrets == nil {
		return nil, errSealed
	}
	return secrets, nil
}

// writeSettings writes st as settings.json, its secrets sealed where the store seals.
func (s *Store) writeSettings(st *settings) error {
	doc := settingsDocument{Schema: settingsSchema, Settings: st.public, Sealed: st.sealed}
	if !st.unreadable && len(st.secrets) > 0 {
		if s.sealer.Name() == "" {
			doc.Secrets = st.secrets
		} else {
			plain, err := json.Marshal(st.secrets)
			if err != nil {
				return err
			}
			sealed, err := s.sealer.Seal(plain)
			if err != nil {
				return err
			}
			doc.Sealed = s.sealer.Name() + ":" + base64.StdEncoding.EncodeToString(sealed)
		}
	}
	data, err := json.MarshalIndent(doc, "", "  ")
	if err != nil {
		return err
	}
	return writeFile(filepath.Join(s.dir, settingsFile), append(data, '\n'))
}

// Settings are the computer's settings: its defaults with what the user changed over them. ErrRefused where the
// secret ones were sealed by another machine or account: they are to be set again.
func (s *Store) Settings() (nikoconf.Settings, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.settings.unreadable {
		return nikoconf.Settings{}, fmt.Errorf("%w: the settings' secrets: %w", ErrRefused, errSealed)
	}
	return s.settings.read()
}

// read is the defaults with the settings changed over them.
func (st *settings) read() (nikoconf.Settings, error) {
	values, secrets, err := settingValues(nikoconf.DesktopSettings())
	if err != nil {
		return nikoconf.Settings{}, err
	}
	for name, value := range st.public {
		if _, ok := computerSettings[name]; ok && !secretSettings[name] {
			values[name] = value
		}
	}
	for name, value := range st.secrets {
		if secretSettings[name] {
			secrets[name] = value
		}
	}
	if values["secrets"], err = json.Marshal(secrets); err != nil {
		return nikoconf.Settings{}, err
	}
	data, err := json.Marshal(values)
	if err != nil {
		return nikoconf.Settings{}, err
	}
	var out nikoconf.Settings
	if err = json.Unmarshal(data, &out); err != nil {
		return nikoconf.Settings{}, fmt.Errorf("%s: %w", settingsFile, err)
	}
	return out, nil
}

// settingValues are the values of settings by the app's names: the secrets' apart.
func settingValues(s nikoconf.Settings) (values, secrets map[string]json.RawMessage, err error) {
	data, err := json.Marshal(s)
	if err != nil {
		return nil, nil, err
	}
	if err = json.Unmarshal(data, &values); err != nil {
		return nil, nil, err
	}
	if err = json.Unmarshal(values["secrets"], &secrets); err != nil {
		return nil, nil, err
	}
	return values, secrets, nil
}

// UpdateSettings sets the named settings (the app's names, SettingNames) to their values in from, the others as they
// were, in one write: the settings then. A secret set empty is none. ErrInvalid for a name that is no computer's
// setting, or a value it may not have, and nothing is written; ErrRefused where the secrets were sealed by another
// machine or account and the change does not set them again.
func (s *Store) UpdateSettings(from nikoconf.Settings, names []string) (nikoconf.Settings, error) {
	values, secrets, err := settingValues(from)
	if err != nil {
		return nikoconf.Settings{}, err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	next := &settings{public: clone(s.settings.public), secrets: clone(s.settings.secrets),
		unreadable: s.settings.unreadable, sealed: s.settings.sealed}
	seen := map[string]bool{}
	for _, name := range names {
		check, ok := computerSettings[name]
		if !ok || seen[name] {
			return nikoconf.Settings{}, fmt.Errorf("%w: %q: no such setting of a computer's, or twice", ErrInvalid, name)
		}
		seen[name] = true
		if check != nil {
			if err = check(&from); err != nil {
				return nikoconf.Settings{}, fmt.Errorf("%w: %s: %w", ErrInvalid, name, err)
			}
		}
		if !secretSettings[name] {
			next.public[name] = values[name]
			continue
		}
		if next.unreadable {
			// the secrets set again from nothing
			next.secrets, next.unreadable, next.sealed = map[string]json.RawMessage{}, false, ""
		}
		if string(secrets[name]) == `""` {
			delete(next.secrets, name)
		} else {
			next.secrets[name] = secrets[name]
		}
	}
	if next.unreadable {
		return nikoconf.Settings{}, fmt.Errorf("%w: the settings' secrets: %w; set them again", ErrRefused, errSealed)
	}
	next.sealed = ""
	result, err := next.read()
	if err != nil {
		return nikoconf.Settings{}, err
	}
	if len(names) == 0 {
		return result, nil
	}
	if err = s.writeSettings(next); err != nil {
		return nikoconf.Settings{}, err
	}
	s.settings = next
	s.tell(Change{Kind: KindSettings})
	return result, nil
}

func clone(m map[string]json.RawMessage) map[string]json.RawMessage {
	out := make(map[string]json.RawMessage, len(m))
	for k, v := range m {
		out[k] = v
	}
	return out
}
