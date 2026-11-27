// Package nikostore is the desktop daemon's store of one user's groups and profiles (docs/design/desktop-stage3.md §4):
// store.json in a directory of that user's under the daemon's, written whole and atomically by the daemon alone, one
// writer at a time. The profiles' beans are nikofmt's, the app's JSON field for field; their secret fields are sealed
// on the disk where the platform has a way to (DPAPI on Windows).
//
// store.json, schema 4 (a subscription's group with its subscription, subscription.go; the route rules, rules.go; the
// raycontrol nodes and the client keys of registrations under way, nodes.go):
//
//	{
//	  "schema": 3,
//	  "next_group": 2, "next_profile": 1,       the ids to give next: an id is never given twice
//	  "next_rule": 1,
//	  "groups": [{"id": 1, "order": 1, "ungrouped": true}, {"id": 2, "order": 2, "name": "…"}],
//	  "profiles": [{"id": 1, "group": 1, "order": 1, "type": "vmess", "bean": {…}, "sealed": "dpapi:…"}],
//	  "rules": [{"id": 1, "name": "…", "userOrder": 1, "enabled": true, "domains": "…", "outbound": -1, …}],
//	  "nodes": [{"host": "…", "spa": {…}, "group_id": 3, …, "sealed": "dpapi:…"}],
//	  "pending_registrations": {"<invite id>": {"pin": "…", "created_at": 1800000000, "sealed": "dpapi:…"}}
//	}
//
// The ungrouped group (the app's ProxyGroup.ungrouped) is made with the store and stays: an import with no group goes
// there. A profile's type is TypeMap's name; "bean" is its JSON without the secret fields where "sealed" holds them.
//
// state.json beside it holds what may be lost: the selected profile, {"selected": 3}. It is written after store.json
// and read leniently: a missing or broken file, or a profile that is no more, is no selection. settings.json holds the
// computer's settings (settings.go).
package nikostore

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"sync"
	"time"

	"libcore/nikoconf"
	"libcore/nikofmt"
	"libcore/nikosub"
)

const (
	storeFile   = "store.json"
	stateFile   = "state.json"
	backupDir   = "backup"
	keptBackups = 3
	// what one profile's bean and one group's name may be: a custom config is the largest bean there is
	maxBean = 1 << 20
	maxName = 1024
)

// The store's errors, by what the caller did wrong; nikoservice gives each its gRPC code.
var (
	ErrNotFound = errors.New("no such profile or group")
	// ErrInvalid: what was asked cannot be a profile or a group
	ErrInvalid = errors.New("invalid")
	// ErrRefused: what was asked is not done to this profile or group (the ungrouped group's deletion, a profile
	// whose secrets cannot be opened here)
	ErrRefused = errors.New("refused")
	// ErrUnsupported: not done by this build
	ErrUnsupported = errors.New("not supported yet")
	// ErrSealerMismatch: store.json was written under an incompatible sealer context (e.g. user-mode vs service).
	ErrSealerMismatch = errors.New("store sealer context mismatch")
)

// Group types, the app's GroupType.
const (
	GroupBasic        = 0
	GroupSubscription = 1
)

// Group is a group of profiles, as ListGroups shows it.
type Group struct {
	ID    int64
	Order int64
	Name  string
	// Ungrouped: the default group, made with the store: the imports with no group go there, it is not deleted
	Ungrouped bool
	Type      int
	// Profiles is how many profiles it has (not stored)
	Profiles int
	// Subscription is a subscription's group's (subscription.go), nil for a basic group; its Link is "" where the
	// address was sealed elsewhere
	Subscription *nikosub.Subscription
	// Node is the raycontrol node whose rows the group holds (nodes.go), its id; "" for none
	Node string

	// subscriptionDisk and subscriptionSealed are what store.json holds of the subscription; unreadable: its sealed
	// fields cannot be opened here, and stay on the disk as they are until it is saved again
	subscriptionDisk   json.RawMessage
	subscriptionSealed string
	unreadable         bool
}

// diskGroup is a group in store.json.
type diskGroup struct {
	ID        int64  `json:"id"`
	Order     int64  `json:"order"`
	Name      string `json:"name,omitempty"`
	Ungrouped bool   `json:"ungrouped,omitempty"`
	Type      int    `json:"type,omitempty"`
	// Subscription and Sealed: a subscription's group's, schema 2 (subscription.go)
	Subscription json.RawMessage `json:"subscription,omitempty"`
	Sealed       string          `json:"sealed,omitempty"`
	// Node: a raycontrol node's group, schema 4 (nodes.go)
	Node string `json:"node,omitempty"`
}

