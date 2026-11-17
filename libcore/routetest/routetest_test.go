package routetest

import (
	"context"
	"errors"
	"fmt"
	"os"
	"testing"
)

type netTimeout struct{}

func (netTimeout) Error() string   { return "i/o timeout" }
func (netTimeout) Timeout() bool   { return true }
func (netTimeout) Temporary() bool { return true }

// Only a lookup that failed for time is the limit's: not a server missing from the config, not the DNS's own answer,
// not a cancellation (a core stopped, a caller gone).
func TestTimeoutError(t *testing.T) {
	for _, tc := range []struct {
		err  error
		want bool
	}{
		{context.DeadlineExceeded, true},
		{fmt.Errorf("lookup: %w", context.DeadlineExceeded), true},
		{os.ErrDeadlineExceeded, true},
		{fmt.Errorf("exchange: %w", netTimeout{}), true},
		{errors.New("DNS server not found: dns-x"), false},
		{errors.New("fake address the core does not remember"), false},
		{context.Canceled, false},
	} {
		if got := timeoutError(tc.err); got != tc.want {
			t.Errorf("%v: %v, want %v", tc.err, got, tc.want)
		}
	}
	if !errors.Is(ErrTimeLimit, context.DeadlineExceeded) {
		t.Error("ErrTimeLimit does not wrap context.DeadlineExceeded")
	}
}
