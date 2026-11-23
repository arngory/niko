package nikostore

import (
	"encoding/json"
	"errors"
	"fmt"
	"reflect"
	"regexp"
	"sort"

	"libcore/nikofmt"
	"libcore/nikoray"
)

// The raycontrol nodes (slice 11): each node the user imported by an invite or a device package (nikoray.Node, the
// JSON of the app's RayNode.toJson), its group of the profiles its subscription gives, and the client keys of
// registrations under way. In store.json, schema 4, so that a node, its group and its rows change in one write (the
// app keeps them in two stores and needs its "stale:" keys for a crash between them).
//
// The device's secrets are sealed as a profile's are (decision 6): the device key, the client keys, the rows'
// credentials and the DoH's token, in "sealed" beside the node's other fields; a pending registration's client key
// alike. A node sealed on another machine stays in the list, its secrets unread, until it is removed or imported
// again; it fetches nothing and builds no profile.

// nodeSecrets are a node's fields the disk holds sealed.
var nodeSecrets = map[string]bool{"device_key": true, "links": true, "doh": true, "identity": true,
	"identity_next": true}

// pendingSecrets are a pending registration's fields the disk holds sealed: its client key.
var pendingSecrets = map[string]bool{"key": true, "cert": true}

var nodeIDPattern = regexp.MustCompile(`^[0-9a-f]{32}$`)

const (
	// maxNodes is how many nodes one store keeps
	maxNodes = 256
	// maxPending is how many registrations under way are kept, the most recently tried
	maxPending = 32
	// registerRetry is how long a registration's client key is sent again, from its last try (RayApi's
	// REGISTER_RETRY_S): twice the node's window for a repeated Register
	registerRetry = 30 * 60
)

type nodeRecord struct {
	id    string
	group int64
	// node is the whole node; nil where its secrets cannot be opened here
	node *nikoray.Node
	// public is what the disk has of it without its secrets, for one that cannot be opened
	public *nikoray.Node
	// disk is the node as store.json holds it, its secrets sealed in its "sealed"
	disk json.RawMessage
}

type pendingRecord struct {
	identity  *nikoray.Identity
	createdAt int64
	disk      json.RawMessage
}

// sealObject is an object for the disk: the fields of secrets sealed in its "sealed", the rest as they are; with no
// sealer, or none of them set, the object as it is.
func sealObject(sealer Sealer, data json.RawMessage, secrets map[string]bool) (json.RawMessage, error) {
	if sealer.Name() == "" {
		return data, nil
	}
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(data, &fields); err != nil {
		return nil, err
	}
	hidden := map[string]json.RawMessage{}
	for name, value := range fields {
		if secrets[name] && string(value) != `""` && string(value) != "null" {
			hidden[name] = value
			delete(fields, name)
		}
	}
	if len(hidden) == 0 {
		return data, nil
	}
	public, sealed, err := sealFields(sealer, fields, hidden)
	if err != nil {
		return nil, err
	}
	if err = json.Unmarshal(public, &fields); err != nil {
		return nil, err
	}
	fields["sealed"], _ = json.Marshal(sealed)
	return json.Marshal(fields)
}

// openObject is the whole object of what the disk holds: its "sealed" opened and put back. errSealed where this
// sealer cannot open it; resealed where it holds secrets in the clear that this sealer would seal.
func openObject(sealer Sealer, disk json.RawMessage, secrets map[string]bool) (whole json.RawMessage, resealed bool, err error) {
	var fields map[string]json.RawMessage
	if err = json.Unmarshal(disk, &fields); err != nil {
		return nil, false, err
	}
	var sealed string
	if raw, ok := fields["sealed"]; ok {
		if err = json.Unmarshal(raw, &sealed); err != nil {
			return nil, false, err
		}
		delete(fields, "sealed")
	}
	public, err := json.Marshal(fields)
	if err != nil {
		return nil, false, err
	}
	if sealed == "" {
		if sealer.Name() != "" {
			for name := range secrets {
				if v, ok := fields[name]; ok && string(v) != `""` && string(v) != "null" {
					resealed = true
				}
			}
		}
		return public, resealed, nil
	}
	whole, err = openBean(sealer, public, sealed)
	return whole, false, err
}

