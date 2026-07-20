# HashMap → substrate/rbtree migration

**Date:** 2026-07-20
**Status:** Design approved, ready for planning
**Slice type:** Refactor / consolidation (no new user-facing surface)

## Goal

Retire the ~180 lines of CLRS red-black machinery duplicated in
`hashmap/core/TreeNode.java` by making `TeachingHashMap` the **second
inheritance-consumer** of the shared `substrate/rbtree/RedBlackTree` kernel.

This is the **rule-of-three confirm**. The `TreeSet` slice extracted the kernel
forward (`RbNode`, `RedBlackTree.insertFixup/deleteFromTree`, `RbEventSink`,
`Color`, `Direction`) and became its first consumer, deliberately leaving the
HashMap's older, thrice-reviewed `TreeNode` copy untouched to keep it out of the
blast radius. With the kernel now proven across a distinct structure (an
element-ordered `NavigableSet`), this slice folds the HashMap's treeified-bin RB
tree onto the same kernel.

Net effect: one red-black implementation in the codebase, consumed by both the
map's treeified bins and the set, via the same F-bounded inheritance mechanism.

## Background: why the HashMap was left duplicated

`TreeSet`'s `SetNode<E> extends RbNode<SetNode<E>>` cleanly, because a set node
has no other base class. The HashMap cannot do this directly:
`TreeNode<K,V> extends Node<K,V>`, and that base is **load-bearing**:

- Bins are `Node<K,V>[] table`; a treeified bin's head is stored and checked as a
  `Node` (`table[index] instanceof TreeNode`).
- The `next` thread (singly-linked in insertion order, with a `prev` back-pointer)
  is walked by iteration, resize, and `splitTreeBin`, and lives on the node base.
- `Node` is the map's `Map.Entry` implementation.

A `TreeNode` is therefore intrusive in **two** structures at once — a linked list
*and* a red-black tree — so it wants two base classes. Java's single inheritance
forbids `TreeNode extends Node` *and* `extends RbNode`. The JDK hits the same wall
and resolves it by *not* sharing (its `balanceInsertion`/`balanceDeletion` are
copy-pasted inside `TreeNode`). This slice does better than the JDK precisely
because translucent is a teaching codebase willing to restructure `Node`.

## Chosen approach: `Node` becomes an interface

Convert `Node<K,V>` from a class to an interface. The linked-list role is then
satisfied structurally (via the interface) and the tree role via inheritance from
the substrate base, resolving the single-inheritance conflict.

Two other approaches were considered and rejected:

- **Accessor-based kernel** — rewrite `RedBlackTree` to operate through an
  `RbLinks<N>` accessor instead of `N extends RbNode<N>`. Rejected: it rewrites a
  clean, shipped, thrice-reviewed F-bounded kernel, churns the `TreeSet` that
  consumes it, and turns `p.right = r.left` into `links.setRight(p, links.left(r))`
  — gutting the teaching readability that is this codebase's whole point.
- **Minimal (enum-only)** — dedup only `Color`/`Direction`, leave the RB algorithm
  duplicated. Rejected: does not retire the duplicate `TreeNode`, so it fails the
  goal.

## Design

### §1 — Node hierarchy

`Node<K,V>` (class → **interface**):

```
interface Node<K, V> extends Map.Entry<K, V> {
    int hash();
    Node<K, V> next();
    void setNext(Node<K, V> next);
    // Map.Entry supplies getKey() / getValue() / setValue(V)
}
```

- **`ChainNode<K,V> implements Node<K,V>`** — the plain chained entry. Holds
  `final int hash`, `final K key`, `V value`, `Node<K,V> next`. Body is today's
  `Node` class body.
- **`TreeNode<K,V> extends RbNode<TreeNode<K,V>> implements Node<K,V>`** — a
  treeified-bin node. Inherits `parent/left/right/red` from `RbNode` (public,
  self-typed `TreeNode<K,V>` → **zero casts** on `.left`/`.right`/`.parent`).
  Stores its own map payload: `final int hash`, `final K key`, `V value`,
  `Node<K,V> next`, `TreeNode<K,V> prev`, `final long seq`.
- **`Entries`** — package-private helper class holding the shared `Map.Entry`
  contract methods `equals(Map.Entry, Object)`, `hashCode(Map.Entry)`,
  `toString(Map.Entry)`. `ChainNode` and `TreeNode` can no longer share a class
  base, and these Object-derived methods cannot be interface `default`s, so both
  node types delegate to `Entries` to avoid a second copy.

