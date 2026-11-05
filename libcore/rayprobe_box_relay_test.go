package libcore

import (
	"bytes"
	"encoding/hex"
	"testing"

	"libcore/knock"
)

// a relay's probe endpoint takes RC1 on K_relay (relay-v1 §9.1): the request's SPA names the relay
func TestProbeRequestRelayKey(t *testing.T) {
	device := make([]byte, 32)
	for i := range device {
		device[i] = byte(i)
	}
	exit, relay := make([]byte, 16), make([]byte, 16)
	for i := range exit {
		exit[i], relay[i] = 0xa0+byte(i), 0xb0+byte(i)
	}
	want, _ := knock.RelayKey(device, exit, relay, "r1")
	req := `{"host":"198.51.100.20","port":8443,"pin":"p","device_id":"d","spa":{"node_id":"` + hex.EncodeToString(relay) +
		`","device_key":"` + hex.EncodeToString(device) + `","relay":{"exit_node_id":"` + hex.EncodeToString(exit) + `","id":"r1"}}}`
	_, key, err := parseProbeRequest(req)
	if err != nil || !bytes.Equal(key, want) {
		t.Fatalf("%x %v", key, err)
	}
	// the node itself: the device key
	_, key, err = parseProbeRequest(`{"spa":{"node_id":"` + hex.EncodeToString(exit) + `","device_key":"` + hex.EncodeToString(device) + `"}}`)
	if err != nil || !bytes.Equal(key, device) {
		t.Fatalf("%x %v", key, err)
	}
	if _, _, err = parseProbeRequest(`{"spa":{"node_id":"00","device_key":"` + hex.EncodeToString(device) + `","relay":{"exit_node_id":"x","id":"r1"}}}`); err == nil {
		t.Fatal("a bad relay passed")
	}
}