func (s *Store) sealNode(n *nikoray.Node) (*nodeRecord, error) {
	data, err := json.Marshal(n)
	if err != nil {
		return nil, err
	}
	disk, err := sealObject(s.sealer, data, nodeSecrets)
	if err != nil {
		return nil, fmt.Errorf("sealing the node's secrets: %w", err)
	}
	return &nodeRecord{id: n.ID(), group: n.GroupID, node: n, disk: disk}, nil
}

func (s *Store) sealPending(identity nikoray.Identity) (*pendingRecord, error) {
	data, err := json.Marshal(identity)
	if err != nil {
		return nil, err
	}
	disk, err := sealObject(s.sealer, data, pendingSecrets)
	if err != nil {
		return nil, fmt.Errorf("sealing the client key: %w", err)
	}
	return &pendingRecord{identity: &identity, createdAt: identity.CreatedAt, disk: disk}, nil
}

// decodeNodes reads the nodes and pending registrations of a store.json. resealed: secrets in the clear that this
// store seals, sealed now; the file is to be written again.
func (s *Store) decodeNodes(st *state, nodes []json.RawMessage, pending map[string]json.RawMessage) (bool, error) {
	resealed := false
	seen := map[string]bool{}
	for _, disk := range nodes {
		var public nikoray.Node
		if err := json.Unmarshal(disk, &public); err != nil {
			return false, fmt.Errorf("a node: %w", err)
		}
		id := public.ID()
		if !nodeIDPattern.MatchString(id) || seen[id] {
			return false, fmt.Errorf("node %q: not a node's id, or twice", id)
		}
		seen[id] = true
		r := &nodeRecord{id: id, group: public.GroupID, disk: disk}
		whole, sealedNow, err := openObject(s.sealer, disk, nodeSecrets)
		switch {
		case errors.Is(err, errSealed):
			public.DeviceKey, public.Links, public.DoH, public.Identity, public.IdentityNext = "", nil, "", nil, nil
			r.public = &public
		case err != nil:
			return false, fmt.Errorf("node %s: %w", id, err)
		default:
			var n nikoray.Node
			if err = json.Unmarshal(whole, &n); err != nil {
				return false, fmt.Errorf("node %s: %w", id, err)
			}
			r.node = &n
			if sealedNow {
				if r, err = s.sealNode(&n); err != nil {
					return false, err
				}
				resealed = true
			}
		}
		st.nodes = append(st.nodes, r)
	}
	st.pending = map[string]*pendingRecord{}
	for invite, disk := range pending {
		whole, sealedNow, err := openObject(s.sealer, disk, pendingSecrets)
		if errors.Is(err, errSealed) {
			// a key that does not open here is of no use: a new one at the next try
			resealed = true
			continue
		}
		if err != nil {
			return false, fmt.Errorf("a pending registration: %w", err)
		}
		var identity nikoray.Identity
		if err = json.Unmarshal(whole, &identity); err != nil {
			return false, fmt.Errorf("a pending registration: %w", err)
		}
		r := &pendingRecord{identity: &identity, createdAt: identity.CreatedAt, disk: disk}
		if sealedNow {
			if r, err = s.sealPending(identity); err != nil {
				return false, err
			}
			resealed = true
		}
		st.pending[invite] = r
	}
	return resealed, nil
}

func (st *state) encodeNodes(doc *document) error {
	for _, r := range st.nodes {
		doc.Nodes = append(doc.Nodes, r.disk)
	}
	for invite, r := range st.pending {
		doc.Pending[invite] = r.disk
	}
	return nil
}

func (st *state) node(id string) (*nodeRecord, int) {
	for i, r := range st.nodes {
		if r.id == id {
			return r, i
		}
	}
	return nil, -1
}

// copyNode is a node of its own, for the caller to change.
func copyNode(n *nikoray.Node) *nikoray.Node {
	data, err := json.Marshal(n)
	if err != nil {
		panic(err)
	}
	var out nikoray.Node
	if err = json.Unmarshal(data, &out); err != nil {
		panic(err)
	}
	return &out
}

// StoredNode is a node as the store has it: with its secrets, or without them where they cannot be opened here
// (Unreadable).
type StoredNode struct {
	Node       *nikoray.Node
	Unreadable bool
}

