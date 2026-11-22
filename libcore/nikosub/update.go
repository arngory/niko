package nikosub

import (
	"context"
	"errors"
	"strconv"
	"strings"
	"time"
	"unicode"

	"libcore/nikofmt"
)

// Subscription is a group's subscription, the app's SubscriptionBean as far as a computer uses it.
type Subscription struct {
	// Link is the address the profiles are fetched from: http or https
	Link string `json:"link"`
	// ForceResolve: the servers' names resolved at each update (GroupUpdater.forceResolve)
	ForceResolve bool `json:"forceResolve,omitempty"`
	// Deduplication: one profile of each server
	Deduplication bool `json:"deduplication,omitempty"`
	// UpdateWhenConnectedOnly: the timed update waits for a connection, and an update asked for warns without one
	UpdateWhenConnectedOnly bool `json:"updateWhenConnectedOnly,omitempty"`
	// CustomUserAgent: "" for the app's
	CustomUserAgent string `json:"customUserAgent,omitempty"`
	// AutoUpdate and AutoUpdateDelay (minutes): the timed update
	AutoUpdate      bool `json:"autoUpdate,omitempty"`
	AutoUpdateDelay int  `json:"autoUpdateDelay"`
	// LastUpdated: unix seconds of the last update, 0 for none
	LastUpdated int64 `json:"lastUpdated,omitempty"`
	// UserInfo is the last answer's subscription-userinfo as it came; Announce its announce, decoded
	UserInfo string `json:"subscriptionUserinfo,omitempty"`
	Announce string `json:"announce,omitempty"`
	// FallbackLink is the provider's spare address (fallback-url); HwidHost the host device ids are of after a move
	FallbackLink string `json:"fallbackLink,omitempty"`
	HwidHost     string `json:"hwidHost,omitempty"`
	// SendHwid: the device's ids sent to panels that limit devices
	SendHwid bool `json:"sendHwid,omitempty"`
}

// DefaultAutoUpdateDelay is SubscriptionBean's: a day.
const DefaultAutoUpdateDelay = 1440

// NewSubscription is a subscription of a link with SubscriptionBean's defaults.
func NewSubscription(link string) Subscription {
	return Subscription{Link: link, AutoUpdateDelay: DefaultAutoUpdateDelay}
}

// Usage is the traffic and expiry the last answer said, ok false for none.
func (s *Subscription) Usage() (Usage, bool) { return ParseUserInfo(s.UserInfo) }

// DefaultName is what a group of a subscription imported without a name is named, the app's "Subscription #<ms>":
// its first update takes the provider's name.
func DefaultName(now time.Time) string {
	return "Subscription #" + strconv.FormatInt(now.UnixMilli(), 10)
}

// ApplyAnswer is what an answer's headers change of a group and its subscription (RawUpdater.doUpdate before the
// profiles): the traffic and expiry, the announcement, a group of the default name ("Subscription #…") takes the
// provider's (profile-title, else content-disposition's filename*), and the provider's interval turns the timed update
// on at the first update. The group's name after it; an error where the app's URLDecoder throws.
func ApplyAnswer(name string, sub *Subscription, header func(string) string) (string, error) {
	sub.UserInfo = header("Subscription-Userinfo")
	sub.Announce = DecodeHeader(header("announce"))
	if strings.HasPrefix(name, "Subscription #") {
		remote := DecodeHeader(header("profile-title"))
		if nikofmt.KtIsBlank(remote) {
			remote = header("content-disposition")
			if !nikofmt.KtIsBlank(remote) {
				decoded, ok := decodeFilename(remote)
				if !ok {
					return "", errors.New("the provider's file name is no URL-encoded text")
				}
				remote = decoded
			}
		}
		if !nikofmt.KtIsBlank(remote) {
			name = remote
		}
	}
	// the provider's interval only on the first update: later the user's choice wins
	if sub.LastUpdated == 0 {
		if minutes, ok := UpdateIntervalMinutes(header("profile-update-interval")); ok {
			sub.AutoUpdate, sub.AutoUpdateDelay = true, minutes
		}
	}
	return name, nil
}

// Update is what one update of a group does.
type Update struct {
	// Name is the group's name after it; Subscription the subscription
	Name         string
	Subscription Subscription
	// Plan is what becomes of the group's profiles
	Plan Plan
	// Moved: the provider moved the subscription to Subscription.Link
	Moved bool
}

// Options are an update's.
type Options struct {
	Client   *Client
	Settings nikofmt.Settings
	// Resolver for a subscription that says forceResolve; nil: the system's
	Resolver Resolver
	// IPv6First: the IPv6 setting prefers IPv6 (ipv6Mode PREFER or ONLY)
	IPv6First bool
	// Now is the update's time, for lastUpdated; nil: time.Now
	Now func() time.Time
}

// RefusedError is a panel's refusal of this device (HwidRefusal), with its own text.
type RefusedError struct {
	Refusal Refusal
	// Text is the panel's announce, cleaned, for the limit reached
	Text   string
	SentID bool
}

func (e *RefusedError) Error() string {
	switch e.Refusal {
	case LimitReached:
		message := "the provider's panel refused this device: the subscription's devices are all taken"
		if e.Text != "" {
			message += "\n⁨" + e.Text + "⁩"
		}
		return message
	case IDRequired:
		if e.SentID {
			return "the provider's panel refused this device's id"
		}
		return "the provider's panel wants this device's id: turn on sending it in the subscription's settings"
	}
	return "refused"
}

