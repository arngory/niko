// quicinitial reads the first flight of a QUIC v1 client: its Initial packets. Their keys come from the Destination
// Connection ID the client picks (RFC 9001 §5.2), so whoever sees the packets can open them, as a censor's DPI does.
// The naive outbound's QUIC is Chromium's, and this shows whether its first flight looks like Chrome's.
//
//	quicinitial capture [HOST:]PORT OUT  listen on UDP HOST:PORT (127.0.0.1 by default), take the first client's
//	                                     datagrams until its ClientHello is whole, write them to OUT (hex, one a line)
//	quicinitial open IN HELLO            print the shape of IN's packets, write the ClientHello in them to HELLO (hex)
//	quicinitial compare REFERENCE GOT    compare the packets' shapes; every difference is a ::warning:: line, and with
//	                                     --strict the exit code is 1. The ClientHello inside is compared by hello.py
//
// What is compared is what two first flights of one client share: the version, the lengths of the connection IDs
// and of the token, the datagrams' sizes, how many packets a datagram holds, the packet numbers and their lengths,
// the kinds of frames. Not compared: how the ClientHello is cut into CRYPTO frames and how many PING and PADDING
// frames sit between them (Chrome draws these at random for each connection: its "chaos protection").
package main

import (
	"bufio"
	"crypto/aes"
	"crypto/cipher"
	"crypto/hkdf"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"fmt"
	"net"
	"os"
	"sort"
	"strconv"
	"strings"
	"time"
)

// RFC 9001 §5.2
var saltV1 = []byte{0x38, 0x76, 0x2c, 0xf7, 0xf5, 0x59, 0x34, 0xb3, 0x4d, 0x17, 0x9a, 0xe6, 0xa4, 0xc8, 0x0c, 0xad, 0xcc, 0xbb, 0x7f, 0x0a}

const versionV1 = 0x00000001

// expandLabel is TLS 1.3's HKDF-Expand-Label with an empty context (RFC 8446 §7.1).
func expandLabel(secret []byte, label string, length int) []byte {
	full := "tls13 " + label
	info := make([]byte, 0, 4+len(full))
	info = binary.BigEndian.AppendUint16(info, uint16(length))
	info = append(info, byte(len(full)))
	info = append(info, full...)
	info = append(info, 0)
	out, err := hkdf.Expand(sha256.New, secret, string(info), length)
	if err != nil {
		panic(err)
	}
	return out
}

type keys struct {
	aead cipher.AEAD
	iv   []byte
	hp   cipher.Block
}

// clientKeys derives the client's Initial keys from the Destination Connection ID of its first packet.
func clientKeys(dcid []byte) (keys, []byte, []byte, []byte) {
	initial, err := hkdf.Extract(sha256.New, dcid, saltV1)
	if err != nil {
		panic(err)
	}
	secret := expandLabel(initial, "client in", 32)
	key := expandLabel(secret, "quic key", 16)
	iv := expandLabel(secret, "quic iv", 12)
	hp := expandLabel(secret, "quic hp", 16)
	block, _ := aes.NewCipher(key)
	aead, _ := cipher.NewGCM(block)
	hpBlock, _ := aes.NewCipher(hp)
	return keys{aead: aead, iv: iv, hp: hpBlock}, key, iv, hp
}

func varint(b []byte) (uint64, int, error) {
	if len(b) == 0 {
		return 0, 0, errors.New("varint: no bytes")
	}
	n := 1 << (b[0] >> 6)
	if len(b) < n {
		return 0, 0, errors.New("varint: cut short")
	}
	v := uint64(b[0] & 0x3f)
	for i := 1; i < n; i++ {
		v = v<<8 | uint64(b[i])
	}
	return v, n, nil
}

// packet is one opened Initial packet.
type packet struct {
	version  uint32
	dcid     []byte
	scid     []byte
	token    []byte
	number   uint64
	numLen   int
	frames   []string // kinds, in the order sent
	crypto   []fragment
	datagram int // index of its datagram
}

type fragment struct {
	offset uint64
	data   []byte
}

