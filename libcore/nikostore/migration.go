package nikostore

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"slices"
	"sort"
	"strings"

	"libcore/nikoconf"
	"libcore/nikofmt"
	"libcore/nikoray"
	"libcore/nikosub"
)

const (
	snapshotVersion  = 1
	// Keep snapshots comfortably below gRPC's 4 MiB default message limit (accounting for protobuf framing).
	maxSnapshotBytes     = 3 << 20
	maxImportRows        = 10000
	maxTrackedSnapshotID = 32
)

type snapshotDocument struct {
	Version    int               `json:"version"`
	SnapshotID string            `json:"snapshot_id,omitempty"`
	Selected   int64             `json:"selected,omitempty"`
	Groups     []snapshotGroup   `json:"groups"`
	Profiles   []snapshotProfile `json:"profiles"`
	Rules      []nikoconf.Rule   `json:"rules"`
	Nodes      []json.RawMessage `json:"nodes"`
	Settings   *snapshotSettings `json:"settings,omitempty"`
}

type snapshotGroup struct {
	ID           int64                 `json:"id"`
	Order        int64                 `json:"order"`
	Name         string                `json:"name,omitempty"`
	Ungrouped    bool                  `json:"ungrouped,omitempty"`
	Type         int                   `json:"type,omitempty"`
	Subscription *nikosub.Subscription `json:"subscription,omitempty"`
	Node         string                `json:"node,omitempty"`
}

type snapshotProfile struct {
	ID    int64           `json:"id"`
	Group int64           `json:"group"`
	Order int64           `json:"order"`
	Type  string          `json:"type"`
	Bean  json.RawMessage `json:"bean"`
}

type snapshotSettings struct {
	Public  map[string]json.RawMessage `json:"public,omitempty"`
	Secrets map[string]json.RawMessage `json:"secrets,omitempty"`
}

// ImportStats reports how many items ImportSnapshot added into the target store.
type ImportStats struct {
	Groups   int
	Profiles int
	Rules    int
	Nodes    int
}

func hasLegacyDPAPISealed(doc *document) bool {
	for _, g := range doc.Groups {
		if strings.HasPrefix(g.Sealed, "dpapi:") {
			return true
		}
	}
	for _, p := range doc.Profiles {
		if strings.HasPrefix(p.Sealed, "dpapi:") {
			return true
		}
	}
	for _, raw := range doc.Nodes {
		if strings.Contains(string(raw), `"dpapi:`) {
			return true
		}
	}
	return false
}

// ExportSnapshot returns a decrypted JSON snapshot of the store's groups, profiles, rules, raycontrol nodes,
// selection, and settings for one-time migration from user-mode to the system service.
func (s *Store) ExportSnapshot() ([]byte, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	doc := snapshotDocument{
		Version:  snapshotVersion,
		Groups:   make([]snapshotGroup, 0, len(s.state.groups)),
		Profiles: make([]snapshotProfile, 0, len(s.state.profiles)),
		Rules:    make([]nikoconf.Rule, 0, len(s.state.rules)),
		Nodes:    make([]json.RawMessage, 0, len(s.state.nodes)),
	}

	skippedGroups := map[int64]bool{}
	for _, g := range s.state.groups {
		if g.Type == GroupSubscription && g.unreadable {
			skippedGroups[g.ID] = true
			continue
		}
		sg := snapshotGroup{
			ID:        g.ID,
			Order:     g.Order,
			Name:      g.Name,
			Ungrouped: g.Ungrouped,
			Type:      g.Type,
			Node:      g.Node,
		}
		if g.Subscription != nil {
			sg.Subscription = copySubscription(g.Subscription)
		}
		doc.Groups = append(doc.Groups, sg)
	}

	exportedProfiles := map[int64]bool{}
	for _, r := range s.state.sorted(0) {
		if r.unreadable || skippedGroups[r.group] {
			continue
		}
		doc.Profiles = append(doc.Profiles, snapshotProfile{
			ID:    r.id,
			Group: r.group,
			Order: r.order,
			Type:  r.typ,
			Bean:  append(json.RawMessage(nil), r.bean...),
		})
		exportedProfiles[r.id] = true
	}

	if s.state.selected != 0 && exportedProfiles[s.state.selected] {
		doc.Selected = s.state.selected
	}

	for _, r := range s.state.rules {
		doc.Rules = append(doc.Rules, copyRule(r))
	}

	for _, r := range s.state.nodes {
		if r.node == nil {
			continue
		}
		raw, err := json.Marshal(r.node)
		if err != nil {
			return nil, err
		}
		doc.Nodes = append(doc.Nodes, raw)
	}

	if s.settings != nil && !s.settings.unreadable && (len(s.settings.public) > 0 || len(s.settings.secrets) > 0) {
		doc.Settings = &snapshotSettings{
			Public:  clone(s.settings.public),
			Secrets: clone(s.settings.secrets),
		}
	}

	// Compute deterministic content digest so retries of the same snapshot are idempotent.
	body, err := json.Marshal(doc)
	if err != nil {
		return nil, err
	}
	sum := sha256.Sum256(body)
	doc.SnapshotID = hex.EncodeToString(sum[:16])

	out, err := json.Marshal(doc)
	if err != nil {
		return nil, err
	}
	if len(out) > maxSnapshotBytes {
		return nil, fmt.Errorf("%w: store snapshot exceeds %d bytes", ErrRefused, maxSnapshotBytes)
	}
	return out, nil
}