// Nodes are the nodes in the order they were imported, each a copy of its own.
func (s *Store) Nodes() []StoredNode {
	s.mu.Lock()
	defer s.mu.Unlock()
	var out []StoredNode
	for _, r := range s.state.nodes {
		if r.node != nil {
			out = append(out, StoredNode{Node: copyNode(r.node)})
		} else {
			out = append(out, StoredNode{Node: copyNode(r.public), Unreadable: true})
		}
	}
	return out
}

// Node is a node with its secrets. ErrNotFound; ErrRefused where its secrets cannot be opened here.
func (s *Store) Node(id string) (*nikoray.Node, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	r, _ := s.state.node(id)
	if r == nil {
		return nil, ErrNotFound
	}
	if r.node == nil {
		return nil, fmt.Errorf("%w: node %s: its secrets cannot be opened here (%v)", ErrRefused, id, errSealed)
	}
	return copyNode(r.node), nil
}

// PendingIdentity is the client key of a try to register with an invite (RayApi.pendingIdentity): the one kept for it
// within 30 minutes of its last try, else a new one of make; kept as tried now. The others kept past their time go.
func (s *Store) PendingIdentity(invite string, now int64, make func() (nikoray.Identity, error)) (nikoray.Identity, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.state.clone()
	for id, r := range st.pending {
		if now-r.createdAt >= registerRetry {
			delete(st.pending, id)
		}
	}
	var identity nikoray.Identity
	if kept := st.pending[invite]; kept != nil {
		identity = *kept.identity
	} else {
		made, err := make()
		if err != nil {
			return nikoray.Identity{}, err
		}
		identity = made
	}
	identity.CreatedAt = now
	r, err := s.sealPending(identity)
	if err != nil {
		return nikoray.Identity{}, err
	}
	st.pending[invite] = r
	// the most recently tried stay
	if len(st.pending) > maxPending {
		var ids []string
		for id := range st.pending {
			ids = append(ids, id)
		}
		sort.Slice(ids, func(i, j int) bool { return st.pending[ids[i]].createdAt > st.pending[ids[j]].createdAt })
		for _, id := range ids[maxPending:] {
			delete(st.pending, id)
		}
	}
	if err = s.commit(st); err != nil {
		return nikoray.Identity{}, err
	}
	return identity, nil
}

// ForgetPending drops the client key kept for an invite: the node answered, or the invite is of no further use.
func (s *Store) ForgetPending(invite string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.state.pending[invite] == nil {
		return nil
	}
	st := s.state.clone()
	delete(st.pending, invite)
	return s.commit(st)
}

// ImportNode keeps the node of a registration (RayNodes.import): the same node's record replaced (its group and rows
// kept, it goes to the end of the list), the client key of a registration through the API with it; forget, the
// invite whose pending key this was, in the same write. The node as kept.
func (s *Store) ImportNode(r nikoray.Registration, identity *nikoray.Identity, forget string) (*nikoray.Node, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.state.clone()
	old, i := st.node(r.SPA.NodeID)
	var oldNode *nikoray.Node
	if old != nil {
		oldNode = old.node
		if oldNode == nil {
			oldNode = old.public
		}
	}
	if old == nil && len(st.nodes) >= maxNodes {
		return nil, fmt.Errorf("%w: over %d nodes", ErrInvalid, maxNodes)
	}
	n, err := nikoray.Import(r, identity, oldNode)
	if err != nil {
		return nil, err
	}
	record, err := s.sealNode(n)
	if err != nil {
		return nil, err
	}
	if i >= 0 {
		st.nodes = append(st.nodes[:i:i], st.nodes[i+1:]...)
	}
	st.nodes = append(st.nodes, record)
	if forget != "" {
		delete(st.pending, forget)
	}
	if err = s.commit(st, Change{Kind: KindNodes}); err != nil {
		return nil, err
	}
	return copyNode(n), nil
}