// announceText is RayNode.nodeNote of a refusal's announce: the panel's words, controls dropped, at most 300
// characters.
func announceText(announce string) string {
	text := strings.Map(func(r rune) rune {
		if unicode.IsControl(r) || r == '⁦' || r == '⁧' || r == '⁨' || r == '⁩' ||
			r == '‪' || r == '‫' || r == '‬' || r == '‭' || r == '‮' {
			return ' '
		}
		return r
	}, DecodeHeader(announce))
	text = strings.Join(strings.Fields(text), " ")
	if runes := []rune(text); len(runes) > 300 {
		text = string(runes[:300]) + "…"
	}
	return text
}

// FetchError is an answer the subscription did not get: no connection, a status other than 200, a body too large.
type FetchError struct{ Err error }

func (e *FetchError) Error() string { return "the subscription's answer: " + e.Err.Error() }

func (e *FetchError) Unwrap() error { return e.Err }

// fetched is an answer and whether the spare address gave it.
type fetched struct {
	response  *Response
	fromSpare bool
}

// fetchSubscription is RawUpdater.fetchSubscription: the main address, and where it fails the spare one once.
func fetchSubscription(ctx context.Context, c *Client, sub *Subscription, address string) (fetched, error) {
	response, err := c.Fetch(ctx, sub, address)
	if err == nil {
		return fetched{response: response}, nil
	}
	if refusal := refusalOfError(err.Error()); refusal != NoRefusal {
		return fetched{}, &RefusedError{Refusal: refusal, SentID: sub.SendHwid && c.InstallSecret != ""}
	}
	spare := sub.FallbackLink
	if nikofmt.KtIsBlank(spare) || spare == address || ctx.Err() != nil {
		return fetched{}, err
	}
	response, spareErr := c.Fetch(ctx, sub, spare)
	if spareErr != nil {
		if refusal := refusalOfError(spareErr.Error()); refusal != NoRefusal {
			return fetched{}, &RefusedError{Refusal: refusal, SentID: sub.SendHwid && c.InstallSecret != ""}
		}
		return fetched{}, err
	}
	return fetched{response: response, fromSpare: true}, nil
}

// hostOf is RawUpdater.hostOf.
func hostOf(address string) string {
	if u, err := nikofmt.ParseJavaURI(address); err == nil && u.Host != "" {
		return u.Host
	}
	return address
}

// Run is RawUpdater.doUpdate of a group of that name, its subscription and its profiles (exists, in their order):
// the answer fetched (the spare address where the main one fails), a move the provider asks for taken once the new
// address answers with profiles, the body read, the headers applied, the profiles merged. Nothing is written: the
// caller writes the result in one go (the app's partial writes, where its store throws midway, are not repeated).
func Run(ctx context.Context, name string, sub Subscription, exists []Existing, o Options) (Update, error) {
	c := o.Client
	if c == nil {
		c = &Client{}
	}
	now := time.Now
	if o.Now != nil {
		now = o.Now
	}
	got, err := fetchSubscription(ctx, c, &sub, sub.Link)
	var refused *RefusedError
	if err != nil && !errors.As(err, &refused) {
		return Update{}, &FetchError{Err: err}
	}
	if err != nil {
		return Update{}, err
	}
	response := got.response
	content := response.Body
	if refusal := RefusalOf(response.header); refusal != NoRefusal {
		return Update{}, &RefusedError{Refusal: refusal, Text: announceText(response.header("announce")),
			SentID: sub.SendHwid && c.InstallSecret != ""}
	}
	// the provider moved the subscription (new-url, new-domain): read before the profiles, as the old address may
	// answer with the move alone. The new address is taken only once it answers with profiles, and the device id
	// stays the one the panel knows (hwidHost)
	move := MoveOf(sub.Link, response.header, content)
	var movedProxies []nikofmt.Bean
	moved := false
	if move.To != "" {
		if answer, err := c.Fetch(ctx, &sub, move.To); err == nil && RefusalOf(answer.header) == NoRefusal {
			if fromNew, err := Parse(answer.Body, "", o.Settings); err == nil && len(fromNew) > 0 {
				if nikofmt.KtIsBlank(sub.HwidHost) {
					sub.HwidHost = hostOf(sub.Link)
				}
				sub.Link = move.To
				response, content, movedProxies, moved = answer, answer.Body, fromNew, true
				move = MoveOf(move.To, answer.header, answer.Body)
			}
		}
	}
	proxies := movedProxies
	if !moved {
		if proxies, err = Parse(content, "", o.Settings); err != nil {
			return Update{}, err
		}
	}
	sub.FallbackLink = SpareAfterAnswer(sub.FallbackLink, move.Spare, got.fromSpare && !moved)
	if name, err = ApplyAnswer(name, &sub, response.header); err != nil {
		return Update{}, err
	}
	proxies = Prepare(proxies)
	if sub.ForceResolve {
		resolver := o.Resolver
		if resolver == nil {
			resolver = defaultResolver{}
		}
		Resolve(ctx, resolver, proxies, o.IPv6First)
	}
	plan, err := Merge(exists, proxies, sub.Deduplication)
	if err != nil {
		return Update{}, err
	}
	sub.LastUpdated = now().Unix()
	return Update{Name: name, Subscription: sub, Plan: plan, Moved: moved}, nil
}
