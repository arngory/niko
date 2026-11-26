//go:build !(linux && with_naive_outbound && with_purego)

package main

func preloadNativeLibraries() {}
