package nikofmt

import (
	"bytes"
	"compress/flate"
	"errors"
	"hash/adler32"
	"io"
	"strconv"
	"strings"
)

// ParseUniversal is fmt/UniversalFmt.kt parseUniversal, NekoBox's own share link of a whole profile:
// sn://<type>?<base64url of the bean's Kryo data, zlib-compressed>, or the old sn://<type>:<base64 of it> without
// zlib. Read strictly, as KryoConverters.strict reads it: data cut short is an error, not a half-filled profile.
// The defaults are applied, as the app's deserializer applies them.
func ParseUniversal(link string) (Bean, error) {
	if strings.HasPrefix(link, "sn://subscription?") {
		return nil, ErrSubscriptionLink
	}
	if strings.Contains(link, "?") {
		typ := substringBefore(substringAfter(link, "sn://"), "?")
		if err := knownType(typ); err != nil {
			return nil, err
		}
		compressed, err := b64Decode(substringAfter(link, "?"))
		if err != nil {
			return nil, err
		}
		data, err := zlibDecompress(compressed)
		if err != nil {
			return nil, err
		}
		return kryoBean(typ, data)
	}
	typ := substringBefore(substringAfter(link, "sn://"), ":")
	if err := knownType(typ); err != nil {
		return nil, err
	}
	data, err := b64Decode(substringAfter(substringAfter(link, ":"), ":"))
	if err != nil {
		return nil, err
	}
	return kryoBean(typ, data)
}

// typeMap is fmt/TypeMap.kt: the types an sn:// link may name.
var typeMap = []string{"socks", "http", "ss", "vmess", "trojan", "trojan-go", "mieru", "naive", "hysteria", "ssh",
	"wg", "tuic", "anytls", "neko", "config"}

// knownType is TypeMap[type] ?: error(…), checked before the data is decoded, as the app does.
func knownType(typ string) error {
	if !contains(typeMap, typ) {
		return errors.New("type " + typ + " not found")
	}
	return nil
}

// zlibMaxOutput is Util.ZLIB_MAX_OUTPUT: a few MB of sn:// would otherwise inflate to gigabytes.
const zlibMaxOutput = 16 << 20

// zlibDecompress is Util.zlibDecompress on java.util.zip.Inflater: a zlib stream (its header checked, its Adler-32
// checked where the data holds it), what follows it ignored. Inflater stops without an error where the data ends
// early or asks for a preset dictionary, so those give what was inflated so far (nothing, for a dictionary); data
// that is not deflate is an error, as is more than zlibMaxOutput.
func zlibDecompress(input []byte) ([]byte, error) {
	if len(input) < 2 {
		return nil, nil // Inflater.needsInput: nothing inflated
	}
	cmf, flg := input[0], input[1]
	if cmf&0x0f != 8 || cmf>>4 > 7 || (uint16(cmf)<<8|uint16(flg))%31 != 0 {
		return nil, errors.New("incorrect header check")
	}
	if flg&0x20 != 0 {
		return nil, nil // a preset dictionary: Inflater.needsDictionary, inflate gives 0
	}
	src := bytes.NewReader(input[2:])
	fr := flate.NewReader(src)
	var out bytes.Buffer
	buf := make([]byte, 8192)
	for {
		n, err := fr.Read(buf)
		if out.Len()+n > zlibMaxOutput {
			return nil, errors.New("compressed data is larger than " + strconv.Itoa(zlibMaxOutput>>20) + " MiB")
		}
		out.Write(buf[:n])
		if err == io.EOF {
			break
		}
		if errors.Is(err, io.ErrUnexpectedEOF) {
			return out.Bytes(), nil // the data ends early: what was inflated
		}
		if err != nil {
			return nil, err
		}
	}
	// the stream's end: Inflater checks the Adler-32 that follows, if the data has all four bytes of it
	rest := input[len(input)-src.Len():]
	if len(rest) >= 4 {
		want := uint32(rest[0])<<24 | uint32(rest[1])<<16 | uint32(rest[2])<<8 | uint32(rest[3])
		if adler32.Checksum(out.Bytes()) != want {
			return nil, errors.New("incorrect data check")
		}
	}
	return out.Bytes(), nil
}
