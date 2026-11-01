package knock

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/binary"
	mrand "math/rand/v2"
	"time"
)

// Niko SPA v1 (single packet authorization), one UDP datagram:
//
//	nonce   16 bytes, random
//	time     8 bytes, unix seconds, big endian
//	mac     32 bytes, HMAC-SHA256(key, "niko-spa-v1" || nonce || time)
//	padding  0-63 random bytes, random length
//
// The server tries each client key, accepts |now - time| <= 30s and drops a
// nonce it has seen in the last 60s. It then lets the sender's address in.
// The packet has no fixed bytes: it reads as random data of a random length.
const (
	spaKeySize   = 32
	spaNonceSize = 16
	spaHeader    = spaNonceSize + 8
	spaMinSize   = spaHeader + sha256.Size
	spaMaxPad    = 63
)

var spaLabel = []byte("niko-spa-v1")

func spaMAC(key, nonce []byte, unix uint64) []byte {
	mac := hmac.New(sha256.New, key)
	mac.Write(spaLabel)
	mac.Write(nonce)
	var t [8]byte
	binary.BigEndian.PutUint64(t[:], unix)
	mac.Write(t[:])
	return mac.Sum(nil)
}

// spaPacketWith builds the packet from given parts (tests use fixed ones).
func spaPacketWith(key, nonce []byte, unix uint64, padding []byte) []byte {
	packet := make([]byte, 0, spaMinSize+len(padding))
	packet = append(packet, nonce...)
	packet = binary.BigEndian.AppendUint64(packet, unix)
	packet = append(packet, spaMAC(key, nonce, unix)...)
	return append(packet, padding...)
}

func spaPacket(key []byte) []byte {
	nonce := make([]byte, spaNonceSize)
	rand.Read(nonce)
	padding := make([]byte, mrand.IntN(spaMaxPad+1))
	rand.Read(padding)
	return spaPacketWith(key, nonce, uint64(time.Now().Unix()), padding)
}

// spaVerify is the server side check, kept next to the client for the tests
// and as the reference for the server.
func spaVerify(key, packet []byte, now time.Time, window time.Duration) bool {
	if len(packet) < spaMinSize || len(packet) > spaMinSize+spaMaxPad {
		return false
	}
	nonce := packet[:spaNonceSize]
	unix := binary.BigEndian.Uint64(packet[spaNonceSize:spaHeader])
	if !hmac.Equal(packet[spaHeader:spaMinSize], spaMAC(key, nonce, unix)) {
		return false
	}
	sent := time.Unix(int64(unix), 0)
	return sent.After(now.Add(-window)) && sent.Before(now.Add(window))
}