The field-read semantics from callers stay stable: `RbNode`'s `parent/left/right/
red` are `public`, so existing `t.left`, `t.red`, `t.parent` reads (in code and
tests) still compile unchanged. Only field reads of the payload that move to
interface-method form (`e.hash` → `e.hash()`, `e.next` → `e.next()`, `e.key` →
`e.getKey()`, `e.value` → `e.getValue()`) change, all inside `hashmap.core`.

### §2 — What TreeNode keeps vs deletes

**Keeps (map-specific descent — no substrate equivalent):**

- `cmp(h1, s1, h2, s2)` — total order for tree insertion: hash, then insertion
  `seq`.
- `find(p, hash, key)` — hash-descent that searches both subtrees on a hash tie
  (a lookup key has no `seq` to disambiguate).
- `build(first, sink)` — treeify from the already-threaded `next` list.
- `insert(root, x, sink)` — BST descent by `cmp`, links `x` as a red leaf, then
  **calls `RedBlackTree.insertFixup(root, x, sink)`** (was: local `insertFixup`).
  The `root == null` first-node shortcut (link as black root, no fixup, no event)
  is retained.
- `root()` — climb to the tree root.
- `deleteFromTree(root, z, sink)` — retained as a **thin static wrapper** that
  delegates to `RedBlackTree.deleteFromTree(root, z, sink)`. Kept as a method (not
  inlined at call sites) so the map's tree operations stay discoverable in one
  place and existing test call sites survive.

**Deletes (now supplied by the substrate kernel):**

- `rotateLeft` / `rotateRight`
- private `insertFixup`
- private `deleteFixup`, `transplant`, `minimum`
- `setColor`
- the local `boolean red` field (now inherited from `RbNode`)

### §3 — Sink adapter (retires `TreeEventSink`)

`hashmap/core/TreeEventSink.java` is the map's local copy of `RbEventSink`'s shape
(`rotated(Direction, Object key)` / `recolored(Object key, Color, Color)`).
**Delete it.**

`TeachingHashMap.sinkFor(i)` builds an `RbEventSink<TreeNode<K,V>>` directly:

- `rotated(Direction dir, TreeNode<K,V> pivot)` → emit the map's `Rotation`
  `MapEvent`, reading `pivot.getKey()`.
- `recolored(TreeNode<K,V> node, Color old, Color neu)` → emit the map's `Recolor`
  `MapEvent`, reading `node.getKey()`.

The adapter **bridges** `substrate.rbtree.{Color,Direction}` → `hashmap.events.
{Color,Direction}` with two small 2-value switches. The map's **public event
vocabulary** (`hashmap.events.Color`, `Direction`, `Rotation`, `Recolor`) is
**unchanged** — enum dedup is deliberately **deferred** (its blast radius reaches
the formatter/serializer/viz layer and is out of scope for this slice).

Silent-rebuild call sites (`splitTreeBin`, `treeify` during resize) that currently
pass `TreeEventSink.NONE` switch to `RbEventSink.none()`.

### §4 — Blast radius & equivalence

- **Confined to `hashmap.core`** (production) and its test package. Verified: `Node`,
  `TreeNode`, and `TreeEventSink` are all package-private; every reference outside
  `hashmap.core` is to the immutable snapshot DTOs (`TreeNodeSnapshot`,
  `TreeSnapshot`), never to a live node.
- **`substrate/rbtree` and all of `treeset` stay byte-unchanged.** The kernel already
  exposes exactly the two public entry points the map needs (`insertFixup`,
  `deleteFromTree`); everything else it needs is subsumed inside them.
- **Behavioral equivalence** is pinned by the existing map tree tests
  (`TreeInsertTest`, `TreeDeleteTest`, `TreeRemoveTest`, `TreeBuildTest`,
  `TreeFindTest`, `TreeSnapshotTest`, `RedBlackInvariants`, `TreeResizeTest`,
  `TreeSplitUntreeifyTest`). The map's current `insertFixup`/`deleteFromTree` bodies
  are structurally identical to the substrate's, so any drift introduced by the swap
  fails these tests. This is the primary regression net, and because the deleted code
  is the historically bug-prone RB-invariant maintenance, replacing it with the
  reviewed substrate version is **risk reduction**, not merely tidiness.

### §5 — Test migration

- **`TreeRotationTest`** calls `TreeNode.rotateLeft`/`rotateRight` directly. Those
  primitives move into the substrate as **package-private** (unreachable from
  `hashmap.core`). Rebase this test onto the **observable** path: drive a
  `TeachingHashMap` / `TreeNode.insert` sequence that forces a left and a right
  rotation, then assert the resulting `parent/left/right` shape **and** the emitted
  `Rotation` events (direction + pivot key). This preserves the test's teeth — it
  still pins rotation behavior — while testing it through the public surface rather
  than a now-private primitive.
- **`TreeNode.insert` / `find` / `build` / `deleteFromTree` call sites** (in
  `TreeInsertTest`, `TreeFindTest`, `TreeBuildTest`, `TreeDeleteTest`) survive
  unchanged: these statics are retained. Where a test passes `TreeEventSink.NONE`,
  it switches to `RbEventSink.none()`.
- **Field-read tests** (`TreeSnapshotTest`, `TreeNodeTest`, `TreeListLinkTest`,
  `TreeResizeTest`, `RedBlackInvariants`) reading `.red/.left/.right/.parent`
  compile unchanged (public inherited `RbNode` fields). `.next`/`.prev` reads become
  method/field forms per §1.

### §6 — New tests

- A structural pin asserting a `TreeNode` **is a** `RbNode` and that its
  `red`/`left`/`right`/`parent` fields are the inherited substrate ones (guards the
  migration from silently regrowing local copies).
- A sink-adapter test: force a rotation and a recolor, assert the map emits
  `Rotation`/`Recolor` `MapEvent`s with correctly **bridged** `Direction`/`Color`
  (guards the substrate→map enum bridge in both directions).

## Out of scope (deferred)

- **`Color`/`Direction` enum dedup** — the map's copies are woven into its public
  event vocabulary; deduping them ripples into the formatter/serializer/viz layer.
  A separate slice.
- Any change to `substrate/rbtree` or `treeset`.
- The long-standing backlog item: a core test pinning `Compare = visited node` on a
  real walk (a `TreeSet` concern, unrelated to this slice).

## Success criteria

- `hashmap/core/TreeNode.java` contains **no** rotation/fixup/transplant/minimum/
  setColor code and **no** local `red` field; it holds only map-specific descent
  (`cmp`/`find`/`build`/`insert`/`root`) plus the thin `deleteFromTree` wrapper.
- `hashmap/core/TreeEventSink.java` is deleted; the map drives the tree through
  `RbEventSink<TreeNode<K,V>>`.
- `substrate/rbtree` and `treeset` are byte-identical to their pre-slice state.
- The full suite passes (476/476 baseline + the new §6 tests), with the rebased
  `TreeRotationTest` still pinning rotation behavior through the public path.