// UpdateNode changes a stored node in place: change gets a copy of it, and what it leaves is kept, unless it says
// nothing changed. The node as kept. ErrNotFound; ErrRefused where its secrets cannot be opened here.
func (s *Store) UpdateNode(id string, change func(n *nikoray.Node) bool) (*nikoray.Node, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.state.clone()
	r, i := st.node(id)
	if r == nil {
		return nil, ErrNotFound
	}
	if r.node == nil {
		return nil, fmt.Errorf("%w: node %s: its secrets cannot be opened here", ErrRefused, id)
	}
	n := copyNode(r.node)
	if !change(n) {
		return n, nil
	}
	if n.ID() != id {
		return nil, fmt.Errorf("%w: a node's id does not change", ErrInvalid)
	}
	record, err := s.sealNode(n)
	if err != nil {
		return nil, err
	}
	st.nodes[i] = record
	if err = s.commit(st, Change{Kind: KindNodes}); err != nil {
		return nil, err
	}
	return copyNode(n), nil
}

// NodeRows is what ApplyNodeRows did.
type NodeRows struct {
	// Stale: a newer subscription was kept already, nothing was written
	Stale bool
	// Group is the node's group; GroupCreated: made now
	Group        int64
	GroupCreated bool
	// the profiles added, changed (their credentials or their place) and deleted
	Added, Changed, Deleted []int64
}

// userTweaks are a row's settings of the user's that its subscription's link does not carry (RaySubscription's
// keepUserTweaks): kept over an update.
var userTweaks = []string{"customOutboundJson", "customConfigJson", "tlsFragment", "udpPrelude", "udpPreludeSni",
	"udpPreludeTtl", "udpReorderInitial"}

// ApplyNodeRows writes a subscription of seq into the node's group (RaySubscription.store): a known key updates its
// row (its id, the selection and the user's own tweaks stay), a new key adds a row at its place, rows of keys gone are
// deleted; the group is made where the node has none, renamed with the node where the user did not name it. update
// sets the node's fields from the subscription, on a copy, before the rows are written; the rows' keys and the group
// are set after it. Nothing is written for a seq older than the one kept (Stale). The selected profile gone, the
// first of the node's is selected.
func (s *Store) ApplyNodeRows(id string, seq uint64, rows []nikoray.Row, update func(n *nikoray.Node)) (NodeRows, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.state.clone()
	r, i := st.node(id)
	if r == nil {
		return NodeRows{}, ErrNotFound
	}
	if r.node == nil {
		return NodeRows{}, fmt.Errorf("%w: node %s: its secrets cannot be opened here", ErrRefused, id)
	}
	old := r.node
	if seq < old.SubSeq {
		return NodeRows{Stale: true}, nil
	}
	n := copyNode(old)
	update(n)
	n.SubSeq = seq
	var result NodeRows
	var changes []Change
	groupName := nikoray.GroupName(n.Host, n.Name)
	group, gi := st.group(n.GroupID)
	if gi < 0 || n.GroupID == 0 {
		var last int64
		for _, g := range st.groups {
			last = max(last, g.Order)
		}
		group = Group{ID: st.nextGroup, Order: last + 1, Name: groupName, Node: id}
		st.nextGroup++
		st.groups = append(st.groups, group)
		result.GroupCreated = true
		changes = append(changes, Change{Kind: KindGroups, IDs: []int64{group.ID}})
	} else if group.Name != groupName && group.Name == old.DisplayName() {
		// the node was named or renamed: its group follows, unless the user named it themselves
		group.Name = groupName
		group.Node = id
		st.groups[gi] = group
		changes = append(changes, Change{Kind: KindGroups, IDs: []int64{group.ID}})
	} else if group.Node != id {
		group.Node = id
		st.groups[gi] = group
	}
	result.Group = group.ID
	known := map[string]int64{}
	if !result.GroupCreated {
		known = old.ProfileIDs
	}
	ids := map[string]int64{}
	taken := map[int64]bool{}
	for k, row := range rows {
		order := int64(k + 1)
		data, err := json.Marshal(row.Bean)
		if err != nil {
			return NodeRows{}, err
		}
		existing := st.profiles[known[row.Key]]
		if existing != nil && existing.group == group.ID && !taken[existing.id] {
			if !existing.unreadable {
				if data, err = keepTweaks(existing.bean, data); err != nil {
					return NodeRows{}, err
				}
			}
			taken[existing.id] = true
			ids[row.Key] = existing.id
			if !existing.unreadable && existing.order == order && sameMeaning(existing.bean, data) {
				continue
			}
			bean, err := nikofmt.DecodeBean(nikofmt.TypeName(row.Bean), data)
			if err != nil {
				return NodeRows{}, fmt.Errorf("row %s: %w", row.Key, err)
			}
			record, err := s.newRecord(existing.id, group.ID, order, bean)
			if err != nil {
				return NodeRows{}, err
			}
			st.profiles[existing.id] = record
			result.Changed = append(result.Changed, existing.id)
			continue
		}
		record, err := s.newRecord(st.nextProfile, group.ID, order, row.Bean)
		if err != nil {
			return NodeRows{}, err
		}
		st.profiles[record.id] = record
		st.nextProfile++
		ids[row.Key] = record.id
		taken[record.id] = true
		result.Added = append(result.Added, record.id)
	}
	for _, pid := range known {
		if p := st.profiles[pid]; p != nil && p.group == group.ID && !taken[pid] {
			delete(st.profiles, pid)
			result.Deleted = append(result.Deleted, pid)
		}
	}
	sort.Slice(result.Deleted, func(i, j int) bool { return result.Deleted[i] < result.Deleted[j] })
	n.GroupID = group.ID
	n.ProfileIDs = ids
	record, err := s.sealNode(n)
	if err != nil {
		return NodeRows{}, err
	}
	st.nodes[i] = record
	if touched := append(append(append([]int64(nil), result.Added...), result.Changed...), result.Deleted...); len(touched) > 0 {
		changes = append(changes, Change{Kind: KindProfiles, IDs: touched})
	}
	changes = append(changes, Change{Kind: KindNodes})
	// the selected row left the subscription: a row of this node selected instead
	selectedGone := st.selected != 0 && st.profiles[st.selected] == nil
	if selectedGone {
		st.selected = 0
		for _, row := range rows {
			st.selected = ids[row.Key]
			break
		}
	}
	if err = s.commit(st, changes...); err != nil {
		return NodeRows{}, err
	}
	if selectedGone {
		_ = s.writeSelected(st.selected)
		s.tell(selectedChange(st.selected))
	}
	return result, nil
}

