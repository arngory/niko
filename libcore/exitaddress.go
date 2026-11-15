package libcore

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"strings"
	"time"

	"github.com/sagernet/sing-box/boxapi"
)

// exitProvider is a lookup service the exit address is asked of: HTTPS only, so the answer is the service's and the
// path to it shows nothing but its name.
type exitProvider struct {
	url   string
	parse func(body []byte) (exitAnswer, error)
}

// exitAnswer is what the app shows: the address the service saw the connection from, its country and city when the
// service names them.
type exitAnswer struct {
	Provider    string `json:"provider"`
	IP          string `json:"ip"`
	CountryCode string `json:"country_code,omitempty"`
	Country     string `json:"country,omitempty"`
	City        string `json:"city,omitempty"`
}

// exitProviders by the ids the app offers (after ThroneForAndroid's exit lookup, without its plain-HTTP one).
var exitProviders = map[string]exitProvider{
	// plain key=value lines; loc is the country's code
	"cloudflare": {"https://www.cloudflare.com/cdn-cgi/trace", func(body []byte) (exitAnswer, error) {
		fields := map[string]string{}
		for _, line := range strings.Split(string(body), "\n") {
			if key, value, ok := strings.Cut(strings.TrimSpace(line), "="); ok {
				fields[key] = value
			}
		}
		return exitAnswer{IP: fields["ip"], CountryCode: fields["loc"]}, nil
	}},
	"ipinfo": {"https://ipinfo.io/json", func(body []byte) (exitAnswer, error) {
		var r struct{ IP, Country, City string }
		err := json.Unmarshal(body, &r)
		return exitAnswer{IP: r.IP, CountryCode: r.Country, City: r.City}, err
	}},
	"ipwho": {"https://ipwho.is/", func(body []byte) (exitAnswer, error) {
		var r struct {
			Success     *bool  `json:"success"`
			Message     string `json:"message"`
			IP          string `json:"ip"`
			CountryCode string `json:"country_code"`
			Country     string `json:"country"`
			City        string `json:"city"`
		}
		if err := json.Unmarshal(body, &r); err != nil {
			return exitAnswer{}, err
		}
		if r.Success != nil && !*r.Success {
			return exitAnswer{}, fmt.Errorf("the service: %s", r.Message)
		}
		return exitAnswer{IP: r.IP, CountryCode: r.CountryCode, Country: r.Country, City: r.City}, nil
	}},
	"ipapi-co": {"https://ipapi.co/json/", func(body []byte) (exitAnswer, error) {
		var r struct {
			Error       bool   `json:"error"`
			Reason      string `json:"reason"`
			IP          string `json:"ip"`
			CountryCode string `json:"country_code"`
			Country     string `json:"country_name"`
			City        string `json:"city"`
		}
		if err := json.Unmarshal(body, &r); err != nil {
			return exitAnswer{}, err
		}
		if r.Error {
			return exitAnswer{}, fmt.Errorf("the service: %s", r.Reason)
		}
		return exitAnswer{IP: r.IP, CountryCode: r.CountryCode, Country: r.Country, City: r.City}, nil
	}},
}

// ExitAddress asks the lookup service [provider] (cloudflare, ipinfo, ipwho, ipapi-co) which address and country the
// connection comes from, through the running core [i]'s final outbound, as the URL test goes: the profile's exit,
// whatever the rules say of the service's host. The service learns the node's address and when it was asked, so the
// app asks only when the user does, after saying so. The answer is JSON (exitAnswer).
func ExitAddress(i *BoxInstance, provider string, timeoutMs int32) (string, error) {
	if i == nil {
		return "", errors.New("the core does not run")
	}
	p, ok := exitProviders[provider]
	if !ok {
		return "", fmt.Errorf("no lookup service %q", provider)
	}
	if timeoutMs <= 0 {
		timeoutMs = 10000
	}
	timeout := time.Duration(timeoutMs) * time.Millisecond
	ctx, cancel := context.WithTimeout(i.ctx, timeout)
	defer cancel()
	// the URL test's dialer (the final outbound), not its client: its 3 s for the TLS handshake and for the answer's
	// headers are too short for a slow profile (a DNS tunnel), the whole timeout is the bound here
	client := &http.Client{Transport: &http.Transport{
		DialContext: func(ctx context.Context, network, address string) (net.Conn, error) {
			return boxapi.DialContext(ctx, i.Box, nil, network, address)
		},
		TLSHandshakeTimeout:   timeout,
		ResponseHeaderTimeout: timeout,
		ForceAttemptHTTP2:     true,
	}}
	defer client.CloseIdleConnections()
	answer, err := exitAddress(ctx, client, p)
	if err != nil {
		return "", err
	}
	answer.Provider = provider
	raw, err := json.Marshal(answer)
	return string(raw), err
}

// exitAddress asks p through client: a 2xx answer of 64 KiB at most, which must name an address.
func exitAddress(ctx context.Context, client *http.Client, p exitProvider) (exitAnswer, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, p.url, nil)
	if err != nil {
		return exitAnswer{}, err
	}
	// Go's own User-Agent: the service learns the address, not which app asked
	request.Header.Set("Accept", "application/json, text/plain")
	response, err := client.Do(request)
	if err != nil {
		return exitAnswer{}, err
	}
	defer response.Body.Close()
	if response.StatusCode/100 != 2 {
		return exitAnswer{}, fmt.Errorf("the service answered %s", response.Status)
	}
	body, err := io.ReadAll(io.LimitReader(response.Body, 64<<10))
	if err != nil {
		return exitAnswer{}, err
	}
	answer, err := p.parse(body)
	if err != nil {
		return exitAnswer{}, err
	}
	addr, err := netip.ParseAddr(strings.TrimSpace(answer.IP))
	if err != nil {
		return exitAnswer{}, fmt.Errorf("the service named no address (%q)", answer.IP)
	}
	answer.IP = addr.Unmap().String()
	answer.CountryCode = strings.ToUpper(strings.TrimSpace(answer.CountryCode))
	if len(answer.CountryCode) != 2 {
		answer.CountryCode = ""
	}
	return answer, nil
}