// Profile is a profile: its place, its type, the name and address a list shows, and its bean.
type Profile struct {
	ID    int64
	Group int64
	Order int64
	// Type is TypeMap's name of the bean's type: vmess, naive, hysteria…
	Type string
	// Name and Address are the bean's displayName and displayAddress
	Name    string
	Address string
	// Kind and Transport are the protocol kind ("vless-reality", "hysteria2", "dnstt"...) and carrier ("tcp", "udp", "dns").
	Kind      string
	Transport string
	// Bean is the bean's JSON, with its secrets; nil in a list's rows
	Bean json.RawMessage
}

// Kind is what a change is of.
type Kind int

const (
	KindProfiles Kind = iota + 1
	KindGroups
	// KindSelected: the selected profile changed; IDs holds the new one, none for no selection
	KindSelected
	// KindSettings: the computer's settings changed (settings.go)
	KindSettings
	// KindRules: route rules added, changed or removed (rules.go)
	KindRules
	// KindNodes: a raycontrol node added, changed or removed (nodes.go); no ids, the nodes are named by theirs
	KindNodes
)

// Change is what one write changed: the ids of the profiles or groups added, changed or removed.
type Change struct {
	Kind Kind
	IDs  []int64
}

// Options are how a store is opened.
type Options struct {
	// Sealer wraps the secret fields on the disk; nil: DefaultSealer
	Sealer Sealer
	// OnChange is told of each change written, in their order, under the store's lock: it must not block and must not
	// call the store
	OnChange func(Change)
	// Now dates the backups; nil: time.Now
	Now func() time.Time
}

// Store is one user's store, open.
type Store struct {
	dir      string
	sealer   Sealer
	onChange func(Change)
	now      func() time.Time

	mu    sync.Mutex
	state *state
	// settings are settings.json's (settings.go)
	settings *settings
}

// state is the store as it is now; a write makes a new one and keeps it only once the file is written. Its records
// are never changed in place, only replaced.
type state struct {
	nextGroup   int64
	nextProfile int64
	groups      []Group // by order
	profiles    map[int64]*record
	// selected is the selected profile's id (state.json); 0: none
	selected int64
	// nextRule and rules: the route rules (rules.go), by their order
	nextRule int64
	rules    []nikoconf.Rule
	// nodes are the raycontrol nodes (nodes.go), in the order imported; pending the client keys of registrations
	// under way, by invite
	nodes   []*nodeRecord
	pending map[string]*pendingRecord
	// importedSnapshots tracks snapshot IDs already merged via ImportSnapshot for idempotency.
	importedSnapshots []string
}

type record struct {
	id, group, order int64
	typ              string
	name, address    string
	kind, transport  string
	// bean is the whole bean; for one whose secrets cannot be opened here, what the disk has of it without them
	bean json.RawMessage
	// disk and sealed are what store.json holds of it, kept so that a write does not seal every profile again
	disk   json.RawMessage
	sealed string
	// unreadable: its secrets cannot be opened here; it is kept as it is on the disk until replaced
	unreadable bool
}

// document is store.json.
type document struct {
	Schema        int           `json:"schema"`
	SealerContext string        `json:"sealer_context,omitempty"`
	NextGroup     int64         `json:"next_group"`
	NextProfile   int64         `json:"next_profile"`
	NextRule      int64         `json:"next_rule"`
	Groups        []diskGroup   `json:"groups"`
	Profiles      []diskProfile `json:"profiles"`
	// Rules: schema 3
	Rules []nikoconf.Rule `json:"rules"`
	// Nodes and Pending: schema 4 (nodes.go)
	Nodes   []json.RawMessage          `json:"nodes"`
	Pending map[string]json.RawMessage `json:"pending_registrations"`
	// ImportedSnapshots tracks snapshot IDs imported via ImportSnapshot for idempotent retries.
	ImportedSnapshots []string `json:"imported_snapshots,omitempty"`
}

type diskProfile struct {
	ID     int64           `json:"id"`
	Group  int64           `json:"group"`
	Order  int64           `json:"order"`
	Type   string          `json:"type"`
	Bean   json.RawMessage `json:"bean"`
	Sealed string          `json:"sealed,omitempty"`
}

