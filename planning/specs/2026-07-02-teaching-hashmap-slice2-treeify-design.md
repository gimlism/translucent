# Teaching HashMap — Slice 2: Treeify + Red-Black Insert — Design

**Status:** Approved 2026-07-02
**Builds on:** Slice 1 (merged, PR #1) — chains, resize, event stream, snapshots,
consumers, fail-fast iterator. See
`docs/superpowers/specs/2026-07-01-teaching-hashmap-design.md` for the overall
project design and locked decisions.

## Goal

Add red-black tree bins to the teaching HashMap. When a chain grows long enough,
it treeifies into a real red-black tree; the event stream shows the tree
assembling and rebalancing (fine-grained `Treeify`/`Rotation`/`Recolor` events).
Tree bins are searched in O(log n), rendered in snapshots with colors, split
correctly on resize, and iterated in insertion order. Untreeify and red-black
deletion are deferred to Slice 3.

## Resolved decisions (Slice 2)

| Question | Decision |
|---|---|
| Tree bin structure | **Dual: red-black tree overlaid on the Slice-1 insertion-order `next` chain** (JDK-style). |
| Tree node ordering | Order by `key.hashCode()`, tie-break by a per-node insertion `seq` — **no `Comparable` requirement**. |
| Treeify event granularity | **Fine-grained** — emit `Treeify`, then the per-insertion `Rotation`/`Recolor` events as the tree assembles. |
| Untreeify / RB delete | **Deferred to Slice 3.** Small split-halves remain (possibly tiny) trees until then. |

## Architecture

The tree is an **overlay** on the existing chain, not a replacement:

- `table[i]` remains the **insertion-order head** (as in Slice 1), never the tree
  root.
- A bin is a tree iff `table[i] instanceof TreeNode`.
- The red-black **root** is found by climbing `parent` pointers from any node in
  the bin (e.g. from the head).
- Because `TreeNode extends Node` and the `next` thread is preserved in insertion
  order, the **Slice-1 `entrySet` iterator works on tree bins unchanged** (it
  follows `next`), and resize can walk `next` for both chain and tree bins.

### Components

**`TreeNode<K,V> extends Node<K,V>`** (`core` package)
- Adds: `TreeNode<K,V> parent, left, right`; `boolean red`; `long seq`. (A `prev`
  back-link for O(1) list unlinking is deferred to Slice 3's deletion.)
- Inherits `hash`, `key`, `value`, `next` from `Node`.
- `seq` is a monotonic insertion sequence (from a map-level `long nextSeq`
  counter), assigned when a node is created or converted, used only as the
  comparator tie-break.
- Helper methods: `root()` (climb `parent`), and the red-black primitives
  (rotate-left, rotate-right, insert-fixup) live here or in a small dedicated
  helper — kept in one focused unit so the RB logic is readable in isolation.

**Ordering comparator**
- `compare(aHash, aSeq, bHash, bSeq)`: primarily `Integer.compare(aHash, bHash)`,
  then `Long.compare(aSeq, bSeq)`. Total order, deterministic, no `Comparable`
  needed. For typical teaching keys (`Integer`, `String`) hashes are distinct, so
  the tie-break rarely triggers, but it guarantees a well-defined tree shape.

**`TeachingHashMap` changes** (`core` package)
- `long nextSeq` field; every `Node`/`TreeNode` creation stamps `seq` (via a
  small factory or at creation sites).
- `get`/`containsKey`/`put` gain a single branch: if the bin head is a
  `TreeNode`, do an O(log n) tree search (climb to root, BST-descend by the
  comparator) instead of the linear chain scan.
- Insert into a tree bin: append to the `next` thread (insertion order) **and**
  RB-insert into the tree with fixup.
- Treeify path (see Data flow).
- `resize` gains tree-bin handling (see Data flow).
- `snapshot()` gains a tree branch (see below).

**Snapshots** (`events` package types already exist from Slice 1)
- `snapshot()`: for a tree bin, walk the tree from the root to build
  `TreeSnapshot(TreeNodeSnapshot root)` with `color` and `left`/`right`; chain
  bins unchanged (`ChainSnapshot`). The contract spike confirmed consumers handle
  all three `BucketSnapshot` variants via the sealed switch.

**Events** (all types already defined in Slice 1's sealed hierarchy — only
emission is added)
- `Treeify(bucketIndex, after)` — emitted when a chain converts to a tree.
- `Rotation(bucketIndex, Direction, pivotKey, after)` — per rotation during fixup.
- `Recolor(bucketIndex, nodeKey, oldColor, newColor, after)` — per recolor.

## Data flow

### Treeify (chain → tree)
Trigger: a `put` grows a *chain* to `treeifyThreshold` (default 4) new-entry count.
1. If `capacity < minTreeifyCapacity` (default 8): **resize instead** of
   treeifying (mirrors the JDK) and return.
2. Otherwise: emit `Treeify(bucketIndex, …)`.
3. Convert each chain `Node` to a `TreeNode` (preserve the `next` thread and
   insertion `seq`).
4. Build the red-black tree by inserting each node in `next` order into an
   initially-empty tree; **each insertion runs standard RB insert-fixup**,
   emitting `Rotation`/`Recolor` events as rotations and recolorings occur.
5. `table[i]` remains the insertion-order head; the resulting root is reachable
   via `parent` climb.

### Put into an existing tree bin
1. Tree-search for the key (climb to root, BST-descend). If found, replace value
   → emit `Put(newEntry=false, previousValue)`; done.
2. If absent: create a `TreeNode`, append to the `next` thread (tail, insertion
   order), RB-insert into the tree with fixup (emitting `Rotation`/`Recolor`),
   `size++`, `modCount++`, emit `Put(newEntry=true)`. **No `Collision` is emitted
   for tree-bin inserts** — `Collision` carries chain-length fields and remains a
   chain-growth concept; the burst of `Rotation`/`Recolor` plus the `Put`
   snapshot already convey the tree insertion. Then the usual `size > threshold`
   resize check.

### Resize with tree bins
- Chain bins: split into lo/hi exactly as Slice 1 (walk `next`, tail-append).
- Tree bins: walk `next`, partition nodes into lo (index `j`) and hi
  (index `j + oldCap`) by `(hash & oldCap) == 0`. For each **non-empty** half,
  rebuild the RB tree over that half. This rebuild is **silent**
  (`TreeEventSink.NONE`): during resize the map's `table` is mid-swap, so
  per-rotation snapshots would render the wrong table. The `Resize`
  before/after snapshots convey the split. (No `Treeify` either — it is a
  tree→tree split, not a chain→tree conversion.)
  Small halves remain trees (untreeify deferred to Slice 3).
- `Resize(before, after)` is emitted as in Slice 1, bracketing the whole rehash.

### Iteration
Unchanged. `entrySet().iterator()` follows `next` (insertion order); tree bins
are threaded, so no tree-specific traversal is needed for iteration.

## Testing strategy (TDD)

- **Treeify trigger:** a chain reaching `treeifyThreshold` at
  `capacity ≥ minTreeifyCapacity` emits `Treeify` and produces a tree bin
  (`table[i] instanceof TreeNode`); below `minTreeifyCapacity` it resizes instead.
- **Event sequence:** treeify emits `Treeify` then a non-empty ordered burst of
  `Rotation`/`Recolor` (assert via `RecordingListener`); the resulting tree's
  root is black (RB invariant) and node colors in the snapshot are consistent.
- **Red-black invariants:** after treeify and after tree inserts, assert the
  snapshot tree satisfies RB properties (root black, no red-red parent/child,
  equal black-height on all root-null paths). A test helper verifies these on a
  `TreeNodeSnapshot`.
- **Correctness under trees:** put/get/containsKey round-trips for many colliding
  keys (forcing a large tree); ordering by hash is respected in the tree shape;
  duplicate-key put replaces value without growing the tree.
- **Iteration:** `entrySet()` still yields all entries in insertion order across a
  treeified bin.
- **Resize of a tree bin:** treeify a bin, force a resize, assert entries are
  preserved, both halves are correctly bucketed, and each non-empty half is a
  valid RB tree.
- **Snapshot:** a tree bin renders as `TreeSnapshot` with correct colors and
  structure; chains still render as `ChainSnapshot`.

## Out of scope (Slice 3)

- Untreeify (tree → chain when small), both removal-driven and retrofitted into
  resize-split.
- Red-black **deletion** (`remove` from a tree bin with delete-fixup).
- Until then, `remove` of a key that lives in a **tree bin throws
  `UnsupportedOperationException`** with a clear "added in Slice 3" message —
  explicit and safe, rather than silently corrupting the tree via the Slice-1
  linear unlink. (`remove` on chain bins is unchanged; `Iterator.remove()` on an
  entry in a tree bin therefore also throws until Slice 3.)

## Notes on faithfulness (for the future "compare to JDK" mode)

- Real `HashMap` stores the tree root at `table[i]` and uses `moveRootToFront`;
  we keep the insertion-order head at `table[i]` and climb to the root instead —
  simpler to explain, same asymptotics.
- Real `HashMap` untreeifies small bins on split (threshold 6) and uses a
  hash→`Comparable`→identity tie-break; we defer untreeify to Slice 3 and use a
  simpler `hash`→`seq` order. These simplifications are intentional and
  documented.
