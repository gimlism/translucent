# HashMap

Buckets holding chains of entries, with a chain converted to a red-black tree once it grows past the
treeify threshold.

## What each method makes you see

| you call | you see, in order |
| --- | --- |
| `put(key, value)` | `Collision`? → `Put` → `Treeify`? → `Rotation`? → `Recolor`? → `Resize`? |
| `remove(key)` | `Remove` → `Untreeify`? → `Rotation`? → `Recolor`? |
| `get(key)` | nothing — reads are silent |

`Collision` fires when the bucket already holds something — with a good hash it is rare, which is
why the demo uses keys that collide on purpose. `Treeify` fires when one chain gets long enough to
become a tree, and the `Rotation`/`Recolor` that follow are the red-black tree balancing itself.
`Resize` fires when the whole table doubles, rehashing every entry.

`Rotation` and `Recolor` do not appear anywhere in `put`'s source. They arrive from the red-black
sink callbacks the map installs, which is why this table is built by running the code rather than
reading it.

## Read alongside

- `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java` — the algorithm
- `src/main/java/com/gimlism/translucent/hashmap/events/` — the eight event types
- `src/main/java/com/gimlism/translucent/substrate/rbtree/` — the shared red-black kernel

## Watch it

- `docs/viz/map.html` — open in a browser, no JDK needed
- demos 7–12 in the [README](../../README.md#hashmap--buckets-chains-and-a-red-black-tree-when-a-chain-gets-long)
