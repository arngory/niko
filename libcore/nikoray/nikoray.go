// Package nikoray is a raycontrol node for the desktop daemon nikod, the static part (docs/design/desktop-stage3.md,
// slice 11), ported from the app's raycontrol package as it reads a node: its invites and device packages
// (invite.go, Rcdev1.kt), its names (names.go, RayNames.kt), the node the device keeps (node.go, RayNodes.kt) and its
// subscription's payload (payload.go, RaySubscription.parse and RayRows.kt). The golden fixtures the app writes
// (shared/golden/raycontrol, the app's io.niko.golden.RaycontrolGoldenTest) hold it to the app's reading. The network
// is libcore/rayapi's (the registration) and libcore/raysub's (the subscription); the store is libcore/nikostore's.
package nikoray
