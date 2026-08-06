# Trie

A radix trie: a prefix tree whose edges carry whole strings rather than single characters, so a
chain with no branching is stored as one edge.

## What each method makes you see

| you call | you see, in order |
| --- | --- |
| `put(key, value)` | `Descend`×n → `SplitEdge`? → `CreateNode`? → `Put` |
| `remove(key)` | `Descend`×n → `Remove` → `Prune`? → `MergeEdge`? |
| `get(key)` | nothing — reads are silent |
| `containsKey(key)` | nothing — reads are silent |

`RadixTrie.put` walks one edge at a time: a full match against an existing edge emits `Descend` and
moves on to the next edge, so `Descend` fires once per edge fully consumed before anything else
happens. The walk ends one of three ways, and `Put` is always the last event because every branch
loops back to the same "key exhausted" check that emits it: landing on a node with no child for the
next character creates a leaf (`CreateNode`) and stops there; landing partway through an existing
edge label breaks it in two (`SplitEdge`) and then either the key ends exactly at the break (straight
to `Put`) or continues past it into a fresh leaf (`CreateNode`, then `Put`). So `SplitEdge`, if it
appears, always comes before `CreateNode`, never after.

`RadixTrie.remove` buffers the walk's edges and narrates them as `Descend` only once it knows the
remove will actually happen, then emits `Remove` before touching the tree's shape — so `Remove`
always precedes whatever tidying follows, never the reverse. What tidying happens depends on what's
left where the key used to be, and there are two distinct, mutually exclusive shapes: if the
now-non-key node still has exactly one child, `remove`'s own "absorb" branch calls the shared
`mergeWithChild` helper directly and returns — `MergeEdge` fires with **no** `Prune` at all. Otherwise
the removed node was a leaf, so it gets unhooked from its parent first (`Prune`) — and only that can
leave the parent non-key with exactly one child of its own, which is what then makes the parent call
`mergeWithChild` on itself (`MergeEdge`). So whenever `MergeEdge` follows a `Prune` in the same
`remove`, it does so *because* that `Prune` just created the one-child shape it depends on — it is
never independent of the `Prune` that preceded it.

`get` and `containsKey` are overridden here rather than inherited, but neither emits — both delegate
to the same silent `find`, which walks edges without narrating any of them. `TeachingTreeSet.contains`,
by contrast, narrates every comparison it makes; see `docs/guide/treeset.md`.

## Read alongside

- `src/main/java/com/gimlism/translucent/trie/core/RadixTrie.java` — the algorithm
- `src/main/java/com/gimlism/translucent/trie/events/` — the seven event types
- `src/main/java/com/gimlism/translucent/trie/core/StandardTrie.java` — the uncompressed trie the
  comparison demo measures against

## Watch it

- `docs/viz/trie.html` — open in a browser, no JDK needed
- `docs/viz/compression-compare.html` — the same keys, fat trie beside compressed
- demos 19–24 in the [README](../../README.md#trie--a-radix-trie-whose-edges-split-and-merge-as-keys-arrive-and-leave)
