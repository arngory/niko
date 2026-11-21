package nikofmt

import (
	"encoding/json"
	"errors"
	"strings"
)

// ParseV2Ray is fmt/v2ray/V2RayFmt.kt parseV2Ray, which the app gives both vmess:// and vless:// links: without a
// '?', v2rayN's base64 JSON (or a Quantumult line in it) first, then Kitsunebi's base64 "method:uuid@host:port",
// then the standard forms. Each form that fails hands the link to the next, so a vless:// link (or a malformed
// vmess:// one) goes through all of them, through Android's lenient base64 decoder too. The defaults are not applied
// (the app applies them after the parser, as parseProxies does).
func ParseV2Ray(link string, settings Settings) (*VMessBean, error) {
	if !strings.Contains(link, "?") {
		if b, err := parseV2RayN(link); err == nil {
			return b, nil
		}
	}
	if b, err := parseKitsunebi(link); err == nil {
		return b, nil
	}
	return parseV2RayStandard(link, settings)
}

// vmessQRCode is V2RayFmt.kt VmessQRCode, v2rayN's JSON, as Gson fills it: a field the JSON leaves out keeps its
// default, a JSON null makes it null.
type vmessQRCode struct {
	v, ps, add, port, id, aid, scy, net, typ, host, path, tls, sni, alpn, fp *string
}

func (q *vmessQRCode) field(name string) **string {
	switch name {
	case "v":
		return &q.v
	case "ps":
		return &q.ps
	case "add":
		return &q.add
	case "port":
		return &q.port
	case "id":
		return &q.id
	case "aid":
		return &q.aid
	case "scy":
		return &q.scy
	case "net":
		return &q.net
	case "type":
		return &q.typ
	case "host":
		return &q.host
	case "path":
		return &q.path
	case "tls":
		return &q.tls
	case "sni":
		return &q.sni
	case "alpn":
		return &q.alpn
	case "fp":
		return &q.fp
	}
	return nil
}

var errV2RayN = errors.New("invalid VmessQRCode")

// parseV2RayN is V2RayFmt.kt parseV2RayN: the base64 after "vmess://" (all of a link without it) as v2rayN's JSON,
// read as Gson().fromJson reads it, or as a Quantumult line where it holds "= vmess".
func parseV2RayN(link string) (*VMessBean, error) {
	result, err := decodeBase64UrlSafe(substringAfter(link, "vmess://"))
	if err != nil {
		return nil, err
	}
	if strings.Contains(result, "= vmess") {
		return parseCsvVMess(result)
	}
	q := &vmessQRCode{}
	for _, f := range []**string{&q.v, &q.ps, &q.add, &q.port, &q.id, &q.scy, &q.net, &q.typ, &q.host, &q.path,
		&q.tls, &q.sni, &q.alpn, &q.fp} {
		*f = ptr("")
	}
	q.aid = ptr("0")
	if err := gsonReadObject(result, func(name string) **string { return q.field(name) }); err != nil {
		return nil, err
	}
	if isEmpty(q.add) || isEmpty(q.port) || isEmpty(q.id) || isEmpty(q.net) {
		return nil, errV2RayN
	}
	if q.aid == nil {
		// aid.toIntOrNull() on Gson's null: Kotlin's null check throws
		return nil, errV2RayN
	}
	b := &VMessBean{}
	b.Name = q.ps
	b.ServerAddress = q.add
	b.ServerPort = toIntOrNull(*q.port)
	b.Encryption = q.scy
	b.Uuid = q.id
	b.AlterId = toIntOrNull(*q.aid)
	b.Type = q.net
	b.Host = q.host
	b.Path = q.path
	headerType := q.typ
	switch *b.Type {
	case "tcp":
		if equals(headerType, "http") {
			b.Type = ptr("http")
		}
	// v2rayN keeps the XHTTP mode in "type"
	case "xhttp", "splithttp":
		b.Type = ptr("xhttp")
		// many panels write "none" there for any net
		if headerType != nil && contains(XHTTPModes, *headerType) {
			b.XhttpMode = headerType
		}
	}
	if equals(q.tls, "tls") || equals(q.tls, "reality") {
		b.Security = ptr("tls")
		b.Sni = q.sni
		if b.Sni == nil || ktIsBlank(*b.Sni) {
			b.Sni = b.Host
		}
		b.Alpn = q.alpn
		b.UtlsFingerprint = q.fp
	}
	return b, nil
}

// isEmpty is TextUtils.isEmpty.
func isEmpty(s *string) bool { return s == nil || *s == "" }

// toIntOrNull is Kotlin's String.toIntOrNull().
func toIntOrNull(s string) *int {
	if v, ok := javaParseInt(s, 10); ok {
		return ptr(int(v))
	}
	return nil
}