// ImportSnapshot merges a decrypted snapshot from ExportSnapshot into this store, re-sealing every secret
// under this store's sealer. Existing groups, profiles, rules, and nodes are preserved, and importing the
// same snapshot ID again is a clean idempotent no-op.
func (s *Store) ImportSnapshot(data []byte) (ImportStats, error) {
	if len(data) == 0 || len(data) > maxSnapshotBytes {
		return ImportStats{}, fmt.Errorf("%w: snapshot size %d out of bounds", ErrInvalid, len(data))
	}
	var doc snapshotDocument
	if err := json.Unmarshal(data, &doc); err != nil {
		return ImportStats{}, fmt.Errorf("%w: snapshot JSON: %v", ErrInvalid, err)
	}
	if doc.Version != snapshotVersion {
		return ImportStats{}, fmt.Errorf("%w: unsupported snapshot version %d", ErrInvalid, doc.Version)
	}
	if len(doc.Groups) > maxImportRows {
		return ImportStats{}, fmt.Errorf("%w: over %d groups in snapshot", ErrInvalid, maxImportRows)
	}
	if len(doc.Profiles) > maxImportRows {
		return ImportStats{}, fmt.Errorf("%w: over %d profiles in snapshot", ErrInvalid, maxImportRows)
	}
	if len(doc.Nodes) > maxNodes {
		return ImportStats{}, fmt.Errorf("%w: over %d nodes in snapshot", ErrInvalid, maxNodes)
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	if doc.SnapshotID != "" && slices.Contains(s.state.importedSnapshots, doc.SnapshotID) {
		return ImportStats{}, nil
	}

	st := s.state.clone()
	var stats ImportStats
	var changedGroups, changedProfiles, changedRules []int64
	nodesChanged := false

	existingNodes := map[string]bool{}
	for _, r := range st.nodes {
		existingNodes[r.id] = true
	}

	var lastGroupOrder int64
	for _, g := range st.groups {
		lastGroupOrder = max(lastGroupOrder, g.Order)
	}

	// Map snapshot group IDs to target store group IDs.
	groupMap := map[int64]int64{}
	skippedNodeGroups := map[int64]bool{}
	for _, g := range doc.Groups {
		if len(g.Name) > maxName {
			return ImportStats{}, fmt.Errorf("%w: group name over %d bytes", ErrInvalid, maxName)
		}
		if g.Ungrouped {
			groupMap[g.ID] = st.ungrouped()
			continue
		}
		if g.Node != "" {
			if !nodeIDPattern.MatchString(g.Node) {
				return ImportStats{}, fmt.Errorf("%w: invalid node id %q", ErrInvalid, g.Node)
			}
			if existingNodes[g.Node] {
				skippedNodeGroups[g.ID] = true
				continue
			}
		}
		lastGroupOrder++
		ng := Group{
			ID:    st.nextGroup,
			Order: lastGroupOrder,
			Name:  g.Name,
			Type:  g.Type,
			Node:  g.Node,
		}
		switch g.Type {
		case GroupBasic:
		case GroupSubscription:
			if g.Subscription == nil {
				return ImportStats{}, fmt.Errorf("%w: subscription group %d missing subscription", ErrInvalid, g.ID)
			}
			sub := *g.Subscription
			if sub.Link != "" {
				if err := checkLink(sub.Link); err != nil {
					return ImportStats{}, err
				}
			}
			if sub.AutoUpdateDelay == 0 {
				sub.AutoUpdateDelay = nikosub.DefaultAutoUpdateDelay
			}
			if err := s.setSubscription(&ng, sub); err != nil {
				return ImportStats{}, err
			}
		default:
			return ImportStats{}, fmt.Errorf("%w: unknown group type %d", ErrInvalid, g.Type)
		}
		st.nextGroup++
		st.groups = append(st.groups, ng)
		groupMap[g.ID] = ng.ID
		changedGroups = append(changedGroups, ng.ID)
		stats.Groups++
	}

	// Import profiles and re-seal under target store's sealer.
	profileMap := map[int64]int64{}
	for _, p := range doc.Profiles {
		if skippedNodeGroups[p.Group] {
			continue
		}
		if len(p.Bean) > maxBean {
			return ImportStats{}, fmt.Errorf("%w: profile bean over %d bytes", ErrInvalid, maxBean)
		}
		bean, err := nikofmt.DecodeBean(p.Type, p.Bean)
		if err != nil {
			return ImportStats{}, fmt.Errorf("%w: profile %d (%q): %v", ErrInvalid, p.ID, p.Type, err)
		}
		targetGroup, ok := groupMap[p.Group]
		if !ok {
			targetGroup = st.ungrouped()
		}
		newID := st.nextProfile
		order := st.nextOrder(targetGroup)
		rec, err := s.newRecord(newID, targetGroup, order, bean)
		if err != nil {
			return ImportStats{}, err
		}
		st.nextProfile++
		st.profiles[newID] = rec
		profileMap[p.ID] = newID
		changedProfiles = append(changedProfiles, newID)
		stats.Profiles++
	}

	// Import raycontrol nodes and re-seal device/client keys under target store's sealer.
	for _, raw := range doc.Nodes {
		var n nikoray.Node
		if err := json.Unmarshal(raw, &n); err != nil {
			return ImportStats{}, fmt.Errorf("%w: node JSON: %v", ErrInvalid, err)
		}
		id := n.ID()
		if !nodeIDPattern.MatchString(id) {
			return ImportStats{}, fmt.Errorf("%w: invalid node id %q", ErrInvalid, id)
		}
		if strings.TrimSpace(n.Host) == "" || strings.TrimSpace(n.DeviceID) == "" || strings.TrimSpace(n.DeviceKey) == "" {
			return ImportStats{}, fmt.Errorf("%w: node %s missing required host, device_id, or device_key", ErrInvalid, id)
		}
		if existingNodes[id] {
			continue
		}
		if len(st.nodes) >= maxNodes {
			return ImportStats{}, fmt.Errorf("%w: over %d nodes", ErrInvalid, maxNodes)
		}
		if n.GroupID != 0 {
			if mapped, ok := groupMap[n.GroupID]; ok {
				n.GroupID = mapped
			} else {
				n.GroupID = 0
			}
		}
		if len(n.ProfileIDs) > 0 {
			remapped := make(map[string]int64, len(n.ProfileIDs))
			for k, oldPID := range n.ProfileIDs {
				if newPID, ok := profileMap[oldPID]; ok {
					remapped[k] = newPID
				}
			}
			n.ProfileIDs = remapped
		}
		rec, err := s.sealNode(&n)
		if err != nil {
			return ImportStats{}, err
		}
		st.nodes = append(st.nodes, rec)
		existingNodes[id] = true
		nodesChanged = true
		stats.Nodes++
	}

	// Import rules, remapping any profile-specific outbounds.
	if len(st.rules)+len(doc.Rules) > maxRules {
		return ImportStats{}, fmt.Errorf("%w: over %d rules", ErrRefused, maxRules)
	}
	for _, r := range doc.Rules {
		r = copyRule(r)
		if r.Outbound > 0 {
			if newPID, ok := profileMap[r.Outbound]; ok {
				r.Outbound = newPID
			} else {
				r.Outbound = 0
			}
		}
		if err := st.checkRule(&r, 0); err != nil {
			return ImportStats{}, err
		}
		r.ID = st.nextRule
		r.UserOrder = st.nextRuleOrder()
		st.nextRule++
		st.rules = append(st.rules, r)
		changedRules = append(changedRules, r.ID)
		stats.Rules++
	}

	// Preserve selection if the target store has none yet.
	selectedChanged := false
	if st.selected == 0 && doc.Selected != 0 {
		if newPID, ok := profileMap[doc.Selected]; ok {
			st.selected = newPID
			selectedChanged = true
		}
	}

	// Prepare settings import if target store has no user overrides yet.
	var nextSettings *settings
	if doc.Settings != nil && s.settings != nil && !s.settings.unreadable &&
		len(s.settings.public) == 0 && len(s.settings.secrets) == 0 {
		candidate := &settings{
			public:  map[string]json.RawMessage{},
			secrets: map[string]json.RawMessage{},
		}
		for k, v := range doc.Settings.Public {
			if _, ok := computerSettings[k]; ok && !secretSettings[k] {
				candidate.public[k] = v
			}
		}
		for k, v := range doc.Settings.Secrets {
			if secretSettings[k] && string(v) != `""` {
				candidate.secrets[k] = v
			}
		}
		if len(candidate.public) > 0 || len(candidate.secrets) > 0 {
			parsed, err := candidate.read()
			if err != nil {
				return ImportStats{}, fmt.Errorf("%w: settings: %v", ErrInvalid, err)
			}
			for k := range candidate.public {
				if check := computerSettings[k]; check != nil {
					if err := check(&parsed); err != nil {
						return ImportStats{}, fmt.Errorf("%w: setting %s: %v", ErrInvalid, k, err)
					}
				}
			}
			for k := range candidate.secrets {
				if check := computerSettings[k]; check != nil {
					if err := check(&parsed); err != nil {
						return ImportStats{}, fmt.Errorf("%w: setting %s: %v", ErrInvalid, k, err)
					}
				}
			}
			nextSettings = candidate
		}
	}

	// Record snapshot ID inside store.json so any retry after commit is a clean no-op.
	if doc.SnapshotID != "" {
		st.importedSnapshots = append(st.importedSnapshots, doc.SnapshotID)
		if len(st.importedSnapshots) > maxTrackedSnapshotID {
			st.importedSnapshots = st.importedSnapshots[len(st.importedSnapshots)-maxTrackedSnapshotID:]
		}
	}

	// Write settings before committing store.json so a settings write error leaves store.json uncommitted.
	if nextSettings != nil {
		if err := s.writeSettings(nextSettings); err != nil {
			return ImportStats{}, err
		}
	}

	var changes []Change
	if len(changedGroups) > 0 {
		sort.Slice(changedGroups, func(i, j int) bool { return changedGroups[i] < changedGroups[j] })
		changes = append(changes, Change{Kind: KindGroups, IDs: changedGroups})
	}
	if len(changedProfiles) > 0 {
		sort.Slice(changedProfiles, func(i, j int) bool { return changedProfiles[i] < changedProfiles[j] })
		changes = append(changes, Change{Kind: KindProfiles, IDs: changedProfiles})
	}
	if len(changedRules) > 0 {
		sort.Slice(changedRules, func(i, j int) bool { return changedRules[i] < changedRules[j] })
		changes = append(changes, Change{Kind: KindRules, IDs: changedRules})
	}
	if nodesChanged {
		changes = append(changes, Change{Kind: KindNodes})
	}

	if err := s.commit(st, changes...); err != nil {
		return ImportStats{}, err
	}
	if selectedChanged {
		if err := s.writeSelected(st.selected); err == nil {
			s.tell(selectedChange(st.selected))
		}
	}
	if nextSettings != nil {
		s.settings = nextSettings
		s.tell(Change{Kind: KindSettings})
	}

	return stats, nil
}
