package libcore

import (
	"context"
	"errors"
	"sync"
	"time"

	E "github.com/sagernet/sing/common/exceptions"
)

// The watchdog's early word (the owner, 2026-11-17): connections through the node's outbound that do not open, many
// in a short while and to more than one place, wake its check of the profile at once instead of at its next turn
// (RayWatchdog.connectionsFailing). One place failing is that place, not the node; a false word costs one test.
const (
	dialFailWindow = 30 * time.Second
	dialFailCount  = 5
	dialFailPlaces = 3
	// between two words: the check takes this long at most, and the failures go on meanwhile
	dialFailQuiet = 2 * time.Minute
	dialFailKept  = 64
)

type dialFailure struct {
	at    time.Time
	place string
}

// dialWatch is the running core's route.DialFailureObserver (box.go): only its main outbound counts.
type dialWatch struct {
	access   sync.Mutex
	failures []dialFailure
	lastWord time.Time
	now      func() time.Time
	word     func(count int)
}

func newDialWatch(word func(count int)) *dialWatch {
	return &dialWatch{now: time.Now, word: word}
}

func (w *dialWatch) DialFailed(outboundTag string, destination string, err error) {
	// what the app closed or cancelled is no failure of the node's
	if outboundTag != "proxy" || err == nil || errors.Is(err, context.Canceled) || E.IsClosedOrCanceled(err) {
		return
	}
	w.access.Lock()
	now := w.now()
	kept := w.failures[:0]
	for _, failure := range w.failures {
		if now.Sub(failure.at) < dialFailWindow {
			kept = append(kept, failure)
		}
	}
	w.failures = append(kept, dialFailure{at: now, place: destination})
	if len(w.failures) > dialFailKept {
		w.failures = w.failures[len(w.failures)-dialFailKept:]
	}
	places := make(map[string]struct{}, len(w.failures))
	for _, failure := range w.failures {
		places[failure.place] = struct{}{}
	}
	count := len(w.failures)
	fire := count >= dialFailCount && len(places) >= dialFailPlaces &&
		(w.lastWord.IsZero() || now.Sub(w.lastWord) >= dialFailQuiet)
	if fire {
		w.lastWord = now
		w.failures = nil
	}
	w.access.Unlock()
	if fire {
		go w.word(count)
	}
}