// Open opens the store in dir, making it if there is none: the directory for the daemon alone, an older schema
// migrated after a backup, what a crash left of a write removed. ErrNewerSchema for a store of a newer Niko.
func Open(dir string, options Options) (*Store, error) {
	s := &Store{dir: dir, sealer: options.Sealer, onChange: options.OnChange, now: options.Now}
	if s.sealer == nil {
		s.sealer = DefaultSealer()
	}
	if s.now == nil {
		s.now = time.Now
	}
	now := s.now
	if err := privateDir(dir); err != nil {
		return nil, err
	}
	removeTemporaries(dir)
	path := filepath.Join(dir, storeFile)
	data, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		fresh := &state{nextGroup: 2, nextProfile: 1, nextRule: 1, groups: []Group{{ID: 1, Order: 1, Ungrouped: true}},
			profiles: map[int64]*record{}, pending: map[string]*pendingRecord{}}
		if err = s.write(fresh); err != nil {
			return nil, err
		}
		s.state = fresh
		if err = s.openSettings(); err != nil {
			return nil, err
		}
		return s, nil
	}
	defer func() {
		if s.state != nil {
			s.state.selected = s.readSelected()
		}
	}()
	if err != nil {
		return nil, err
	}
	if err = privateFile(path); err != nil {
		return nil, err
	}
	schema, err := schemaOf(data)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", path, err)
	}
	if schema > Schema() {
		return nil, fmt.Errorf("%w: schema %d, this one knows %d", ErrNewerSchema, schema, Schema())
	}
	migrated := schema < Schema()
	if migrated {
		if err = backup(dir, schema, data, now()); err != nil {
			return nil, fmt.Errorf("the backup before the migration: %w", err)
		}
		if data, err = migrate(data, schema); err != nil {
			return nil, err
		}
	}
	var resealed bool
	if s.state, resealed, err = s.decode(data); err != nil {
		return nil, fmt.Errorf("%s: %w", path, err)
	}
	if migrated || resealed {
		if err = s.write(s.state); err != nil {
			return nil, err
		}
	}
	if err = s.openSettings(); err != nil {
		return nil, err
	}
	return s, nil
}

// Dir is where the store is.
func (s *Store) Dir() string { return s.dir }

// selection is state.json.
type selection struct {
	Selected      int64  `json:"selected,omitempty"`
	ActiveProfile int64  `json:"activeProfile,omitempty"`
	ActiveMode    string `json:"activeMode,omitempty"`
}

func (s *Store) readStateFile() selection {
	data, err := os.ReadFile(filepath.Join(s.dir, stateFile))
	if err != nil {
		return selection{}
	}
	var sel selection
	if json.Unmarshal(data, &sel) != nil {
		return selection{}
	}
	return sel
}

// readSelected is the selection state.json holds, of a profile that is there; 0 for anything else.
func (s *Store) readSelected() int64 {
	sel := s.readStateFile()
	if s.state.profiles[sel.Selected] == nil {
		return 0
	}
	return sel.Selected
}

func (s *Store) writeSelected(id int64) error {
	sel := s.readStateFile()
	sel.Selected = id
	data, err := json.Marshal(sel)
	if err != nil {
		return err
	}
	return writeFile(filepath.Join(s.dir, stateFile), append(data, '\n'))
}

// SetActiveConnection records the profile and service mode actually connected via NikoService.Connect
// (or clears them with profileID == 0 on disconnect/raw start) so BeforeRestore rebuilds the exact session.
func (s *Store) SetActiveConnection(profileID int64, mode string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	sel := s.readStateFile()
	sel.Selected = s.state.selected
	sel.ActiveProfile = profileID
	sel.ActiveMode = mode
	data, err := json.Marshal(sel)
	if err != nil {
		return err
	}
	return writeFile(filepath.Join(s.dir, stateFile), append(data, '\n'))
}

// ActiveConnection returns the last profile ID and mode connected via NikoService.Connect (0, "" if none).
func (s *Store) ActiveConnection() (int64, string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	sel := s.readStateFile()
	if sel.ActiveProfile == 0 || s.state.profiles[sel.ActiveProfile] == nil {
		return 0, ""
	}
	return sel.ActiveProfile, sel.ActiveMode
}

