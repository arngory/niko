//go:build !windows

package nikostore

// No wrapping off Windows: the store's directory is the daemon's alone (0700, its files 0600), root's for the service.
// systemd-creds (the host's key or a TPM2) is to be decided with the Linux packaging (§4, «Секреты»).
func platformSealer() Sealer         { return NoSealer{} }
func platformUserModeSealer() Sealer { return NoSealer{} }
