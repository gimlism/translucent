# HashMap

Buckets holding chains of entries, with a chain converted to a red-black tree once it grows past the
treeify threshold.

## What each method makes you see

| you call | you see, in order |
| --- | --- |
| `put(key, value)` | `Put` always fires; `Collision`?, `Treeify`?, `Rotation`?, `Recolor`?, `Resize`? also appear, in an order that depends on the bucket — see below |
| `remove(key)` | `Untreeify`? or `Rotation`?/`Recolor`? → `Remove` |
| `get(key)` | nothing — reads are silent |

Where the rest land depends on the bucket a new key falls into. Landing in a plain chain: `Put`
fires first (the entry now exists), then `Collision`? if the bucket wasn't empty, then `Treeify`? if
the chain just crossed the threshold — and if it treeifies, `Rotation`/`Recolor` follow as the tree
is built. Landing in a bucket that's *already* a tree is the other way round: the red-black insert's
`Rotation`/`Recolor` fire first, rebalancing around the new node, and only then `Put` — there's no
`Collision` or `Treeify` here, since the bucket was treeified on some earlier put. Replacing an
existing key's value is quietest of all: only `Put` fires. `Resize` fires last of all, when the
whole table doubles, rehashing every entry.

For `remove`, a tree bin either shrinks back into a chain (`Untreeify`) or stays a tree and
rebalances (`Rotation`/`Recolor`) — never both — and either way that happens before `Remove`. A
plain chain bin only ever emits `Remove`.

`Rotation` and `Recolor` do not appear anywhere in `put`'s or `remove`'s source. They arrive from
the red-black sink callbacks the map installs, which is why this table is built by running the code
rather than reading it.

## Read alongside

- `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java` — the algorithm
- `src/main/java/com/gimlism/translucent/hashmap/events/` — the eight event types
- `src/main/java/com/gimlism/translucent/substrate/rbtree/` — the shared red-black kernel

## Watch it

- `docs/viz/map.html` — open in a browser, no JDK needed
- demos 7–12 in the [README](../../README.md#hashmap--buckets-chains-and-a-red-black-tree-when-a-chain-gets-long)
