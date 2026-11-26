package nikoservice

import (
	"context"
	"errors"
	"net"
	"net/http"
	"net/netip"
	"time"

	"libcore/redact"
)

// syncLogRedactions collects raycontrol node hosts and DNS names across all opened user stores
// and registers them with libcore/redact so daemon logs never leak node addresses (BG-10).
func (s *server) syncLogRedactions() {
	if s.users == nil {
		return
	}
	var literals []string
	for _, u := range s.users.opened() {
		for _, stored := range u.store.Nodes() {
			if stored.Node == nil {
				continue
			}
			if stored.Node.Host != "" {
				literals = append(literals, stored.Node.Host)
			}
			if stored.Node.DNSName != "" {
				literals = append(literals, stored.Node.DNSName)
			}
			for _, past := range stored.Node.PastNames {
				if past != "" {
					literals = append(literals, past)
				}
			}
			for _, relay := range stored.Node.Relays {
				if relay.Host != "" {
					literals = append(literals, relay.Host)
				}
			}
			for _, addr := range stored.Node.Addrs {
				if addr != "" {
					literals = append(literals, addr)
				}
			}
			for _, addr := range stored.Node.SubAddrs {
				if addr != "" {
					literals = append(literals, addr)
				}
			}
		}
	}
	redact.SetLiteralsSlice(literals)
}

// bypassTransport returns an HTTP RoundTripper for daemon-initiated subscription and rule-set
// requests that bypasses the active TUN via s.bypass() (physical interface Control + core direct
// DNS), avoiding system proxy loops. When a test injects s.transport, that transport is returned.
func (s *server) bypassTransport() http.RoundTripper {
	if s.transport != nil {
		return s.transport
	}
	s.bypassTransportOnce.Do(func() {
		base, ok := http.DefaultTransport.(*http.Transport)
		var tr *http.Transport
		if ok {
			tr = base.Clone()
		} else {
			tr = &http.Transport{}
		}
		// Never follow OS/environment proxy settings for daemon-originated requests.
		tr.Proxy = nil
		tr.DialContext = s.dialBypass
		s.cachedBypassRT = tr
	})
	return s.cachedBypassRT
}

func (s *server) dialBypass(ctx context.Context, network, address string) (net.Conn, error) {
	control, lookup := s.bypass()
	dialer := &net.Dialer{
		Timeout:   30 * time.Second,
		KeepAlive: 30 * time.Second,
		Control:   control,
	}
	host, port, err := net.SplitHostPort(address)
	if err != nil {
		return dialer.DialContext(ctx, network, address)
	}
	if _, parseErr := netip.ParseAddr(host); parseErr == nil {
		return dialer.DialContext(ctx, network, address)
	}
	addrs, err := lookup(ctx, host)
	if err != nil {
		return nil, err
	}
	if len(addrs) == 0 {
		return nil, &net.DNSError{Err: "no such host", Name: host, IsNotFound: true}
	}
	var lastErr error
	for _, addr := range addrs {
		conn, dialErr := dialer.DialContext(ctx, network, net.JoinHostPort(addr.String(), port))
		if dialErr == nil {
			return conn, nil
		}
		lastErr = dialErr
	}
	if lastErr == nil {
		lastErr = errors.New("dial failed")
	}
	return nil, lastErr
}
