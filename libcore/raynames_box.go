package libcore

import (
	"context"
	"errors"
	"net/netip"

	M "github.com/sagernet/sing/common/metadata"

	mDNS "github.com/miekg/dns"
)

// nodeLookup resolves a node's name for libcore's own requests (names-v1, raynames) as the core's "local" DNS does:
// the app's resolver on the network under the VPN (Android's DnsResolver, Private DNS included). The default
// resolver would ask through the app's own VPN, which takes the app's traffic too and may answer from FakeIP.
func nodeLookup(ctx context.Context, name string) ([]netip.Addr, error) {
	return lookupWith(ctx, name, func(ctx context.Context, request *mDNS.Msg) (*mDNS.Msg, error) {
		if gLocalDNSTransport == nil {
			return nil, errors.New("no local resolver")
		}
		return gLocalDNSTransport.Exchange(ctx, request)
	})
}

type lookupAnswer struct {
	response *mDNS.Msg
	err      error
}

// lookupWith asks A and AAAA of name at once and takes what came by ctx's end: Android's raw query waits 5 s on its
// own whatever the context, and the lookup has 3 s (raynames.LookupTimeout). A late answer is left to its goroutine,
// which the buffer lets end.
func lookupWith(ctx context.Context, name string, exchange func(context.Context, *mDNS.Msg) (*mDNS.Msg, error)) ([]netip.Addr, error) {
	qtypes := []uint16{mDNS.TypeA, mDNS.TypeAAAA}
	answers := make(chan lookupAnswer, len(qtypes))
	for _, qtype := range qtypes {
		go func() {
			request := new(mDNS.Msg)
			request.SetQuestion(mDNS.Fqdn(name), qtype)
			response, err := exchange(ctx, request)
			answers <- lookupAnswer{response, err}
		}()
	}
	var (
		addresses []netip.Addr
		errs      []error
	)
	for range qtypes {
		select {
		case answer := <-answers:
			if answer.err != nil {
				errs = append(errs, answer.err)
				continue
			}
			for _, record := range answer.response.Answer {
				switch record := record.(type) {
				case *mDNS.A:
					addresses = append(addresses, M.AddrFromIP(record.A).Unmap())
				case *mDNS.AAAA:
					addresses = append(addresses, M.AddrFromIP(record.AAAA))
				}
			}
		case <-ctx.Done():
			errs = append(errs, ctx.Err())
			if len(addresses) > 0 {
				return addresses, nil
			}
			return nil, errors.Join(errs...)
		}
	}
	if len(addresses) == 0 && len(errs) > 0 {
		return nil, errors.Join(errs...)
	}
	return addresses, nil
}
