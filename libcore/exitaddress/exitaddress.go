// Package exitaddress queries an HTTPS lookup service through a proxy dialer to discover
// the apparent exit IP address and geo-location.
package exitaddress

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"strings"
	"time"
)

// DefaultProvider is the default HTTPS exit lookup provider.
const DefaultProvider = "cloudflare"

// Answer is the validated response returned by an exit lookup service.
type Answer struct {
	Provider    string `json:"provider"`
	IP          string `json:"ip"`
	CountryCode string `json:"country_code,omitempty"`
	Country     string `json:"country,omitempty"`
	City        string `json:"city,omitempty"`
}

// Provider describes an HTTPS lookup endpoint and its response parser.
type Provider struct {
	URL   string
	Parse func(body []byte) (Answer, error)
}

// Providers maps supported provider IDs to their HTTPS endpoints.
var Providers = map[string]Provider{
	"cloudflare": {"https://www.cloudflare.com/cdn-cgi/trace", func(body []byte) (Answer, error) {
		fields := map[string]string{}
		for _, line := range strings.Split(string(body), "\n") {
			if key, value, ok := strings.Cut(strings.TrimSpace(line), "="); ok {
				fields[key] = value
			}
		}
		return Answer{IP: fields["ip"], CountryCode: fields["loc"]}, nil
	}},
	"ipinfo": {"https://ipinfo.io/json", func(body []byte) (Answer, error) {
		var r struct{ IP, Country, City string }
		err := json.Unmarshal(body, &r)
		return Answer{IP: r.IP, CountryCode: r.Country, City: r.City}, err
	}},
	"ipwho": {"https://ipwho.is/", func(body []byte) (Answer, error) {
		var r struct {
			Success     *bool  `json:"success"`
			Message     string `json:"message"`
			IP          string `json:"ip"`
			CountryCode string `json:"country_code"`
			Country     string `json:"country"`
			City        string `json:"city"`
		}
		if err := json.Unmarshal(body, &r); err != nil {
			return Answer{}, err
		}
		if r.Success != nil && !*r.Success {
			return Answer{}, fmt.Errorf("the service: %s", r.Message)
		}
		return Answer{IP: r.IP, CountryCode: r.CountryCode, Country: r.Country, City: r.City}, nil
	}},
	"ipapi-co": {"https://ipapi.co/json/", func(body []byte) (Answer, error) {
		var r struct {
			Error       bool   `json:"error"`
			Reason      string `json:"reason"`
			IP          string `json:"ip"`
			CountryCode string `json:"country_code"`
			Country     string `json:"country_name"`
			City        string `json:"city"`
		}
		if err := json.Unmarshal(body, &r); err != nil {
			return Answer{}, err
		}
		if r.Error {
			return Answer{}, fmt.Errorf("the service: %s", r.Reason)
		}
		return Answer{IP: r.IP, CountryCode: r.CountryCode, Country: r.Country, City: r.City}, nil
	}},
}

// Lookup queries provider using dial (e.g. boxapi.DialContext) within timeout.
func Lookup(ctx context.Context, dial func(ctx context.Context, network, address string) (net.Conn, error), provider string, timeout time.Duration) (Answer, error) {
	if provider == "" {
		provider = DefaultProvider
	}
	p, ok := Providers[provider]
	if !ok {
		return Answer{}, fmt.Errorf("no lookup service %q", provider)
	}
	if timeout <= 0 {
		timeout = 10 * time.Second
	}
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	client := &http.Client{Transport: &http.Transport{
		DialContext:           dial,
		TLSHandshakeTimeout:   timeout,
		ResponseHeaderTimeout: timeout,
		ForceAttemptHTTP2:     true,
	}}
	defer client.CloseIdleConnections()
	ans, err := Query(ctx, client, p)
	if err != nil {
		return Answer{}, err
	}
	ans.Provider = provider
	return ans, nil
}

// Query executes p over client, enforcing 2xx status, 64 KiB body cap, and valid IP parsing.
func Query(ctx context.Context, client *http.Client, p Provider) (Answer, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, p.URL, nil)
	if err != nil {
		return Answer{}, err
	}
	request.Header.Set("Accept", "application/json, text/plain")
	response, err := client.Do(request)
	if err != nil {
		return Answer{}, err
	}
	defer response.Body.Close()
	if response.StatusCode/100 != 2 {
		return Answer{}, fmt.Errorf("the service answered %s", response.Status)
	}
	body, err := io.ReadAll(io.LimitReader(response.Body, 64<<10))
	if err != nil {
		return Answer{}, err
	}
	answer, err := p.Parse(body)
	if err != nil {
		return Answer{}, err
	}
	addr, err := netip.ParseAddr(strings.TrimSpace(answer.IP))
	if err != nil {
		return Answer{}, fmt.Errorf("the service named no address (%q)", answer.IP)
	}
	answer.IP = addr.Unmap().String()
	answer.CountryCode = strings.ToUpper(strings.TrimSpace(answer.CountryCode))
	if len(answer.CountryCode) != 2 {
		answer.CountryCode = ""
	}
	return answer, nil
}
