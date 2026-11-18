package libcore

import "libcore/rayclient"

// SetRaycontrolClient sets the X-Raycontrol-Client header of every request to a raycontrol node and its relays
// (raycontrol docs/spec/client-min-v1.md §1), "niko/<version> (android; <ABI>)". false: a value the node would not
// read, and no header goes.
func SetRaycontrolClient(value string) bool {
	return rayclient.Set(value)
}
