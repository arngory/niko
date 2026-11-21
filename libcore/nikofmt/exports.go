package nikofmt

// What libcore/nikoconf, the app's config build in Go, shares with the parsers: the same Kotlin and JVM behaviour
// for the same beans, so the two packages read a field alike.

// KtTrim is Kotlin's String.trim().
func KtTrim(s string) string { return ktTrim(s) }

// KtIsBlank is Kotlin's String.isBlank(): empty or whitespace as Kotlin's Char.isWhitespace takes it.
func KtIsBlank(s string) bool { return ktIsBlank(s) }

// KtLowercase is Kotlin's String.lowercase() (Locale.ROOT).
func KtLowercase(s string) string { return ktLowercase(s) }

// KtLines is Kotlin's String.lines(): split at \r\n, \n and \r.
func KtLines(s string) []string { return ktLines(s) }

// KtToIntOrNull is Kotlin's String.toIntOrNull(): an optional sign and digits as Character.digit takes them (other
// scripts' decimal digits too), within an Int.
func KtToIntOrNull(s string) (int, bool) {
	n, ok := javaParseInt(s, 10)
	return int(n), ok
}

// JavaParseLong is Long.parseLong(s, radix) for radix 8, 10 or 16, and Kotlin's toLongOrNull() for 10: KtToIntOrNull's
// rules within a Long; ok false where Java throws.
func JavaParseLong(s string, radix int) (int64, bool) { return javaParseLong(s, radix) }

// IsIPAddress is ktx String.isIpAddress: the app's own IPv4 and IPv6 patterns.
func IsIPAddress(s string) bool { return isIPAddress(s) }
