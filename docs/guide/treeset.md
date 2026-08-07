# TreeSet

A red-black tree: a binary search tree that rotates and recolours after each change so no path is
more than twice as long as any other.

## What each method makes you see

| you call | you see, in order |
| --- | --- |
| `add(element)` | `Compare`×n → `Add`? → `Rotation`?/`Recolor`? |
| `remove(element)` | `Compare`×n → `Rotation`?/`Recolor`? → `Remove`? |
| `contains(element)` | `Compare`×n |
| `floor(element)` | `Compare`×n |

★ **Reads narrate here.** Unlike the ArrayList and the Trie, whose reads are silent, every
comparison this set makes is an event — so `contains` and `floor` animate the walk down the tree
without changing anything. Each `Compare` carries the element **at the node being visited**, not the
element you searched for.

`Add` always fires before any rebalancing: `TeachingTreeSet.add` links the new node and emits `Add`
first, then hands the tree to `RedBlackTree.insertFixup`, whose `Rotation`/`Recolor` callbacks fire
after. A duplicate add skips `Add` entirely, though: the walk still runs and still emits its
`Compare`s, but once it finds an equal element `add` returns `false` on the spot, without linking a
node or firing anything else. `remove` is the mirror image — the walk's `Compare`s locate the node,
then `RedBlackTree.deleteFromTree` rebalances (`Rotation`/`Recolor`, only if the removed node was
black), and only once that returns does `remove` emit `Remove`. Not every removal rebalances: on the
tree built by an ascending 10/20/30/40/50 insert, `remove(30)` needs no fixup at all, while
`remove(10)` does — which target you pick decides whether the guide's `remove` row even has
`Rotation`/`Recolor` to show. Removing an absent element skips `Remove` the same way a duplicate add
skips `Add`: the walk's `Compare`s still narrate the search, but once it runs off the tree without a
match `remove` returns `false` before any fixup or `Remove` fires.

★ **This tree narrates its failures.** `TeachingHashMap.remove` and `RadixTrie.remove` on an absent
key emit nothing at all, so a failed removal there is invisible. This set is the odd one out: a
duplicate `add` and an absent-element `remove` both still fire their `Compare`s, so it's the only
structure in the library where you can watch a write fail.

There is no fixed order between `Rotation` and `Recolor` themselves. `RedBlackTree.insertFixup` and
`.deleteFixup` each walk up the tree recolouring and rotating as they go, and which comes first (or
how many recolours happen) depends on the shape of the imbalance — a red uncle recolours three nodes
and keeps climbing with no rotation at all; a black uncle recolours and then rotates once. Read the
`?` on `Rotation`/`Recolor` as "may appear, zero or more times, in whatever order this particular
rebalance needs" — same convention `docs/guide/map.md` uses for the map's own red-black bins. The
`?` on `Add`/`Remove` is a plainer either/or: each fires at most once, or not at all.

## Read alongside

- `src/main/java/com/gimlism/translucent/treeset/core/TeachingTreeSet.java` — the algorithm
- `src/main/java/com/gimlism/translucent/treeset/events/` — the five event types
- `src/main/java/com/gimlism/translucent/substrate/rbtree/` — the shared red-black kernel

## Watch it

- `docs/viz/treeset.html` — open in a browser, no JDK needed
- demos 13–18 in the [README](../../README.md#treeset--a-red-black-tree-that-rotates-and-recolours-to-stay-balanced)
