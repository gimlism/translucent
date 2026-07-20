# HashMap → substrate/rbtree Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retire the ~180 lines of red-black machinery duplicated in `hashmap/core/TreeNode.java` by making `TeachingHashMap` a second inheritance-consumer of the shared `substrate/rbtree` kernel.

**Architecture:** Convert `Node<K,V>` from a class to an interface so `TreeNode` can `extends RbNode<TreeNode<K,V>>` (gaining `parent/left/right/red` and the shared rotate/fixup/delete algorithm) while still satisfying the linked-list/`Map.Entry` role structurally. `TreeNode` keeps only map-specific descent (`cmp`/`find`/`build`/`insert`/`root`); it delegates rebalancing to `RedBlackTree.insertFixup`/`deleteFromTree`. The map drives the tree through an `RbEventSink<TreeNode>` adapter that bridges the substrate's `Color`/`Direction` enums to the map's own.

**Tech Stack:** Java 21, Maven, JUnit 5.

## Global Constraints

- Java 21; `maven.compiler.release=21`. Build/test with `mvn` — Maven is the source of truth (Eclipse/LSP may show stale phantom errors).
- **`substrate/rbtree` and all of `treeset` must remain byte-identical.** This slice touches only `hashmap.core` (production) and `hashmap.core` tests. Verify with `git diff --stat` before every commit.
- The map's **public event vocabulary is unchanged**: `hashmap.events.Color`, `Direction`, `Rotation`, `Recolor` keep their current shape. Enum dedup is explicitly deferred (out of scope).
- `Node`, `TreeNode`, `ChainNode`, `Entries`, and the sink adapter are all **package-private** (no `public`) — they are internal to `hashmap.core`.
- Every mutation the kernel applies is committed to the node BEFORE the sink fires (the "snapshot-before-settled" discipline) — this is already guaranteed by the substrate kernel; do not reintroduce a local copy.
- Substrate kernel API available: `RedBlackTree.insertFixup(N root, N x, RbEventSink<N> sink)` and `RedBlackTree.deleteFromTree(N root, N z, RbEventSink<N> sink)`, both `public static <N extends RbNode<N>>`; `RbEventSink.none()`; `RbNode<N>` fields `public N parent, left, right; public boolean red`.
- Commit message trailer for every commit:
  ```
  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm
  ```

---

## File Structure

