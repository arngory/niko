package libcore

import (
	"io"

	"libcore/redact"
)

// Log redaction: thin gomobile wrappers delegating to libcore/redact so Android and desktop
// (nikod) share identical scrubbing rules (BG-10).

// SetLogRedactions sets the literal strings left out of the log from now on (the user's node hosts), one per line.
func SetLogRedactions(list string) {
	redact.SetLiterals(list)
}

// RedactLog is text without secrets and the registered literals, for the log and the debug export.
func RedactLog(text string) string {
	return redact.Log(text)
}

// redactingWriter redacts what passes to w.
type redactingWriter struct{ w io.Writer }

func (r redactingWriter) Write(p []byte) (int, error) {
	return redact.NewWriter(r.w).Write(p)
}
