package nikostore

import (
	"encoding/json"
	"errors"
	"fmt"
	"sort"
	"strings"
	"unicode/utf8"

	"libcore/nikofmt"
	"libcore/nikosub"
)

// A subscription's group (slice 9): its subscription (nikosub.Subscription, the app's SubscriptionBean) beside the
// group in store.json, the addresses sealed as a profile's secrets are, since a subscription's address is its token;
// its profiles are the subscription's, which only an update (ApplyUpdate) adds, changes and orders.
//
// store.json, schema 2: a group of type 1 has "subscription", its fields with the app's names, and "sealed" where the
// sealer holds its link and spare address:
//
//	{"id": 3, "order": 3, "name": "Provider", "type": 1, "subscription": {"autoUpdateDelay": 1440, …}, "sealed": "dpapi:…"}

// subscriptionSecrets are the subscription's fields sealed on the disk.
var subscriptionSecrets = map[string]bool{"link": true, "fallbackLink": true}

// sealSubscription is a subscription for the disk: its JSON without the secret fields and those sealed.
func sealSubscription(sealer Sealer, sub *nikosub.Subscription) (json.RawMessage, string, error) {
	data, err := json.Marshal(sub)
	if err != nil {
		return nil, "", err
	}
	if sealer.Name() == "" {
		return data, "", nil
	}
	var fields map[string]json.RawMessage
	if err = json.Unmarshal(data, &fields); err != nil {
		return nil, "", err
	}
	secrets := map[string]json.RawMessage{}
	for name, value := range fields {
		if subscriptionSecrets[name] && string(value) != `""` {
			secrets[name] = value
			delete(fields, name)
		}
	}
	if len(secrets) == 0 {
		return data, "", nil
	}
	return sealFields(sealer, fields, secrets)
}

// errNoLink: a subscription's address is http or https.
var errNoLink = fmt.Errorf("%w: a subscription's address is an http or https URL", ErrInvalid)

// checkLink is a subscription's address: an http or https URL with a host, as the app's update fetches.
func checkLink(link string) error {
	if len(link) > 8192 {
		return errNoLink
	}
	lower := strings.ToLower(link)
	if !strings.HasPrefix(lower, "http://") && !strings.HasPrefix(lower, "https://") {
		return errNoLink
	}
	if u, err := nikofmt.ParseJavaURI(link); err != nil || u.Host == "" {
		return errNoLink
	}
	return nil
}

// SubscriptionSettings are what the user sets of a subscription; the rest is its updates'.
type SubscriptionSettings struct {
	Link                    string
	Deduplication           bool
	ForceResolve            bool
	UpdateWhenConnectedOnly bool
	CustomUserAgent         string
	AutoUpdate              bool
	// AutoUpdateDelay in minutes, 0 for the app's (a day)
	AutoUpdateDelay int
	IsSelector      bool
	AutoSelect      bool
	FrontProxy      int64
	LandingProxy    int64
	SortOrder       int
}