// parseCsvVMess is V2RayFmt.kt parseCsvVMess, a Quantumult line: "name = vmess,host,port,method,"uuid",options".
// Split at every ',' and not trimmed, so a line with spaces after its commas fails at the port.
func parseCsvVMess(csv string) (*VMessBean, error) {
	args := strings.Split(csv, ",")
	if len(args) < 5 {
		return nil, errors.New("csv vmess: too few fields") // args[4]: IndexOutOfBoundsException
	}
	b := &VMessBean{}
	b.ServerAddress = ptr(args[1])
	port, ok := javaParseInt(args[2], 10)
	if !ok {
		return nil, errors.New("csv vmess: the port is not a number")
	}
	b.ServerPort = ptr(int(port))
	b.Encryption = ptr(args[3])
	b.Uuid = ptr(strings.ReplaceAll(args[4], "\"", ""))
	for _, it := range args[5:] {
		switch {
		case it == "over-tls=true":
			b.Security = ptr("tls")
		case strings.HasPrefix(it, "tls-host="):
			b.Host = ptr(substringAfter(it, "="))
		case strings.HasPrefix(it, "obfs="):
			b.Type = ptr(substringAfter(it, "="))
		case strings.HasPrefix(it, "obfs-path=") || strings.Contains(it, "Host:"):
			b.Path = ptr(substringBefore(substringAfter(it, "obfs-path=\""), "\"obfs"))
			b.Host = ptr(substringBefore(substringAfter(it, "Host:"), "["))
		}
	}
	return b, nil
}

var errKitsunebi = errors.New("invalid kitsunebi format")

// parseKitsunebi is V2RayFmt.kt tryResolveVmess4Kitsunebi: base64 of "method:uuid@host:port" (NGUtil.decode), then
// its query: remarks, alterId, path, tls, allowInsecure, obfs and obfsParam.
func parseKitsunebi(server string) (*VMessBean, error) {
	result := strings.ReplaceAll(server, "vmess://", "")
	indexSplit := strings.IndexByte(result, '?')
	if indexSplit > 0 {
		result = result[:indexSplit]
	}
	result = ngDecode(result)
	arr1 := strings.Split(result, "@")
	if len(arr1) != 2 {
		return nil, errKitsunebi
	}
	arr21 := strings.Split(arr1[0], ":")
	arr22 := strings.Split(arr1[1], ":")
	if len(arr21) != 2 {
		return nil, errKitsunebi
	}
	if len(arr22) < 2 {
		return nil, errKitsunebi // arr22[1]: IndexOutOfBoundsException
	}
	b := &VMessBean{}
	b.ServerAddress = ptr(arr22[0])
	port, ok := javaParseInt(arr22[1], 10)
	if !ok {
		port = 0 // NGUtil.parseInt
	}
	b.ServerPort = ptr(int(port))
	b.Uuid = ptr(arr21[1])
	b.Encryption = ptr(arr21[0])
	if indexSplit < 0 {
		return b, nil
	}
	u, err := parseOkURL("https://localhost/path?" + substringAfter(server, "?"))
	if err != nil {
		return nil, err
	}
	if v := u.queryParameter("remarks"); v != nil {
		b.Name = v
	}
	if v := u.queryParameter("alterId"); v != nil {
		aid, ok := javaParseInt(*v, 10)
		if !ok {
			return nil, errors.New("kitsunebi: the alterId is not a number")
		}
		b.AlterId = ptr(int(aid))
	}
	if v := u.queryParameter("path"); v != nil {
		b.Path = v
	}
	if u.queryParameter("tls") != nil {
		b.Security = ptr("tls")
	}
	if v := u.queryParameter("allowInsecure"); v != nil && (*v == "1" || *v == "true") {
		b.AllowInsecure = ptr(true)
	}
	if v := u.queryParameter("obfs"); v != nil {
		b.Type = ptr(strings.ReplaceAll(strings.ReplaceAll(*v, "websocket", "ws"), "none", "tcp"))
		if *b.Type == "ws" {
			if p := u.queryParameter("obfsParam"); p != nil {
				if strings.HasPrefix(*p, "{") {
					host, err := jsonGetStr(*p, "Host")
					if err != nil {
						return nil, err
					}
					b.Host = host
				} else if equals(b.Security, "tls") {
					b.Sni = p
				}
			}
		}
	}
	return b, nil
}

// jsonGetStr is org.json's JSONObject(text) and ktx getStr(name): the value of a string member that is not blank,
// else nil; an error where the text is no JSON object. Read here as standard JSON: org.json takes more (unquoted
// names, comments), which a link of this form does not carry.
func jsonGetStr(text, name string) (*string, error) {
	var object map[string]any
	if err := json.Unmarshal([]byte(text), &object); err != nil || object == nil {
		return nil, errors.New("not a JSON object")
	}
	if s, ok := object[name].(string); ok && !ktIsBlank(s) {
		return &s, nil
	}
	return nil, nil
}