// Selected is the selected profile's id; 0: none.
func (s *Store) Selected() int64 {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.state.selected
}

// Select selects a profile, or none with id 0. ErrNotFound for no such profile; ErrRefused for one whose secrets
// cannot be opened here (it cannot connect).
func (s *Store) Select(id int64) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if id != 0 {
		r := s.state.profiles[id]
		if r == nil {
			return ErrNotFound
		}
		if r.unreadable {
			return fmt.Errorf("%w: profile %d: its secrets cannot be opened here", ErrRefused, id)
		}
	}
	if id == s.state.selected {
		return nil
	}
	if err := s.writeSelected(id); err != nil {
		return err
	}
	s.state.selected = id
	s.tell(selectedChange(id))
	return nil
}

func selectedChange(id int64) Change {
	if id == 0 {
		return Change{Kind: KindSelected}
	}
	return Change{Kind: KindSelected, IDs: []int64{id}}
}

func (s *Store) tell(c Change) {
	if s.onChange != nil && c.Kind != 0 {
		s.onChange(c)
	}
}

// decode reads a store.json of the current schema. resealed: a profile's secrets were open on the disk though this
// store seals them (the file written where nothing sealed), and are sealed now; the file is to be written again.
func (s *Store) decode(data []byte) (st *state, resealed bool, err error) {
	var doc document
	if err = json.Unmarshal(data, &doc); err != nil {
		return nil, false, err
	}
	if doc.SealerContext != "" && doc.SealerContext != s.sealer.Name() &&
		(doc.SealerContext == "dpapi-user" || s.sealer.Name() == "dpapi-user") {
		return nil, false, fmt.Errorf("%w: store is %q, daemon uses %q", ErrSealerMismatch, doc.SealerContext, s.sealer.Name())
	}
	if doc.SealerContext == "" && s.sealer.Name() == "dpapi-user" && hasLegacyDPAPISealed(&doc) {
		return nil, false, fmt.Errorf("%w: store has legacy dpapi secrets, daemon uses %q", ErrSealerMismatch, s.sealer.Name())
	}
	st = &state{nextGroup: doc.NextGroup, nextProfile: doc.NextProfile, profiles: map[int64]*record{},
		importedSnapshots: append([]string(nil), doc.ImportedSnapshots...)}
	groups := map[int64]bool{}
	ungrouped := false
	for _, d := range doc.Groups {
		if d.ID <= 0 || groups[d.ID] {
			return nil, false, fmt.Errorf("group %d: not a group's id, or twice", d.ID)
		}
		groups[d.ID] = true
		if d.Ungrouped {
			if ungrouped {
				return nil, false, errors.New("two ungrouped groups")
			}
			ungrouped = true
		}
		g := Group{ID: d.ID, Order: d.Order, Name: d.Name, Ungrouped: d.Ungrouped, Type: d.Type, Node: d.Node}
		if d.Type == GroupSubscription {
			if len(d.Subscription) == 0 || d.Ungrouped {
				return nil, false, fmt.Errorf("group %d: a subscription's group without its subscription", d.ID)
			}
			sealedNow, err := s.decodeSubscription(&g, d)
			if err != nil {
				return nil, false, fmt.Errorf("group %d: %w", d.ID, err)
			}
			resealed = resealed || sealedNow
		}
		st.groups = append(st.groups, g)
		st.nextGroup = max(st.nextGroup, d.ID+1)
	}
	if !ungrouped {
		return nil, false, errors.New("no ungrouped group")
	}
	sortGroups(st.groups)
	for _, p := range doc.Profiles {
		if p.ID <= 0 || st.profiles[p.ID] != nil || !groups[p.Group] {
			return nil, false, fmt.Errorf("profile %d: not a profile's id, twice, or of no group", p.ID)
		}
		r := &record{id: p.ID, group: p.Group, order: p.Order, typ: p.Type, disk: p.Bean, sealed: p.Sealed}
		bean, err := openBean(s.sealer, p.Bean, p.Sealed)
		if errors.Is(err, errSealed) {
			bean, r.unreadable = p.Bean, true
		} else if err != nil {
			return nil, false, fmt.Errorf("profile %d: %w", p.ID, err)
		}
		r.bean = bean
		if r.name, r.address, r.kind, r.transport, err = display(p.Type, bean); err != nil {
			return nil, false, fmt.Errorf("profile %d: %w", p.ID, err)
		}
		if p.Sealed == "" && s.sealer.Name() != "" {
			if r.disk, r.sealed, err = sealBean(s.sealer, bean); err != nil {
				return nil, false, fmt.Errorf("profile %d: sealing the secrets: %w", p.ID, err)
			}
			resealed = resealed || r.sealed != ""
		}
		st.profiles[p.ID] = r
		st.nextProfile = max(st.nextProfile, p.ID+1)
	}
	st.nextGroup = max(st.nextGroup, 1)
	st.nextProfile = max(st.nextProfile, 1)
	if st.rules, st.nextRule, err = decodeRules(doc.Rules, doc.NextRule); err != nil {
		return nil, false, err
	}
	sealedNow, err := s.decodeNodes(st, doc.Nodes, doc.Pending)
	if err != nil {
		return nil, false, err
	}
	return st, resealed || sealedNow, nil
}