// openDatagram opens the Initial packets a datagram holds (several may be coalesced, RFC 9000 §12.2). The keys come
// from the first packet's DCID: the client keeps it for all its Initial packets until the server answers.
func openDatagram(d []byte, index int, k *keys) ([]packet, error) {
	var out []packet
	for len(d) > 0 {
		if d[0]&0x80 == 0 {
			return out, fmt.Errorf("datagram %d: a short-header packet in the first flight", index)
		}
		if len(d) < 7 {
			return out, fmt.Errorf("datagram %d: header cut short", index)
		}
		p := packet{version: binary.BigEndian.Uint32(d[1:5]), datagram: index}
		if p.version != versionV1 {
			return out, fmt.Errorf("datagram %d: QUIC version %08x, only v1 is read", index, p.version)
		}
		if (d[0]>>4)&0x03 != 0 {
			return out, fmt.Errorf("datagram %d: not an Initial packet (type %d)", index, (d[0]>>4)&0x03)
		}
		i := 5
		l := int(d[i])
		i++
		if len(d) < i+l+1 {
			return out, fmt.Errorf("datagram %d: DCID cut short", index)
		}
		p.dcid = d[i : i+l]
		i += l
		l = int(d[i])
		i++
		if len(d) < i+l {
			return out, fmt.Errorf("datagram %d: SCID cut short", index)
		}
		p.scid = d[i : i+l]
		i += l
		tl, n, err := varint(d[i:])
		if err != nil || len(d) < i+n+int(tl) {
			return out, fmt.Errorf("datagram %d: token cut short", index)
		}
		i += n
		p.token = d[i : i+int(tl)]
		i += int(tl)
		length, n, err := varint(d[i:])
		if err != nil {
			return out, fmt.Errorf("datagram %d: length: %w", index, err)
		}
		i += n
		pnOffset := i
		end := pnOffset + int(length)
		if end > len(d) || pnOffset+4+16 > len(d) {
			return out, fmt.Errorf("datagram %d: packet cut short", index)
		}
		if k.aead == nil {
			*k, _, _, _ = clientKeys(p.dcid)
		}
		// header protection (RFC 9001 §5.4): the sample is 16 bytes, 4 past the packet number's start
		mask := make([]byte, 16)
		k.hp.Encrypt(mask, d[pnOffset+4:pnOffset+4+16])
		header := append([]byte(nil), d[:pnOffset+4]...)
		header[0] ^= mask[0] & 0x0f
		p.numLen = int(header[0]&0x03) + 1
		for j := 0; j < p.numLen; j++ {
			header[pnOffset+j] ^= mask[1+j]
			p.number = p.number<<8 | uint64(header[pnOffset+j])
		}
		header = header[:pnOffset+p.numLen]
		nonce := append([]byte(nil), k.iv...)
		for j := 0; j < 8; j++ {
			nonce[len(nonce)-1-j] ^= byte(p.number >> (8 * j))
		}
		plain, err := k.aead.Open(nil, nonce, d[pnOffset+p.numLen:end], header)
		if err != nil {
			return out, fmt.Errorf("datagram %d: packet %d does not open: %w", index, p.number, err)
		}
		if err := p.readFrames(plain); err != nil {
			return out, fmt.Errorf("datagram %d: packet %d: %w", index, p.number, err)
		}
		out = append(out, p)
		d = d[end:]
		// what may follow a packet in a datagram is another packet or zeros the sender padded with
		if len(d) > 0 && d[0] == 0 {
			break
		}
	}
	return out, nil
}

func (p *packet) readFrames(b []byte) error {
	for len(b) > 0 {
		t, n, err := varint(b)
		if err != nil {
			return err
		}
		b = b[n:]
		switch t {
		case 0x00: // PADDING: a run of them is one
			for len(b) > 0 && b[0] == 0 {
				b = b[1:]
			}
			p.frames = append(p.frames, "PADDING")
		case 0x01:
			p.frames = append(p.frames, "PING")
		case 0x06:
			off, n, err := varint(b)
			if err != nil {
				return err
			}
			b = b[n:]
			l, n, err := varint(b)
			if err != nil || len(b) < n+int(l) {
				return errors.New("CRYPTO frame cut short")
			}
			b = b[n:]
			p.crypto = append(p.crypto, fragment{offset: off, data: b[:l]})
			b = b[l:]
			p.frames = append(p.frames, "CRYPTO")
		case 0x02, 0x03:
			return errors.New("an ACK in the client's first flight")
		case 0x1c:
			return errors.New("CONNECTION_CLOSE in the client's first flight")
		default:
			return fmt.Errorf("frame type %#x in an Initial packet", t)
		}
	}
	return nil
}

