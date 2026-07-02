# Teaching HashMap — Slice 2: Treeify + Red-Black Insert Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add real red-black tree bins to the teaching HashMap: chains treeify into balanced trees, the event stream shows the tree assembling (`Treeify`/`Rotation`/`Recolor`), tree bins are searched in O(log n), rendered with colors, and split on resize.

**Architecture:** A red-black tree is overlaid on Slice 1's insertion-order `next` thread. `table[i]` stays the insertion-order head; the RB root is reached by climbing `parent`. `TreeNode extends Node` adds `parent/left/right/red/seq`. The RB algorithm lives in `TreeNode.java` and reports rotations/recolorings through a `TreeEventSink` callback that `TeachingHashMap` wires to real events. Untreeify and RB deletion are deferred to Slice 3 (tree-bin `remove` throws until then).

**Tech Stack:** Java 21, Maven, JUnit 5.

## Global Constraints

- Java 21 target (`maven.compiler.release=21`), Maven, built/tested on JDK 26.
- Packages: `com.gimlism.translucent.hashmap.core` (map + tree), `.events` (types already defined in Slice 1), `.consumer`, `.demo`.
- Teaching-default configurable constants (unchanged from Slice 1): `initialCapacity=8`, `loadFactor=0.75`, `treeifyThreshold=4`, `untreeifyThreshold=2`, `minTreeifyCapacity=8`.
- Tree node ordering for **insertion**: by `Integer.compare(hash)`, tie-break by `Long.compare(seq)` (a monotonic insertion counter). No `Comparable` requirement.
- Tree **search** descends by hash, matches by `Objects.equals`, and on a hash-tie-with-unequal-key searches both subtrees (a search key has no `seq`).
- Events already exist in Slice 1's sealed hierarchy; Slice 2 only adds emission of `Treeify`, `Rotation`, `Recolor`.
- `Collision` is NOT emitted for tree-bin inserts (it is a chain-length concept).
- `remove` of a key present in a tree bin throws `UnsupportedOperationException` (Slice 3 adds RB delete).

## Deviation from the spec (approved by user at handoff)

- Spec §"Resize with tree bins" says the split "emits `Rotation`/`Recolor`." This plan instead rebuilds tree halves **silently during resize** (passing `TreeEventSink.NONE`), because the event sink's `snapshot()` reads the map's live `table`, which is mid-swap during resize — per-rotation frames would render the wrong table. The `Resize` before/after snapshots convey the split. Treeify-on-put remains fine-grained (table is stable there).

## File structure

- Create `core/TreeEventSink.java` — callback interface (`rotated`, `recolored`) + `NONE`.
- Create `core/TreeNode.java` — `TreeNode<K,V> extends Node<K,V>` + static RB operations (`rotateLeft/Right`, `insert`, `find`, `build`) + `root()` + `cmp`/`setColor` helpers.
- Modify `core/TeachingHashMap.java` — `nextSeq`; tree-aware `findNode`; `put` tree branch + treeify trigger; `treeifyBin`; `sinkFor`; tree-aware `snapshot`; tree-aware `resize`/`splitTreeBin`; `remove` tree guard; `isTreeBin` (test hook).
- Modify `demo/Demo.java` + test — demonstrate treeify.
- Tests under `src/test/java/com/gimlism/translucent/hashmap/core/` and `.../demo/`.

---

