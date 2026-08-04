# Teaching Radix Trie — Core — Design

**Status:** Draft 2026-07-04
**Builds on:** the generic substrate (PR #9) — this is its **first real consumer**.
The trie provides only its own concrete sealed event vocabulary + snapshot and an
(next-slice) `EventRenderer`, and inherits recording/replay/live-visualization from
`com.gimlism.translucent.substrate` for free. Sibling of `TeachingHashMap` (a Map by
hashing) and `TeachingArrayList`; the trie is a Map by **prefix**.

## Goal

Build an instrumented teaching **radix (PATRICIA) trie** — a `Map<String, V>` whose
keys share structure by common prefix, with **compressed, `String`-labeled edges**
(single-child chains collapse into one multi-character edge). Every mutation emits a
self-contained immutable event; the event stream is the deliverable, consumed through
the substrate. It is the roadmap's **acid test**: variable fan-out + edge labels +
path-shaped focus that the binary `TreeNodeSnapshot` cannot express, forcing a
genuinely new snapshot shape (each node has N children, each edge labeled by a String).

The ASCII visualizer (the variable-fan-out renderer) is the **next** slice.

## Resolved decisions

| Question | Decision |
|---|---|
| Variant | **Radix / PATRICIA** — compressed multi-character edge labels. Insert **splits** edges; remove **prunes** leaves and **merges** single-child chains. |
| Children representation | **Sorted map:** each node holds a `TreeMap<Character, TrieNode>` keyed by each child edge's **first character** (no two edges from a node share a first char). Variable fan-out, lexicographic iteration, deterministic rendering. |
| Map interface | **Full `java.util.Map` compliance** via `AbstractMap<String, V>` — plus `keysWithPrefix(String)`, the trie's headline capability. Iteration order is **lexicographic** (sorted DFS). |
| Scope this slice | **Complete core:** insert (with split), get/containsKey/size, remove (with prune/merge), sorted `entrySet` with fail-fast `Iterator` + `Iterator.remove`, `keysWithPrefix`. The viz is deferred. |
| Event granularity | **Fine-grained** — a `Descend`/`CreateNode`/`SplitEdge` per structural step of the walk, then a terminal `Put`; `Remove` then `Prune`/`MergeEdge`. Consistent with the rotations/shifts granularity elsewhere; it makes prefix-sharing and compression visible. |
| Event payload | **Full immutable whole-trie snapshot** per event, mirroring the map/list model. |
| Null values | **Permitted** (like `HashMap`). An `isKey` flag distinguishes "key present, value null" from "not a key". |
| Second implementation | **Deferred.** Edge labels are `String`s (a standard one-char trie would use length-1 labels), so the model is reusable if a comparison implementation is added later. |

## Tech stack & module layout

- Java 21, Maven, JUnit 5. Depends on `substrate`.
- Package `com.gimlism.translucent.trie`:
  - `core` — `RadixTrie`, package-private `TrieNode`.
  - `events` — sealed `TrieEvent`, snapshot records, `TrieEventListener`, `TrieEventFormatter`.
  - `consumer` — `ConsoleTrieEventLogger`, `TrieRecordingListener`.
  - `demo` — `TrieDemo`.

## Core data structure

`RadixTrie<V> extends AbstractMap<String, V>`. Internal (package-private) `TrieNode`:

```java
class TrieNode {
    boolean isKey;                              // does a key end here?
    V value;                                    // meaningful iff isKey (may be null)
    final TreeMap<Character, TrieNode> children = new TreeMap<>(); // by child edge's first char
    String edgeLabel;                           // the (compressed) label on the edge from the parent
}
```

The **root** has no incoming edge (`edgeLabel` empty) and `isKey` iff the empty string
`""` was inserted. **Radix invariants** (the correctness gate, asserted adversarially
in tests): every non-root node has a non-empty `edgeLabel`; the children of a node have
distinct first characters; every non-key, non-root internal node has **≥ 2 children**
(a 1-child non-key node is always merged away).

### Insert `put(key, value)` — walk, consuming the key; may split

At a node with remaining suffix `s` of `key`:
- `s` empty → this node is the key: set `isKey`/`value`, emit `Put`. (Descends led here.)
- else let `c = s.charAt(0)`, `child = children.get(c)`:
  - **no child:** attach a new leaf node with `edgeLabel = s` → `CreateNode` → `Put`.
  - **child with label `L`,** common prefix `p = commonPrefix(s, L)`:
    - `p == L` (edge fully matched): `Descend` into child, `s = s.substring(L.length())`, continue.
    - `p == s` (key ends inside the edge): **`SplitEdge`** at `p` — insert an intermediate
      (key) node; the old child hangs below it with label `L.substring(p.length())` → `Put`.
    - else (they diverge at `p`): **`SplitEdge`** at `p` (intermediate non-key node) with the
      old child rehung and a new leaf (`CreateNode`) for `s.substring(p.length())` → `Put`.

**Grammar:** `Descend* → [SplitEdge] → [CreateNode] → Put` (`Put` terminal).

### Remove `remove(key)` — find, unmark, then prune/merge upward

Find the node `N` for `key` (walk consuming full edge labels; any mismatch ⇒ absent, no-op
returning `null`). If `N.isKey`: unmark it, emit `Remove(key, old)`. Then restore invariants:
- `N` has **≥ 2 children:** nothing more (still a branch).
- `N` has **1 child:** **`MergeEdge`** — `N` absorbs its sole child (concatenate labels).
- `N` has **0 children** (leaf): **`Prune`** — detach `N` from its parent `P`; then if `P`
  is non-root, non-key, and now has exactly 1 child, **`MergeEdge`** `P` with that child.

**Grammar:** `Remove → [Prune] → [MergeEdge]` (or `Remove → MergeEdge`).

### Reads (no events)
`get`/`containsKey` walk edges; `keysWithPrefix(prefix)` walks to the prefix node then
DFS-collects keys in lexicographic order; `entrySet()` is a sorted DFS with a fail-fast
(`modCount`) iterator whose `remove()` delegates to `remove(key)`.

## Event model

```java
sealed interface TrieEvent extends StructureEvent
    permits Descend, CreateNode, SplitEdge, Put, Remove, MergeEdge, Prune {
    TrieSnapshot after();   // covariant narrowing of StructureEvent.after()
}
```

Immutable `record`s (final field lists settle during TDD). `path` is the string from the
root to the node the event concerns — the hook a later renderer uses for path-shaped focus:

- `Descend(String label, String path, TrieSnapshot after)` — followed an existing edge.
- `CreateNode(String label, String path, TrieSnapshot after)` — new leaf edge (new branch).
- `SplitEdge(String originalLabel, String commonPrefix, String path, TrieSnapshot after)` —
  an edge was split at `commonPrefix` (a new intermediate node appears). *Radix-specific.*
- `Put(String key, Object value, Object previousValue, boolean newKey, TrieSnapshot after)`.
- `Remove(String key, Object removedValue, TrieSnapshot after)`.
- `MergeEdge(String mergedLabel, String path, TrieSnapshot after)` — two edges merged into
  one compressed edge. *Radix-specific.*
- `Prune(String label, String path, TrieSnapshot after)` — a leaf node was removed.

### Snapshot model — the new shape

```java
record TrieSnapshot(TrieNodeSnapshot root, int size) implements StructureSnapshot {}
record TrieNodeSnapshot(boolean key, Object value, List<TrieEdge> children) {}  // children sorted by label
record TrieEdge(String label, TrieNodeSnapshot target) {}
```

Each node carries **N labeled children** (vs the binary `left`/`right` of
`TreeNodeSnapshot`) with **`String` edge labels** (vs implicit single chars) — the
expressiveness the acid test demanded. Snapshots copy out values; no references into live
nodes. `TrieNodeSnapshot.value` is meaningful only when `key` is true.

### Dispatch — via the substrate

`TrieEventListener extends StructureEventListener<TrieEvent>` (named functional alias, like
`MapEventListener`). `RadixTrie` holds `List<StructureEventListener<TrieEvent>>`, exposes
`addListener`/`removeListener`, and emits synchronously with the same **re-entrancy guard**
(a listener may read but not mutate mid-dispatch → `ConcurrentModificationException`).
`TrieRecordingListener` is an empty subclass of the substrate `RecordingListener<TrieEvent>`.

## Consumers

- `TrieEventFormatter.format(TrieEvent)` — one-line labels, e.g. `DESCEND "sh" -> "sh"`,
  `SPLIT "ore"@"o" -> "sho"`, `PUT "shore"=2 (new)`, `MERGE -> "shore"`, `PRUNE "e" <- "she"`.
- `ConsoleTrieEventLogger implements TrieEventListener` — prints the labels (validates the
  stream end to end), mirroring `ConsoleEventLogger`.

## Testing strategy (TDD)

- **Radix invariants (adversarial, the primary gate):** insert many keys with rich shared
  prefixes (and the empty key, and keys that are prefixes of others: `"a","ab","abc"`,
  `"she","shell","shore","short"`), asserting after *every* insert **and** every delete
  that the snapshot tree satisfies the radix invariants (non-empty labels, distinct child
  first-chars, ≥2 children per non-key internal node) and that the key/value set is exactly
  correct. The delete analogue deletes in ascending/descending/mixed orders.
- **Split cases:** key-ends-inside-edge (`put "sh"` after `"shore"`), diverge-mid-edge
  (`put "shell"` after `"shore"`) each emit exactly one `SplitEdge` with the right
  `commonPrefix`, then `Put`.
- **Merge/prune cases:** removing a leaf `Prune`s and, when the parent drops to one child,
  `MergeEdge`s; removing a key with one child `MergeEdge`s; removing a branch key emits only
  `Remove`.
- **Event sequence:** scripted inserts/removes assert exact event order via a
  `RecordingListener<TrieEvent>` (`Descend* → SplitEdge? → CreateNode? → Put`, etc.).
- **Map compliance:** put/get round-trips incl. null values and the empty key; `size`;
  lexicographic `entrySet` iteration and `keysWithPrefix`; fail-fast `modCount`;
  `Iterator.remove`.
- **Snapshot integrity & substrate:** snapshots immutable/independent of later mutation;
  a `TrieEvent` is a `StructureEvent` whose `after()` is a `StructureSnapshot`; a substrate
  `RecordingListener<TrieEvent>` records; re-entrant mutation from a listener is rejected.

## Out of scope (future)

- The variable-fan-out ASCII **visualizer** (next slice) — the acid test's visible payoff.
- A **second implementation** for side-by-side comparison (standard one-char-per-edge trie,
  or an R-way-array children variant).
- Prefix operations beyond `keysWithPrefix` (`longestPrefixOf`, range/floor/ceiling).
- Non-`String` key types.