// display is the name, address, protocol kind, and carrier transport of a stored bean, read leniently.
func display(typ string, data json.RawMessage) (string, string, string, string, error) {
	bean, err := nikofmt.NewBean(typ)
	if err != nil {
		return "", "", "", "", fmt.Errorf("type %q: %w", typ, err)
	}
	if err = json.Unmarshal(data, bean); err != nil {
		return "", "", "", "", err
	}
	bean.InitializeDefaultValues()
	kind, transport := nikofmt.Kind(bean)
	return nikofmt.DisplayName(bean), nikofmt.DisplayAddress(bean), kind, transport, nil
}

// write writes st as store.json.
func (s *Store) write(st *state) error {
	doc := document{Schema: Schema(), SealerContext: s.sealer.Name(), NextGroup: st.nextGroup,
		NextProfile: st.nextProfile, NextRule: st.nextRule, Groups: []diskGroup{}, Profiles: []diskProfile{},
		Rules: append([]nikoconf.Rule{}, st.rules...), Nodes: []json.RawMessage{}, Pending: map[string]json.RawMessage{},
		ImportedSnapshots: append([]string(nil), st.importedSnapshots...)}
	for _, g := range st.groups {
		doc.Groups = append(doc.Groups, diskGroup{ID: g.ID, Order: g.Order, Name: g.Name, Ungrouped: g.Ungrouped,
			Type: g.Type, Subscription: g.subscriptionDisk, Sealed: g.subscriptionSealed, Node: g.Node})
	}
	if err := st.encodeNodes(&doc); err != nil {
		return err
	}
	for _, r := range st.sorted(0) {
		doc.Profiles = append(doc.Profiles, diskProfile{ID: r.id, Group: r.group, Order: r.order, Type: r.typ,
			Bean: r.disk, Sealed: r.sealed})
	}
	data, err := json.MarshalIndent(doc, "", "  ")
	if err != nil {
		return err
	}
	return writeFile(filepath.Join(s.dir, storeFile), append(data, '\n'))
}

// commit writes st and makes it the store's, then tells the changes; on a failed write the store stays as it was.
// Under s.mu.
func (s *Store) commit(st *state, changes ...Change) error {
	if err := s.write(st); err != nil {
		return err
	}
	s.state = st
	for _, c := range changes {
		s.tell(c)
	}
	// the selected profile deleted: no selection. state.json may keep the old id where its write fails; it is no
	// selection when read, the profile being gone
	if st.selected != 0 && st.profiles[st.selected] == nil {
		st.selected = 0
		_ = s.writeSelected(0)
		s.tell(selectedChange(0))
	}
	return nil
}

func (st *state) clone() *state {
	next := &state{nextGroup: st.nextGroup, nextProfile: st.nextProfile, groups: append([]Group(nil), st.groups...),
		profiles: make(map[int64]*record, len(st.profiles)), selected: st.selected, nextRule: st.nextRule,
		rules: append([]nikoconf.Rule(nil), st.rules...), nodes: append([]*nodeRecord(nil), st.nodes...),
		pending:           make(map[string]*pendingRecord, len(st.pending)),
		importedSnapshots: append([]string(nil), st.importedSnapshots...)}
	for id, p := range st.pending {
		next.pending[id] = p
	}
	for id, r := range st.profiles {
		next.profiles[id] = r
	}
	return next
}

func (st *state) group(id int64) (Group, int) {
	for i, g := range st.groups {
		if g.ID == id {
			return g, i
		}
	}
	return Group{}, -1
}

