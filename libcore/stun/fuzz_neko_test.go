package stun

import (
	"encoding/binary"
	"testing"
)

// Whatever a server sends, parsing an answer and reading its addresses never panics (neko: an XOR-MAPPED-ADDRESS
// longer than 20 bytes ran past its buffer and took the app down).
func FuzzNekoPacket(f *testing.F) {
	attr := func(t uint16, value []byte) []byte {
		b := make([]byte, 4, 4+len(value))
		binary.BigEndian.PutUint16(b[0:2], t)
		binary.BigEndian.PutUint16(b[2:4], uint16(len(value)))
		return append(b, value...)
	}
	header := func(attrs []byte) []byte {
		b := make([]byte, 20)
		binary.BigEndian.PutUint16(b[0:2], 0x0101)
		binary.BigEndian.PutUint16(b[2:4], uint16(len(attrs)))
		binary.BigEndian.PutUint32(b[4:8], magicCookie)
		return append(b, attrs...)
	}
	f.Add(header(attr(attributeXorMappedAddress, make([]byte, 24))))
	f.Add(header(attr(attributeMappedAddress, []byte{0, 1, 0, 80, 1, 2, 3, 4})))
	f.Add(header(attr(attributeMappedAddress, []byte{0, 2, 0, 80})))
	f.Add(header(attr(attributeXorMappedAddress, []byte{0, 1})))
	f.Fuzz(func(t *testing.T, data []byte) {
		p, err := newPacketFromBytes(data)
		if err != nil {
			return
		}
		p.getXorMappedAddr()
		p.getMappedAddr()
		p.getChangedAddr()
		p.getOtherAddr()
	})
}

func TestNekoLongAddressAttribute(t *testing.T) {
	b := make([]byte, 20)
	binary.BigEndian.PutUint16(b[0:2], 0x0101)
	binary.BigEndian.PutUint16(b[2:4], 28)
	binary.BigEndian.PutUint32(b[4:8], magicCookie)
	b = append(b, 0, byte(attributeXorMappedAddress), 0, 24)
	b = append(b, make([]byte, 24)...)
	p, err := newPacketFromBytes(b)
	if err != nil {
		t.Skip(err)
	}
	if p.getXorMappedAddr() != nil {
		t.Fatal("a 24-byte address taken")
	}
}
