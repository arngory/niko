package libcore

import (
	"context"
	"net"
	"testing"
	"time"

	mDNS "github.com/miekg/dns"
)

// The node's name gets the lookup's time and no more, though the resolver waits longer; an answer that came is kept
func TestNodeLookupBoundByContext(t *testing.T) {
	exchange := func(ctx context.Context, request *mDNS.Msg) (*mDNS.Msg, error) {
		if request.Question[0].Qtype == mDNS.TypeAAAA {
			time.Sleep(2 * time.Second) // as Android's raw query, deaf to ctx
			return nil, context.DeadlineExceeded
		}
		response := new(mDNS.Msg)
		response.SetReply(request)
		response.Answer = []mDNS.RR{&mDNS.A{Hdr: mDNS.RR_Header{Name: request.Question[0].Name, Rrtype: mDNS.TypeA, Class: mDNS.ClassINET}, A: net.IPv4(203, 0, 113, 7)}}
		return response, nil
	}
	ctx, cancel := context.WithTimeout(context.Background(), 200*time.Millisecond)
	defer cancel()
	start := time.Now()
	addresses, err := lookupWith(ctx, "n3kq7p.example.com", exchange)
	if elapsed := time.Since(start); elapsed > time.Second {
		t.Fatal("the lookup outlived its context: ", elapsed)
	}
	if err != nil || len(addresses) != 1 || addresses[0].String() != "203.0.113.7" {
		t.Fatal(addresses, err)
	}
}
