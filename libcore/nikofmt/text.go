package nikofmt

import (
	"errors"
	"strings"
)

// SubscriptionFound is a subscription's link met in a text the import reads: the app then takes the whole text for
// that subscription (parseProxies' SubscriptionFoundException), not for profiles. errors.Is(err, ErrSubscriptionLink)
// holds for it; its message does not carry the link, which may hold a token.
type SubscriptionFound struct {
	// Link is the link as the text has it: clash://install-config?…, sn://subscription?…, or an http(s) URL that is no
	// proxy (the app wraps that one into clash://install-config?url=…; reading subscriptions comes with them)
	Link string
}

func (e *SubscriptionFound) Error() string { return ErrSubscriptionLink.Error() }

func (e *SubscriptionFound) Unwrap() error { return ErrSubscriptionLink }

// ParseText is ktx/Formats.kt parseProxies, the import of a text (a paste, the clipboard, a QR code's text, a file):
// every link in it, read twice, split into lines and each trimmed line at its spaces, then line by line; whichever
// way gives more profiles is taken, the lines' on a tie. A link that is none of the kinds ParseLink reads, or that it
// drops, is skipped. The first subscription's link met, in either reading, ends it with *SubscriptionFound: the app
// imports that subscription instead of any profile.
func ParseText(text string, settings Settings) ([]Bean, error) {
	lines := strings.Split(text, "\n")
	var byWord, byLine []Bean
	for _, line := range lines {
		for _, word := range strings.Split(ktTrim(line), " ") {
			bean, err := parseWord(word, settings)
			if err != nil {
				return nil, err
			}
			if bean != nil {
				byWord = append(byWord, bean)
			}
		}
	}
	for _, line := range lines {
		bean, err := parseWord(ktTrim(line), settings)
		if err != nil {
			return nil, err
		}
		if bean != nil {
			byLine = append(byLine, bean)
		}
	}
	if len(byWord) > len(byLine) {
		return byWord, nil
	}
	return byLine, nil
}

// parseWord is parseProxies' parseLink: the bean, nil for a link it skips, the subscription it found.
func parseWord(link string, settings Settings) (Bean, error) {
	bean, err := ParseLink(link, settings)
	if errors.Is(err, ErrSubscriptionLink) {
		return nil, &SubscriptionFound{Link: link}
	}
	if err != nil {
		return nil, nil
	}
	return bean, nil
}

// DecodeUTF8 is the text of a file's bytes as the JVM reads it (readText, String(bytes)): UTF-8, what is not UTF-8
// replaced by U+FFFD as Java replaces it.
func DecodeUTF8(data []byte) string { return javaUTF8(data) }