// assemble puts the CRYPTO fragments together from offset 0; whole reports whether the ClientHello in them is.
func assemble(packets []packet) (hello []byte, whole bool) {
	var frags []fragment
	for _, p := range packets {
		frags = append(frags, p.crypto...)
	}
	sort.Slice(frags, func(i, j int) bool { return frags[i].offset < frags[j].offset })
	for _, f := range frags {
		if f.offset > uint64(len(hello)) {
			break // a gap: the rest comes later
		}
		if end := f.offset + uint64(len(f.data)); end > uint64(len(hello)) {
			hello = append(hello, f.data[uint64(len(hello))-f.offset:]...)
		}
	}
	if len(hello) < 4 || hello[0] != 0x01 {
		return hello, false
	}
	need := 4 + (int(hello[1])<<16 | int(hello[2])<<8 | int(hello[3]))
	if len(hello) < need {
		return hello, false
	}
	return hello[:need], true
}

// flight opens a client's datagrams, in the order received.
func flight(datagrams [][]byte) ([]packet, error) {
	var k keys
	var all []packet
	for i, d := range datagrams {
		ps, err := openDatagram(d, i, &k)
		if err != nil {
			return all, err
		}
		all = append(all, ps...)
	}
	return all, nil
}

// shape is the first flight as text, one line per part, comparable between two connections of one client.
func shape(datagrams [][]byte, packets []packet) []string {
	var sizes, perDatagram, numbers, numLens []string
	count := map[int]int{}
	for _, p := range packets {
		count[p.datagram]++
		numbers = append(numbers, strconv.FormatUint(p.number, 10))
		numLens = append(numLens, strconv.Itoa(p.numLen))
	}
	for i, d := range datagrams {
		sizes = append(sizes, strconv.Itoa(len(d)))
		perDatagram = append(perDatagram, strconv.Itoa(count[i]))
	}
	kinds := map[string]bool{}
	for _, p := range packets {
		for _, f := range p.frames {
			kinds[f] = true
		}
	}
	var frames []string
	for f := range kinds {
		frames = append(frames, f)
	}
	sort.Strings(frames)
	first := packets[0]
	return []string{
		fmt.Sprintf("version %08x", first.version),
		fmt.Sprintf("dcid length %d", len(first.dcid)),
		fmt.Sprintf("scid length %d", len(first.scid)),
		fmt.Sprintf("token length %d", len(first.token)),
		"datagrams " + strconv.Itoa(len(datagrams)),
		"datagram sizes " + strings.Join(sizes, ","),
		"packets per datagram " + strings.Join(perDatagram, ","),
		"packet numbers " + strings.Join(numbers, ","),
		"packet number lengths " + strings.Join(numLens, ","),
		"frame kinds " + strings.Join(frames, ","),
	}
}

func readDatagrams(path string) ([][]byte, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	var out [][]byte
	s := bufio.NewScanner(f)
	s.Buffer(make([]byte, 1<<16), 1<<20)
	for s.Scan() {
		line := strings.TrimSpace(s.Text())
		if line == "" {
			continue
		}
		d, err := hex.DecodeString(line)
		if err != nil {
			return nil, fmt.Errorf("%s: %w", path, err)
		}
		out = append(out, d)
	}
	if len(out) == 0 {
		return nil, fmt.Errorf("%s: no datagrams", path)
	}
	return out, s.Err()
}

