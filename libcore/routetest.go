package libcore

import (
	"encoding/json"
	"fmt"

	"libcore/routetest"
)

// RouteTest says which rule of the running core [i] a connection would match and where it would go: the route
// tester of the app (after ThroneForAndroid's, the owner's decision of 2026-11-15). [query] is JSON: network, host,
// port, inbound, an app's uid and package names, resolve. The connection is prepared and the rules walked as the
// router does (route.Router's prepareMatchMetadata and matchRule): through the VPN an app asks the core's DNS for a
// domain first and connects to the address it gets, a fake one standing for the domain; a sniff action takes what
// the port suggests; a resolve action resolves as the router's does. No connection is opened, but with resolve the
// core's DNS is really asked, through its DNS rules, and keeps what an app's query would leave: a FakeDNS address for
// the domain, an entry in its cache, a reverse mapping. Without resolve nothing is asked.
func RouteTest(i *BoxInstance, query string) (string, error) {
	if i == nil {
		return "", fmt.Errorf("the core does not run")
	}
	var q routetest.Query
	if err := json.Unmarshal([]byte(query), &q); err != nil {
		return "", fmt.Errorf("query: %w", err)
	}
	result, err := routetest.Run(i.ctx, q)
	if err != nil {
		return "", err
	}
	raw, err := json.Marshal(result)
	return string(raw), err
}
