// Package nikosub is a provider's subscription in Go as the app updates one (app/src/main/java/io/niko/group:
// RawUpdater.kt, GroupUpdater.kt, SubscriptionHeaders.kt; bg/SubscriptionUpdater.kt), for the desktop daemon
// (docs/design/desktop-stage3.md, slice 9): the answer fetched (fetch.go), its body read as the app reads it
// (Parse: a Clash config, a WireGuard .conf, JSON, base64 of links, links), its headers (headers.go: traffic and
// expiry, the provider's name and interval, a move to another address), and the profiles it gives merged into the
// group's as the app merges them (merge.go). The fixtures in shared/golden/subscriptions, which the app's own code
// writes (io.niko.golden.SubscriptionsGoldenTest), are what golden_test.go holds this package to.
//
// Links are nikofmt's (ParseText is the app's parseProxies). A raycontrol node's subscription is not one of these:
// it comes with the nodes (slice 11). Like nikofmt, the package does not import the root package libcore.
package nikosub

import (
	"errors"
	"strings"

	"libcore/nikofmt"
)

// ErrNoProfiles is a body the app finds no profiles in (no_proxies_found): the update fails, the group stays.
var ErrNoProfiles = errors.New("no profiles found in the subscription")

// Parse is RawUpdater.parseRaw of a subscription's body: a Clash config where it says "proxies:", a WireGuard
// .conf where it says "[Interface]", else JSON, else base64 of links, else links. Each kind the app tries is tried
// in its order, a kind that fails giving way to the next, as the app's catches do; what the app lets throw fails
// here too (a Clash config whose proxies are no list, a subscription's link among the links). The beans have the
// defaults the app's reading gives them, which is not all of them for a single Shadowsocks or Hysteria JSON (the
// app's store then throws on them: Merge fails as its update does). An empty list is a body of no profiles the app
// takes, a JSON object of no kind it knows: the update leaves the group empty. ErrNoProfiles where it finds none.
// fileName is a file's name, for a WireGuard profile's ("" for a subscription's answer).
func Parse(text, fileName string, settings nikofmt.Settings) ([]nikofmt.Bean, error) {
	if strings.Contains(text, "proxies:") {
		proxies, err := parseClash(text)
		var fatal *fatalYAML
		switch {
		case err == nil:
			return proxies, nil
		case errors.Is(err, errNoProxies), errors.As(err, &fatal):
			return nil, err
		}
	} else if strings.Contains(text, "[Interface]") {
		if proxies, err := parseWireGuard(text); err == nil {
			if fileName != "" {
				for _, p := range proxies {
					nikofmt.Base(p).Name = ptrTo(strings.TrimSuffix(fileName, ".conf"))
				}
			}
			return proxies, nil
		}
	}
	if value, err := nikofmt.OrgNextValue(text); err == nil {
		if proxies, err := parseJSONValue(value); err == nil {
			return proxies, nil
		}
	}
	if decoded, err := nikofmt.DecodeBase64UrlSafe(text); err == nil {
		if proxies, err := nikofmt.ParseText(decoded, settings); err == nil && len(proxies) > 0 {
			return proxies, nil
		}
	}
	proxies, err := nikofmt.ParseText(text, settings)
	if err != nil {
		return nil, err // a subscription's link: the app's SubscriptionFoundException goes through
	}
	if len(proxies) > 0 {
		return proxies, nil
	}
	return nil, ErrNoProfiles
}