// capture takes the first client's datagrams until its ClientHello is whole. Datagrams from another address, or
// with another DCID, are left out; so are retransmissions after the hello is whole.
func capture(where, out string) error {
	if !strings.Contains(where, ":") {
		where = "127.0.0.1:" + where
	}
	addr, err := net.ResolveUDPAddr("udp", where)
	if err != nil {
		return err
	}
	conn, err := net.ListenUDP("udp", addr)
	if err != nil {
		return err
	}
	defer conn.Close()
	conn.SetReadDeadline(time.Now().Add(2 * time.Minute))
	var from *net.UDPAddr
	var dcid []byte
	var datagrams [][]byte
	buf := make([]byte, 65536)
	for {
		n, src, err := conn.ReadFromUDP(buf)
		if err != nil {
			if len(datagrams) > 0 {
				return fmt.Errorf("the ClientHello was not whole in %d datagrams: %w", len(datagrams), err)
			}
			return err
		}
		d := append([]byte(nil), buf[:n]...)
		if n < 7 || d[0]&0x80 == 0 || int(d[5]) > n-6 {
			continue
		}
		id := d[6 : 6+int(d[5])]
		if from == nil {
			from, dcid = src, append([]byte(nil), id...)
			// the rest of the first flight follows at once
			conn.SetReadDeadline(time.Now().Add(20 * time.Second))
		} else if src.String() != from.String() || string(id) != string(dcid) {
			continue
		}
		datagrams = append(datagrams, d)
		packets, err := flight(datagrams)
		if err != nil {
			return err
		}
		if _, whole := assemble(packets); whole {
			break
		}
	}
	var b strings.Builder
	for _, d := range datagrams {
		b.WriteString(hex.EncodeToString(d) + "\n")
	}
	return os.WriteFile(out, []byte(b.String()), 0o644)
}

func open(in string) ([]string, []byte, error) {
	datagrams, err := readDatagrams(in)
	if err != nil {
		return nil, nil, err
	}
	packets, err := flight(datagrams)
	if err != nil {
		return nil, nil, err
	}
	if len(packets) == 0 {
		return nil, nil, fmt.Errorf("%s: no Initial packet", in)
	}
	hello, whole := assemble(packets)
	if !whole {
		return nil, nil, fmt.Errorf("%s: the ClientHello is not whole (%d bytes)", in, len(hello))
	}
	return shape(datagrams, packets), hello, nil
}

func compare(reference, got string, strict bool) (int, error) {
	want, _, err := open(reference)
	if err != nil {
		return 2, err
	}
	have, _, err := open(got)
	if err != nil {
		return 2, err
	}
	differ := 0
	for i := range want {
		if want[i] != have[i] {
			differ++
			fmt.Printf("::warning::QUIC first flight differs from Chrome's: Chrome %q, here %q\n", want[i], have[i])
		}
	}
	if differ == 0 {
		fmt.Printf("the QUIC first flight has Chrome's shape: %s\n", strings.Join(have, "; "))
		return 0, nil
	}
	if strict {
		return 1, nil
	}
	return 0, nil
}

func main() {
	args := os.Args[1:]
	usage := func() {
		fmt.Fprintln(os.Stderr, "usage: quicinitial capture [HOST:]PORT OUT | open IN HELLO | compare REFERENCE GOT [--strict]")
		os.Exit(2)
	}
	if len(args) < 3 {
		usage()
	}
	switch args[0] {
	case "capture":
		if err := capture(args[1], args[2]); err != nil {
			fmt.Fprintln(os.Stderr, "quicinitial:", err)
			os.Exit(1)
		}
	case "open":
		lines, hello, err := open(args[1])
		if err != nil {
			fmt.Fprintln(os.Stderr, "quicinitial:", err)
			os.Exit(1)
		}
		fmt.Println(strings.Join(lines, "\n"))
		if err := os.WriteFile(args[2], []byte(hex.EncodeToString(hello)+"\n"), 0o644); err != nil {
			fmt.Fprintln(os.Stderr, "quicinitial:", err)
			os.Exit(1)
		}
	case "compare":
		strict := len(args) > 3 && args[3] == "--strict"
		code, err := compare(args[1], args[2], strict)
		if err != nil {
			fmt.Fprintln(os.Stderr, "quicinitial:", err)
		}
		os.Exit(code)
	default:
		usage()
	}
}
