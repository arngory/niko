package nikoconf

import (
	"sort"
	"unicode/utf16"
)

// The orders of the JVM's HashMap and HashSet, where the app's build lets them into the config: a set's members as
// a list (the domains resolved directly, a rule's uids) and a map's values (the members of a selector or urltest). A
// HashMap iterates its table's buckets in order, each bucket's entries as they came; the bucket is the key's hash,
// spread (h ^ h>>>16), modulo the table's size, which grows by doubling past three quarters full. A bucket turned
// into a tree (8 entries of one bucket, a table of 64) is not followed.

// javaHashSetOrder is Kotlin's toHashSet() of a list, iterated: each value once, in the set's order.
func javaHashSetOrder[T comparable](items []T, hash func(T) int32) []T {
	var distinct []T
	seen := map[T]bool{}
	for _, v := range items {
		if !seen[v] {
			seen[v] = true
			distinct = append(distinct, v)
		}
	}
	// HashSet(mapCapacity(size)): Kotlin sizes the set for the whole list
	table := javaTableSize(kotlinMapCapacity(len(items)), len(distinct))
	return javaHashOrder(distinct, hash, table)
}

// javaHashOrder orders the keys, distinct and in the order they went in, as a table of this size iterates them.
func javaHashOrder[T any](keys []T, hash func(T) int32, table int) []T {
	out := append([]T(nil), keys...)
	bucket := func(v T) int32 {
		h := hash(v)
		return (h ^ int32(uint32(h)>>16)) & int32(table-1)
	}
	sort.SliceStable(out, func(i, j int) bool { return bucket(out[i]) < bucket(out[j]) })
	return out
}

// kotlinMapCapacity is Kotlin's mapCapacity: a capacity that holds expectedSize without growing.
func kotlinMapCapacity(expectedSize int) int {
	switch {
	case expectedSize < 3:
		return expectedSize + 1
	case expectedSize < 1<<30:
		return int(float32(expectedSize)/0.75 + 1)
	}
	return 1<<31 - 1
}

// javaTableSize is the table of a HashMap made with this initial capacity (< 0: the default constructor) once size
// keys went in: HashMap.resize as it grows.
func javaTableSize(initialCapacity, size int) int {
	capacity, threshold := 0, 0
	if initialCapacity >= 0 {
		threshold = tableSizeFor(initialCapacity)
	}
	resize := func() {
		switch {
		case capacity > 0:
			newThreshold := 0
			if capacity >= 16 {
				newThreshold = threshold << 1
			}
			capacity <<= 1
			if newThreshold == 0 {
				newThreshold = int(float32(capacity) * 0.75)
			}
			threshold = newThreshold
		case threshold > 0: // the initial capacity, kept in the threshold
			capacity = threshold
			threshold = int(float32(capacity) * 0.75)
		default:
			capacity, threshold = 16, 12
		}
	}
	for n := 1; n <= size; n++ {
		if capacity == 0 {
			resize()
		}
		if n > threshold {
			resize()
		}
	}
	return max(capacity, 1)
}

// tableSizeFor is HashMap.tableSizeFor: the power of two at or above c, 1 at least.
func tableSizeFor(c int) int {
	n := 1
	for n < c {
		n <<= 1
	}
	return n
}

// javaStringHash is String.hashCode, over UTF-16.
func javaStringHash(s string) int32 {
	var h int32
	for _, c := range utf16.Encode([]rune(s)) {
		h = 31*h + int32(c)
	}
	return h
}

// javaInt is an Integer that may be null.
type javaInt struct {
	value int
	null  bool
}

// javaIntegerHash is Integer.hashCode, HashMap's 0 for null.
func javaIntegerHash(v javaInt) int32 {
	if v.null {
		return 0
	}
	return int32(v.value)
}

// javaLongHash is Long.hashCode.
func javaLongHash(v int64) int32 { return int32(v ^ int64(uint64(v)>>32)) }

// javaLongMap is a HashMap<Long, String> made with the default constructor: its values in its order.
type javaLongMap struct {
	keys []int64
	m    map[int64]string
}

func (m *javaLongMap) put(k int64, v string) {
	if m.m == nil {
		m.m = map[int64]string{}
	}
	if _, ok := m.m[k]; !ok {
		m.keys = append(m.keys, k)
	}
	m.m[k] = v
}

func (m *javaLongMap) get(k int64) (string, bool) {
	v, ok := m.m[k]
	return v, ok
}

func (m *javaLongMap) values() []string {
	var out []string
	for _, k := range javaHashOrder(m.keys, javaLongHash, javaTableSize(-1, len(m.keys))) {
		out = append(out, m.m[k])
	}
	return out
}
