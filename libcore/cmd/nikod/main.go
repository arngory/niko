// Command nikod is Niko's desktop daemon: the core's daemon (the fork's experimental/boxdd) with NikoService beside
// its own services (docs/desktop/STAGE3.md). It imports libcore's subpackages only, not the root package, which is
// gomobile's.
package main

import (
	"libcore/nikoservice"

	"github.com/sagernet/sing-box/experimental/boxdd"
)

func main() {
	boxdd.Main(boxdd.Options{Services: []boxdd.Service{nikoservice.Service()}})
}
