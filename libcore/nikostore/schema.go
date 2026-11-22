package nikostore

import (
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
)

// The schema of store.json (§4, «Смена схемы и копии»): its "schema" says which layout it is. An older file is
// migrated forward when the store opens, after a copy of it goes to backup/<schema>-<date>/; a newer one (a downgrade
// of Niko) is refused and left as it is.

// migration takes store.json, decoded to its top-level fields, from the schema before it to its own. migrations[i]
// takes schema i+1 to i+2.
type migration func(document map[string]json.RawMessage) error

// migrations: a layout change adds one here and a fixture of the old layout to the tests (schema_test.go).
var migrations = []migration{
	// 1 to 2: a subscription's group holds its subscription (subscription.go). Schema 1 could hold no subscription's
	// group, its SaveGroup refused them; a group of type 1 there, which no build wrote, becomes a basic one. The schema
	// itself says that a build of schema 1 must not open the store: it would write the subscriptions away.
	func(document map[string]json.RawMessage) error {
		var groups []map[string]json.RawMessage
		if err := json.Unmarshal(document["groups"], &groups); err != nil {
			return err
		}
		for _, g := range groups {
			if t := string(g["type"]); t != "" && t != "0" {
				if _, ok := g["subscription"]; !ok {
					delete(g, "type")
				}
			}
		}
		data, err := json.Marshal(groups)
		document["groups"] = data
		return err
	},
}

// Schema is the layout this build writes: the first, and one more for each migration it has.
func Schema() int { return 1 + len(migrations) }

// ErrNewerSchema is a store written by a newer Niko: this one does not open it, nor write over it.
var ErrNewerSchema = errors.New("the store is of a newer Niko than this one")

// errNoSchema is a store.json without its schema: not the store's.
var errNoSchema = errors.New("store.json has no schema")

// schemaOf is the schema a store.json says it is.
func schemaOf(data []byte) (int, error) {
	var head struct {
		Schema int `json:"schema"`
	}
	if err := json.Unmarshal(data, &head); err != nil {
		return 0, err
	}
	if head.Schema < 1 {
		return 0, errNoSchema
	}
	return head.Schema, nil
}

// migrate takes a store.json of schema from to the current one.
func migrate(data []byte, from int) ([]byte, error) {
	var document map[string]json.RawMessage
	if err := json.Unmarshal(data, &document); err != nil {
		return nil, err
	}
	for schema := from; schema < Schema(); schema++ {
		if err := migrations[schema-1](document); err != nil {
			return nil, fmt.Errorf("schema %d to %d: %w", schema, schema+1, err)
		}
		document["schema"] = json.RawMessage(strconv.Itoa(schema + 1))
	}
	return json.Marshal(document)
}
