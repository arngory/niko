package libcore

import "libcore/knock"

// SpaSetDeviceKey stores a raycontrol device key (64 hex) for the knock
// outbound's spa_v2.device_key_ref, so the key never appears in the config.
// An empty key removes it.
func SpaSetDeviceKey(ref string, keyHex string) {
	knock.SetDeviceKey(ref, keyHex)
}
