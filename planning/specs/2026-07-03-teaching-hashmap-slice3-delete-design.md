# Teaching HashMap — Slice 3: Red-Black Deletion + Untreeify — Design

**Status:** Approved 2026-07-03
**Builds on:** Slice 1 (merged, PR #1) and Slice 2 (merged, PR #2) — chains,
resize, event stream, snapshots, consumers, fail-fast iterator, and red-black
tree bins with treeify. See the Slice 1 and Slice 2 design specs for the overall
design and locked decisions.

## Goal

Complete the data structure: red-black **deletion** from tree bins, and
**untreeify** (tree → chain) when a bin gets small. Deleting from a tree bin now
works (the Slice-2 `UnsupportedOperationException` guard is removed); the event
stream shows the tree rebalancing on removal (`Rotation`/`Recolor`) or collapsing
back to a chain (`Untreeify`). This is the last slice of the data structure; the
visualizer and the "compare to real JDK" mode remain separate future work.

## Resolved decisions (Slice 3)

| Question | Decision |
|---|---|
| Delete event granularity | **Fine-grained** — `Rotation`/`Recolor` during delete-fixup, consistent with insert/treeify. |
| Successor-replacement signal | **No new event type.** Conveyed by the fixup events + `Remove`'s before/after snapshots. The sealed `MapEvent` hierarchy is unchanged. |
| `prev` back-link on `TreeNode` | **Added** (faithful to `java.util.HashMap`) — tree bins become a doubly-linked list threaded through the RB tree, giving O(1) unlink on delete. |
| Untreeify triggers | **Both** removal-driven (after a delete shrinks a bin) **and** split-driven (a resize-split half that is small becomes a chain). |
| Delete vs untreeify path | **JDK shortcut:** unlink from the list first, then if the remaining bin is ≤ `untreeifyThreshold` untreeify (skipping RB-delete); otherwise RB-delete with fixup. A delete emits *either* `Untreeify` *or* `Rotation`/`Recolor`, never both. |

## Architecture

### `TreeNode` gains `prev`
A tree bin is a **doubly-linked list** (`next`/`prev`, insertion order) overlaid
on the red-black tree (`parent`/`left`/`right`). `prev` is a `TreeNode` field
(plain-`Node` chains keep forward-only `next`, no `prev`). Every site that
currently maintains `next` must also maintain `prev`:
- `treeifyBin` — when threading the converted `TreeNode`s.
- `splitTreeBin` — when re-threading each half.
- the tree-insert branch of `put` — when appending the new node to the tail.

### Red-black deletion (`TreeNode`, reported via `TreeEventSink`)
Pointer-based, preserving node identity (no value-copy — that would corrupt
`seq` and the threading). Follows the standard CLRS/JDK approach: when the target
has two children, splice in its in-order successor by relinking pointers, then
run **delete-fixup** (rotations + recolourings, emitting `Rotation`/`Recolor`) to
restore the red-black invariants. Returns the new root, or `null` if the bin is
now empty. The delete primitives live in `TreeNode.java` alongside insert, so the
red-black logic stays in one focused unit.

### Untreeify (tree → plain-`Node` chain)
Walk the remaining `next` thread (insertion order), create plain `Node`s
mirroring each `TreeNode`'s hash/key/value, link them into a chain, and set
`table[i]` to the chain head. Because `table[i]` is then a plain `Node`,
`isTreeBin` correctly reports `false`. Untreeify is used by two callers (below).

## Data flow

### `TeachingHashMap.remove(key)` — tree-bin path (guard removed)
1. `find` the node `p` for `key` (climb to root, tree search). If absent, return
   `null` (unchanged no-op).
2. `size--`, `modCount++` — **before** the structural work, so any balancing
   events carry the correct post-delete size (the consistency fix applied to
   insert in Slice 2's follow-up).
3. **Unlink `p` from the `prev`/`next` list** — O(1) via `prev`. The list now
   holds the surviving nodes in insertion order.
4. Count the surviving nodes:
   - **`remaining == 0`:** `table[i] = null` (empty bin).
   - **`remaining ≤ untreeifyThreshold` (default 2):** **untreeify** — build a
     plain-`Node` chain from the surviving list, set `table[i]` to the head, and
     emit `Untreeify(bucketIndex, after)`. No RB-delete is performed (the tree is
     discarded).
   - **otherwise:** perform the **RB-tree delete** of `p` (delete-fixup emitting
     `Rotation`/`Recolor`); `table[i]` remains the surviving insertion-order head.
5. Emit `Remove(key, oldValue, bucketIndex, after)`.

**Event order for one tree-bin delete:** `(Untreeify | Rotation/Recolor…)` →
`Remove`. A delete never emits both `Untreeify` and balancing events — the tree
only rebalances when it stays a tree.

`Iterator.remove()` on a tree-bin entry now genuinely deletes (it delegates to
`remove(key)`), which previously threw.

### Split-driven untreeify (in `splitTreeBin`)
When `resize` splits a tree bin into lo/hi halves, a half with
`≤ untreeifyThreshold` nodes becomes a **plain-`Node` chain** instead of a tiny
tree (fixing Slice 2's "small halves stay trees"). This happens **silently**
(`TreeEventSink.NONE`, and no `Untreeify` event) — consistent with the rest of
resize-split, because the map's `table` is mid-swap during resize; the `Resize`
before/after snapshots convey the change.

## Testing strategy (TDD)

- **RB-delete invariants (adversarial):** build large colliding-key trees and
  delete keys in ascending, descending, and mixed orders, asserting after *every*
  delete that the bin's snapshot tree satisfies the red-black properties (root
  black, no red-red, equal black-heights) and that the remaining key set is
  exactly correct. This is the delete analogue of the Slice-2 insert-invariant
  test and the primary correctness gate.
- **Delete event size-consistency:** balancing events emitted during a tree-bin
  delete carry a snapshot whose `size` matches the post-delete bin contents.
- **Removal-driven untreeify:** shrinking a tree bin to ≤ `untreeifyThreshold`
  turns it into a chain (`isTreeBin` false), emits exactly one `Untreeify` (and
  no `Rotation`/`Recolor`), preserves all remaining entries in insertion order.
- **RB-delete path (no untreeify):** deleting from a larger tree keeps it a tree
  (`isTreeBin` true), emits `Rotation`/`Recolor` as needed, no `Untreeify`.
- **Delete to empty:** removing the last entry of a tree bin leaves `table[i]`
  null and the map size correct.
- **Split-driven untreeify:** resizing a tree bin whose halves are small yields
  plain-chain bins (`isTreeBin` false), all entries preserved, silently (no
  `Untreeify` event during resize; `Resize` still emitted).
- **`Iterator.remove` on a tree entry** deletes the entry (no longer throws);
  iteration afterward still yields insertion order.
- **List consistency:** after treeify / tree-insert / split, the `prev`/`next`
  doubly-linked list is internally consistent (each node's `next.prev == node`).

## Out of scope

- The "compare to real JDK HashMap" mode (documented divergences remain: honest
  node-count untreeify check rather than the JDK's structural proxy; simplified
  `hash`→`seq` ordering; teaching-tuned thresholds).
- The graphical visualizer / recording-playback UI.

With this slice, the teaching HashMap is a complete `Map` with chains, resize,
red-black tree bins, treeify **and** untreeify, and full deletion — every
mutation observable through the event stream.