### Task 1: TreeEventSink + TreeNode skeleton

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/core/TreeEventSink.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/core/TreeNode.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/TreeNodeTest.java`

**Interfaces:**
- Consumes: `Node<K,V>` (Slice 1), `com.gimlism.translucent.hashmap.events.{Color,Direction}`.
- Produces:
  - `interface TreeEventSink { void rotated(Direction dir, Object pivotKey); void recolored(Object nodeKey, Color oldColor, Color newColor); TreeEventSink NONE = ...; }`
  - `class TreeNode<K,V> extends Node<K,V>` with package-private fields `TreeNode<K,V> parent, left, right;`, `boolean red;`, `final long seq;`, constructor `TreeNode(int hash, K key, V value, Node<K,V> next, long seq)`, and `TreeNode<K,V> root()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/TreeNodeTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class TreeNodeTest {
    @Test
    void rootClimbsParentPointers() {
        TreeNode<Integer, String> a = new TreeNode<>(1, 1, "a", null, 0);
        TreeNode<Integer, String> b = new TreeNode<>(2, 2, "b", null, 1);
        TreeNode<Integer, String> c = new TreeNode<>(3, 3, "c", null, 2);
        a.left = b; b.parent = a;
        b.left = c; c.parent = b;
        assertSame(a, c.root());
        assertSame(a, a.root());
        assertNull(a.parent);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `TreeNode`.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/gimlism/translucent/hashmap/core/TreeEventSink.java`
```java
package com.gimlism.translucent.hashmap.core;

import com.gimlism.translucent.hashmap.events.Color;
import com.gimlism.translucent.hashmap.events.Direction;

/**
 * Callback through which the red-black tree operations report structural
 * changes, so the map can translate them into MapEvents. Keeps the RB algorithm
 * decoupled from the event model.
 */
interface TreeEventSink {
    void rotated(Direction dir, Object pivotKey);

    void recolored(Object nodeKey, Color oldColor, Color newColor);

    /** A sink that ignores everything (used where events aren't wanted). */
    TreeEventSink NONE = new TreeEventSink() {
        @Override public void rotated(Direction dir, Object pivotKey) { }
        @Override public void recolored(Object nodeKey, Color oldColor, Color newColor) { }
    };
}
```

`src/main/java/com/gimlism/translucent/hashmap/core/TreeNode.java`
```java
package com.gimlism.translucent.hashmap.core;

/**
 * A red-black tree node for a treeified bin. Extends {@link Node} so it keeps the
 * insertion-order {@code next} thread (used for iteration and resize) while also
 * participating in the tree via {@code parent/left/right}. Ordering across nodes
 * is by hash, then by the monotonic insertion {@code seq}.
 */
class TreeNode<K, V> extends Node<K, V> {
    TreeNode<K, V> parent;
    TreeNode<K, V> left;
    TreeNode<K, V> right;
    boolean red;
    final long seq;

    TreeNode(int hash, K key, V value, Node<K, V> next, long seq) {
        super(hash, key, value, next);
        this.seq = seq;
    }

    /** Climb to the tree root from this node. */
    TreeNode<K, V> root() {
        TreeNode<K, V> r = this;
        while (r.parent != null) r = r.parent;
        return r;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TreeEventSink.java src/main/java/com/gimlism/translucent/hashmap/core/TreeNode.java src/test/java/com/gimlism/translucent/hashmap/core/TreeNodeTest.java
git commit -m "feat(core): add TreeNode and TreeEventSink for tree bins"
```

---

### Task 2: Rotations + colour/comparator helpers

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TreeNode.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/TreeRotationTest.java`

**Interfaces:**
- Consumes: `TreeNode`, `TreeEventSink`, `Direction`, `Color`.
- Produces (static on `TreeNode`):
  - `static <K,V> TreeNode<K,V> rotateLeft(TreeNode<K,V> root, TreeNode<K,V> p, TreeEventSink sink)` — returns the (possibly new) root; emits `sink.rotated(LEFT, p.key)`.
  - `static <K,V> TreeNode<K,V> rotateRight(TreeNode<K,V> root, TreeNode<K,V> p, TreeEventSink sink)` — mirror; emits `sink.rotated(RIGHT, p.key)`.
  - `static <K,V> void setColor(TreeNode<K,V> n, boolean red, TreeEventSink sink)` — flips colour, emitting `recolored` only on an actual change.
  - `static int cmp(int h1, long s1, int h2, long s2)` — hash then seq.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/TreeRotationTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.gimlism.translucent.hashmap.events.Direction;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeRotationTest {
    static final class Rec implements TreeEventSink {
        final List<String> log = new ArrayList<>();
        public void rotated(Direction d, Object p) { log.add("ROT " + d + " " + p); }
        public void recolored(Object k, com.gimlism.translucent.hashmap.events.Color o,
                              com.gimlism.translucent.hashmap.events.Color n) {
            log.add("COL " + k + " " + o + "->" + n);
        }
    }

    // Build   p            and left-rotate about p to get   r
    //        / \                                            / \
    //       a   r                                          p   c
    //          / \                                        / \
    //         b   c                                      a   b
    @Test
    void leftRotationRestructuresAndReports() {
        TreeNode<Integer, String> p = new TreeNode<>(2, 2, "p", null, 0);
        TreeNode<Integer, String> a = new TreeNode<>(1, 1, "a", null, 1);
        TreeNode<Integer, String> r = new TreeNode<>(4, 4, "r", null, 2);
        TreeNode<Integer, String> b = new TreeNode<>(3, 3, "b", null, 3);
        TreeNode<Integer, String> c = new TreeNode<>(5, 5, "c", null, 4);
        p.left = a; a.parent = p;
        p.right = r; r.parent = p;
        r.left = b; b.parent = r;
        r.right = c; c.parent = r;

        Rec rec = new Rec();
        TreeNode<Integer, String> newRoot = TreeNode.rotateLeft(p, p, rec);

        assertSame(r, newRoot);
        assertSame(p, r.left);
        assertSame(c, r.right);
        assertSame(a, p.left);
        assertSame(b, p.right);
        assertSame(r, p.parent);
        assertSame(b, p.right); // b moved under p
        assertEquals(List.of("ROT LEFT 2"), rec.log);
    }

    @Test
    void rightRotationIsInverseOfLeft() {
        TreeNode<Integer, String> r = new TreeNode<>(4, 4, "r", null, 0);
        TreeNode<Integer, String> p = new TreeNode<>(2, 2, "p", null, 1);
        TreeNode<Integer, String> c = new TreeNode<>(5, 5, "c", null, 2);
        TreeNode<Integer, String> a = new TreeNode<>(1, 1, "a", null, 3);
        TreeNode<Integer, String> b = new TreeNode<>(3, 3, "b", null, 4);
        r.left = p; p.parent = r;
        r.right = c; c.parent = r;
        p.left = a; a.parent = p;
        p.right = b; b.parent = p;

        Rec rec = new Rec();
        TreeNode<Integer, String> newRoot = TreeNode.rotateRight(r, r, rec);

        assertSame(p, newRoot);
        assertSame(a, p.left);
        assertSame(r, p.right);
        assertSame(b, r.left);
        assertSame(c, r.right);
        assertEquals(List.of("ROT RIGHT 4"), rec.log);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `rotateLeft`.

- [ ] **Step 3: Write minimal implementation**

Add to `TreeNode` (imports at top of file):
```java
import com.gimlism.translucent.hashmap.events.Color;
import com.gimlism.translucent.hashmap.events.Direction;
```

Add these static methods inside `TreeNode`:
```java
    /** Total order used for tree INSERTION: hash, then insertion seq. */
    static int cmp(int h1, long s1, int h2, long s2) {
        int c = Integer.compare(h1, h2);
        return c != 0 ? c : Long.compare(s1, s2);
    }

    /** Flip a node's colour, reporting only real changes. */
    static <K, V> void setColor(TreeNode<K, V> n, boolean red, TreeEventSink sink) {
        if (n.red != red) {
            sink.recolored(n.key, n.red ? Color.RED : Color.BLACK, red ? Color.RED : Color.BLACK);
            n.red = red;
        }
    }

    static <K, V> TreeNode<K, V> rotateLeft(TreeNode<K, V> root, TreeNode<K, V> p, TreeEventSink sink) {
        TreeNode<K, V> r = p.right;
        p.right = r.left;
        if (r.left != null) r.left.parent = p;
        r.parent = p.parent;
        if (p.parent == null) root = r;
        else if (p == p.parent.left) p.parent.left = r;
        else p.parent.right = r;
        r.left = p;
        p.parent = r;
        sink.rotated(Direction.LEFT, p.key);
        return root;
    }

    static <K, V> TreeNode<K, V> rotateRight(TreeNode<K, V> root, TreeNode<K, V> p, TreeEventSink sink) {
        TreeNode<K, V> l = p.left;
        p.left = l.right;
        if (l.right != null) l.right.parent = p;
        l.parent = p.parent;
        if (p.parent == null) root = l;
        else if (p == p.parent.right) p.parent.right = l;
        else p.parent.left = l;
        l.right = p;
        p.parent = l;
        sink.rotated(Direction.RIGHT, p.key);
        return root;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `TreeRotationTest` green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TreeNode.java src/test/java/com/gimlism/translucent/hashmap/core/TreeRotationTest.java
git commit -m "feat(core): add tree rotations and colour helpers"
```

---

### Task 3: Red-black insert with fixup

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TreeNode.java`
- Create: `src/test/java/com/gimlism/translucent/hashmap/core/RedBlackInvariants.java` (test helper)
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/TreeInsertTest.java`

**Interfaces:**
- Consumes: `TreeNode` rotations/`setColor`/`cmp` (Task 2).
- Produces:
  - `static <K,V> TreeNode<K,V> insert(TreeNode<K,V> root, TreeNode<K,V> x, TreeEventSink sink)` — BST-inserts `x` by `cmp(hash,seq)`, runs red-black fixup, returns the new (black) root. `x` must not already be in the tree.
- Test helper `RedBlackInvariants.assertValid(TreeNode<?,?> root)` and `RedBlackInvariants.collectKeys(TreeNode<?,?> root, List)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/RedBlackInvariants.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

/** Test-only checker for red-black tree invariants. */
final class RedBlackInvariants {
    private RedBlackInvariants() { }

    static void assertValid(TreeNode<?, ?> root) {
        if (root == null) return;
        assertFalse(root.red, "root must be black");
        blackHeight(root);
    }

    // Returns black-height; throws on a red-red violation or unequal black-heights.
    private static int blackHeight(TreeNode<?, ?> n) {
        if (n == null) return 1;
        if (n.red) {
            assertFalse(n.left != null && n.left.red, "red node has red left child");
            assertFalse(n.right != null && n.right.red, "red node has red right child");
        }
        if (n.left != null) assertEquals(n, n.left.parent, "left child parent link");
        if (n.right != null) assertEquals(n, n.right.parent, "right child parent link");
        int lh = blackHeight(n.left);
        int rh = blackHeight(n.right);
        assertEquals(lh, rh, "black-height mismatch");
        return lh + (n.red ? 0 : 1);
    }

    static void collectKeys(TreeNode<?, ?> n, List<Object> out) {
        if (n == null) return;
        collectKeys(n.left, out);
        out.add(n.key);
        collectKeys(n.right, out);
    }
}
```

`src/test/java/com/gimlism/translucent/hashmap/core/TreeInsertTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeInsertTest {
    @Test
    void insertingAscendingKeysStaysBalancedAndComplete() {
        TreeNode<Integer, String> root = null;
        for (int k = 1; k <= 20; k++) {
            root = TreeNode.insert(root, new TreeNode<>(k, k, "v" + k, null, k), TreeEventSink.NONE);
            RedBlackInvariants.assertValid(root);
        }
        List<Object> keys = new ArrayList<>();
        RedBlackInvariants.collectKeys(root, keys);
        // in-order traversal is sorted by hash (== key here)
        List<Object> expected = new ArrayList<>();
        for (int k = 1; k <= 20; k++) expected.add(k);
        assertEquals(expected, keys);
    }

    @Test
    void insertEmitsBalancingEvents() {
        var rec = new TreeRotationTest.Rec();
        TreeNode<Integer, String> root = null;
        for (int k = 1; k <= 5; k++) {
            root = TreeNode.insert(root, new TreeNode<>(k, k, "v" + k, null, k), rec);
        }
        RedBlackInvariants.assertValid(root);
        // ascending inserts force at least one rotation and several recolourings
        assertTrue(rec.log.stream().anyMatch(s -> s.startsWith("ROT")), "expected a rotation");
        assertTrue(rec.log.stream().anyMatch(s -> s.startsWith("COL")), "expected a recolour");
    }

    @Test
    void tiedHashesAreOrderedBySeq() {
        // same hash, distinct seq -> deterministic order by seq
        TreeNode<Integer, String> root = null;
        root = TreeNode.insert(root, new TreeNode<>(7, 100, "a", null, 0), TreeEventSink.NONE);
        root = TreeNode.insert(root, new TreeNode<>(7, 200, "b", null, 1), TreeEventSink.NONE);
        root = TreeNode.insert(root, new TreeNode<>(7, 300, "c", null, 2), TreeEventSink.NONE);
        RedBlackInvariants.assertValid(root);
        List<Object> keys = new ArrayList<>();
        RedBlackInvariants.collectKeys(root, keys);
        assertEquals(List.of(100, 200, 300), keys); // seq order
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `insert`.

- [ ] **Step 3: Write minimal implementation**

Add to `TreeNode`:
```java
    /**
     * BST-insert {@code x} (ordered by hash then seq), then restore red-black
     * invariants. Returns the new root. {@code x} must be a fresh node not
     * already present in the tree.
     */
    static <K, V> TreeNode<K, V> insert(TreeNode<K, V> root, TreeNode<K, V> x, TreeEventSink sink) {
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
        return insertFixup(root, x, sink);
    }

    private static <K, V> TreeNode<K, V> insertFixup(TreeNode<K, V> root, TreeNode<K, V> x, TreeEventSink sink) {
        while (x.parent != null && x.parent.red) {
            TreeNode<K, V> p = x.parent;
            TreeNode<K, V> g = p.parent; // p is red => p is not root => g != null
            if (p == g.left) {
                TreeNode<K, V> u = g.right;
                if (u != null && u.red) {
                    setColor(p, false, sink);
                    setColor(u, false, sink);
                    setColor(g, true, sink);
                    x = g;
                } else {
                    if (x == p.right) {
                        x = p;
                        root = rotateLeft(root, x, sink);
                        p = x.parent;
                        g = p.parent;
                    }
                    setColor(p, false, sink);
                    setColor(g, true, sink);
                    root = rotateRight(root, g, sink);
                }
            } else {
                TreeNode<K, V> u = g.left;
                if (u != null && u.red) {
                    setColor(p, false, sink);
                    setColor(u, false, sink);
                    setColor(g, true, sink);
                    x = g;
                } else {
                    if (x == p.left) {
                        x = p;
                        root = rotateRight(root, x, sink);
                        p = x.parent;
                        g = p.parent;
                    }
                    setColor(p, false, sink);
                    setColor(g, true, sink);
                    root = rotateLeft(root, g, sink);
                }
            }
        }
        if (root.red) setColor(root, false, sink);
        return root;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `TreeInsertTest` green (RB invariants hold across 20 inserts).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TreeNode.java src/test/java/com/gimlism/translucent/hashmap/core/RedBlackInvariants.java src/test/java/com/gimlism/translucent/hashmap/core/TreeInsertTest.java
git commit -m "feat(core): red-black insert with fixup and balancing events"
```

---

### Task 4: Tree search (find)

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TreeNode.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/TreeFindTest.java`

**Interfaces:**
- Consumes: `TreeNode.insert` (Task 3).
- Produces: `static <K,V> TreeNode<K,V> find(TreeNode<K,V> p, int hash, Object key)` — returns the matching node or `null`; descends by hash, matches by `Objects.equals`, and searches both subtrees when hashes tie but keys differ.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/TreeFindTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TreeFindTest {
    private static TreeNode<Integer, String> tree(int... keys) {
        TreeNode<Integer, String> root = null;
        for (int k : keys) {
            root = TreeNode.insert(root, new TreeNode<>(k, k, "v" + k, null, k), TreeEventSink.NONE);
        }
        return root;
    }

    @Test
    void findsPresentKeysByDistinctHash() {
        TreeNode<Integer, String> root = tree(5, 1, 9, 3, 7, 2, 8);
        for (int k : new int[]{5, 1, 9, 3, 7, 2, 8}) {
            assertEquals("v" + k, TreeNode.find(root, k, k).value);
        }
    }

    @Test
    void returnsNullForAbsentKey() {
        TreeNode<Integer, String> root = tree(5, 1, 9);
        assertNull(TreeNode.find(root, 42, 42));
    }

    @Test
    void findsAcrossTiedHashesViaBothSubtrees() {
        // three distinct keys all with hash 7 -> find must search both subtrees
        TreeNode<String, String> root = null;
        String[] keys = {"a", "b", "c", "d"};
        for (int i = 0; i < keys.length; i++) {
            root = TreeNode.insert(root, new TreeNode<>(7, keys[i], "V" + keys[i], null, i), TreeEventSink.NONE);
        }
        for (String k : keys) {
            assertEquals("V" + k, TreeNode.find(root, 7, k).value);
        }
        assertNull(TreeNode.find(root, 7, "z"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `find`.

- [ ] **Step 3: Write minimal implementation**

Add import to `TreeNode`:
```java
import java.util.Objects;
```

Add to `TreeNode`:
```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `TreeFindTest` green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TreeNode.java src/test/java/com/gimlism/translucent/hashmap/core/TreeFindTest.java
git commit -m "feat(core): tree search with tied-hash dual-subtree fallback"
```

---

### Task 5: Build a tree from a threaded chain

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TreeNode.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/TreeBuildTest.java`

**Interfaces:**
- Consumes: `TreeNode.insert` (Task 3).
- Produces: `static <K,V> TreeNode<K,V> build(TreeNode<K,V> first, TreeEventSink sink)` — inserts each node of the `next`-threaded list (in order) into a fresh RB tree and returns the root. Does NOT modify the `next` thread (iteration order preserved).

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/TreeBuildTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeBuildTest {
    @Test
    void buildProducesValidTreeAndKeepsNextThread() {
        // thread nodes 4 -> 2 -> 5 -> 1 -> 3 in insertion order via next
        int[] order = {4, 2, 5, 1, 3};
        TreeNode<Integer, String> first = null, prev = null;
        for (int i = 0; i < order.length; i++) {
            TreeNode<Integer, String> t = new TreeNode<>(order[i], order[i], "v" + order[i], null, i);
            if (prev == null) first = t; else prev.next = t;
            prev = t;
        }

        TreeNode<Integer, String> root = TreeNode.build(first, TreeEventSink.NONE);

        RedBlackInvariants.assertValid(root);
        // tree holds all keys, in-order == sorted by hash
        List<Object> inOrder = new ArrayList<>();
        RedBlackInvariants.collectKeys(root, inOrder);
        assertEquals(List.of(1, 2, 3, 4, 5), inOrder);
        // next thread still in original insertion order
        List<Object> threaded = new ArrayList<>();
        for (Node<Integer, String> e = first; e != null; e = e.next) threaded.add(e.key);
        assertEquals(List.of(4, 2, 5, 1, 3), threaded);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `build`.

- [ ] **Step 3: Write minimal implementation**

Add to `TreeNode`:
```java
    /**
     * Build a red-black tree over an already-threaded list of tree nodes (linked
     * by {@code next} in insertion order), inserting each in turn. The
     * {@code next} thread is left intact for iteration. Returns the root.
     */
    static <K, V> TreeNode<K, V> build(TreeNode<K, V> first, TreeEventSink sink) {
        TreeNode<K, V> root = null;
        for (TreeNode<K, V> x = first; x != null; ) {
            @SuppressWarnings("unchecked")
            TreeNode<K, V> next = (TreeNode<K, V>) x.next;
            root = insert(root, x, sink);
            x = next;
        }
        return root;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `TreeBuildTest` green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TreeNode.java src/test/java/com/gimlism/translucent/hashmap/core/TreeBuildTest.java
git commit -m "feat(core): build a red-black tree from a threaded chain"
```

---

### Task 6: Map integration — treeify on threshold, tree-aware get/put

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/TreeifyTest.java`

**Interfaces:**
- Consumes: `TreeNode.{find,insert,build,root}`, `TreeEventSink`, `Treeify`, `Rotation`, `Recolor`.
- Produces on `TeachingHashMap`:
  - field `long nextSeq;`
  - `boolean isTreeBin(int index)` (package-private test hook) — `table[index] instanceof TreeNode`.
  - tree-aware `findNode` (used by `get`/`containsKey`).
  - `put` grows chains, triggers `treeifyBin`, and inserts into existing tree bins.
  - `private void treeifyBin(int i)` and `private TreeEventSink sinkFor(int i)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/TreeifyTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.RecordingListener;
import com.gimlism.translucent.hashmap.events.Recolor;
import com.gimlism.translucent.hashmap.events.Rotation;
import com.gimlism.translucent.hashmap.events.Treeify;
import org.junit.jupiter.api.Test;

class TreeifyTest {
    // keys 0, 8, 16, 24 all land in bucket 0 at capacity 8 ((cap-1)&hash == 0)
    private static TeachingHashMap<Integer, String> collidingMap() {
        return new TeachingHashMap<>();
    }

    @Test
    void chainTreeifiesAtThreshold() {
        var map = collidingMap();
        map.put(0, "a");
        map.put(8, "b");
        map.put(16, "c");
        assertFalse(map.isTreeBin(0), "still a chain at length 3");
        map.put(24, "d"); // 4th -> treeifyThreshold(4)
        assertTrue(map.isTreeBin(0), "bucket 0 is now a tree");
        assertEquals(4, map.size());
        // map was loaded with put(0,"a"), put(8,"b"), put(16,"c"), put(24,"d")
        for (int k : new int[]{0, 8, 16, 24}) {
            assertEquals("abcd".substring(k / 8, k / 8 + 1), map.get(k));
        }
    }

    @Test
    void treeifyEmitsTreeifyThenBalancingEvents() {
        var map = collidingMap();
        var rec = new RecordingListener();
        map.addListener(rec);
        map.put(0, "a");
        map.put(8, "b");
        map.put(16, "c");
        map.put(24, "d");
        // exactly one Treeify, and it precedes the rotation/recolour burst
        long treeifyCount = rec.events().stream().filter(e -> e instanceof Treeify).count();
        assertEquals(1, treeifyCount);
        int treeifyIdx = -1;
        for (int i = 0; i < rec.events().size(); i++) {
            if (rec.events().get(i) instanceof Treeify) { treeifyIdx = i; break; }
        }
        boolean balancingAfter = rec.events().subList(treeifyIdx + 1, rec.events().size()).stream()
            .anyMatch(e -> e instanceof Rotation || e instanceof Recolor);
        assertTrue(balancingAfter, "expected rotation/recolour after Treeify");
    }

    @Test
    void treeBinLookupAndReplaceWork() {
        var map = collidingMap();
        for (int k : new int[]{0, 8, 16, 24, 32}) map.put(k, "v" + k);
        assertTrue(map.isTreeBin(0));
        for (int k : new int[]{0, 8, 16, 24, 32}) assertEquals("v" + k, map.get(k));
        assertEquals("v8", map.put(8, "v8b")); // replace returns old
        assertEquals("v8b", map.get(8));
        assertEquals(5, map.size()); // replace does not grow
    }

    @Test
    void belowMinTreeifyCapacityResizesInsteadOfTreeifying() {
        // capacity 4 < minTreeifyCapacity 8: a 4-long chain must resize, not treeify
        var map = new TeachingHashMap<Integer, String>(4, 0.75f, 4, 2, 8);
        // keys 0,4,8,12 collide at cap 4 ((cap-1)&hash == 0); but resize fires first
        map.put(0, "a");
        map.put(4, "b");
        map.put(8, "c"); // size 3 > threshold(3) -> resize happens along the way
        map.put(12, "d");
        assertFalse(map.isTreeBin(0), "should have resized rather than treeified");
        assertTrue(map.capacity() > 4);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `isTreeBin` / no treeify behaviour.

- [ ] **Step 3: Write minimal implementation**

Add imports to `TeachingHashMap`:
```java
import com.gimlism.translucent.hashmap.events.Color;
import com.gimlism.translucent.hashmap.events.Direction;
import com.gimlism.translucent.hashmap.events.Recolor;
import com.gimlism.translucent.hashmap.events.Rotation;
import com.gimlism.translucent.hashmap.events.Treeify;
```

Add the field (near `modCount`):
```java
    long nextSeq;
```

Add the test hook and sink factory:
```java
    /** True if bucket {@code index} is a treeified bin. (Test/inspection hook.) */
    boolean isTreeBin(int index) {
        return table[index] instanceof TreeNode;
    }

    /** A sink that turns tree structural changes into events for bucket {@code i}. */
    private TreeEventSink sinkFor(int i) {
        return new TreeEventSink() {
            @Override public void rotated(Direction dir, Object pivotKey) {
                emit(new Rotation(i, dir, pivotKey, snapshot()));
            }
            @Override public void recolored(Object nodeKey, Color oldColor, Color newColor) {
                emit(new Recolor(i, nodeKey, oldColor, newColor, snapshot()));
            }
        };
    }
```

Make `findNode` tree-aware (replace the existing method body):
```java
    private Node<K, V> findNode(Object key) {
        int h = hash(key);
        int i = indexFor(h, table.length);
        Node<K, V> head = table[i];
        if (head instanceof TreeNode) {
            @SuppressWarnings("unchecked")
            TreeNode<K, V> t = (TreeNode<K, V>) head;
            return TreeNode.find(t.root(), h, key);
        }
        for (Node<K, V> e = head; e != null; e = e.next) {
            if (e.hash == h && Objects.equals(e.key, key)) return e;
        }
        return null;
    }
```

Replace `put` with the tree-aware version:
```java
    @Override
    public V put(K key, V value) {
        int h = hash(key);
        int i = indexFor(h, table.length);
        Node<K, V> head = table[i];

        // --- tree bin ---
        if (head instanceof TreeNode) {
            @SuppressWarnings("unchecked")
            TreeNode<K, V> root = ((TreeNode<K, V>) head).root();
            TreeNode<K, V> found = TreeNode.find(root, h, key);
            if (found != null) {
                V old = found.value;
                found.value = value;
                emit(new Put(key, value, old, i, false, snapshot()));
                return old;
            }
            TreeNode<K, V> node = new TreeNode<>(h, key, value, null, nextSeq++);
            Node<K, V> tail = head;
            while (tail.next != null) tail = tail.next;
            tail.next = node; // preserve insertion order in the next thread
            TreeNode.insert(root, node, sinkFor(i)); // new root reachable via climb
            size++;
            modCount++;
            emit(new Put(key, value, null, i, true, snapshot())); // no Collision for tree bins
            if (size > threshold) resize();
            return null;
        }

        // --- chain bin (Slice 1 behaviour) ---
        for (Node<K, V> e = head; e != null; e = e.next) {
            if (e.hash == h && Objects.equals(e.key, key)) {
                V old = e.value;
                e.value = value;
                emit(new Put(key, value, old, i, false, snapshot()));
                return old;
            }
        }
        int chainBefore = 0;
        Node<K, V> created = new Node<>(h, key, value, null);
        if (head == null) {
            table[i] = created;
        } else {
            Node<K, V> tail = head;
            chainBefore = 1;
            while (tail.next != null) { tail = tail.next; chainBefore++; }
            tail.next = created;
        }
        size++;
        modCount++;
        emit(new Put(key, value, null, i, true, snapshot()));
        if (chainBefore > 0) {
            emit(new Collision(key, i, chainBefore, chainBefore + 1, snapshot()));
        }
        if (chainBefore + 1 >= treeifyThreshold) {
            treeifyBin(i);
        }
        if (size > threshold) resize();
        return null;
    }
```

Add `treeifyBin`:
```java
    /** Convert the chain at bucket {@code i} into a red-black tree. */
    private void treeifyBin(int i) {
        if (table.length < minTreeifyCapacity) {
            resize(); // grow instead of treeifying a small table (mirrors the JDK)
            return;
        }
        emit(new Treeify(i, snapshot())); // announce: bucket i is still the chain here
        // convert chain Nodes to TreeNodes, preserving order via the next thread
        TreeNode<K, V> first = null;
        TreeNode<K, V> prev = null;
        for (Node<K, V> e = table[i]; e != null; e = e.next) {
            TreeNode<K, V> t = new TreeNode<>(e.hash, e.key, e.value, null, nextSeq++);
            if (prev == null) first = t; else prev.next = t;
            prev = t;
        }
        table[i] = first;
        TreeNode.build(first, sinkFor(i)); // assembles the tree, emitting Rotation/Recolor
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `TreeifyTest` green; all Slice 1 tests still pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java src/test/java/com/gimlism/translucent/hashmap/core/TreeifyTest.java
git commit -m "feat(core): treeify chains and route get/put through tree bins"
```

---

### Task 7: Tree-aware snapshots

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/TreeSnapshotTest.java`

**Interfaces:**
- Consumes: `TreeNode.root`, `TreeSnapshot`, `TreeNodeSnapshot`, `Color`.
- Produces: `snapshot()` renders a tree bin as `TreeSnapshot(TreeNodeSnapshot root)` with colours; chains unchanged. Helper `private TreeNodeSnapshot treeSnapshot(TreeNode<K,V> n)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/TreeSnapshotTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.gimlism.translucent.hashmap.events.ChainSnapshot;
import com.gimlism.translucent.hashmap.events.Color;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import com.gimlism.translucent.hashmap.events.TreeNodeSnapshot;
import com.gimlism.translucent.hashmap.events.TreeSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeSnapshotTest {
    @Test
    void treeBinRendersAsTreeSnapshotWithBlackRoot() {
        var map = new TeachingHashMap<Integer, String>();
        for (int k : new int[]{0, 8, 16, 24, 32}) map.put(k, "v" + k);
        MapSnapshot snap = map.snapshot();
        assertInstanceOf(TreeSnapshot.class, snap.buckets().get(0));
        TreeSnapshot ts = (TreeSnapshot) snap.buckets().get(0);
        assertEquals(Color.BLACK, ts.root().color(), "root must be black");
        // all five keys present in the snapshot tree
        List<Object> keys = new ArrayList<>();
        collect(ts.root(), keys);
        assertEquals(5, keys.size());
        assertEquals(List.of(0, 8, 16, 24, 32), keys.stream().sorted().toList());
    }

    @Test
    void chainBinStillRendersAsChainSnapshot() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(1, "a");
        map.put(9, "b"); // bucket 1, length 2, no treeify
        MapSnapshot snap = map.snapshot();
        assertInstanceOf(ChainSnapshot.class, snap.buckets().get(1));
    }

    private static void collect(TreeNodeSnapshot n, List<Object> out) {
        if (n == null) return;
        collect(n.left(), out);
        out.add(n.key());
        collect(n.right(), out);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — bucket 0 is a `ChainSnapshot` / `ClassCastException`, because `snapshot()` still walks chains only.

- [ ] **Step 3: Write minimal implementation**

Add imports to `TeachingHashMap`:
```java
import com.gimlism.translucent.hashmap.events.TreeNodeSnapshot;
import com.gimlism.translucent.hashmap.events.TreeSnapshot;
```

In `snapshot()`, change the per-bucket handling so a tree head produces a `TreeSnapshot`. Replace the loop body that builds each bucket with:
```java
        for (Node<K, V> head : table) {
            if (head == null) {
                buckets.add(new EmptyBucket());
            } else if (head instanceof TreeNode) {
                @SuppressWarnings("unchecked")
                TreeNode<K, V> t = (TreeNode<K, V>) head;
                buckets.add(new TreeSnapshot(treeSnapshot(t.root())));
            } else {
                List<EntrySnapshot> entries = new ArrayList<>();
                for (Node<K, V> e = head; e != null; e = e.next) {
                    entries.add(new EntrySnapshot(e.key, e.value, e.hash));
                }
                buckets.add(new ChainSnapshot(entries));
            }
        }
```

Add the helper:
```java
    private TreeNodeSnapshot treeSnapshot(TreeNode<K, V> n) {
        if (n == null) return null;
        return new TreeNodeSnapshot(
            n.key, n.value,
            n.red ? Color.RED : Color.BLACK,
            treeSnapshot(n.left),
            treeSnapshot(n.right));
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `TreeSnapshotTest` green; Slice 1 `SnapshotCaptureTest` still passes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java src/test/java/com/gimlism/translucent/hashmap/core/TreeSnapshotTest.java
git commit -m "feat(core): render tree bins as TreeSnapshot with colours"
```

---

### Task 8: Tree-aware resize (split)

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/TreeResizeTest.java`

**Interfaces:**
- Consumes: `TreeNode.build`, `TreeEventSink.NONE`.
- Produces: `resize()` dispatches tree bins to `private void splitTreeBin(Node<K,V>[] newTab, int j, TreeNode<K,V> head, int oldCap)` (rebuilds each non-empty half silently). Chain split unchanged.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/TreeResizeTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TreeResizeTest {
    @Test
    void treeBinSplitsAcrossResizePreservingEntries() {
        // small cap, high load factor so we control when resize fires
        var map = new TeachingHashMap<Integer, String>(8, 10.0f, 4, 2, 8);
        // keys 0,8,16,24,32,40 all in bucket 0 at cap 8 -> treeify at 4th
        int[] keys = {0, 8, 16, 24, 32, 40};
        for (int k : keys) map.put(k, "v" + k);
        assertTrue(map.isTreeBin(0), "precondition: bucket 0 treeified");

        map.forceResize(); // package-private test hook added in this task

        // capacity doubled to 16: with (cap-1)&hash, 0/16/32 -> bucket 0, 8/24/40 -> bucket 8
        assertEquals(16, map.capacity());
        for (int k : keys) assertEquals("v" + k, map.get(k), "entry preserved across split");
        assertEquals(6, map.size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `forceResize`; and without tree-split, a treeified bin would not rehash correctly.

- [ ] **Step 3: Write minimal implementation**

Add a package-private test hook and route tree bins in `resize`. Replace the bucket loop inside `resize()` so it dispatches on bin type, and add `forceResize` + `splitTreeBin`:

```java
    /** Test hook: force a rehash to the next capacity. */
    void forceResize() {
        resize();
    }
```

In `resize()`, replace the rehash loop (the `for (int j = 0; j < oldCap; j++)` block) with:
```java
        for (int j = 0; j < oldCap; j++) {
            Node<K, V> head = oldTab[j];
            if (head == null) continue;
            if (head instanceof TreeNode) {
                @SuppressWarnings("unchecked")
                TreeNode<K, V> t = (TreeNode<K, V>) head;
                splitTreeBin(newTab, j, t, oldCap);
            } else {
                Node<K, V> e = head;
                while (e != null) {
                    Node<K, V> next = e.next;
                    int idx = indexFor(e.hash, newCap);
                    e.next = null;
                    if (newTab[idx] == null) {
                        newTab[idx] = e;
                    } else {
                        Node<K, V> tail = newTab[idx];
                        while (tail.next != null) tail = tail.next;
                        tail.next = e;
                    }
                    e = next;
                }
            }
        }
```

Add `splitTreeBin` (rebuilds each half silently — see the plan's deviation note):
```java
    /**
     * Split a tree bin during resize: partition its nodes (walked in insertion
     * order via next) into the low bucket {@code j} and high bucket
     * {@code j + oldCap}, then rebuild each non-empty half's red-black tree.
     * Rebuild is silent (TreeEventSink.NONE) because the table is mid-swap here;
     * the Resize before/after snapshots convey the change. Small halves remain
     * trees (untreeify is Slice 3).
     */
    private void splitTreeBin(Node<K, V>[] newTab, int j, TreeNode<K, V> head, int oldCap) {
        TreeNode<K, V> loHead = null, loTail = null, hiHead = null, hiTail = null;
        for (Node<K, V> e = head; e != null; ) {
            @SuppressWarnings("unchecked")
            TreeNode<K, V> t = (TreeNode<K, V>) e;
            Node<K, V> next = e.next;
            t.parent = null;
            t.left = null;
            t.right = null;
            t.next = null;
            if ((t.hash & oldCap) == 0) {
                if (loTail == null) loHead = t; else loTail.next = t;
                loTail = t;
            } else {
                if (hiTail == null) hiHead = t; else hiTail.next = t;
                hiTail = t;
            }
            e = next;
        }
        if (loHead != null) {
            TreeNode.build(loHead, TreeEventSink.NONE);
            newTab[j] = loHead;
        }
        if (hiHead != null) {
            TreeNode.build(hiHead, TreeEventSink.NONE);
            newTab[j + oldCap] = hiHead;
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `TreeResizeTest` green; Slice 1 resize tests still pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java src/test/java/com/gimlism/translucent/hashmap/core/TreeResizeTest.java
git commit -m "feat(core): split tree bins on resize (rebuild halves)"
```

---

### Task 9: Tree-bin remove guard

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/TreeRemoveGuardTest.java`

**Interfaces:**
- Consumes: `TreeNode.find`, `TreeNode.root`.
- Produces: `remove(Object)` throws `UnsupportedOperationException` when the key is present in a tree bin; chain remove unchanged; absent keys still return `null`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/TreeRemoveGuardTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TreeRemoveGuardTest {
    private static TeachingHashMap<Integer, String> treeified() {
        var map = new TeachingHashMap<Integer, String>();
        for (int k : new int[]{0, 8, 16, 24}) map.put(k, "v" + k);
        assertTrue(map.isTreeBin(0));
        return map;
    }

    @Test
    void removingKeyInTreeBinThrows() {
        var map = treeified();
        assertThrows(UnsupportedOperationException.class, () -> map.remove(8));
    }

    @Test
    void removingAbsentKeyFromTreeBinReturnsNull() {
        var map = treeified();
        assertNull(map.remove(1)); // key 1 not present, bucket 1 empty -> null, no throw
    }

    @Test
    void chainRemoveStillWorks() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(1, "a");
        map.put(2, "b");
        assertEquals("a", map.remove(1));
        assertEquals(1, map.size());
    }

    @Test
    void iteratorRemoveOnTreeEntryThrows() {
        var map = treeified();
        Iterator<Map.Entry<Integer, String>> it = map.entrySet().iterator();
        it.next();
        assertThrows(UnsupportedOperationException.class, it::remove);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — `remove` on a tree bin currently does the Slice-1 linear unlink (no throw), so `removingKeyInTreeBinThrows` fails.

- [ ] **Step 3: Write minimal implementation**

At the top of `remove(Object key)`, before the chain scan, add the tree-bin guard:
```java
    @Override
    public V remove(Object key) {
        int h = hash(key);
        int i = indexFor(h, table.length);
        Node<K, V> head = table[i];
        if (head instanceof TreeNode) {
            @SuppressWarnings("unchecked")
            TreeNode<K, V> t = (TreeNode<K, V>) head;
            if (TreeNode.find(t.root(), h, key) == null) return null; // absent: no-op
            throw new UnsupportedOperationException(
                "remove from a tree bin is added in Slice 3");
        }
        Node<K, V> prev = null;
        for (Node<K, V> e = head; e != null; prev = e, e = e.next) {
            if (e.hash == h && Objects.equals(e.key, key)) {
                if (prev == null) table[i] = e.next;
                else prev.next = e.next;
                size--;
                modCount++;
                V old = e.value;
                emit(new Remove(key, old, i, snapshot()));
                return old;
            }
        }
        return null;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `TreeRemoveGuardTest` green; Slice 1 `TeachingHashMapRemoveTest` still passes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java src/test/java/com/gimlism/translucent/hashmap/core/TreeRemoveGuardTest.java
git commit -m "feat(core): guard tree-bin remove until Slice 3 delete lands"
```

---

### Task 10: Demo the treeify

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/demo/Demo.java`
- Modify: `src/test/java/com/gimlism/translucent/hashmap/demo/DemoTest.java`

**Interfaces:**
- Consumes: `TeachingHashMap`, `ConsoleEventLogger`.
- Produces: `Demo.run` additionally inserts enough colliding keys to trigger a treeify, so the output includes `TREEIFY` and `ROTATE` lines.

- [ ] **Step 1: Write the failing test**

Replace `DemoTest.java` body with:
```java
package com.gimlism.translucent.hashmap.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DemoTest {
    @Test
    void runShowsCollisionResizeAndTreeify() {
        var buffer = new ByteArrayOutputStream();
        Demo.run(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("COLLISION"), "expected collision, got:\n" + out);
        assertTrue(out.contains("RESIZE"), "expected resize, got:\n" + out);
        assertTrue(out.contains("TREEIFY"), "expected treeify, got:\n" + out);
        assertTrue(out.contains("ROTATE"), "expected rotation, got:\n" + out);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — output has no `TREEIFY`/`ROTATE` (demo only collides 3 keys).

- [ ] **Step 3: Write minimal implementation**

Replace the collision section of `Demo.run` so it inserts a 4th colliding key (triggering treeify at threshold 4):
```java
    static void run(PrintStream out) {
        var map = new TeachingHashMap<Integer, String>();
        map.addListener(new ConsoleEventLogger(out));

        out.println("== inserting keys that collide in bucket 0 until it treeifies ==");
        for (int k : new int[]{0, 8, 16, 24}) { // 4th key hits treeifyThreshold
            map.put(k, "v" + k);
        }

        out.println("== inserting more keys until the table resizes ==");
        for (int k = 1; k <= 7; k++) {
            map.put(k, "v" + k);
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `DemoTest` green (output shows COLLISION, TREEIFY, ROTATE, RESIZE). Optionally eyeball: `mvn -q compile exec:java -Dexec.mainClass=com.gimlism.translucent.hashmap.demo.Demo` (only if exec plugin configured; otherwise run from an IDE).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/demo/Demo.java src/test/java/com/gimlism/translucent/hashmap/demo/DemoTest.java
git commit -m "feat(demo): demonstrate treeify with rotation events"
```

---

## Self-Review

**Spec coverage:**
- Dual tree/chain structure, `table[i]` = head, root via parent climb → Task 1 + Task 6. ✓
- `TreeNode extends Node` with parent/left/right/red/seq → Task 1. ✓
- Ordering by hash then seq (insert); hash + equals + dual-subtree (find) → Task 2 (`cmp`), Task 3 (insert), Task 4 (find). ✓
- Tree-aware get/containsKey/put → Task 6. ✓
- Treeify at threshold, resize-instead below `minTreeifyCapacity`, fine-grained `Treeify`+`Rotation`+`Recolor` → Task 6. ✓
- No `Collision` for tree-bin inserts → Task 6 (`put` tree branch). ✓
- Tree-aware snapshot (`TreeSnapshot` with colours) → Task 7. ✓
- Resize split of tree bins → Task 8 (silent rebuild; deviation documented). ✓
- Iteration unchanged (follows `next`) → verified by Slice 1 `IteratorTest` continuing to pass after treeify; `TreeifyTest`/`TreeResizeTest` also read via `get`. ✓ (No new iteration code needed.)
- `remove` on tree bin throws; chain remove unchanged → Task 9. ✓
- Untreeify + RB delete deferred → not implemented (Slice 3). ✓
- Demo → Task 10. ✓

**Placeholder scan:** None. (Every test step contains complete, runnable code and real assertions.)

**Type consistency:** `TreeEventSink.rotated(Direction,Object)` / `recolored(Object,Color,Color)`; `TreeNode.{rotateLeft,rotateRight,insert,find,build,root,cmp,setColor}`; map `nextSeq`, `isTreeBin`, `sinkFor`, `treeifyBin`, `splitTreeBin`, `forceResize`, `treeSnapshot` — names used consistently across tasks. `TreeNodeSnapshot(key,value,color,left,right)` and `TreeSnapshot(root)` match the Slice-1 record definitions (`.key()/.value()/.color()/.left()/.right()`, `.root()`).

## Next slice

- **Slice 3:** red-black **deletion** with delete-fixup, **untreeify** (tree→chain below `untreeifyThreshold`, both removal-driven and retrofitted into resize-split), `Untreeify` event emission, and `prev` links on `TreeNode` for O(1) list unlinking. Removes the Task 9 guard.
