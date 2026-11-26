package libcore

import (
	"context"
	"encoding/json"
	"errors"
	"net"
	"net/http"
	"time"

	"libcore/exitaddress"

	"github.com/sagernet/sing-box/boxapi"
)

type exitProvider = exitaddress.Provider
type exitAnswer = exitaddress.Answer

var exitProviders = exitaddress.Providers

// ExitAddress asks the lookup service [provider] (cloudflare, ipinfo, ipwho, ipapi-co) which address and country the
// connection comes from, through the running core [i]'s final outbound, as the URL test goes.
func ExitAddress(i *BoxInstance, provider string, timeoutMs int32) (string, error) {
	if i == nil {
		return "", errors.New("the core does not run")
	}
	answer, err := exitaddress.Lookup(i.ctx, func(ctx context.Context, network, address string) (net.Conn, error) {
		return boxapi.DialContext(ctx, i.Box, nil, network, address)
	}, provider, time.Duration(timeoutMs)*time.Millisecond)
	if err != nil {
		return "", err
	}
	raw, err := json.Marshal(answer)
	return string(raw), err
}

func exitAddress(ctx context.Context, client *http.Client, p exitProvider) (exitAnswer, error) {
	return exitaddress.Query(ctx, client, p)
}
