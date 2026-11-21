package nikoservice

import (
	"sync"

	"libcore/nikopb"

	"google.golang.org/protobuf/proto"
)

// watchBuffer is how many changes a watcher may lag behind before it is given a reset instead of them.
const watchBuffer = 256

// feed numbers one user's changes, in the order their writes were made, and hands them to the user's watchers
// (NikoService.Watch). The revision lives in the daemon's memory: a daemon started again counts from 0, and each
// watcher starts with a reset anyway.
type feed struct {
	mu       sync.Mutex
	revision uint64
	watchers map[*watcher]struct{}
}

type watcher struct {
	changes chan *nikopb.Change
	// lost: a change did not fit; under feed.mu
	lost bool
}

func newFeed() *feed { return &feed{watchers: map[*watcher]struct{}{}} }

// publish gives a change its revision and every watcher a copy. It never blocks: a watcher with no room is marked
// lost and gets a reset in place of what it missed.
func (f *feed) publish(change *nikopb.Change) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.revision++
	change.Revision = f.revision
	for w := range f.watchers {
		if w.lost {
			continue
		}
		select {
		case w.changes <- proto.Clone(change).(*nikopb.Change):
		default:
			w.lost = true
		}
	}
}

// subscribe is a new watcher and the revision it starts from: nothing published after it is missed.
func (f *feed) subscribe() (*watcher, uint64) {
	f.mu.Lock()
	defer f.mu.Unlock()
	w := &watcher{changes: make(chan *nikopb.Change, watchBuffer)}
	f.watchers[w] = struct{}{}
	return w, f.revision
}

func (f *feed) unsubscribe(w *watcher) {
	f.mu.Lock()
	defer f.mu.Unlock()
	delete(f.watchers, w)
}

// next is what to send of a change taken from the watcher: the change, or a reset with the revision now where the
// watcher lost some, what it still held dropped (the reset covers it).
func (f *feed) next(w *watcher, change *nikopb.Change) *nikopb.Change {
	f.mu.Lock()
	defer f.mu.Unlock()
	if !w.lost {
		return change
	}
	w.lost = false
	for {
		select {
		case <-w.changes:
		default:
			return &nikopb.Change{Revision: f.revision, Reset_: true}
		}
	}
}
