package main

import (
	"encoding/binary"
	"encoding/hex"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// RFC 9001 Appendix A.1: the client's Initial keys for the DCID 0x8394c8f03e515708.
func TestClientKeysRFC9001(t *testing.T) {
	dcid, _ := hex.DecodeString("8394c8f03e515708")
	_, key, iv, hp := clientKeys(dcid)
	for _, c := range []struct{ name, got, want string }{
		{"key", hex.EncodeToString(key), "1f369613dd76d5467730efcbe3b1a22d"},
		{"iv", hex.EncodeToString(iv), "fa044b2f42a3fd3b46fb255c"},
		{"hp", hex.EncodeToString(hp), "9f50449e04a0e810283a1e9933adedd2"},
	} {
		if c.got != c.want {
			t.Errorf("%s: %s, want %s", c.name, c.got, c.want)
		}
	}
}

func appendVarint(b []byte, v uint64) []byte {
	switch {
	case v < 1<<6:
		return append(b, byte(v))
	case v < 1<<14:
		return binary.BigEndian.AppendUint16(b, uint16(v)|0x4000)
	default:
		return binary.BigEndian.AppendUint32(b, uint32(v)|0x80000000)
	}
}

// seal builds a protected Initial packet as a client would, padded to size.
func seal(dcid []byte, number uint64, payload []byte, size int) []byte {
	k, _, _, _ := clientKeys(dcid)
	header := []byte{0xc0 | 0x01} // long header, Initial, 2-byte packet number
	header = binary.BigEndian.AppendUint32(header, versionV1)
	header = append(header, byte(len(dcid)))
	header = append(header, dcid...)
	header = append(header, 0) // SCID: none, as Chrome
	header = append(header, 0) // token: none
	overhead := len(header) + 2 + 2 + 16
	if pad := size - overhead - len(payload); pad > 0 {
		payload = append(payload, make([]byte, pad)...)
	}
	header = appendVarint(header, uint64(2+len(payload)+16)) // 2-byte length
	if header[len(header)-2]&0xc0 != 0x40 {
		panic("length needs 2 bytes")
	}
	pnOffset := len(header)
	header = binary.BigEndian.AppendUint16(header, uint16(number))
	nonce := append([]byte(nil), k.iv...)
	for j := 0; j < 8; j++ {
		nonce[len(nonce)-1-j] ^= byte(number >> (8 * j))
	}
	packet := k.aead.Seal(append([]byte(nil), header...), nonce, payload, header)
	mask := make([]byte, 16)
	k.hp.Encrypt(mask, packet[pnOffset+4:pnOffset+4+16])
	packet[0] ^= mask[0] & 0x0f
	packet[pnOffset] ^= mask[1]
	packet[pnOffset+1] ^= mask[2]
	return packet
}

func cryptoFrame(offset uint64, data []byte) []byte {
	b := appendVarint([]byte{0x06}, offset)
	b = appendVarint(b, uint64(len(data)))
	return append(b, data...)
}

func TestFlightRoundTrip(t *testing.T) {
	dcid, _ := hex.DecodeString("0011223344556677")
	body := make([]byte, 1500)
	for i := range body {
		body[i] = byte(i)
	}
	hello := append([]byte{0x01, 0, byte(len(body) >> 8), byte(len(body))}, body...)
	// the hello in three pieces over two datagrams, the second piece first, a PING between: chaos protection
	first := append(cryptoFrame(900, hello[900:1000]), 0x01)
	first = append(first, cryptoFrame(0, hello[:900])...)
	second := cryptoFrame(1000, hello[1000:])
	datagrams := [][]byte{seal(dcid, 1, first, 1250), seal(dcid, 2, second, 1250)}

	dir := t.TempDir()
	in := filepath.Join(dir, "flight.hex")
	var s strings.Builder
	for _, d := range datagrams {
		s.WriteString(hex.EncodeToString(d) + "\n")
	}
	os.WriteFile(in, []byte(s.String()), 0o644)
	lines, got, err := open(in)
	if err != nil {
		t.Fatal(err)
	}
	if hex.EncodeToString(got) != hex.EncodeToString(hello) {
		t.Fatalf("the ClientHello came out different: %d bytes, want %d", len(got), len(hello))
	}
	want := []string{
		"version 00000001", "dcid length 8", "scid length 0", "token length 0", "datagrams 2",
		"datagram sizes 1250,1250", "packets per datagram 1,1", "packet numbers 1,2", "packet number lengths 2,2",
		"frame kinds CRYPTO,PADDING,PING",
	}
	if strings.Join(lines, "\n") != strings.Join(want, "\n") {
		t.Errorf("shape:\n%s\nwant:\n%s", strings.Join(lines, "\n"), strings.Join(want, "\n"))
	}

	// half a flight is not a whole ClientHello
	os.WriteFile(in, []byte(hex.EncodeToString(datagrams[0])+"\n"), 0o644)
	if _, _, err := open(in); err == nil || !strings.Contains(err.Error(), "not whole") {
		t.Errorf("half a flight: %v", err)
	}
	// a flipped byte does not open
	bad := append([]byte(nil), datagrams[1]...)
	bad[len(bad)-1] ^= 1
	os.WriteFile(in, []byte(hex.EncodeToString(datagrams[0])+"\n"+hex.EncodeToString(bad)+"\n"), 0o644)
	if _, _, err := open(in); err == nil || !strings.Contains(err.Error(), "does not open") {
		t.Errorf("a flipped byte: %v", err)
	}
}
