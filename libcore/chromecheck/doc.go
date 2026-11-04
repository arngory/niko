// Package chromecheck holds the live check of the core's "chrome" fingerprint against a real browser
// (chromecheck_test.go, TestChromeLive): CI (.github/workflows/fingerprint.yml) runs it with Google Chrome stable,
// weekly and when the core changes. The package has no code of its own.
package chromecheck
