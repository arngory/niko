package libcore

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"testing"
	"time"
)

func TestDialWatchWord(t *testing.T) {
	now := time.Date(2026, 11, 17, 12, 0, 0, 0, time.UTC)
	var access sync.Mutex
	words := 0
	watch := newDialWatch(func(int) {
		access.Lock()
		words++
		access.Unlock()
	})
	watch.now = func() time.Time { return now }
	heard := func() int {
		time.Sleep(20 * time.Millisecond)
		access.Lock()
		defer access.Unlock()
		return words
	}
	fail := errors.New("no answer from the node")
	// one place failing again and again is that place: no word
	for i := 0; i < 6; i++ {
		watch.DialFailed("proxy", "a:443", fail)
	}
	if heard() != 0 {
		t.Fatal("a word for one place")
	}
	// other outbounds, cancellations: not counted
	for i := 0; i < 5; i++ {
		watch.DialFailed("direct", fmt.Sprintf("d%d:443", i), fail)
		watch.DialFailed("proxy", fmt.Sprintf("c%d:443", i), context.Canceled)
	}
	if heard() != 0 {
		t.Fatal("a word for other outbounds or cancellations")
	}
	// within the window, a third place: the word, once
	watch.DialFailed("proxy", "b:443", fail)
	watch.DialFailed("proxy", "c:443", fail)
	if heard() != 1 {
		t.Fatalf("%d words", heard())
	}
	// failures going on within the quiet time: no second word
	for i := 0; i < 6; i++ {
		watch.DialFailed("proxy", fmt.Sprintf("e%d:443", i), fail)
	}
	if heard() != 1 {
		t.Fatal("a word within the quiet time")
	}
	// after it, again
	now = now.Add(dialFailQuiet)
	for i := 0; i < 5; i++ {
		watch.DialFailed("proxy", fmt.Sprintf("f%d:443", i), fail)
	}
	if heard() != 2 {
		t.Fatalf("%d words after the quiet time", heard())
	}
	// failures older than the window do not add up
	now = now.Add(dialFailQuiet)
	for i := 0; i < 4; i++ {
		watch.DialFailed("proxy", fmt.Sprintf("g%d:443", i), fail)
	}
	now = now.Add(dialFailWindow)
	watch.DialFailed("proxy", "h:443", fail)
	if heard() != 2 {
		t.Fatal("old failures counted")
	}
}
