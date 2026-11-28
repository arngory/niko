//go:build !((linux || darwin) && with_naive_outbound && with_purego)

package main

func preloadNativeLibraries() {}
