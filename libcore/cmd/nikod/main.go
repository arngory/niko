// Command nikod is Niko's desktop daemon: the core's daemon (the fork's experimental/boxdd) with NikoService beside
// its own services (docs/design/desktop-stage3.md). It imports libcore's subpackages only, not the root package, which is
// gomobile's.
package main

import (
	"github.com/sagernet/sing-box/experimental/boxdd"
)

func main() {
	boxdd.Main(daemonOptions())
}
