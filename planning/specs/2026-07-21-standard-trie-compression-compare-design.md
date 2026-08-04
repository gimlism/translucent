# Standard (one-char-per-edge) trie + compression compare — design

**Date:** 2026-07-21
**Slice:** 2nd trie implementation, for a compression comparison against the existing radix trie.
**Scope:** core only (data structure + shared-subset event emission + node-count metric + compare demo). ASCII and web viz are deferred to their own later brainstorms, matching how `RadixTrie` was sequenced.

## Motivation

`RadixTrie` (PATRICIA) compresses single-child chains into one multi-character edge. A *standard* trie never does — every edge is exactly one character, so a key of length _n_ is a chain of _n_ nodes. That structural difference **is** the compression lesson. This slice adds the uncompressed trie so the two can be measured on the same keys; the compression ratio is the slice's payload, not a downstream afterthought.

## Architecture

`StandardTrie<V> extends AbstractMap<String, V>` in `trie.core`, **beside** `RadixTrie`, backed by `StandardTrieNode<V>`. Each node carries an optional value (`isKey`/`value`) and `TreeMap<Character, StandardTrieNode<V>> children`. The incoming edge is a single `char` — represented by the child's key in its parent's map; there is no multi-character `edgeLabel` field (the defining contrast with `TrieNode`).

### Deliberate contract-identity with RadixTrie

The **only** difference the compare should surface is compression, so `StandardTrie` matches `RadixTrie`'s `Map<String,V>` contract exactly:

- null key rejected (`Objects.requireNonNull`);
- empty-string key stored at the root;
- `entrySet()` returns immutable snapshots (`SimpleImmutableEntry`); `Map.Entry.setValue` throws `UnsupportedOperationException` (same documented limitation);
- `modCount` + fail-fast (`ConcurrentModificationException`) iterator, with a working `Iterator.remove`;
- children ordered by `TreeMap<Character,…>` ⇒ lexicographic entry/`keysWithPrefix` order;
- `keysWithPrefix(String)` returns an unmodifiable lexicographic snapshot;
- value-replace (`put` on an existing key) is non-structural — no `modCount`/`size` change, no `CreateNode`.

### Reuse: the whole `trie.events` package, unchanged

`StandardTrie` emits the existing `TrieEvent` sealed type and the existing `TrieSnapshot` / `TrieNodeSnapshot` / `TrieEdge` records — labels just happen to be length-1 strings. **No new event types, no new snapshot types.** It reuses `TrieEventFormatter` (an exhaustive switch that already handles every case) and the substrate `EventDispatcher`/`StructureEventListener`. Console/recording consumers therefore work for free.

Identical snapshot shape ⇒ identical JSON ⇒ the existing renderers (deferred to a later viz slice) already draw it. Apples-to-apples by construction.

## Event grammar (a strict subset — the compression story made visible)

Everything narrates **one char at a time**. `StandardTrie` emits only `Descend`, `CreateNode`, `Put`, `Remove`, `Prune` — **never** `SplitEdge` or `MergeEdge` (nothing compresses, so nothing splits or merges).

- **insert:** `Descend* → CreateNode* → Put`
  - one `Descend` per already-existing char matched on the walk down (each edge is one char, so each step is one `Descend`, `path` advancing one char);
  - one `CreateNode` per *new* char appended (a chain of single-char nodes);
  - terminal `Put` marker.
  - Example — `put("cat")` into an empty trie: `CREATE "c"→"c"`, `CREATE "a"→"ca"`, `CREATE "t"→"cat"`, `PUT "cat"=1 (new)`. (Radix would emit a single `CREATE "cat"`.)
- **remove:** `Descend* → Remove → Prune*`
  - narrate the walk (buffered — narrated only if the removal proceeds; an absent-key or non-key-node remove is **silent**, matching `RadixTrie`);
  - the `Remove` marker;
  - then cascade `Prune`, one per node that becomes childless-and-non-key, walking up the chain toward the root (the root itself is never pruned). This replaces radix's `MergeEdge` compression cleanup — the standard trie only ever prunes.

`Descend`/`CreateNode`/`Prune` `path`/`label` semantics are reused verbatim from the radix events; `label` is a single character for this trie.

## The compression compare (the slice payload)

A **consumer-side** helper, outside both cores:

- `trie.compare.TrieMetrics.nodeCount(TrieSnapshot)` — walks a snapshot and counts nodes (root included, or root excluded — pick one and document it; the comparison only needs consistency). Takes a snapshot, never an internal field, so it is automatically apples-to-apples and touches neither data structure.
- `trie.compare.CompressionCompareDemo` (+ its test) — inserts one shared-prefix key set into both `StandardTrie` and `RadixTrie` and asserts:
  1. **identical `entrySet`** on both — proves the two implement the same map, so the comparison is fair;
  2. **`nodeCount(standard) > nodeCount(radix)`** on a prefix-sharing set — proves compression actually happened;
  3. a **pinned exact count** for the canonical set `{she, shell, shore, shy}`, computed carefully at implementation time (not guessed here), so the ratio is a regression-locked number rather than a hand-wave.

`TrieMetrics.nodeCount` obtains each trie's snapshot via the existing package-visible `snapshot()` (or by recording the final event's `after()`); the test lives in the same package family as the tries so it can reach the snapshot without widening public surface. If snapshot access requires a package boundary crossing, prefer recording the terminal event's `after()` snapshot over widening `snapshot()` to public.

## Testing

Mirror the `RadixTrie*` core suite against `StandardTrie`:

- **insert:** empty-trie chain creation (per-char `CreateNode` count), shared-prefix branching, value-replace is non-structural, empty-string key at root;
- **remove:** leaf prune, cascade prune up a chain, branch node stays, absent-key and non-key-node removes are silent (no events), `Iterator.remove`;
- **read:** `get`/`containsKey`/`keysWithPrefix` on present/absent/prefix keys; reads emit no events;
- **contract parity:** null-key rejection, immutable `entrySet`, fail-fast CME iterator;
- **event grammar:** a `TrieRecordingListener` (shared) pins the exact `Descend*→CreateNode*→Put` and `Descend*→Remove→Prune*` sequences, and asserts **no** `SplitEdge`/`MergeEdge` is ever emitted;
- **compare:** the three `CompressionCompareDemo` assertions above.

## Non-goals (deferred to their own brainstorms)

- ASCII terminal renderer / side-by-side view;
- the 4-slice web viz arc (static replay, live SSE, REPL, browser controls);
- any change to `RadixTrie`, `trie.events`, or the substrate (this slice is purely additive).

## Named constraints / gotchas

- **No core mutation of existing files.** `RadixTrie`, `trie.events`, and the substrate stay byte-unchanged; the slice is additive (new `trie.core` classes + new `trie.compare` package).
- **Silent no-op removes** — buffer the walk, narrate only on a removal that proceeds (verbatim discipline from `RadixTrie`).
- **Snapshot-before-settled** does not bite here the way it does in the RB structures (no rotation mid-op), but each event's `after()` must reflect the state **after** that step's mutation, consistent with the radix trie (e.g. a `CreateNode`'s snapshot already contains the new node).
- **Prune cascade order** — prune from the removed node upward; stop at the first node that is still a key or still has a child (or the root). Each pruned node is one `Prune` event.