func (st *state) ungrouped() int64 {
	for _, g := range st.groups {
		if g.Ungrouped {
			return g.ID
		}
	}
	return 0
}

// sorted are the profiles of a group (0: of all) in the list's order: by group, by their order, by id.
func (st *state) sorted(group int64) []*record {
	rank := map[int64]int{}
	for i, g := range st.groups {
		rank[g.ID] = i
	}
	var out []*record
	for _, r := range st.profiles {
		if group == 0 || r.group == group {
			out = append(out, r)
		}
	}
	sort.Slice(out, func(i, j int) bool {
		a, b := out[i], out[j]
		if a.group != b.group {
			return rank[a.group] < rank[b.group]
		}
		if a.order != b.order {
			return a.order < b.order
		}
		return a.id < b.id
	})
	return out
}

// nextOrder is the order of a profile added at the end of a group, as ProxyEntity's nextOrder.
func (st *state) nextOrder(group int64) int64 {
	var last int64
	for _, r := range st.profiles {
		if r.group == group {
			last = max(last, r.order)
		}
	}
	return last + 1
}

func sortGroups(groups []Group) {
	sort.SliceStable(groups, func(i, j int) bool {
		if groups[i].Order != groups[j].Order {
			return groups[i].Order < groups[j].Order
		}
		return groups[i].ID < groups[j].ID
	})
}

func (r *record) profile(withBean bool) Profile {
	p := Profile{ID: r.id, Group: r.group, Order: r.order, Type: r.typ, Name: r.name, Address: r.address,
		Kind: r.kind, Transport: r.transport}
	if withBean {
		p.Bean = append(json.RawMessage(nil), r.bean...)
	}
	return p
}

// newRecord is the record of a bean, sealed for the disk.
func (s *Store) newRecord(id, group, order int64, bean nikofmt.Bean) (*record, error) {
	data, err := json.Marshal(bean)
	if err != nil {
		return nil, err
	}
	if len(data) > maxBean {
		return nil, fmt.Errorf("%w: a bean over %d bytes", ErrInvalid, maxBean)
	}
	kind, transport := nikofmt.Kind(bean)
	r := &record{id: id, group: group, order: order, typ: nikofmt.TypeName(bean), name: nikofmt.DisplayName(bean),
		address: nikofmt.DisplayAddress(bean), kind: kind, transport: transport, bean: data}
	if r.typ == "" {
		return nil, fmt.Errorf("%w: a bean of no type", ErrInvalid)
	}
	if r.disk, r.sealed, err = sealBean(s.sealer, data); err != nil {
		return nil, fmt.Errorf("sealing the secrets: %w", err)
	}
	return r, nil
}

// DefaultGroup is the ungrouped group's id.
func (s *Store) DefaultGroup() int64 {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.state.ungrouped()
}

// Groups are the groups in their order, each with how many profiles it has.
func (s *Store) Groups() []Group {
	s.mu.Lock()
	defer s.mu.Unlock()
	counts := map[int64]int{}
	for _, r := range s.state.profiles {
		counts[r.group]++
	}
	groups := append([]Group(nil), s.state.groups...)
	for i := range groups {
		groups[i].Profiles = counts[groups[i].ID]
		groups[i].Subscription = copySubscription(groups[i].Subscription)
	}
	return groups
}

// Profiles are a group's profiles (0: every group's) in the list's order, without their beans.
func (s *Store) Profiles(group int64) ([]Profile, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, i := s.state.group(group); group != 0 && i < 0 {
		return nil, ErrNotFound
	}
	var out []Profile
	for _, r := range s.state.sorted(group) {
		out = append(out, r.profile(false))
	}
	return out, nil
}

// Profile is a profile with its bean.
func (s *Store) Profile(id int64) (Profile, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	r := s.state.profiles[id]
	if r == nil {
		return Profile{}, ErrNotFound
	}
	if r.unreadable {
		return Profile{}, fmt.Errorf("%w: profile %d's secrets cannot be opened here (%v)", ErrRefused, id, errSealed)
	}
	return r.profile(true), nil
}