**Create:**
- `hashmap/core/ChainNode.java` — the plain chained `Map.Entry` (today's `Node` class body).
- `hashmap/core/Entries.java` — shared `Map.Entry` `equals`/`hashCode`/`toString` helper.

**Modify:**
- `hashmap/core/Node.java` — class → interface (`extends Map.Entry`, adds `hash()`/`next()`/`setNext()`).
- `hashmap/core/TreeNode.java` — `extends RbNode<TreeNode<K,V>> implements Node<K,V>`; delete duplicated RB code + local `red`; `insert` calls the kernel; `deleteFromTree` becomes a thin wrapper.
- `hashmap/core/TeachingHashMap.java` — `sinkFor` returns `RbEventSink<TreeNode<K,V>>` with the enum bridge; payload field reads → interface calls; `new Node<>` → `new ChainNode<>`; `TreeEventSink.NONE` → `RbEventSink.none()`.

**Delete:**
- `hashmap/core/TreeEventSink.java` — replaced by `RbEventSink<TreeNode<K,V>>`.

**Test changes:**
- Delete `hashmap/core/TreeRotationTest.java` (isolated-primitive test; the primitive now lives in and is tested by the substrate — `substrate/rbtree/RedBlackTreeInsertTest.insertEmitsRecolorAndRotationEvents` asserts `rot:LEFT`).
- Repoint `TreeEventSink.NONE` → `RbEventSink.none()` in any test that constructs a silent sink.
- Add `hashmap/core/TreeKernelMigrationTest.java` (Task 2) — structural pin + sink-bridge integration pin.

---

## Task 1: Migrate the node hierarchy onto the substrate kernel

This is one atomic structural change: `Node` cannot be an interface while `TreeNode extends Node` (class) and `TeachingHashMap` reads node fields — all three must change together to compile. The gate is **the existing behavioral suite staying green** — those tests (`TreeInsertTest`, `TreeDeleteTest`, `TreeRemoveTest`, `TreeBuildTest`, `TreeFindTest`, `TreeSnapshotTest`, `TreeResizeTest`, `TreeSplitUntreeifyTest`, `RedBlackInvariants`, `MapLevelInvariantsTest`, `EventEmissionTest`) are the specification for this refactor.

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/core/ChainNode.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/core/Entries.java`
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/Node.java`
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TreeNode.java`
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`
- Delete: `src/main/java/com/gimlism/translucent/hashmap/core/TreeEventSink.java`
- Delete: `src/test/java/com/gimlism/translucent/hashmap/core/TreeRotationTest.java`
- Test (repoint sinks): `src/test/java/com/gimlism/translucent/hashmap/core/TreeInsertTest.java`, `TreeBuildTest.java`, `TreeDeleteTest.java`, `TreeFindTest.java` (any that reference `TreeEventSink`)

**Interfaces:**
- Consumes (from substrate, already present): `RbNode<N>`, `RedBlackTree.insertFixup`/`deleteFromTree`, `RbEventSink<N>`, `substrate.rbtree.Color`, `substrate.rbtree.Direction`.
- Produces (for the rest of `hashmap.core`):
  - `interface Node<K,V> extends Map.Entry<K,V>` with `int hash()`, `Node<K,V> next()`, `void setNext(Node<K,V> next)`.
  - `class ChainNode<K,V> implements Node<K,V>` — ctor `ChainNode(int hash, K key, V value, Node<K,V> next)`.
  - `class TreeNode<K,V> extends RbNode<TreeNode<K,V>> implements Node<K,V>` — ctor unchanged: `TreeNode(int hash, K key, V value, Node<K,V> next, long seq)`; retains statics `cmp`, `find`, `build`, `insert`, and `deleteFromTree` — all now taking `RbEventSink<TreeNode<K,V>>`; retains instance `root()`; fields `hash`, `key`, `value`, `next`, `prev`, `seq` (+ inherited `parent`/`left`/`right`/`red`).
  - `TeachingHashMap.sinkFor(int i)` returns `RbEventSink<TreeNode<K,V>>`.

- [ ] **Step 1: Rewrite `Node.java` as an interface**

Replace the entire file with:

```java
package com.gimlism.translucent.hashmap.core;

import java.util.Map;

/**
 * A single map entry, in either a plain chain ({@link ChainNode}) or a treeified
 * bin ({@link TreeNode}). The {@code next} thread links entries in insertion order
 * for iteration and resize; {@code hash} is the raw key hashCode (no spreading).
 */
interface Node<K, V> extends Map.Entry<K, V> {
    int hash();

    Node<K, V> next();

    void setNext(Node<K, V> next);
}
```

- [ ] **Step 2: Create `Entries.java` (shared Map.Entry contract)**

```java
package com.gimlism.translucent.hashmap.core;

import java.util.Map;
import java.util.Objects;

/**
 * Shared {@link Map.Entry} {@code equals}/{@code hashCode}/{@code toString} for the
 * node types. {@link ChainNode} and {@link TreeNode} no longer share a class base
 * and these Object-derived methods cannot be interface {@code default}s, so both
 * delegate here to avoid a second copy.
 */
final class Entries {
    private Entries() { }

    static boolean equals(Map.Entry<?, ?> self, Object o) {
        if (!(o instanceof Map.Entry<?, ?> e)) return false;
        return Objects.equals(self.getKey(), e.getKey())
                && Objects.equals(self.getValue(), e.getValue());
    }

    static int hashCode(Map.Entry<?, ?> self) {
        return Objects.hashCode(self.getKey()) ^ Objects.hashCode(self.getValue());
    }

    static String toString(Map.Entry<?, ?> self) {
        return self.getKey() + "=" + self.getValue();
    }
}
```

- [ ] **Step 3: Create `ChainNode.java` (the plain entry)**

```java
package com.gimlism.translucent.hashmap.core;

/** A single chained entry. {@code hash} is the raw key hashCode (no spreading). */
class ChainNode<K, V> implements Node<K, V> {
    final int hash;
    final K key;
    V value;
    Node<K, V> next;

    ChainNode(int hash, K key, V value, Node<K, V> next) {
        this.hash = hash;
        this.key = key;
        this.value = value;
        this.next = next;
    }

    @Override public int hash() { return hash; }
    @Override public K getKey() { return key; }
    @Override public V getValue() { return value; }
    @Override public Node<K, V> next() { return next; }
    @Override public void setNext(Node<K, V> next) { this.next = next; }

    @Override
    public V setValue(V newValue) {
        V old = value;
        value = newValue;
        return old;
    }

    @Override public boolean equals(Object o) { return Entries.equals(this, o); }
    @Override public int hashCode() { return Entries.hashCode(this); }
    @Override public String toString() { return Entries.toString(this); }
}
```

- [ ] **Step 4: Rewrite `TreeNode.java` onto the kernel**

Replace the entire file with (note: all duplicated RB code — `rotateLeft`/`rotateRight`/`insertFixup`/`deleteFixup`/`transplant`/`minimum`/`setColor` — and the local `red` field are gone; `insert` calls `RedBlackTree.insertFixup`, `deleteFromTree` wraps `RedBlackTree.deleteFromTree`):

```java
package com.gimlism.translucent.hashmap.core;

import java.util.Objects;

import com.gimlism.translucent.substrate.rbtree.RbEventSink;
import com.gimlism.translucent.substrate.rbtree.RbNode;
import com.gimlism.translucent.substrate.rbtree.RedBlackTree;

/**
 * A red-black tree node for a treeified bin. Extends the shared {@link RbNode}
 * kernel base (contributing {@code parent/left/right/red} and, via
 * {@link RedBlackTree}, the rotate/fixup/delete algorithm) and implements
 * {@link Node} so it keeps the insertion-order {@code next} thread used for
 * iteration and resize. Ordering across nodes is by hash, then by the monotonic
 * insertion {@code seq}.
 */
class TreeNode<K, V> extends RbNode<TreeNode<K, V>> implements Node<K, V> {
    final int hash;
    final K key;
    V value;
    Node<K, V> next;
    TreeNode<K, V> prev;
    final long seq;

    TreeNode(int hash, K key, V value, Node<K, V> next, long seq) {
        this.hash = hash;
        this.key = key;
        this.value = value;
        this.next = next;
        this.seq = seq;
    }

    @Override public int hash() { return hash; }
    @Override public K getKey() { return key; }
    @Override public V getValue() { return value; }
    @Override public Node<K, V> next() { return next; }
    @Override public void setNext(Node<K, V> next) { this.next = next; }

    @Override
    public V setValue(V newValue) {
        V old = value;
        value = newValue;
        return old;
    }

    @Override public boolean equals(Object o) { return Entries.equals(this, o); }
    @Override public int hashCode() { return Entries.hashCode(this); }
    @Override public String toString() { return Entries.toString(this); }

    /** Climb to the tree root from this node. */
    TreeNode<K, V> root() {
        TreeNode<K, V> r = this;
        while (r.parent != null) r = r.parent;
        return r;
    }

    /** Total order used for tree INSERTION: hash, then insertion seq. */
    static int cmp(int h1, long s1, int h2, long s2) {
        int c = Integer.compare(h1, h2);
        return c != 0 ? c : Long.compare(s1, s2);
    }

    /**
     * BST-insert {@code x} (ordered by hash then seq), then restore red-black
     * invariants via the shared kernel. Returns the new root. {@code x} must be a
     * fresh node not already present in the tree.
     */
    static <K, V> TreeNode<K, V> insert(
            TreeNode<K, V> root, TreeNode<K, V> x, RbEventSink<TreeNode<K, V>> sink) {
        x.left = null;
        x.right = null;
        if (root == null) {
            x.parent = null;
            x.red = false; // first node is the black root
            return x;
        }
        TreeNode<K, V> p = root;
        TreeNode<K, V> parent;
        int dir;
        do {
            parent = p;
            dir = cmp(x.hash, x.seq, p.hash, p.seq);
            p = dir < 0 ? p.left : p.right;
        } while (p != null);
        x.parent = parent;
        if (dir < 0) parent.left = x; else parent.right = x;
        x.red = true;
        return RedBlackTree.insertFixup(root, x, sink);
    }

    /**
     * Find the node for {@code key} (with the given hash). Descends by hash; on a
     * hash tie with an unequal key, searches both subtrees (a lookup key has no
     * seq to disambiguate). O(log n) when hashes are distinct.
     */
    static <K, V> TreeNode<K, V> find(TreeNode<K, V> p, int hash, Object key) {
        while (p != null) {
            if (hash < p.hash) {
                p = p.left;
            } else if (hash > p.hash) {
                p = p.right;
            } else if (Objects.equals(p.key, key)) {
                return p;
            } else {
                TreeNode<K, V> r = find(p.right, hash, key);
                if (r != null) return r;
                p = p.left;
            }
        }
        return null;
    }

    /**
     * Build a red-black tree over an already-threaded list of tree nodes (linked
     * by {@code next} in insertion order), inserting each in turn. The
     * {@code next} thread is left intact for iteration. Returns the root.
     */
    static <K, V> TreeNode<K, V> build(TreeNode<K, V> first, RbEventSink<TreeNode<K, V>> sink) {
        TreeNode<K, V> root = null;
        for (TreeNode<K, V> x = first; x != null; ) {
            @SuppressWarnings("unchecked")
            TreeNode<K, V> next = (TreeNode<K, V>) x.next;
            root = insert(root, x, sink);
            x = next;
        }
        return root;
    }

    /**
     * Remove {@code z} from the red-black tree via the shared kernel (pointer-based,
     * preserving node identity). Returns the new root, or null if empty. Touches only
     * tree links (parent/left/right/red), never next/prev.
     */
    static <K, V> TreeNode<K, V> deleteFromTree(
            TreeNode<K, V> root, TreeNode<K, V> z, RbEventSink<TreeNode<K, V>> sink) {
        return RedBlackTree.deleteFromTree(root, z, sink);
    }
}
```

- [ ] **Step 5: Delete `TreeEventSink.java`**

Run: `git rm src/main/java/com/gimlism/translucent/hashmap/core/TreeEventSink.java`

- [ ] **Step 6: Rewrite `sinkFor` + add the enum bridge in `TeachingHashMap.java`**

Replace the `sinkFor` method (currently at `hashmap/core/TeachingHashMap.java:141-150`) with:

```java
    /** A sink that turns tree structural changes into events for bucket {@code i}. */
    private RbEventSink<TreeNode<K, V>> sinkFor(int i) {
        return new RbEventSink<>() {
            @Override public void rotated(
                    com.gimlism.translucent.substrate.rbtree.Direction dir, TreeNode<K, V> pivot) {
                emit(new Rotation(i, bridge(dir), pivot.getKey(), snapshot()));
            }
            @Override public void recolored(TreeNode<K, V> node,
                    com.gimlism.translucent.substrate.rbtree.Color oldColor,
                    com.gimlism.translucent.substrate.rbtree.Color newColor) {
                emit(new Recolor(i, node.getKey(), bridge(oldColor), bridge(newColor), snapshot()));
            }
        };
    }

    private static Direction bridge(com.gimlism.translucent.substrate.rbtree.Direction d) {
        return d == com.gimlism.translucent.substrate.rbtree.Direction.LEFT
                ? Direction.LEFT : Direction.RIGHT;
    }

    private static Color bridge(com.gimlism.translucent.substrate.rbtree.Color c) {
        return c == com.gimlism.translucent.substrate.rbtree.Color.RED
                ? Color.RED : Color.BLACK;
    }
```

Add the import near the other substrate import (`import com.gimlism.translucent.substrate.events.EventDispatcher;`):

```java
import com.gimlism.translucent.substrate.rbtree.RbEventSink;
```

(Use fully-qualified `com.gimlism.translucent.substrate.rbtree.Color`/`Direction` inside the sink — do NOT add plain imports for them; the map already imports `hashmap.events.Color`/`Direction` and the two names would clash.)

- [ ] **Step 7: Convert node payload accesses in `TeachingHashMap.java` (compiler-driven)**

The `Node`→interface change turns field reads on `Node<K,V>`-typed references into method calls. Run `mvn -q test-compile` and fix each error by this mechanical rule:

| Old (field) | New (interface method) |
|---|---|
| `x.hash` | `x.hash()` |
| `x.key` | `x.getKey()` |
| `x.value` | `x.getValue()` |
| `x.next` (read) | `x.next()` |
| `x.next = y` (write) | `x.setNext(y)` |
| `new Node<>(h, k, v, nx)` | `new ChainNode<>(h, k, v, nx)` |
| `TreeEventSink.NONE` | `RbEventSink.none()` |

Notes:
- On a reference already typed `TreeNode<K,V>`, either form compiles; prefer the method form for the interface-provided members, but `.prev`, `.seq`, `.left`, `.right`, `.parent`, `.red` remain **direct field access** (TreeNode fields / inherited public `RbNode` fields).
- The cast idiom `(TreeNode<K, V>) head.next` becomes `(TreeNode<K, V>) head.next()`.
- `setValue` is unchanged (it is a `Map.Entry` method on both node types).
- Repeat `mvn -q test-compile` until it reports zero errors. The compiler is the exhaustive checklist — there is no field access it will miss.

- [ ] **Step 8: Repoint silent sinks + delete the primitive test in the test tree**

Run `git rm src/test/java/com/gimlism/translucent/hashmap/core/TreeRotationTest.java`.

Then `mvn -q test-compile` and, in any test still failing to compile, apply the same rule from Step 7 (chiefly `TreeEventSink.NONE` → `RbEventSink.none()`, and add `import com.gimlism.translucent.substrate.rbtree.RbEventSink;`). Tests that call `TreeNode.insert`/`find`/`build`/`deleteFromTree` keep their call sites — only the sink argument type changes. Tests constructing `new TreeNode<>(...)` are unaffected (ctor unchanged). Tests reading `.red`/`.left`/`.right`/`.parent` are unaffected (public inherited fields).

- [ ] **Step 9: Run the full suite (the refactor's gate)**

Run: `mvn -q test`
Expected: **BUILD SUCCESS**, all existing tests green (baseline was 476 tests; this task removes 2 primitive rotation tests → expect 474 green, no failures). If any behavioral test fails, the migration changed behavior — stop and diff against the substrate kernel; do not "fix" the test.

- [ ] **Step 10: Verify protected surface is byte-unchanged**

Run: `git diff --stat -- src/main/java/com/gimlism/translucent/substrate src/main/java/com/gimlism/translucent/treeset src/test/java/com/gimlism/translucent/substrate src/test/java/com/gimlism/translucent/treeset`
Expected: **no output** (zero changes to substrate + treeset). If anything shows, revert it.

- [ ] **Step 11: Commit**

```bash
git add -A src/main/java/com/gimlism/translucent/hashmap/core \
          src/test/java/com/gimlism/translucent/hashmap/core
git commit -m "refactor(hashmap): retire duplicate RB kernel; TreeNode extends substrate RbNode

Node becomes an interface (ChainNode/TreeNode implement it); TreeNode
extends RbNode<TreeNode> and delegates rebalancing to RedBlackTree.
Deletes ~180 lines of duplicated rotate/fixup/delete + the local
TreeEventSink, driving the tree via RbEventSink<TreeNode> with a
substrate->map Color/Direction bridge. Behavior pinned by the existing
tree suite; substrate + treeset byte-unchanged.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm"
```

---

## Task 2: Add the migration-guard tests

Two guards the spec (§6) asks for: a structural pin so the migration can't silently regrow a local RB copy, and an integration pin on the substrate→map enum bridge.

**Files:**
- Create: `src/test/java/com/gimlism/translucent/hashmap/core/TreeKernelMigrationTest.java`

**Interfaces:**
- Consumes: `TreeNode<K,V>` (is-a `RbNode`), `TeachingHashMap` public API (`put`, `addListener`), `MapEvent`/`Rotation`/`Recolor`, `hashmap.events.Direction`/`Color`, `StructureEventListener<MapEvent>`.

- [ ] **Step 1: Write the structural + bridge tests**

Create the file:

```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.events.Color;
import com.gimlism.translucent.hashmap.events.Direction;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.Recolor;
import com.gimlism.translucent.hashmap.events.Rotation;
import com.gimlism.translucent.substrate.events.StructureEventListener;
import com.gimlism.translucent.substrate.rbtree.RbNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the substrate-kernel migration: TreeNode inherits its RB structure from the
 * shared kernel, and rotation/recolor events surface as map events with correctly
 * bridged Direction/Color enums.
 */
class TreeKernelMigrationTest {

    // Structural pin: a TreeNode IS an RbNode, so its links/colour come from the
    // shared base — not a regrown local copy.
    @Test
    void treeNodeInheritsFromSubstrateKernel() {
        TreeNode<Integer, String> n = new TreeNode<>(1, 1, "a", null, 0);
        assertInstanceOf(RbNode.class, n, "TreeNode must extend the shared RbNode base");
        // The inherited fields are usable directly (public on RbNode):
        n.red = true;
        n.left = null;
        n.right = null;
        n.parent = null;
        assertTrue(n.red);
    }

    // Bridge pin: driving the map hard enough to treeify a bin and rebalance it
    // emits Rotation/Recolor MAP events whose enums are the map's own (bridged from
    // the substrate kernel's enums inside sinkFor).
    @Test
    void rotationAndRecolorSurfaceAsBridgedMapEvents() {
        TeachingHashMap<CollidingKey, String> map = new TeachingHashMap<>();
        List<MapEvent> events = new ArrayList<>();
        map.addListener((StructureEventListener<MapEvent>) events::add);

        // All keys collide into one bucket, forcing treeify + RB rebalancing.
        for (int i = 0; i < 12; i++) {
            map.put(new CollidingKey(i), "v" + i);
        }

        List<Rotation> rotations = events.stream()
                .filter(e -> e instanceof Rotation).map(e -> (Rotation) e).toList();
        List<Recolor> recolors = events.stream()
                .filter(e -> e instanceof Recolor).map(e -> (Recolor) e).toList();

        assertTrue(!rotations.isEmpty(), "treeify should force at least one rotation");
        assertTrue(!recolors.isEmpty(), "treeify should force at least one recolor");
        // Every emitted enum is the map's own (bridged), never the substrate's:
        for (Rotation r : rotations) {
            assertTrue(r.direction() == Direction.LEFT || r.direction() == Direction.RIGHT,
                    "rotation direction must be a map Direction");
        }
        for (Recolor r : recolors) {
            assertTrue(r.oldColor() != null && r.newColor() != null);
            assertTrue(r.newColor() == Color.RED || r.newColor() == Color.BLACK,
                    "recolor colour must be a map Color");
        }
    }

    // A key whose hashCode collides for every instance, so all entries land in one
    // bucket and the bin treeifies.
    private static final class CollidingKey {
        final int id;
        CollidingKey(int id) { this.id = id; }
        @Override public int hashCode() { return 42; }
        @Override public boolean equals(Object o) {
            return o instanceof CollidingKey k && k.id == id;
        }
    }
}
```

- [ ] **Step 2: Run the new test to verify it passes**

Run: `mvn -q -Dtest=TreeKernelMigrationTest test`
Expected: PASS (2 tests). If `rotationAndRecolorSurfaceAsBridgedMapEvents` finds zero rotations, raise the loop count / confirm the treeify threshold — the collision guarantees one bucket, so enough puts must treeify it. Confirm the map's treeify threshold and min-treeify-capacity if needed (`TeachingHashMap` constants); 12 colliding puts on a default map is comfortably above the 8-entry treeify threshold once capacity permits.

- [ ] **Step 3: Run the full suite**

Run: `mvn -q test`
Expected: **BUILD SUCCESS**, all green (474 from Task 1 + 2 new = 476).

- [ ] **Step 4: Verify protected surface still byte-unchanged**

Run: `git diff --stat -- src/main/java/com/gimlism/translucent/substrate src/main/java/com/gimlism/translucent/treeset`
Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/gimlism/translucent/hashmap/core/TreeKernelMigrationTest.java
git commit -m "test(hashmap): pin RbNode inheritance + substrate->map enum bridge

Guards the RB-kernel migration: TreeNode is-a RbNode (no regrown local
copy), and treeify-driven rotations/recolors surface as map events with
the map's own bridged Direction/Color enums.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm"
```

---

## Self-Review

**Spec coverage:**
- §1 Node hierarchy (interface + ChainNode + Entries) → Task 1 Steps 1-3. ✓
- §1 TreeNode extends RbNode, zero casts on links → Task 1 Step 4. ✓
- §2 keeps cmp/find/build/insert/root, thin deleteFromTree wrapper; deletes rotate/fixup/transplant/minimum/setColor/local red → Task 1 Step 4. ✓
- §3 delete TreeEventSink, sinkFor → RbEventSink<TreeNode> + enum bridge, NONE → RbEventSink.none() → Task 1 Steps 5-8. ✓
- §4 blast radius confined; substrate+treeset byte-unchanged; behavioral equivalence via existing tests → Task 1 Steps 9-10, gate stated. ✓
- §5 test migration (TreeRotationTest rebased/deleted; insert/find/build/deleteFromTree call sites survive; field-read tests unchanged) → Task 1 Steps 7-8, with the substrate-owns-the-primitive rationale. ✓
- §6 new tests (structural pin + sink-bridge pin) → Task 2. ✓
- Out-of-scope (enum dedup, substrate/treeset changes) → Global Constraints + protected-surface checks. ✓

**Placeholder scan:** No TBD/TODO; every code step shows complete file/method bodies. The one compiler-driven step (Task 1 Step 7) gives an exhaustive mechanical rule table rather than enumerating ~40 brittle line edits — the compiler is the exact, complete checklist, which is the honest tool for a wide rename-shaped refactor. ✓

**Type consistency:** `sinkFor` returns `RbEventSink<TreeNode<K,V>>` in both the spec interface block and Task 1 Step 6. `insert`/`build`/`deleteFromTree`/`find` signatures in the TreeNode rewrite (Step 4) match the "Produces" block and the Step 8 note that only the sink arg type changes. `bridge` overloads map substrate→map enums consistently. `ChainNode` ctor `(int, K, V, Node<K,V>)` matches the `new Node<>`→`new ChainNode<>` rule. ✓
