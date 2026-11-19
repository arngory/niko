package libcore

import (
	"context"
	"errors"
	"net/netip"
	"sync"

	M "github.com/sagernet/sing/common/metadata"

	mDNS "github.com/miekg/dns"
)

// nodeLookup resolves a node's name for libcore's own requests (names-v1, raynames) as the core's "local" DNS does:
// the app's resolver on the network under the VPN (Android's DnsResolver, Private DNS included). The default
// resolver would ask through the app's own VPN, which takes the app's traffic too and may answer from FakeIP.
func nodeLookup(ctx context.Context, name string) ([]netip.Addr, error) {
	if gLocalDNSTransport == nil {
		return nil, errors.New("no local resolver")
	}
	var (
		access    sync.Mutex
		addresses []netip.Addr
		errs      []error
		group     sync.WaitGroup
	)
	for _, qtype := range []uint16{mDNS.TypeA, mDNS.TypeAAAA} {
		group.Add(1)
		go func() {
			defer group.Done()
			request := new(mDNS.Msg)
			request.SetQuestion(mDNS.Fqdn(name), qtype)
			response, err := gLocalDNSTransport.Exchange(ctx, request)
			access.Lock()
			defer access.Unlock()
			if err != nil {
				errs = append(errs, err)
				return
			}
			for _, answer := range response.Answer {
				switch record := answer.(type) {
				case *mDNS.A:
					addresses = append(addresses, M.AddrFromIP(record.A))
				case *mDNS.AAAA:
					addresses = append(addresses, M.AddrFromIP(record.AAAA))
				}
			}
		}()
	}
	group.Wait()
	if len(addresses) == 0 && len(errs) > 0 {
		return nil, errors.Join(errs...)
	}
	return addresses, nil
}