// ProfileToShare is a profile to give another device by its link (ShareProfile): as Profile, and ErrRefused for a
// raycontrol node's profile. Its rows carry this device's credentials and the node knocks for this device's key
// (salamander-device-v1 §6): the other device registers with an invite of its own.
func (s *Store) ProfileToShare(id int64) (Profile, error) {
	p, err := s.Profile(id)
	if err != nil {
		return p, err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if g, i := s.state.group(p.Group); i >= 0 && g.Node != "" {
		return Profile{}, fmt.Errorf("%w: profile %d is a raycontrol node's: another device takes an invite of its own", ErrRefused, id)
	}
	return p, nil
}

// SaveProfile saves a profile from its type and bean: a new one (ID 0) at the end of its group (0: the ungrouped
// one), or the one of that ID with a new bean of the same type, where it is. The bean is read strictly as its type
// and given its defaults; the name and address are its own. The profile as saved.
func (s *Store) SaveProfile(p Profile) (Profile, error) {
	if len(p.Bean) > maxBean {
		return Profile{}, fmt.Errorf("%w: a bean over %d bytes", ErrInvalid, maxBean)
	}
	bean, err := nikofmt.DecodeBean(p.Type, p.Bean)
	if err != nil {
		return Profile{}, fmt.Errorf("%w: the bean of type %q: %v", ErrInvalid, p.Type, err)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.state.clone()
	id, group, order := p.ID, p.Group, int64(0)
	if id == 0 {
		if group, err = st.addable(group); err != nil {
			return Profile{}, err
		}
		id, order = st.nextProfile, st.nextOrder(group)
		st.nextProfile++
	} else {
		old := st.profiles[id]
		if old == nil {
			return Profile{}, ErrNotFound
		}
		if old.typ != p.Type {
			return Profile{}, fmt.Errorf("%w: profile %d is of type %s, not %s", ErrInvalid, id, old.typ, p.Type)
		}
		group, order = old.group, old.order
	}
	r, err := s.newRecord(id, group, order, bean)
	if err != nil {
		return Profile{}, err
	}
	st.profiles[id] = r
	if err = s.commit(st, Change{Kind: KindProfiles, IDs: []int64{id}}); err != nil {
		return Profile{}, err
	}
	return r.profile(true), nil
}

// addable is the group profiles may be added to: the one asked for (0: the ungrouped one), not a subscription's.
func (st *state) addable(group int64) (int64, error) {
	if group == 0 {
		return st.ungrouped(), nil
	}
	g, i := st.group(group)
	if i < 0 {
		return 0, ErrNotFound
	}
	if g.Type != GroupBasic {
		return 0, fmt.Errorf("%w: group %d is a subscription's: its profiles are its subscription's", ErrRefused, group)
	}
	if g.Node != "" {
		return 0, fmt.Errorf("%w: group %d is a raycontrol node's: its profiles are its subscription's", ErrRefused, group)
	}
	return group, nil
}

// AddProfiles adds the beans as profiles at the end of a group (0: the ungrouped one), in their order, in one write:
// an import. The profiles added.
func (s *Store) AddProfiles(group int64, beans []nikofmt.Bean) ([]Profile, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.state.clone()
	group, err := st.addable(group)
	if err != nil {
		return nil, err
	}
	order := st.nextOrder(group)
	var added []Profile
	var ids []int64
	for _, bean := range beans {
		r, err := s.newRecord(st.nextProfile, group, order, bean)
		if err != nil {
			return nil, err
		}
		st.profiles[r.id] = r
		st.nextProfile++
		order++
		added = append(added, r.profile(false))
		ids = append(ids, r.id)
	}
	if len(ids) == 0 {
		return nil, nil
	}
	if err = s.commit(st, Change{Kind: KindProfiles, IDs: ids}); err != nil {
		return nil, err
	}
	return added, nil
}

// DeleteProfiles deletes the profiles of those ids that are there. The ids deleted.
func (s *Store) DeleteProfiles(ids []int64) ([]int64, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.state.clone()
	var deleted []int64
	for _, id := range ids {
		if st.profiles[id] != nil {
			delete(st.profiles, id)
			deleted = append(deleted, id)
		}
	}
	if len(deleted) == 0 {
		return nil, nil
	}
	if err := s.commit(st, Change{Kind: KindProfiles, IDs: deleted}); err != nil {
		return nil, err
	}
	return deleted, nil
}

// MoveProfiles puts profiles, in the order given, into a group before one of its profiles (0: at its end); that
// group's profiles are then numbered again from 1, as GroupManager.rearrange does. Into a basic group only.
func (s *Store) MoveProfiles(ids []int64, group, before int64) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.state.clone()
	if len(ids) == 0 {
		return fmt.Errorf("%w: no profile to move", ErrInvalid)
	}
	g, i := st.group(group)
	if i < 0 {
		return ErrNotFound
	}
	if g.Type != GroupBasic {
		return fmt.Errorf("%w: group %d is a subscription's: its profiles are its subscription's", ErrRefused, group)
	}
	if g.Node != "" {
		return fmt.Errorf("%w: group %d is a raycontrol node's: its profiles are its subscription's", ErrRefused, group)
	}
	moving := map[int64]bool{}
	var moved []*record
	for _, id := range ids {
		r := st.profiles[id]
		if r == nil {
			return ErrNotFound
		}
		if !moving[id] {
			moving[id] = true
			moved = append(moved, r)
		}
	}
	if before != 0 {
		r := st.profiles[before]
		if r == nil || r.group != group || moving[before] {
			return fmt.Errorf("%w: profile %d is not one of the group's to put the others before", ErrInvalid, before)
		}
	}
	var placed []*record
	for _, r := range st.sorted(group) {
		if moving[r.id] {
			continue
		}
		if r.id == before {
			placed = append(placed, moved...)
		}
		placed = append(placed, r)
	}
	if before == 0 {
		placed = append(placed, moved...)
	}
	var changed []int64
	for n, r := range placed {
		order := int64(n + 1)
		if r.group == group && r.order == order {
			continue
		}
		next := *r
		next.group, next.order = group, order
		st.profiles[r.id] = &next
		changed = append(changed, r.id)
	}
	if len(changed) == 0 {
		return nil
	}
	sort.Slice(changed, func(i, j int) bool { return changed[i] < changed[j] })
	return s.commit(st, Change{Kind: KindProfiles, IDs: changed})
}

// SaveGroup saves a group: a new one (ID 0) at the end, or the name of the one of that ID. A basic group only: a
// subscription's is SaveSubscriptionGroup's. The group as saved.
func (s *Store) SaveGroup(g Group) (Group, error) {
	if g.Type != GroupBasic {
		return Group{}, fmt.Errorf("%w: a subscription's group is saved with its subscription", ErrInvalid)
	}
	if len(g.Name) > maxName {
		return Group{}, fmt.Errorf("%w: a name over %d bytes", ErrInvalid, maxName)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.state.clone()
	var saved Group
	if g.ID == 0 {
		var last int64
		for _, other := range st.groups {
			last = max(last, other.Order)
		}
		saved = Group{ID: st.nextGroup, Order: last + 1, Name: g.Name}
		st.nextGroup++
		st.groups = append(st.groups, saved)
	} else {
		old, i := st.group(g.ID)
		if i < 0 {
			return Group{}, ErrNotFound
		}
		if old.Type != GroupBasic {
			return Group{}, fmt.Errorf("%w: group %d is a subscription's: it is saved with its subscription", ErrRefused, g.ID)
		}
		saved = old
		saved.Name = g.Name
		st.groups[i] = saved
	}
	if err := s.commit(st, Change{Kind: KindGroups, IDs: []int64{saved.ID}}); err != nil {
		return Group{}, err
	}
	for _, r := range st.profiles {
		if r.group == saved.ID {
			saved.Profiles++
		}
	}
	return saved, nil
}

// DeleteGroup deletes a group and its profiles; the ungrouped group stays.
func (s *Store) DeleteGroup(id int64) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.state.clone()
	g, i := st.group(id)
	if i < 0 {
		return ErrNotFound
	}
	if g.Ungrouped {
		return fmt.Errorf("%w: the ungrouped group stays", ErrRefused)
	}
	if g.Node != "" {
		return fmt.Errorf("%w: group %d is a raycontrol node's: it goes with the node", ErrRefused, id)
	}
	st.groups = append(st.groups[:i:i], st.groups[i+1:]...)
	var deleted []int64
	for pid, r := range st.profiles {
		if r.group == id {
			delete(st.profiles, pid)
			deleted = append(deleted, pid)
		}
	}
	sort.Slice(deleted, func(i, j int) bool { return deleted[i] < deleted[j] })
	changes := []Change{{Kind: KindGroups, IDs: []int64{id}}}
	if len(deleted) > 0 {
		changes = append(changes, Change{Kind: KindProfiles, IDs: deleted})
	}
	return s.commit(st, changes...)
}
