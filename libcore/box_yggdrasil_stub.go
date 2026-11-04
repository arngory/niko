//go:build !with_yggdrasil

package libcore

import (
	"errors"

	"github.com/sagernet/sing-box/adapter/endpoint"
)

func registerYggdrasil(registry *endpoint.Registry) {}

func YggdrasilGenerateKey() (string, error) {
	return "", errors.New("yggdrasil is not included in this build")
}

func YggdrasilAddress(privateKeyHex string) string { return "" }

func YggdrasilPeers(i *BoxInstance, tag string) (string, error) {
	return "", errors.New("yggdrasil is not included in this build")
}
