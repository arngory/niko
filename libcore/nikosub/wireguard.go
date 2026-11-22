package nikosub

import (
	"errors"
	"strconv"
	"strings"

	"libcore/nikofmt"
)

// A WireGuard .conf as the app reads it (RawUpdater.parseWireGuard on ini4j 0.5.4's Ini): sections of the same name
// are one section (ini4j is not multi-section by default), so a .conf of several [Peer]s is one peer of each key's
// last value; an option's values in their order, its last one where one is asked for.

// iniSection is a section: its keys in the order first met, each with its values.
type iniSection struct {
	keys   []string
	values map[string][]string
}

// get is Section.get: the key's last value; ok false for no such key.
func (s *iniSection) get(key string) (string, bool) {
	v := s.values[key]
	if len(v) == 0 {
		return "", false
	}
	return v[len(v)-1], true
}

// iniFile is an Ini: its sections by name, in their order.
type iniFile struct {
	names    []string
	sections map[string]*iniSection
}

var errINI = errors.New("not an ini file")

// parseINI is new Ini(reader) with ini4j's defaults: lines at \n, \r and \r\n; a trimmed line starting with ';' or '#'
// a comment; a line ending in '\' continued by the next one, trimmed; [name] a section, its name trimmed; name=value
// or name:value at the first '=' or ':', both trimmed and unescaped (\\, \t, \n, \f, \b, \r, \uXXXX, else the
// character), an empty value kept empty. An option before any section, a line of neither kind, a section line with
// anything after its ']' are parse errors.
func parseINI(text string) (*iniFile, error) {
	f := &iniFile{sections: map[string]*iniSection{}}
	var current *iniSection
	lines := splitLines(text)
	for i := 0; i < len(lines); i++ {
		line := javaTrim(lines[i])
		for strings.HasSuffix(line, `\`) && i+1 < len(lines) {
			i++
			line = line[:len(line)-1] + javaTrim(lines[i])
		}
		if line == "" || line[0] == ';' || line[0] == '#' {
			continue
		}
		if line[0] == '[' {
			if !strings.HasSuffix(line, "]") {
				return nil, errINI
			}
			name := javaTrim(unescapeINI(line[1 : len(line)-1]))
			if name == "" {
				return nil, errINI
			}
			current = f.sections[name]
			if current == nil {
				current = &iniSection{values: map[string][]string{}}
				f.sections[name] = current
				f.names = append(f.names, name)
			}
			continue
		}
		if current == nil {
			return nil, errINI
		}
		at := strings.IndexAny(line, "=:")
		if at < 0 {
			return nil, errINI
		}
		key := javaTrim(unescapeINI(line[:at]))
		if key == "" {
			return nil, errINI
		}
		value := javaTrim(unescapeINI(line[at+1:]))
		if _, ok := current.values[key]; !ok {
			current.keys = append(current.keys, key)
		}
		current.values[key] = append(current.values[key], value)
	}
	return f, nil
}

// splitLines is BufferedReader.readLine's lines.
func splitLines(text string) []string {
	text = strings.ReplaceAll(text, "\r\n", "\n")
	lines := strings.Split(strings.ReplaceAll(text, "\r", "\n"), "\n")
	if len(lines) > 0 && lines[len(lines)-1] == "" {
		lines = lines[:len(lines)-1]
	}
	return lines
}

// unescapeINI is ini4j's EscapeTool.unescape.
func unescapeINI(s string) string {
	if !strings.Contains(s, `\`) {
		return s
	}
	var b strings.Builder
	r := []rune(s)
	for i := 0; i < len(r); i++ {
		c := r[i]
		if c == '\\' && i+1 < len(r) {
			i++
			c = r[i]
			switch c {
			case 'u':
				if i+5 <= len(r) {
					if n, err := strconv.ParseUint(string(r[i+1:i+5]), 16, 16); err == nil {
						b.WriteRune(rune(n))
						i += 4
						continue
					}
				}
			case 't':
				c = '\t'
			case 'n':
				c = '\n'
			case 'f':
				c = '\f'
			case 'b':
				c = '\b'
			case 'r':
				c = '\r'
			}
		}
		b.WriteRune(c)
	}
	return b.String()
}

// isAmneziaWGKey is AmneziaWG.isKey: an [Interface] or [Peer] key of AmneziaWG's (lower case, without '_' and '-'),
// the keys nikoconf builds the endpoint of.
func isAmneziaWGKey(key string) bool {
	switch strings.NewReplacer("_", "", "-", "").Replace(nikofmt.KtLowercase(nikofmt.KtTrim(key))) {
	case "jc", "jmin", "jmax", "s1", "s2", "s3", "s4",
		"h1", "h2", "h3", "h4", "contentpaddingaddition", "rekeyaftertime", "rekeytimeout", "rejectaftertime",
		"keepalivetimeout", "maxhandshakeattempts",
		"i1", "i2", "i3", "i4", "i5", "id", "ip", "ib", "headerprotectionkey",
		"randomtrailers", "disablecookies",
		"advancedsecurity":
		return true
	}
	return false
}

var errWireGuard = errors.New("not a WireGuard config")

// parseWireGuard is RawUpdater.parseWireGuard: a profile for each peer with an endpoint, its port and public key, of
// the interface's addresses (each Address line split at commas, not trimmed), key, MTU (1280 for AmneziaWG without
// one, else 1420) and AmneziaWG lines; an AmneziaWG keepalive range goes with those.
func parseWireGuard(text string) ([]nikofmt.Bean, error) {
	ini, err := parseINI(text)
	if err != nil {
		return nil, err
	}
	iface := ini.sections["Interface"]
	if iface == nil {
		return nil, errWireGuard
	}
	bean := &nikofmt.WireGuardBean{}
	bean.InitializeDefaultValues()
	addresses := iface.values["Address"]
	if len(addresses) == 0 {
		return nil, errWireGuard
	}
	var local []string
	for _, a := range addresses {
		local = append(local, strings.Split(a, ",")...)
	}
	bean.LocalAddress = ptrTo(strings.Join(local, "\n"))
	if key, ok := iface.get("PrivateKey"); ok {
		bean.PrivateKey = ptrTo(key)
	} else {
		bean.PrivateKey = nil
	}
	var awg []string
	for _, k := range iface.keys {
		if isAmneziaWGKey(k) {
			v, _ := iface.get(k)
			awg = append(awg, k+" = "+nikofmt.KtTrim(v))
		}
	}
	bean.AmneziaWG = ptrTo(strings.Join(awg, "\n"))
	if mtu, ok := iface.get("MTU"); ok {
		if n, ok := nikofmt.KtToIntOrNull(nikofmt.KtTrim(mtu)); ok {
			bean.Mtu = ptrTo(n)
		} else {
			bean.Mtu = nil
		}
	} else {
		bean.Mtu = nil
	}
	if bean.Mtu == nil {
		if !nikofmt.KtIsBlank(*bean.AmneziaWG) {
			bean.Mtu = ptrTo(1280)
		} else {
			bean.Mtu = ptrTo(1420)
		}
	}
	peer := ini.sections["Peer"]
	if peer == nil {
		return nil, errWireGuard
	}
	var beans []nikofmt.Bean
	// one section, whatever the .conf's count of [Peer]
	for _, p := range []*iniSection{peer} {
		endpoint, ok := p.get("Endpoint")
		if !ok || nikofmt.KtIsBlank(endpoint) || !strings.Contains(endpoint, ":") {
			continue
		}
		cloned, err := nikofmt.KryoClone(bean)
		if err != nil {
			return nil, err
		}
		peerBean := cloned.(*nikofmt.WireGuardBean)
		peerBean.ServerAddress = ptrTo(substringBeforeLast(endpoint, ":"))
		port, ok := nikofmt.KtToIntOrNull(substringAfterLast(endpoint, ":"))
		if !ok {
			continue
		}
		peerBean.ServerPort = &port
		publicKey, ok := p.get("PublicKey")
		if !ok {
			continue
		}
		peerBean.PeerPublicKey = &publicKey
		if psk, ok := p.get("PresharedKey"); ok {
			peerBean.PeerPreSharedKey = &psk
		} else {
			peerBean.PeerPreSharedKey = nil
		}
		keepalive := ""
		if k, ok := p.get("PersistentKeepalive"); ok {
			keepalive = nikofmt.KtTrim(k)
		}
		if n, ok := nikofmt.KtToIntOrNull(keepalive); ok {
			peerBean.PersistentKeepalive = ptrTo(min(max(n, 0), 65535))
		} else {
			peerBean.PersistentKeepalive = ptrTo(0)
		}
		if strings.Contains(keepalive, "-") {
			var lines []string
			for _, l := range []string{*peerBean.AmneziaWG, "PersistentKeepalive = " + keepalive} {
				if !nikofmt.KtIsBlank(l) {
					lines = append(lines, l)
				}
			}
			peerBean.AmneziaWG = ptrTo(strings.Join(lines, "\n"))
		}
		peerBean.InitializeDefaultValues()
		beans = append(beans, peerBean)
	}
	if len(beans) == 0 {
		return nil, errWireGuard
	}
	return beans, nil
}

// javaTrim is String.trim: the characters up to ' ' at both ends.
func javaTrim(s string) string { return strings.TrimFunc(s, func(r rune) bool { return r <= ' ' }) }
