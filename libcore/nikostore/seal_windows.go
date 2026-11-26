//go:build windows

package nikostore

import (
	"unsafe"

	"golang.org/x/sys/windows"
)

// dpapi seals with CryptProtectData in the daemon's own context: SYSTEM's for the service, so only SYSTEM on this
// machine opens it. No CRYPTPROTECT_LOCAL_MACHINE, with which any user of the machine could; no UI ever. The entropy
// keeps these blobs from being taken for another program's of the same account.
type dpapi struct{}

var (
	dpapiEntropy     = []byte("niko store secrets v1")
	dpapiUserEntropy = []byte("niko user store secrets v1")
)

func platformSealer() Sealer         { return dpapi{} }
func platformUserModeSealer() Sealer { return dpapiUser{} }

func (dpapi) Name() string { return "dpapi" }

// dpapiUser seals with CryptProtectData in the unprivileged desktop user's context using a
// distinct label ("dpapi-user") and distinct entropy so user-mode stores are never silently
// confused with SYSTEM service stores ("dpapi").
type dpapiUser struct{}

func (dpapiUser) Name() string { return "dpapi-user" }

func (dpapiUser) Seal(plain []byte) ([]byte, error) {
	in, entropy := dataBlob(plain), dataBlob(dpapiUserEntropy)
	var out windows.DataBlob
	err := windows.CryptProtectData(&in, nil, &entropy, 0, nil, windows.CRYPTPROTECT_UI_FORBIDDEN, &out)
	if err != nil {
		return nil, err
	}
	return takeBlob(&out), nil
}

func (dpapiUser) Open(sealed []byte) ([]byte, error) {
	in, entropy := dataBlob(sealed), dataBlob(dpapiUserEntropy)
	var out windows.DataBlob
	err := windows.CryptUnprotectData(&in, nil, &entropy, 0, nil, windows.CRYPTPROTECT_UI_FORBIDDEN, &out)
	if err != nil {
		return nil, err
	}
	return takeBlob(&out), nil
}

func (dpapi) Seal(plain []byte) ([]byte, error) {
	in, entropy := dataBlob(plain), dataBlob(dpapiEntropy)
	var out windows.DataBlob
	err := windows.CryptProtectData(&in, nil, &entropy, 0, nil, windows.CRYPTPROTECT_UI_FORBIDDEN, &out)
	if err != nil {
		return nil, err
	}
	return takeBlob(&out), nil
}

func (dpapi) Open(sealed []byte) ([]byte, error) {
	in, entropy := dataBlob(sealed), dataBlob(dpapiEntropy)
	var out windows.DataBlob
	err := windows.CryptUnprotectData(&in, nil, &entropy, 0, nil, windows.CRYPTPROTECT_UI_FORBIDDEN, &out)
	if err != nil {
		return nil, err
	}
	return takeBlob(&out), nil
}

func dataBlob(b []byte) windows.DataBlob {
	if len(b) == 0 {
		return windows.DataBlob{}
	}
	return windows.DataBlob{Size: uint32(len(b)), Data: &b[0]}
}

// takeBlob copies what DPAPI returned and frees its memory, the plain text's wiped first.
func takeBlob(out *windows.DataBlob) []byte {
	if out.Data == nil {
		return []byte{}
	}
	defer windows.LocalFree(windows.Handle(unsafe.Pointer(out.Data)))
	data := unsafe.Slice(out.Data, out.Size)
	result := make([]byte, len(data))
	copy(result, data)
	clear(data)
	return result
}