// keepTweaks is the new bean's JSON with the user's tweaks of the old one's.
func keepTweaks(old, next json.RawMessage) (json.RawMessage, error) {
	var from, to map[string]json.RawMessage
	if err := json.Unmarshal(old, &from); err != nil {
		return nil, err
	}
	if err := json.Unmarshal(next, &to); err != nil {
		return nil, err
	}
	for _, name := range userTweaks {
		if v, ok := from[name]; ok {
			to[name] = v
		} else {
			delete(to, name)
		}
	}
	return json.Marshal(to)
}

// sameMeaning: two JSON texts of one meaning.
func sameMeaning(a, b json.RawMessage) bool {
	var x, y any
	if json.Unmarshal(a, &x) != nil || json.Unmarshal(b, &y) != nil {
		return false
	}
	return reflect.DeepEqual(x, y)
}

// DeleteNode removes a node, its group and the group's profiles (D-NODE-07): its device key goes with it, and the
// node comes back only with a new registration.
func (s *Store) DeleteNode(id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.state.clone()
	r, i := st.node(id)
	if r == nil {
		return ErrNotFound
	}
	st.nodes = append(st.nodes[:i:i], st.nodes[i+1:]...)
	changes := []Change{{Kind: KindNodes}}
	var groups, deleted []int64
	kept := st.groups[:0:0]
	for _, g := range st.groups {
		if g.Node == id || g.ID == r.group && !g.Ungrouped && r.group != 0 {
			groups = append(groups, g.ID)
			continue
		}
		kept = append(kept, g)
	}
	st.groups = kept
	for pid, p := range st.profiles {
		for _, g := range groups {
			if p.group == g {
				delete(st.profiles, pid)
				deleted = append(deleted, pid)
			}
		}
	}
	sort.Slice(deleted, func(i, j int) bool { return deleted[i] < deleted[j] })
	if len(groups) > 0 {
		changes = append(changes, Change{Kind: KindGroups, IDs: groups})
	}
	if len(deleted) > 0 {
		changes = append(changes, Change{Kind: KindProfiles, IDs: deleted})
	}
	return s.commit(st, changes...)
}