// SaveSubscriptionGroup saves a subscription's group: a new one (ID 0) at the end, its name the app's default
// ("Subscription #…") where none is given, which its first update replaces by the provider's; or the name and the
// settings of the one of that ID, what its updates set kept. The group as saved.
func (s *Store) SaveSubscriptionGroup(id int64, name string, settings SubscriptionSettings) (Group, error) {
	if len(name) > maxName {
		return Group{}, fmt.Errorf("%w: a name over %d bytes", ErrInvalid, maxName)
	}
	settings.Link = strings.TrimSpace(settings.Link)
	if err := checkLink(settings.Link); err != nil {
		return Group{}, err
	}
	if settings.AutoUpdateDelay == 0 {
		settings.AutoUpdateDelay = nikosub.DefaultAutoUpdateDelay
	}
	if settings.AutoUpdateDelay < 15 || settings.AutoUpdateDelay > 60*24*365 {
		return Group{}, fmt.Errorf("%w: an update's interval is from 15 minutes to a year", ErrInvalid)
	}
	if len(settings.CustomUserAgent) > maxName {
		return Group{}, fmt.Errorf("%w: a user agent over %d bytes", ErrInvalid, maxName)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.state.clone()
	var saved Group
	var sub nikosub.Subscription
	if id == 0 {
		var last int64
		for _, other := range st.groups {
			last = max(last, other.Order)
		}
		if name == "" {
			name = nikosub.DefaultName(s.now())
		}
		saved = Group{ID: st.nextGroup, Order: last + 1, Name: name, Type: GroupSubscription}
		sub = nikosub.NewSubscription(settings.Link)
		st.nextGroup++
		st.groups = append(st.groups, saved)
	} else {
		old, i := st.group(id)
		if i < 0 {
			return Group{}, ErrNotFound
		}
		if old.Type != GroupSubscription {
			return Group{}, fmt.Errorf("%w: group %d is no subscription's", ErrRefused, id)
		}
		if old.unreadable && settings.Link == "" {
			return Group{}, fmt.Errorf("%w: its address was sealed elsewhere: it is to be given again", ErrRefused)
		}
		saved = old
		if name != "" {
			saved.Name = name
		}
		sub = *old.Subscription
		if sub.Link != settings.Link {
			// another address: what the old one's provider said goes with it
			sub.FallbackLink, sub.HwidHost = "", ""
		}
		sub.Link = settings.Link
	}
	sub.Deduplication = settings.Deduplication
	sub.ForceResolve = settings.ForceResolve
	sub.UpdateWhenConnectedOnly = settings.UpdateWhenConnectedOnly
	sub.CustomUserAgent = settings.CustomUserAgent
	sub.AutoUpdate = settings.AutoUpdate
	sub.AutoUpdateDelay = settings.AutoUpdateDelay
	saved.IsSelector = settings.IsSelector
	saved.AutoSelect = settings.AutoSelect
	saved.FrontProxy = settings.FrontProxy
	saved.LandingProxy = settings.LandingProxy
	saved.SortOrder = settings.SortOrder
	if err := s.setSubscription(&saved, sub); err != nil {
		return Group{}, err
	}
	_, i := st.group(saved.ID)
	st.groups[i] = saved
	if err := s.commit(st, Change{Kind: KindGroups, IDs: []int64{saved.ID}}); err != nil {
		return Group{}, err
	}
	return st.withCount(saved), nil
}

// setSubscription gives a group its subscription, sealed for the disk.
func (s *Store) setSubscription(g *Group, sub nikosub.Subscription) error {
	disk, sealed, err := sealSubscription(s.sealer, &sub)
	if err != nil {
		return fmt.Errorf("sealing the subscription: %w", err)
	}
	g.Subscription, g.subscriptionDisk, g.subscriptionSealed, g.unreadable = &sub, disk, sealed, false
	return nil
}

func (st *state) withCount(g Group) Group {
	g.Profiles = 0
	for _, r := range st.profiles {
		if r.group == g.ID {
			g.Profiles++
		}
	}
	g.Subscription = copySubscription(g.Subscription)
	return g
}

func copySubscription(sub *nikosub.Subscription) *nikosub.Subscription {
	if sub == nil {
		return nil
	}
	c := *sub
	return &c
}

// ErrUnreadable is a subscription whose address was sealed on another machine or by another account: it is to be
// given again (SaveSubscriptionGroup) before the group updates.
var ErrUnreadable = errors.New("its address was sealed by another machine or account")

// SubscriptionOf is a subscription's group and its profiles as an update reads them: each bean in the list's order
// (by number, then id), what the disk has of one whose secrets cannot be opened here.
func (s *Store) SubscriptionOf(id int64) (Group, []nikosub.Existing, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	g, i := s.state.group(id)
	if i < 0 {
		return Group{}, nil, ErrNotFound
	}
	if g.Type != GroupSubscription || g.Subscription == nil {
		return Group{}, nil, fmt.Errorf("%w: group %d is no subscription's", ErrRefused, id)
	}
	if g.unreadable {
		return Group{}, nil, fmt.Errorf("%w: group %d: %v", ErrRefused, id, ErrUnreadable)
	}
	var exists []nikosub.Existing
	for _, r := range s.state.sorted(id) {
		bean, err := nikofmt.NewBean(r.typ)
		if err != nil {
			return Group{}, nil, err
		}
		if err = json.Unmarshal(r.bean, bean); err != nil {
			return Group{}, nil, fmt.Errorf("profile %d: %w", r.id, err)
		}
		bean.InitializeDefaultValues()
		exists = append(exists, nikosub.Existing{ID: r.id, Order: r.order, Bean: bean})
	}
	return s.state.withCount(g), exists, nil
}

// Subscriptions are the subscriptions' groups, in their order.
func (s *Store) Subscriptions() []Group {
	var out []Group
	for _, g := range s.Groups() {
		if g.Type == GroupSubscription {
			out = append(out, g)
		}
	}
	return out
}

// Applied is what an update wrote: the profiles added, the ids changed (added, changed, moved or deleted).
type Applied struct {
	Added   []int64
	Changed []int64
}

// ApplyUpdate writes an update of a subscription's group in one write: the group's name and subscription, its profiles
// as the plan has them. A profile of the plan the user deleted meanwhile stays deleted, one deleted by the plan and
// gone already is no matter; the selected profile deleted is no selection.
func (s *Store) ApplyUpdate(id int64, update nikosub.Update) (Applied, error) {
	if len(update.Name) > maxName {
		// at a character's start: protobuf takes valid UTF-8 only
		cut := maxName
		for cut > 0 && !utf8.RuneStart(update.Name[cut]) {
			cut--
		}
		update.Name = update.Name[:cut]
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.state.clone()
	g, i := st.group(id)
	if i < 0 {
		return Applied{}, ErrNotFound
	}
	if g.Type != GroupSubscription {
		return Applied{}, fmt.Errorf("%w: group %d is no subscription's", ErrRefused, id)
	}
	// an update runs for a while with the network: what the user changed meanwhile wins over what it read at its
	// start; a link changed is another provider's, whose profiles these are not
	cur := g.Subscription
	if cur == nil || g.unreadable {
		return Applied{}, fmt.Errorf("%w: group %d has no subscription to update", ErrRefused, id)
	}
	if cur.Link != update.From.Link {
		return Applied{}, fmt.Errorf("%w: the subscription's link changed while it updated", ErrRefused)
	}
	g.Name = mergeField(g.Name, update.FromName, update.Name)
	if err := s.setSubscription(&g, mergeSubscription(*cur, update.From, update.Subscription)); err != nil {
		return Applied{}, err
	}
	st.groups[i] = g
	var applied Applied
	for _, gone := range update.Plan.Deleted {
		if r := st.profiles[gone]; r != nil && r.group == id {
			delete(st.profiles, gone)
			applied.Changed = append(applied.Changed, gone)
		}
	}
	for _, p := range update.Plan.Profiles {
		switch {
		case p.ID == 0:
			r, err := s.newRecord(st.nextProfile, id, p.Order, p.Bean)
			if err != nil {
				return Applied{}, err
			}
			st.profiles[r.id] = r
			st.nextProfile++
			applied.Added = append(applied.Added, r.id)
			applied.Changed = append(applied.Changed, r.id)
		case p.State == nikosub.Kept:
		default:
			old := st.profiles[p.ID]
			if old == nil || old.group != id {
				continue
			}
			r, err := s.newRecord(p.ID, id, p.Order, p.Bean)
			if err != nil {
				return Applied{}, err
			}
			st.profiles[p.ID] = r
			applied.Changed = append(applied.Changed, p.ID)
		}
	}
	sort.Slice(applied.Changed, func(i, j int) bool { return applied.Changed[i] < applied.Changed[j] })
	changes := []Change{{Kind: KindGroups, IDs: []int64{id}}}
	if len(applied.Changed) > 0 {
		changes = append(changes, Change{Kind: KindProfiles, IDs: applied.Changed})
	}
	if err := s.commit(st, changes...); err != nil {
		return Applied{}, err
	}
	return applied, nil
}

// decodeSubscription reads a subscription's group from the disk: its sealed fields opened where this sealer can
// (else it is unreadable, kept on the disk as it is), sealed now where the disk had them open and this store seals.
// resealed: the file is to be written again.
func (s *Store) decodeSubscription(g *Group, d diskGroup) (resealed bool, err error) {
	g.subscriptionDisk, g.subscriptionSealed = d.Subscription, d.Sealed
	data, err := openBean(s.sealer, d.Subscription, d.Sealed)
	if errors.Is(err, errSealed) {
		data, g.unreadable = d.Subscription, true
	} else if err != nil {
		return false, err
	}
	var sub nikosub.Subscription
	if err = json.Unmarshal(data, &sub); err != nil {
		return false, err
	}
	if sub.AutoUpdateDelay == 0 {
		sub.AutoUpdateDelay = nikosub.DefaultAutoUpdateDelay
	}
	g.Subscription = &sub
	if d.Sealed == "" && s.sealer.Name() != "" && !g.unreadable {
		disk, sealed, err := sealSubscription(s.sealer, &sub)
		if err != nil {
			return false, fmt.Errorf("sealing the subscription: %w", err)
		}
		g.subscriptionDisk, g.subscriptionSealed = disk, sealed
		return sealed != "", nil
	}
	return false, nil
}

// mergeField is a field's value after an update that began from from: the update's where it changed the field and
// the user did not meanwhile, else the current one.
func mergeField[T comparable](current, from, updated T) T {
	if updated != from && current == from {
		return updated
	}
	return current
}

// mergeSubscription merges each field of an update's subscription into the current one (mergeField).
func mergeSubscription(current, from, updated nikosub.Subscription) nikosub.Subscription {
	m := current
	m.Link = mergeField(current.Link, from.Link, updated.Link)
	m.ForceResolve = mergeField(current.ForceResolve, from.ForceResolve, updated.ForceResolve)
	m.Deduplication = mergeField(current.Deduplication, from.Deduplication, updated.Deduplication)
	m.UpdateWhenConnectedOnly = mergeField(current.UpdateWhenConnectedOnly, from.UpdateWhenConnectedOnly,
		updated.UpdateWhenConnectedOnly)
	m.CustomUserAgent = mergeField(current.CustomUserAgent, from.CustomUserAgent, updated.CustomUserAgent)
	m.AutoUpdate = mergeField(current.AutoUpdate, from.AutoUpdate, updated.AutoUpdate)
	m.AutoUpdateDelay = mergeField(current.AutoUpdateDelay, from.AutoUpdateDelay, updated.AutoUpdateDelay)
	m.LastUpdated = mergeField(current.LastUpdated, from.LastUpdated, updated.LastUpdated)
	m.UserInfo = mergeField(current.UserInfo, from.UserInfo, updated.UserInfo)
	m.Announce = mergeField(current.Announce, from.Announce, updated.Announce)
	m.FallbackLink = mergeField(current.FallbackLink, from.FallbackLink, updated.FallbackLink)
	m.HwidHost = mergeField(current.HwidHost, from.HwidHost, updated.HwidHost)
	m.SendHwid = mergeField(current.SendHwid, from.SendHwid, updated.SendHwid)
	return m
}
