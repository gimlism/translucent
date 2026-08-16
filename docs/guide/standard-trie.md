# Standard trie

An uncompressed trie: one node per character, so `shell` is a chain of five nodes and no edge ever
carries more than a single letter. Compare `docs/guide/trie.md`, whose radix trie stores that same
chain as one edge.

## What each method makes you see

| you call | you see, in order |
| --- | --- |
| `put(key, value)` | `Descend`×n → `CreateNode`×n → `Put` |
| `remove(key)` | `Descend`×n → `Remove`? → `Prune`×n |
| `get(key)` | nothing — reads are silent |
| `containsKey(key)` | nothing — reads are silent |

In the table above, `×n` means the event fires once per step and may not occur at all, while `?` marks conditionally-firing operations.

**There is no `SplitEdge` row and no `MergeEdge` row, and that absence is the lesson.** Those two
events exist because a radix trie stores runs of characters on one edge and must therefore break
edges apart and glue them back together; a standard trie stores one character per node, so there is
nothing to split and nothing to merge. Everything the compression buys — and everything it costs — is
the difference between these two tables.

`StandardTrie.put` consumes the key one character at a time. While the next character already has a
child, it moves down and emits `Descend`; the first character with no child ends the walking phase for
good, and the rest of the key is grown as a chain of fresh nodes, one `CreateNode` each. So every
`Descend` in a single `put` precedes every `CreateNode` in it, never the other way round — and a
`put` of a key already present is all `Descend` and no `CreateNode` at all. `Put` is always last,
whether the walk ended by exhausting the key against existing nodes or by growing new ones.

`StandardTrie.remove` buffers its walk and narrates it as `Descend` only once it knows the removal
will actually happen, then emits `Remove` before changing the tree's shape. What follows is the prune
cascade: starting at the node the key ended on and working back up towards the root, each node that
is now childless, no longer a key, and not the root is unhooked from its parent and emits one
`Prune`. The cascade stops at the first node that fails any of those tests — so removing a key whose
node still has children prunes nothing, and removing the last key down a long chain prunes once per
node the chain no longer needs. This is where the radix trie's `MergeEdge` has no counterpart: an
uncompressed trie does not tidy a surviving single-child chain, it simply keeps it.

Removing a key that isn't there fires nothing at all: if the walk falls off (a child is missing) or
lands on a node that exists but was never a key, `remove` returns before the buffered walk is ever
narrated — no `Descend`, no `Remove`, and so no `Prune` either. The same silence as an absent-key
`RadixTrie.remove` and `TeachingHashMap.remove`; `TeachingTreeSet` is the one structure that narrates
a failed write (see `docs/guide/treeset.md`).

`get` and `containsKey` both delegate to the same silent `find`, which walks children without
narrating any of them.

## Read alongside

- `src/main/java/com/gimlism/translucent/trie/core/StandardTrie.java` — the algorithm
- `src/main/java/com/gimlism/translucent/trie/core/RadixTrie.java` — the compressed form of the same idea
- `src/main/java/com/gimlism/translucent/trie/events/` — the seven event types, of which this trie
  uses five

## Watch it

- `docs/viz/standard-trie.html` — open in a browser, no JDK needed
- `docs/viz/trie.html` — the same keys, stored as a radix trie
- `docs/viz/compression-compare.html` — both at once, with what the compression saves
- demos 19–24 in the [README](../../README.md#trie-standard--one-node-per-character-and-a-prune-cascade-when-keys-leave)
