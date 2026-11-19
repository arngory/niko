package nikofmt

import (
	"errors"
	"regexp"
	"strconv"
	"strings"
)

var (
	hysteria2Scheme    = regexp.MustCompile(`^(hysteria2|hy2)://`)
	hysteria2Authority = regexp.MustCompile(`^https://([^/?#]*)`)
)

var errHysteriaLink = errors.New("invalid hysteria link")

// ParseHysteria2 is fmt/hysteria/HysteriaFmt.kt parseHysteria2: hysteria2:// or hy2://, port hopping in the authority
// (host:443,5000-6000) or in mport, salamander's password, the Niko parameters. The defaults are not applied (the app
// applies them after the parser, as parseProxies does).
func ParseHysteria2(link string, settings Settings) (*HysteriaBean, error) {
	var hopPorts *string
	httpsURL := hysteria2Scheme.ReplaceAllLiteralString(link, "https://")
	if m := hysteria2Authority.FindStringSubmatchIndex(httpsURL); m != nil {
		authority := httpsURL[m[2]:m[3]]
		hostPort := authority[strings.LastIndexByte(authority, '@')+1:]
		var ports string
		if strings.HasPrefix(hostPort, "[") {
			if _, after, ok := strings.Cut(hostPort, "]"); ok {
				ports = strings.TrimPrefix(after, ":")
			}
		} else if i := strings.LastIndexByte(hostPort, ':'); i >= 0 {
			ports = hostPort[i+1:]
		}
		if strings.ContainsAny(ports, ",-") {
			first, ok := javaParseInt(ports[:strings.IndexAny(ports, ",-")], 10)
			if !ok {
				return nil, errHysteriaLink
			}
			hopPorts = &ports
			// the URL gets the first port; Kotlin's removeSuffix, then the rest of the link after the authority
			httpsURL = "https://" + strings.TrimSuffix(authority, ports) + strconv.Itoa(int(first)) + httpsURL[m[1]:]
		}
	}
	u, err := parseOkURL(httpsURL)
	if err != nil {
		return nil, errHysteriaLink
	}
	b := &HysteriaBean{ProtocolVersion: ptr(2)}
	b.ServerAddress = ptr(u.host)
	if hopPorts != nil {
		b.ServerPorts = hopPorts
	} else {
		b.ServerPorts = ptr(strconv.Itoa(u.port))
	}
	if !ktIsBlank(u.password) {
		b.AuthPayload = ptr(u.username + ":" + u.password)
	} else {
		b.AuthPayload = ptr(u.username)
	}
	b.Name = u.fragment
	if mport := u.queryParameter("mport"); mport != nil {
		b.ServerPorts = mport
	}
	if sni := u.queryParameter("sni"); sni != nil {
		b.Sni = sni
	}
	if insecure := u.queryParameter("insecure"); insecure != nil {
		b.AllowInsecure = ptr(*insecure == "1" || *insecure == "true")
	}
	// salamander's password whatever "obfs" says, as the app reads it
	if password := u.queryParameter("obfs-password"); password != nil {
		b.Obfuscation = password
	}
	// pinSHA256 (the whole certificate's hash) is not carried: the core pins keys only; niko-pin is the key's pin.
	// Only the first niko-ygg-via counts here: the app passes no list of a repeated parameter for Hysteria2
	err = applyNikoParams(&b.AbstractBean, settings, u.queryParameter, func(name string) []*string {
		if v := u.queryParameter(name); v != nil {
			return []*string{v}
		}
		return nil
	})
	if err != nil {
		return nil, err
	}
	b.NikoAck = ptr(equals(u.queryParameter("niko-ack"), "1"))
	return b, nil
}
