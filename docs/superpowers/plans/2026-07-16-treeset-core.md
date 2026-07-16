# TreeSet Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a comparison-ordered `TeachingTreeSet` (a `NavigableSet` backed by a red-black tree) as the 4th teaching structure, driven by a structure-neutral red-black kernel extracted into a new `substrate/rbtree` package.

**Architecture:** The RB *rebalancing* math (rotations, recolor, insert/delete fixup) is extracted to `substrate/rbtree`, generic over a self-typed node `RbNode<N extends RbNode<N>>` and driven by a neutral `RbEventSink`. `TeachingTreeSet` supplies the per-structure parts — ordering (natural or `Comparator`), the comparison walk (emitting `Compare` narration), node identity (`SetNode`), and the translation of neutral rotate/recolor callbacks into `SetEvent`s. The HashMap is untouched; its migration onto the shared kernel is a deferred follow-up.

**Tech Stack:** Java 21 (compiled on JDK 26 via `maven.compiler.release=21`), Maven, JUnit 5. Zero new dependencies.

## Global Constraints

- **Java version:** target Java 21 (`maven.compiler.release=21`); build/test on JDK 26. Verify with `mvn`, not the IDE — stale Eclipse/LSP diagnostics are a known false-positive source in this repo.
- **Package root:** `com.gimlism.translucent`.
- **Events are immutable and non-generic in their payload:** every `SetEvent` carries `Object` elements (mirrors `MapEvent`), and every event carries a whole-structure `SetSnapshot after()` taken at emission.
- **Snapshot-before-settled (named invariant):** any event emitted mid-operation MUST see fully committed state — colour flipped, links relinked, `size` updated — *before* `snapshot()` runs. Mid-`fixup` events build their snapshot by climbing `parent` to the true root, never from the possibly-stale `TeachingTreeSet.root` field.
- **Listeners must not throw and must not re-enter mutation:** dispatch goes through the shared `EventDispatcher`; every public mutator wraps its body in `beginMutation()` / `finally endMutation()`.
- **Reads narrate:** `contains` and the comparison-driven navigators (`lower`/`floor`/`ceiling`/`higher`) emit `Compare` frames; endpoint ops (`first`/`last`/`pollFirst`/`pollLast`) do not (no comparison occurs).
- **TDD:** write the failing test, watch it fail, implement minimally, watch it pass, commit. One logical change per commit.
- **Test source root:** `src/test/java/...`; main under `src/main/java/...`.

---

### Task 1: `substrate/rbtree` foundation — `Color`, `Direction`, `RbNode`, `RbEventSink`

**Files:**
- Create: `src/main/java/com/gimlism/translucent/substrate/rbtree/Color.java`
- Create: `src/main/java/com/gimlism/translucent/substrate/rbtree/Direction.java`
- Create: `src/main/java/com/gimlism/translucent/substrate/rbtree/RbNode.java`
- Create: `src/main/java/com/gimlism/translucent/substrate/rbtree/RbEventSink.java`
- Create: `src/main/java/com/gimlism/translucent/substrate/rbtree/package-info.java`
- Test: `src/test/java/com/gimlism/translucent/substrate/rbtree/RbNodeTest.java`

**Interfaces:**
- Produces: `enum Color { RED, BLACK }`; `enum Direction { LEFT, RIGHT }`; `abstract class RbNode<N extends RbNode<N>>` with **public** mutable fields `N parent, left, right; boolean red;`; `interface RbEventSink<N extends RbNode<N>>` with `void rotated(Direction dir, N pivot)`, `void recolored(N node, Color oldColor, Color newColor)`, and a `NONE` constant.
- Note: fields are `public` because the kernel (`substrate/rbtree`) and every consumer (e.g. `treeset/core`) live in different packages; the map's `TreeNode` used package-private fields only because its whole tree lived in one package. This is an internal teaching-substrate base, not general API.

- [ ] **Step 1: Write the failing test**

```java
package com.gimlism.translucent.substrate.rbtree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RbNodeTest {

    /** Minimal concrete node proving the self-type base is usable by a consumer. */
    static final class IntNode extends RbNode<IntNode> {
        final int key;
        IntNode(int key) { this.key = key; }
    }

    @Test
    void freshNodeHasNullLinksAndIsNotRed() {
        IntNode n = new IntNode(5);
        assertNull(n.parent);
        assertNull(n.left);
        assertNull(n.right);
        assertFalse(n.red);
    }

    @Test
    void linksAreTheConcreteNodeType() {
        IntNode a = new IntNode(1);
        IntNode b = new IntNode(2);
        a.right = b;
        b.parent = a;
        // No cast needed: a.right is IntNode, so .key is directly reachable.
        assertEquals(2, a.right.key);
        assertSame(a, b.parent);
    }

    @Test
    void noneSinkSwallowsCallbacks() {
        IntNode n = new IntNode(7);
        RbEventSink<IntNode> sink = RbEventSink.none();
        // Must not throw and must not record anything observable.
        sink.rotated(Direction.LEFT, n);
        sink.recolored(n, Color.RED, Color.BLACK);
    }

    @Test
    void recordingSinkSeesNodesUncast() {
        IntNode pivot = new IntNode(3);
        List<Integer> rotations = new ArrayList<>();
        RbEventSink<IntNode> sink = new RbEventSink<>() {
            @Override public void rotated(Direction dir, IntNode p) { rotations.add(p.key); }
            @Override public void recolored(IntNode node, Color o, Color n) { }
        };
        sink.rotated(Direction.RIGHT, pivot);
        assertEquals(List.of(3), rotations);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=RbNodeTest`
Expected: FAIL — compilation error, `Color`/`Direction`/`RbNode`/`RbEventSink` do not exist.

- [ ] **Step 3: Write minimal implementation**

`Color.java`:
```java
package com.gimlism.translucent.substrate.rbtree;

/** Red-black tree node colour, surfaced in snapshots so balancing is visible. */
public enum Color { RED, BLACK }
```

`Direction.java`:
```java
package com.gimlism.translucent.substrate.rbtree;

/** Rotation direction for red-black tree balancing events. */
public enum Direction { LEFT, RIGHT }
```

`RbNode.java`:
```java
package com.gimlism.translucent.substrate.rbtree;

/**
 * Base of a red-black tree node. Self-typed ({@code N extends RbNode<N>}) so the
 * shared {@link RedBlackTree} algorithm returns and assigns the concrete node type
 * with zero casts — the same F-bounded pattern as {@code Enum<E extends Enum<E>>}.
 *
 * <p>The link and colour fields are {@code public} because the rebalancing kernel
 * (this package) and each per-structure consumer (a different package) both
 * manipulate them directly. This is an internal teaching-substrate base, not a
 * general-purpose public API.
 */
public abstract class RbNode<N extends RbNode<N>> {
    public N parent;
    public N left;
    public N right;
    public boolean red;
}
```

`RbEventSink.java`:
```java
package com.gimlism.translucent.substrate.rbtree;

/**
 * Callback through which {@link RedBlackTree} reports structural changes, so a
 * consumer can translate them into its own event vocabulary. Keeps the RB algorithm
 * decoupled from any one structure's event model. The node is passed uncast (type
 * {@code N}) so the consumer can read whatever it stores on the node (an element, a
 * key, …).
 */
public interface RbEventSink<N extends RbNode<N>> {
    void rotated(Direction dir, N pivot);

    void recolored(N node, Color oldColor, Color newColor);

    /** A sink that ignores everything (used where events aren't wanted). */
    static <N extends RbNode<N>> RbEventSink<N> none() {
        return new RbEventSink<>() {
            @Override public void rotated(Direction dir, N pivot) { }
            @Override public void recolored(N node, Color oldColor, Color newColor) { }
        };
    }
}
```

`package-info.java`:
```java
/**
 * Structure-neutral red-black tree rebalancing kernel. The rotation, recolor, and
 * insert/delete fixup math operate over a self-typed {@link RbNode} and report
 * changes through an {@link RbEventSink}. Ordering, search, and node identity are
 * supplied by each consumer (e.g. {@code TeachingTreeSet}); only the rebalancing is
 * shared. First consumer: the TreeSet. The HashMap's own red-black tree is a
 * deferred migration candidate onto this kernel.
 */
package com.gimlism.translucent.substrate.rbtree;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=RbNodeTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/substrate/rbtree src/test/java/com/gimlism/translucent/substrate/rbtree/RbNodeTest.java
git commit -m "feat(rbtree): substrate RB foundation — RbNode, RbEventSink, Color, Direction"
```

---

### Task 2: `RedBlackTree` insert kernel

**Files:**
- Create: `src/main/java/com/gimlism/translucent/substrate/rbtree/RedBlackTree.java`
- Test: `src/test/java/com/gimlism/translucent/substrate/rbtree/RbInvariants.java` (test-only checker)
- Test: `src/test/java/com/gimlism/translucent/substrate/rbtree/RedBlackTreeInsertTest.java`

**Interfaces:**
- Consumes: `RbNode`, `RbEventSink`, `Color`, `Direction` (Task 1).
- Produces: `RedBlackTree` with `public static <N extends RbNode<N>> N insertFixup(N root, N x, RbEventSink<N> sink)` — restores RB invariants after `x` has already been linked as a **red leaf** at its BST position by the caller; returns the (possibly new) root. Also package-visible helpers `rotateLeft`, `rotateRight`, `setColor` used by Task 3.
- Produces (test): `RbInvariants.assertValid(RbNode<?> root)` and `RbInvariants.blackHeight(...)` — a generic checker reused by Task 3 tests.

- [ ] **Step 1: Write the failing test**

`RbInvariants.java` (test helper — generic over any `RbNode`):
```java
package com.gimlism.translucent.substrate.rbtree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Test-only checker for red-black tree invariants over any RbNode tree. */
final class RbInvariants {
    private RbInvariants() { }

    static void assertValid(RbNode<?> root) {
        if (root == null) return;
        assertFalse(root.red, "root must be black");
        blackHeight(root);
    }

    // Returns black-height; throws on a red-red violation, bad parent link, or unequal black-heights.
    static int blackHeight(RbNode<?> n) {
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
}
```

`RedBlackTreeInsertTest.java`:
```java
package com.gimlism.translucent.substrate.rbtree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedBlackTreeInsertTest {

    static final class IntNode extends RbNode<IntNode> {
        final int key;
        IntNode(int key) { this.key = key; }
    }

    /** BST-link x under root by int order, as a red leaf, then run insertFixup. */
    private IntNode add(IntNode root, int key, RbEventSink<IntNode> sink) {
        IntNode x = new IntNode(key);
        if (root == null) { x.red = false; return x; }
        IntNode p = root, parent = null;
        int dir = 0;
        while (p != null) { parent = p; dir = Integer.compare(key, p.key); p = dir < 0 ? p.left : p.right; }
        x.parent = parent;
        x.red = true;
        if (dir < 0) parent.left = x; else parent.right = x;
        return RedBlackTree.insertFixup(root, x, sink);
    }

    private static void inorder(IntNode n, List<Integer> out) {
        if (n == null) return;
        inorder(n.left, out); out.add(n.key); inorder(n.right, out);
    }

    @Test
    void firstInsertIsBlackRoot() {
        IntNode root = add(null, 10, RbEventSink.none());
        assertFalse(root.red, "lone root is black");
        RbInvariants.assertValid(root);
    }

    @Test
    void ascendingInsertsStayBalanced() {
        IntNode root = null;
        for (int k = 1; k <= 20; k++) root = add(root, k, RbEventSink.none());
        RbInvariants.assertValid(root);
        List<Integer> keys = new ArrayList<>();
        inorder(root, keys);
        List<Integer> expected = new ArrayList<>();
        for (int k = 1; k <= 20; k++) expected.add(k);
        assertEquals(expected, keys);
    }

    @Test
    void insertEmitsRecolorAndRotationEvents() {
        List<String> log = new ArrayList<>();
        RbEventSink<IntNode> sink = new RbEventSink<>() {
            @Override public void rotated(Direction dir, IntNode p) { log.add("rot:" + dir + ":" + p.key); }
            @Override public void recolored(IntNode n, Color o, Color c) { log.add("col:" + n.key + ":" + o + "->" + c); }
        };
        IntNode root = null;
        // 10,20,30 forces a left rotation about 10 (a classic RB fixup).
        for (int k : new int[]{10, 20, 30}) root = add(root, k, sink);
        RbInvariants.assertValid(root);
        assertEquals(20, root.key, "30 inserted -> rotate-left promotes 20 to root");
        assertTrue(log.stream().anyMatch(s -> s.startsWith("rot:LEFT")), "expected a left rotation: " + log);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=RedBlackTreeInsertTest`
Expected: FAIL — `RedBlackTree` does not exist.

- [ ] **Step 3: Write minimal implementation**

`RedBlackTree.java` (insert half — ports the map's `TreeNode` rotate/setColor/insertFixup, generalized to `RbNode<N>`):
```java
package com.gimlism.translucent.substrate.rbtree;

/**
 * Structure-neutral red-black rebalancing over a self-typed {@link RbNode}. Callers
 * own ordering, search, and node creation; they link a fresh red leaf at its BST
 * position and call {@link #insertFixup}, or locate a node and call
 * {@link #deleteFromTree}. This class touches only {@code parent/left/right/red} and
 * reports rotations/recolors through the {@link RbEventSink}.
 *
 * <p>Every colour flip and relink is applied BEFORE the sink fires, so a listener's
 * snapshot is a true after-image (the "snapshot-before-settled" discipline).
 */
public final class RedBlackTree {
    private RedBlackTree() { }

    /** Flip a node's colour, reporting only real changes (mutating before emitting). */
    static <N extends RbNode<N>> void setColor(N n, boolean red, RbEventSink<N> sink) {
        if (n.red != red) {
            Color oldColor = n.red ? Color.RED : Color.BLACK;
            Color newColor = red ? Color.RED : Color.BLACK;
            n.red = red;
            sink.recolored(n, oldColor, newColor);
        }
    }

    static <N extends RbNode<N>> N rotateLeft(N root, N p, RbEventSink<N> sink) {
        N r = p.right;
        p.right = r.left;
        if (r.left != null) r.left.parent = p;
        r.parent = p.parent;
        if (p.parent == null) root = r;
        else if (p == p.parent.left) p.parent.left = r;
        else p.parent.right = r;
        r.left = p;
        p.parent = r;
        sink.rotated(Direction.LEFT, p);
        return root;
    }

    static <N extends RbNode<N>> N rotateRight(N root, N p, RbEventSink<N> sink) {
        N l = p.left;
        p.left = l.right;
        if (l.right != null) l.right.parent = p;
        l.parent = p.parent;
        if (p.parent == null) root = l;
        else if (p == p.parent.right) p.parent.right = l;
        else p.parent.left = l;
        l.right = p;
        p.parent = l;
        sink.rotated(Direction.RIGHT, p);
        return root;
    }

    /**
     * Restore red-black invariants after {@code x} was linked as a red leaf at its
     * BST position. Returns the (possibly new) root.
     */
    public static <N extends RbNode<N>> N insertFixup(N root, N x, RbEventSink<N> sink) {
        while (x.parent != null && x.parent.red) {
            N p = x.parent;
            N g = p.parent; // p is red => not root => g != null
            if (p == g.left) {
                N u = g.right;
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
                N u = g.left;
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
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=RedBlackTreeInsertTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/substrate/rbtree/RedBlackTree.java src/test/java/com/gimlism/translucent/substrate/rbtree/RbInvariants.java src/test/java/com/gimlism/translucent/substrate/rbtree/RedBlackTreeInsertTest.java
git commit -m "feat(rbtree): generic insertFixup + rotations, invariant-checked over a test node"
```

---

### Task 3: `RedBlackTree` delete kernel

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/substrate/rbtree/RedBlackTree.java` (add delete methods)
- Test: `src/test/java/com/gimlism/translucent/substrate/rbtree/RedBlackTreeDeleteTest.java`

**Interfaces:**
- Consumes: Task 2's `RedBlackTree.insertFixup`, `rotateLeft/Right`, `setColor`; Task 2's test `RbInvariants`.
- Produces: `public static <N extends RbNode<N>> N deleteFromTree(N root, N z, RbEventSink<N> sink)` — removes the already-located node `z` (successor-splice + fixup, link-only), returns the new root or null.

- [ ] **Step 1: Write the failing test**

```java
package com.gimlism.translucent.substrate.rbtree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedBlackTreeDeleteTest {

    static final class IntNode extends RbNode<IntNode> {
        final int key;
        IntNode(int key) { this.key = key; }
    }

    private IntNode add(IntNode root, int key) {
        IntNode x = new IntNode(key);
        if (root == null) { x.red = false; return x; }
        IntNode p = root, parent = null; int dir = 0;
        while (p != null) { parent = p; dir = Integer.compare(key, p.key); p = dir < 0 ? p.left : p.right; }
        x.parent = parent; x.red = true;
        if (dir < 0) parent.left = x; else parent.right = x;
        return RedBlackTree.insertFixup(root, x, RbEventSink.none());
    }

    private IntNode find(IntNode root, int key) {
        IntNode p = root;
        while (p != null) {
            int c = Integer.compare(key, p.key);
            if (c == 0) return p;
            p = c < 0 ? p.left : p.right;
        }
        return null;
    }

    private static void inorder(IntNode n, List<Integer> out) {
        if (n == null) return;
        inorder(n.left, out); out.add(n.key); inorder(n.right, out);
    }

    @Test
    void deleteLeafKeepsInvariants() {
        IntNode root = null;
        for (int k : new int[]{10, 5, 15, 3}) root = add(root, k);
        root = RedBlackTree.deleteFromTree(root, find(root, 3), RbEventSink.none());
        RbInvariants.assertValid(root);
        List<Integer> keys = new ArrayList<>();
        inorder(root, keys);
        assertEquals(List.of(5, 10, 15), keys);
    }

    @Test
    void deleteInternalTwoChildNodeSplicesSuccessor() {
        IntNode root = null;
        for (int k : new int[]{20, 10, 30, 5, 15, 25, 35}) root = add(root, k);
        root = RedBlackTree.deleteFromTree(root, find(root, 20), RbEventSink.none());
        RbInvariants.assertValid(root);
        List<Integer> keys = new ArrayList<>();
        inorder(root, keys);
        assertEquals(List.of(5, 10, 15, 25, 30, 35), keys);
    }

    @Test
    void deleteEveryNodeInRandomOrderStaysValidThenEmpty() {
        int[] ins = {50, 30, 70, 20, 40, 60, 80, 10, 25, 35, 45, 55, 65, 75, 85};
        int[] del = {70, 20, 50, 85, 10, 40, 60, 30, 80, 25, 55, 35, 65, 45, 75};
        IntNode root = null;
        for (int k : ins) root = add(root, k);
        List<Integer> remaining = new ArrayList<>();
        for (int k : ins) remaining.add(k);
        for (int k : del) {
            root = RedBlackTree.deleteFromTree(root, find(root, k), RbEventSink.none());
            remaining.remove((Integer) k);
            RbInvariants.assertValid(root);
            List<Integer> keys = new ArrayList<>();
            inorder(root, keys);
            List<Integer> sorted = new ArrayList<>(remaining);
            sorted.sort(null);
            assertEquals(sorted, keys, "after deleting " + k);
        }
        assertNull(root, "tree empty after all deletes");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=RedBlackTreeDeleteTest`
Expected: FAIL — `deleteFromTree` does not exist.

- [ ] **Step 3: Write minimal implementation**

Append to `RedBlackTree.java` (before the closing brace) — ports the map's `deleteFromTree`/`transplant`/`minimum`/`deleteFixup`, generalized to `RbNode<N>`:
```java
    /**
     * Remove {@code z} from the tree (pointer-based), restoring invariants. Returns
     * the new root, or null if the tree becomes empty. Touches only tree links.
     */
    public static <N extends RbNode<N>> N deleteFromTree(N root, N z, RbEventSink<N> sink) {
        N y = z;
        boolean yWasBlack = !y.red;
        N x;
        N xParent;

        if (z.left == null) {
            x = z.right;
            xParent = z.parent;
            root = transplant(root, z, z.right);
        } else if (z.right == null) {
            x = z.left;
            xParent = z.parent;
            root = transplant(root, z, z.left);
        } else {
            y = minimum(z.right);
            yWasBlack = !y.red;
            x = y.right;
            if (y.parent == z) {
                xParent = y;
            } else {
                xParent = y.parent;
                root = transplant(root, y, y.right);
                y.right = z.right;
                y.right.parent = y;
            }
            root = transplant(root, z, y);
            y.left = z.left;
            y.left.parent = y;
            setColor(y, z.red, sink);
        }

        if (yWasBlack) {
            root = deleteFixup(root, x, xParent, sink);
        }
        z.parent = null;
        z.left = null;
        z.right = null;
        return root;
    }

    private static <N extends RbNode<N>> N transplant(N root, N u, N v) {
        if (u.parent == null) root = v;
        else if (u == u.parent.left) u.parent.left = v;
        else u.parent.right = v;
        if (v != null) v.parent = u.parent;
        return root;
    }

    private static <N extends RbNode<N>> N minimum(N n) {
        while (n.left != null) n = n.left;
        return n;
    }

    private static <N extends RbNode<N>> N deleteFixup(N root, N x, N xParent, RbEventSink<N> sink) {
        while (x != root && (x == null || !x.red)) {
            if (x == xParent.left) {
                N w = xParent.right;
                if (w != null && w.red) {
                    setColor(w, false, sink);
                    setColor(xParent, true, sink);
                    root = rotateLeft(root, xParent, sink);
                    w = xParent.right;
                }
                if (w == null
                        || ((w.left == null || !w.left.red) && (w.right == null || !w.right.red))) {
                    if (w != null) setColor(w, true, sink);
                    x = xParent;
                    xParent = x.parent;
                } else {
                    if (w.right == null || !w.right.red) {
                        if (w.left != null) setColor(w.left, false, sink);
                        setColor(w, true, sink);
                        root = rotateRight(root, w, sink);
                        w = xParent.right;
                    }
                    setColor(w, xParent.red, sink);
                    setColor(xParent, false, sink);
                    if (w.right != null) setColor(w.right, false, sink);
                    root = rotateLeft(root, xParent, sink);
                    x = root;
                    xParent = null;
                }
            } else {
                N w = xParent.left;
                if (w != null && w.red) {
                    setColor(w, false, sink);
                    setColor(xParent, true, sink);
                    root = rotateRight(root, xParent, sink);
                    w = xParent.left;
                }
                if (w == null
                        || ((w.right == null || !w.right.red) && (w.left == null || !w.left.red))) {
                    if (w != null) setColor(w, true, sink);
                    x = xParent;
                    xParent = x.parent;
                } else {
                    if (w.left == null || !w.left.red) {
                        if (w.right != null) setColor(w.right, false, sink);
                        setColor(w, true, sink);
                        root = rotateLeft(root, w, sink);
                        w = xParent.left;
                    }
                    setColor(w, xParent.red, sink);
                    setColor(xParent, false, sink);
                    if (w.left != null) setColor(w.left, false, sink);
                    root = rotateRight(root, xParent, sink);
                    x = root;
                    xParent = null;
                }
            }
        }
        if (x != null) setColor(x, false, sink);
        return root;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=RedBlackTreeDeleteTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/substrate/rbtree/RedBlackTree.java src/test/java/com/gimlism/translucent/substrate/rbtree/RedBlackTreeDeleteTest.java
git commit -m "feat(rbtree): generic deleteFromTree + delete-fixup, adversarially invariant-checked"
```

---

### Task 4: TreeSet event vocabulary + snapshot + formatter

**Files:**
- Create: `src/main/java/com/gimlism/translucent/treeset/events/SetNodeSnapshot.java`
- Create: `src/main/java/com/gimlism/translucent/treeset/events/SetSnapshot.java`
- Create: `src/main/java/com/gimlism/translucent/treeset/events/SetEvent.java`
- Create: `src/main/java/com/gimlism/translucent/treeset/events/Compare.java`
- Create: `src/main/java/com/gimlism/translucent/treeset/events/Add.java`
- Create: `src/main/java/com/gimlism/translucent/treeset/events/Remove.java`
- Create: `src/main/java/com/gimlism/translucent/treeset/events/Rotation.java`
- Create: `src/main/java/com/gimlism/translucent/treeset/events/Recolor.java`
- Create: `src/main/java/com/gimlism/translucent/treeset/events/SetEventListener.java`
- Create: `src/main/java/com/gimlism/translucent/treeset/events/SetEventFormatter.java`
- Create: `src/main/java/com/gimlism/translucent/treeset/events/package-info.java`
- Test: `src/test/java/com/gimlism/translucent/treeset/events/SetEventTest.java`

**Interfaces:**
- Consumes: `StructureEvent`, `StructureSnapshot`, `StructureEventListener` (substrate/events); `Color`, `Direction` (substrate/rbtree).
- Produces:
  - `record SetNodeSnapshot(Object element, boolean red, SetNodeSnapshot left, SetNodeSnapshot right)`
  - `record SetSnapshot(SetNodeSnapshot root, int size) implements StructureSnapshot` — `root` nullable (empty set), `size >= 0`.
  - `sealed interface SetEvent extends StructureEvent permits Compare, Add, Remove, Rotation, Recolor { SetSnapshot after(); }`
  - `record Compare(Object element, Direction went, boolean found, SetSnapshot after) implements SetEvent` — `went` is `LEFT`/`RIGHT` for the branch taken; `null` when `found` is true (landed on an equal element).
  - `record Add(Object element, SetSnapshot after) implements SetEvent`
  - `record Remove(Object element, SetSnapshot after) implements SetEvent`
  - `record Rotation(Direction dir, Object pivot, SetSnapshot after) implements SetEvent`
  - `record Recolor(Object element, Color oldColor, Color newColor, SetSnapshot after) implements SetEvent`
  - `interface SetEventListener extends StructureEventListener<SetEvent> {}`
  - `SetEventFormatter` with `static String format(SetEvent e)`.

- [ ] **Step 1: Write the failing test**

```java
package com.gimlism.translucent.treeset.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.substrate.rbtree.Color;
import com.gimlism.translucent.substrate.rbtree.Direction;
import com.gimlism.translucent.substrate.events.StructureEvent;
import org.junit.jupiter.api.Test;

class SetEventTest {

    private static SetSnapshot leaf(int e) {
        return new SetSnapshot(new SetNodeSnapshot(e, false, null, null), 1);
    }

    @Test
    void snapshotRejectsNegativeSize() {
        assertThrows(IllegalArgumentException.class, () -> new SetSnapshot(null, -1));
    }

    @Test
    void emptySnapshotHasNullRoot() {
        SetSnapshot s = new SetSnapshot(null, 0);
        assertNull(s.root());
        assertEquals(0, s.size());
    }

    @Test
    void eventsExposeCovariantAfterSnapshot() {
        SetEvent add = new Add(7, leaf(7));
        StructureEvent asBase = add;
        assertTrue(asBase.after() instanceof SetSnapshot);
        assertEquals(7, ((Add) add).element());
    }

    @Test
    void compareCarriesDirectionAndFoundFlag() {
        Compare walked = new Compare(5, Direction.LEFT, false, leaf(5));
        assertEquals(Direction.LEFT, walked.went());
        Compare landed = new Compare(5, null, true, leaf(5));
        assertNull(landed.went());
        assertTrue(landed.found());
    }

    @Test
    void formatterCaptionsEachEvent() {
        assertTrue(SetEventFormatter.format(new Add(7, leaf(7))).contains("7"));
        assertTrue(SetEventFormatter.format(new Remove(7, leaf(7))).toLowerCase().contains("remove"));
        assertTrue(SetEventFormatter.format(new Rotation(Direction.LEFT, 3, leaf(3))).toLowerCase().contains("rotate"));
        assertTrue(SetEventFormatter.format(new Recolor(3, Color.RED, Color.BLACK, leaf(3))).contains("BLACK"));
        assertTrue(SetEventFormatter.format(new Compare(5, Direction.LEFT, false, leaf(5))).toLowerCase().contains("compare"));
        assertTrue(SetEventFormatter.format(new Compare(5, null, true, leaf(5))).toLowerCase().contains("found"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=SetEventTest`
Expected: FAIL — event/snapshot types do not exist.

- [ ] **Step 3: Write minimal implementation**

`SetNodeSnapshot.java`:
```java
package com.gimlism.translucent.treeset.events;

/**
 * Immutable snapshot of one binary red-black tree node: its {@code element}, its
 * colour, and its two children (null when absent). The binary, colour-carrying shape
 * is the counterpart of the trie's N-ary {@code TrieNodeSnapshot}.
 */
public record SetNodeSnapshot(Object element, boolean red, SetNodeSnapshot left, SetNodeSnapshot right) {}
```

`SetSnapshot.java`:
```java
package com.gimlism.translucent.treeset.events;

import com.gimlism.translucent.substrate.events.StructureSnapshot;

/** Immutable whole-set state: the tree {@code root} (null when empty) and element count. */
public record SetSnapshot(SetNodeSnapshot root, int size) implements StructureSnapshot {
    public SetSnapshot {
        if (size < 0) {
            throw new IllegalArgumentException("size (" + size + ") must be >= 0");
        }
    }
}
```

`SetEvent.java`:
```java
package com.gimlism.translucent.treeset.events;

import com.gimlism.translucent.substrate.events.StructureEvent;

/**
 * An immutable record of a single {@code TeachingTreeSet} step. The vocabulary blends
 * the trie's node-link narration with the map's red-black rebalance events:
 * <ul>
 *   <li><b>add:</b> {@code Compare* -> Add -> (Rotation|Recolor)*} — the comparison walk,
 *       then the new element (linked before rebalancing), then any rebalance steps.</li>
 *   <li><b>remove:</b> {@code Compare* -> (Rotation|Recolor)* -> Remove} — the walk to the
 *       node, the rebalance, then the terminal removal marker.</li>
 *   <li><b>contains / navigation:</b> {@code Compare*} only (traversal narration; no
 *       structural change).</li>
 * </ul>
 * Unlike the trie there is no {@code CreateNode}: every node <em>is</em> an element,
 * so {@link Add} is the node creation.
 */
public sealed interface SetEvent extends StructureEvent
        permits Compare, Add, Remove, Rotation, Recolor {
    /** Whole-set snapshot at emission (covariant narrowing of {@link StructureEvent#after()}). */
    @Override
    SetSnapshot after();
}
```

`Compare.java`:
```java
package com.gimlism.translucent.treeset.events;

import com.gimlism.translucent.substrate.rbtree.Direction;

/**
 * The comparison walk visited a node holding {@code element}. Traversal narration: the
 * snapshot is unchanged from the previous frame; the focus advances. {@code went} is the
 * branch taken ({@code LEFT}/{@code RIGHT}); on the terminal comparison that lands on an
 * equal element, {@code found} is true and {@code went} is {@code null}.
 */
public record Compare(Object element, Direction went, boolean found, SetSnapshot after) implements SetEvent {}
```

`Add.java`:
```java
package com.gimlism.translucent.treeset.events;

/** A new {@code element} was linked at its BST position (before rebalancing). */
public record Add(Object element, SetSnapshot after) implements SetEvent {}
```

`Remove.java`:
```java
package com.gimlism.translucent.treeset.events;

/** {@code element} was removed (terminal marker, after any rebalancing). */
public record Remove(Object element, SetSnapshot after) implements SetEvent {}
```

`Rotation.java`:
```java
package com.gimlism.translucent.treeset.events;

import com.gimlism.translucent.substrate.rbtree.Direction;

/** A red-black rebalancing rotation about {@code pivot}, in direction {@code dir}. */
public record Rotation(Direction dir, Object pivot, SetSnapshot after) implements SetEvent {}
```

`Recolor.java`:
```java
package com.gimlism.translucent.treeset.events;

import com.gimlism.translucent.substrate.rbtree.Color;

/** A red-black rebalancing recolour of the node holding {@code element}. */
public record Recolor(Object element, Color oldColor, Color newColor, SetSnapshot after) implements SetEvent {}
```

`SetEventListener.java`:
```java
package com.gimlism.translucent.treeset.events;

import com.gimlism.translucent.substrate.events.StructureEventListener;

/**
 * Synchronous consumer of a {@code TeachingTreeSet}'s event stream.
 *
 * <p><b>Reads narrate.</b> Unlike the other structures, a TreeSet emits
 * {@link Compare} frames during read operations ({@code contains}, the relative
 * navigators). The no-mutation-during-dispatch rule therefore applies to
 * read-narration events too: a listener must not mutate the set from within
 * {@code onEvent}, even one fired by a read — doing so would corrupt the in-progress
 * comparison walk (reads are not wrapped in the re-entrancy guard, so it would not be
 * caught). Read, record, or render — do not mutate, and do not throw.
 */
public interface SetEventListener extends StructureEventListener<SetEvent> {}
```

`SetEventFormatter.java`:
```java
package com.gimlism.translucent.treeset.events;

/** Human-readable one-line captions for {@link SetEvent}s (mirrors MapEventFormatter). */
public final class SetEventFormatter {
    private SetEventFormatter() { }

    public static String format(SetEvent e) {
        return switch (e) {
            case Compare c -> c.found()
                    ? "compare " + c.element() + " -> found"
                    : "compare " + c.element() + " -> go " + c.went();
            case Add a -> "add " + a.element();
            case Remove r -> "remove " + r.element();
            case Rotation r -> "rotate " + r.dir() + " about " + r.pivot();
            case Recolor r -> "recolor " + r.element() + " " + r.oldColor() + " -> " + r.newColor();
        };
    }
}
```

`package-info.java`:
```java
/**
 * The {@code TeachingTreeSet} event vocabulary: the comparison-walk narration
 * ({@link com.gimlism.translucent.treeset.events.Compare}), the structural
 * {@link com.gimlism.translucent.treeset.events.Add}/
 * {@link com.gimlism.translucent.treeset.events.Remove} markers, and the red-black
 * rebalance events ({@link com.gimlism.translucent.treeset.events.Rotation}/
 * {@link com.gimlism.translucent.treeset.events.Recolor}). Every event carries an
 * immutable whole-set {@link com.gimlism.translucent.treeset.events.SetSnapshot}.
 */
package com.gimlism.translucent.treeset.events;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=SetEventTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/treeset/events src/test/java/com/gimlism/translucent/treeset/events/SetEventTest.java
git commit -m "feat(treeset): event vocabulary — Compare/Add/Remove/Rotation/Recolor + snapshot + formatter"
```

---

### Task 5: `SetNode` + `TeachingTreeSet` — add / contains / size / clear / in-order iterator

**Files:**
- Create: `src/main/java/com/gimlism/translucent/treeset/core/SetNode.java`
- Create: `src/main/java/com/gimlism/translucent/treeset/core/TeachingTreeSet.java`
- Create: `src/main/java/com/gimlism/translucent/treeset/core/package-info.java`
- Create: `src/main/java/com/gimlism/translucent/treeset/package-info.java`
- Test: `src/test/java/com/gimlism/translucent/treeset/core/SetInvariants.java` (test helper)
- Test: `src/test/java/com/gimlism/translucent/treeset/core/TeachingTreeSetAddTest.java`

**Interfaces:**
- Consumes: `RbNode`, `RbEventSink`, `RedBlackTree.insertFixup`, `Color`, `Direction` (substrate/rbtree); all Task 4 events; `EventDispatcher`, `StructureEventListener` (substrate/events).
- Produces:
  - `class SetNode<E> extends RbNode<SetNode<E>>` with a `final E element;` and a constructor `SetNode(E element)`.
  - `class TeachingTreeSet<E> extends AbstractSet<E> implements NavigableSet<E>` — this task implements `add`, `contains`, `size`, `clear`, `iterator()` (in-order, fail-fast), `comparator()`, both constructors, plus package-visible `snapshot()` and `addListener`/`removeListener`. **All other `NavigableSet` methods are stubbed** `throw new UnsupportedOperationException()` in this task and filled in by Tasks 6–9. Produces `SetSnapshot snapshot()` and the private `compare(E,E)` helper contract for later tasks.

- [ ] **Step 1: Write the failing test**

`SetInvariants.java`:
```java
package com.gimlism.translucent.treeset.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Test-only red-black + BST-ordering checker over a TeachingTreeSet's tree. */
final class SetInvariants {
    private SetInvariants() { }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void assertValid(TeachingTreeSet<?> set) {
        SetNode<?> root = set.rootForTest();
        if (root == null) return;
        assertFalse(root.red, "root must be black");
        blackHeight(root);
        List<Object> keys = new ArrayList<>();
        inorder(root, keys);
        // The ordering invariant is "ascending by the SET'S order", not natural order:
        // use the set's comparator when present, else fall back to natural Comparable.
        Comparator cmp = set.comparator();
        for (int i = 1; i < keys.size(); i++) {
            int c = cmp != null
                    ? cmp.compare(keys.get(i - 1), keys.get(i))
                    : ((Comparable) keys.get(i - 1)).compareTo(keys.get(i));
            assertFalse(c >= 0, "in-order keys must strictly ascend by the set's order: " + keys);
        }
    }

    private static int blackHeight(SetNode<?> n) {
        if (n == null) return 1;
        if (n.red) {
            assertFalse(n.left != null && n.left.red, "red-red left");
            assertFalse(n.right != null && n.right.red, "red-red right");
        }
        if (n.left != null) assertEquals(n, n.left.parent, "left parent link");
        if (n.right != null) assertEquals(n, n.right.parent, "right parent link");
        int lh = blackHeight(n.left);
        int rh = blackHeight(n.right);
        assertEquals(lh, rh, "black-height mismatch");
        return lh + (n.red ? 0 : 1);
    }

    private static void inorder(SetNode<?> n, List<Object> out) {
        if (n == null) return;
        inorder(n.left, out); out.add(n.element); inorder(n.right, out);
    }
}
```

`TeachingTreeSetAddTest.java`:
```java
package com.gimlism.translucent.treeset.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.treeset.events.Add;
import com.gimlism.translucent.treeset.events.Compare;
import com.gimlism.translucent.treeset.events.SetEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class TeachingTreeSetAddTest {

    private List<SetEvent> record(TeachingTreeSet<Integer> set) {
        List<SetEvent> log = new ArrayList<>();
        set.addListener(log::add);
        return log;
    }

    @Test
    void addReturnsTrueThenFalseOnDuplicate() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        assertTrue(set.add(5));
        assertFalse(set.add(5));
        assertEquals(1, set.size());
    }

    @Test
    void iteratesInSortedOrder() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{8, 3, 10, 1, 6, 14, 4, 7, 13}) set.add(k);
        List<Integer> out = new ArrayList<>(set);
        List<Integer> expected = new ArrayList<>(out);
        Collections.sort(expected);
        assertEquals(expected, out);
        SetInvariants.assertValid(set);
    }

    @Test
    void staysBalancedUnderManyInserts() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k = 0; k < 200; k++) set.add(k);
        SetInvariants.assertValid(set);
        assertEquals(200, set.size());
    }

    @Test
    void firstEventOnEmptyAddIsAddNotCompare() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        List<SetEvent> log = record(set);
        set.add(42);
        assertInstanceOf(Add.class, log.get(0), "first-ever add emits Add (no Compare on an empty tree)");
    }

    @Test
    void secondAddNarratesCompareThenAdd() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        set.add(10);
        List<SetEvent> log = record(set);
        set.add(5);
        assertInstanceOf(Compare.class, log.get(0), "second add compares against the root first");
        assertTrue(log.stream().anyMatch(e -> e instanceof Add), "then adds");
    }

    @Test
    void duplicateAddNarratesCompareButEmitsNoAdd() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        set.add(10);
        List<SetEvent> log = record(set);
        set.add(10);
        assertTrue(log.stream().anyMatch(e -> e instanceof Compare c && c.found()), "compare finds it");
        assertFalse(log.stream().anyMatch(e -> e instanceof Add), "no Add for a duplicate");
    }

    @Test
    void containsNarratesCompareWithoutStructuralEvent() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{10, 5, 15}) set.add(k);
        List<SetEvent> log = record(set);
        assertTrue(set.contains(15));
        assertTrue(log.stream().allMatch(e -> e instanceof Compare), "reads emit only Compare frames");
        assertTrue(log.stream().anyMatch(e -> e instanceof Compare c && c.found()));
    }

    @Test
    void containsOnEmptySetEmitsNothing() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        List<SetEvent> log = record(set);
        assertFalse(set.contains(1));
        assertTrue(log.isEmpty(), "no walk starts on an empty set");
    }

    @Test
    void everyEventSnapshotIsATrueAfterImage() {
        // Snapshot-before-settled: the Add frame already contains the new element,
        // and mid-fixup Rotation/Recolor frames render the rebalanced tree (climbed
        // from the event's node, not a stale cached root).
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        List<SetEvent> log = record(set);
        for (int k : new int[]{10, 20, 30}) set.add(k); // 30 forces a rotation
        for (SetEvent e : log) {
            if (e instanceof Add a) {
                assertTrue(containsElement(a.after().root(), a.element()),
                        "Add frame must already contain the added element");
            }
        }
        // Last frame is the fully balanced tree: root 20.
        SetEvent last = log.get(log.size() - 1);
        assertEquals(20, last.after().root().element());
        SetInvariants.assertValid(set);
    }

    private static boolean containsElement(
            com.gimlism.translucent.treeset.events.SetNodeSnapshot n, Object e) {
        if (n == null) return false;
        return n.element().equals(e) || containsElement(n.left(), e) || containsElement(n.right(), e);
    }

    @Test
    void comparatorControlsOrder() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>(Comparator.reverseOrder());
        for (int k : new int[]{1, 2, 3, 4, 5}) set.add(k);
        assertEquals(List.of(5, 4, 3, 2, 1), new ArrayList<>(set));
        assertEquals(Comparator.reverseOrder(), set.comparator());
        SetInvariants.assertValid(set);
    }

    @Test
    void matchesJdkTreeSetIterationOnAdds() {
        TeachingTreeSet<Integer> mine = new TeachingTreeSet<>();
        TreeSet<Integer> oracle = new TreeSet<>();
        int[] xs = {50, 20, 80, 10, 30, 70, 90, 25, 5, 60, 40, 85, 15};
        for (int x : xs) { mine.add(x); oracle.add(x); }
        assertEquals(new ArrayList<>(oracle), new ArrayList<>(mine));
        assertEquals(oracle.size(), mine.size());
    }

    @Test
    void clearEmptiesTheSet() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{1, 2, 3}) set.add(k);
        set.clear();
        assertEquals(0, set.size());
        assertTrue(set.isEmpty());
        assertFalse(set.iterator().hasNext());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=TeachingTreeSetAddTest`
Expected: FAIL — `TeachingTreeSet` / `SetNode` do not exist.

- [ ] **Step 3: Write minimal implementation**

`SetNode.java`:
```java
package com.gimlism.translucent.treeset.core;

import com.gimlism.translucent.substrate.rbtree.RbNode;

/**
 * A red-black tree node holding one set element. Self-typed through
 * {@link RbNode} so the shared rebalancing kernel manipulates {@code SetNode} links
 * directly. No value, no insertion thread — unlike the map's {@code TreeNode}, the
 * element is the whole payload.
 */
class SetNode<E> extends RbNode<SetNode<E>> {
    final E element;

    SetNode(E element) {
        this.element = element;
    }
}
```

`TeachingTreeSet.java`:
```java
package com.gimlism.translucent.treeset.core;

import com.gimlism.translucent.substrate.events.EventDispatcher;
import com.gimlism.translucent.substrate.events.StructureEventListener;
import com.gimlism.translucent.substrate.rbtree.Color;
import com.gimlism.translucent.substrate.rbtree.Direction;
import com.gimlism.translucent.substrate.rbtree.RbEventSink;
import com.gimlism.translucent.substrate.rbtree.RedBlackTree;
import com.gimlism.translucent.treeset.events.Add;
import com.gimlism.translucent.treeset.events.Compare;
import com.gimlism.translucent.treeset.events.Recolor;
import com.gimlism.translucent.treeset.events.Rotation;
import com.gimlism.translucent.treeset.events.SetEvent;
import com.gimlism.translucent.treeset.events.SetNodeSnapshot;
import com.gimlism.translucent.treeset.events.SetSnapshot;
import java.util.AbstractSet;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.SortedSet;

/**
 * A teaching {@link NavigableSet} backed by a red-black tree, ordered by natural
 * ordering or a supplied {@link Comparator}. Every mutation and every comparison is
 * observable through an immutable {@link SetEvent} stream. Rebalancing is delegated to
 * the shared {@link RedBlackTree} kernel; this class owns ordering, the comparison
 * walk, node identity, and event translation.
 */
public class TeachingTreeSet<E> extends AbstractSet<E> implements NavigableSet<E> {

    private SetNode<E> root;
    private int size;
    int modCount;
    private final Comparator<? super E> comparator;

    private final EventDispatcher<SetEvent> dispatcher = new EventDispatcher<>("set");

    /** Translates the kernel's neutral rotate/recolor callbacks into SetEvents, snapshotting the live tree. */
    private final RbEventSink<SetNode<E>> sink = new RbEventSink<>() {
        @Override public void rotated(Direction dir, SetNode<E> pivot) {
            emit(new Rotation(dir, pivot.element, snapshotFrom(pivot)));
        }
        @Override public void recolored(SetNode<E> node, Color oldColor, Color newColor) {
            emit(new Recolor(node.element, oldColor, newColor, snapshotFrom(node)));
        }
    };

    public TeachingTreeSet() {
        this.comparator = null;
    }

    public TeachingTreeSet(Comparator<? super E> comparator) {
        this.comparator = comparator;
    }

    @SuppressWarnings("unchecked")
    private int compare(E a, E b) {
        return comparator != null ? comparator.compare(a, b) : ((Comparable<? super E>) a).compareTo(b);
    }

    @Override
    public Comparator<? super E> comparator() {
        return comparator;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean contains(Object o) {
        @SuppressWarnings("unchecked")
        E e = (E) o;
        SetNode<E> node = root;
        while (node != null) {
            int c = compare(e, node.element);
            if (c == 0) {
                emit(new Compare(node.element, null, true, snapshot()));
                return true;
            }
            Direction went = c < 0 ? Direction.LEFT : Direction.RIGHT;
            emit(new Compare(node.element, went, false, snapshot()));
            node = c < 0 ? node.left : node.right;
        }
        return false;
    }

    @Override
    public boolean add(E e) {
        beginMutation();
        try {
            if (root == null) {
                root = new SetNode<>(e);
                root.red = false;      // black root, committed before the frame
                size++;
                modCount++;
                emit(new Add(e, snapshot()));
                return true;
            }
            SetNode<E> node = root;
            SetNode<E> parent = null;
            int dir = 0;
            while (node != null) {
                int c = compare(e, node.element);
                if (c == 0) {
                    emit(new Compare(node.element, null, true, snapshot()));
                    return false;      // already present -> no Add
                }
                Direction went = c < 0 ? Direction.LEFT : Direction.RIGHT;
                emit(new Compare(node.element, went, false, snapshot()));
                parent = node;
                dir = c;
                node = c < 0 ? node.left : node.right;
            }
            SetNode<E> x = new SetNode<>(e);
            x.parent = parent;
            x.red = true;
            if (dir < 0) parent.left = x; else parent.right = x;
            size++;
            modCount++;
            emit(new Add(e, snapshot()));          // committed link + size before the frame
            root = RedBlackTree.insertFixup(root, x, sink);
            return true;
        } finally {
            dispatcher.endMutation();
        }
    }

    /**
     * Reset to empty in O(1). Guarded like every other mutator (a listener may not
     * clear the set mid-dispatch). Emits no event by design — the vocabulary has no
     * bulk-clear event; a visualizer sees the next frame as the empty tree.
     */
    @Override
    public void clear() {
        beginMutation();
        try {
            root = null;
            size = 0;
            modCount++;
        } finally {
            dispatcher.endMutation();
        }
    }

    @Override
    public Iterator<E> iterator() {
        return new AscendingIterator();
    }

    /** Leftmost (minimum) node, or null when empty. */
    private SetNode<E> firstNode() {
        SetNode<E> n = root;
        if (n == null) return null;
        while (n.left != null) n = n.left;
        return n;
    }

    /** In-order successor of {@code n}. */
    private SetNode<E> successor(SetNode<E> n) {
        if (n.right != null) {
            SetNode<E> s = n.right;
            while (s.left != null) s = s.left;
            return s;
        }
        SetNode<E> p = n.parent;
        SetNode<E> c = n;
        while (p != null && c == p.right) { c = p; p = p.parent; }
        return p;
    }

    private final class AscendingIterator implements Iterator<E> {
        private SetNode<E> next = firstNode();
        private SetNode<E> lastReturned;
        private int expectedModCount = modCount;

        @Override public boolean hasNext() { return next != null; }

        @Override public E next() {
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            if (next == null) throw new NoSuchElementException();
            lastReturned = next;
            next = successor(next);
            return lastReturned.element;
        }

        @Override public void remove() {
            throw new UnsupportedOperationException("Iterator.remove is added in a later task");
        }
    }

    // --- snapshot + dispatch ----------------------------------------------------

    /** Immutable whole-set snapshot from the cached root (valid outside a fixup). */
    SetSnapshot snapshot() {
        return new SetSnapshot(snap(root), size);
    }

    /** Snapshot built by climbing to the true root from {@code anyNode} (valid mid-fixup). */
    private SetSnapshot snapshotFrom(SetNode<E> anyNode) {
        SetNode<E> r = anyNode;
        while (r.parent != null) r = r.parent;
        return new SetSnapshot(snap(r), size);
    }

    private SetNodeSnapshot snap(SetNode<E> n) {
        if (n == null) return null;
        return new SetNodeSnapshot(n.element, n.red, snap(n.left), snap(n.right));
    }

    public void addListener(StructureEventListener<SetEvent> listener) {
        dispatcher.addListener(listener);
    }

    public void removeListener(StructureEventListener<SetEvent> listener) {
        dispatcher.removeListener(listener);
    }

    private void emit(SetEvent event) {
        dispatcher.emit(event);
    }

    private void beginMutation() {
        dispatcher.beginMutation();
    }

    /** Test hook: the tree root (package-visible for invariant checkers). */
    SetNode<E> rootForTest() {
        return root;
    }

    // --- NavigableSet methods filled in by later tasks --------------------------

    @Override public E first() { throw new UnsupportedOperationException(); }
    @Override public E last() { throw new UnsupportedOperationException(); }
    @Override public E lower(E e) { throw new UnsupportedOperationException(); }
    @Override public E floor(E e) { throw new UnsupportedOperationException(); }
    @Override public E ceiling(E e) { throw new UnsupportedOperationException(); }
    @Override public E higher(E e) { throw new UnsupportedOperationException(); }
    @Override public E pollFirst() { throw new UnsupportedOperationException(); }
    @Override public E pollLast() { throw new UnsupportedOperationException(); }
    @Override public Iterator<E> descendingIterator() { throw new UnsupportedOperationException(); }
    @Override public NavigableSet<E> descendingSet() { throw new UnsupportedOperationException(); }
    @Override public NavigableSet<E> subSet(E from, boolean fromInc, E to, boolean toInc) { throw new UnsupportedOperationException(); }
    @Override public NavigableSet<E> headSet(E to, boolean inclusive) { throw new UnsupportedOperationException(); }
    @Override public NavigableSet<E> tailSet(E from, boolean inclusive) { throw new UnsupportedOperationException(); }
    @Override public SortedSet<E> subSet(E from, E to) { throw new UnsupportedOperationException(); }
    @Override public SortedSet<E> headSet(E to) { throw new UnsupportedOperationException(); }
    @Override public SortedSet<E> tailSet(E from) { throw new UnsupportedOperationException(); }
}
```

`treeset/core/package-info.java`:
```java
/** The red-black-tree-backed {@code TeachingTreeSet} and its node type. */
package com.gimlism.translucent.treeset.core;
```

`treeset/package-info.java`:
```java
/**
 * The comparison-ordered {@code TeachingTreeSet}: a {@link java.util.NavigableSet}
 * backed by a red-black tree on the shared instrumentation substrate. The 4th teaching
 * structure; first consumer of {@code substrate/rbtree}.
 */
package com.gimlism.translucent.treeset;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=TeachingTreeSetAddTest`
Expected: PASS (12 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/treeset src/test/java/com/gimlism/translucent/treeset/core/SetInvariants.java src/test/java/com/gimlism/translucent/treeset/core/TeachingTreeSetAddTest.java
git commit -m "feat(treeset): TeachingTreeSet add/contains/size/clear/iterator + Compare narration"
```

---

### Task 6: `remove` + `Iterator.remove`

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/treeset/core/TeachingTreeSet.java` (implement `remove`, replace the iterator's `remove`)
- Test: `src/test/java/com/gimlism/translucent/treeset/core/TeachingTreeSetRemoveTest.java`

**Interfaces:**
- Consumes: `RedBlackTree.deleteFromTree` (Task 3); the `sink`, `compare`, `snapshot`, event types from Task 5.
- Produces: working `boolean remove(Object)` emitting `Compare*` (walk) then rebalance events then a terminal `Remove`; a working `Iterator.remove()`.
- Ordering rule (snapshot-before-settled): decrement `size` and `modCount` **before** calling `deleteFromTree`, so the mid-fixup frames report the final element count; emit the terminal `Remove` after the kernel returns.

- [ ] **Step 1: Write the failing test**

```java
package com.gimlism.translucent.treeset.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.treeset.events.Compare;
import com.gimlism.translucent.treeset.events.Remove;
import com.gimlism.translucent.treeset.events.SetEvent;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class TeachingTreeSetRemoveTest {

    @Test
    void removeReturnsTrueOnlyWhenPresent() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{10, 5, 15}) set.add(k);
        assertTrue(set.remove(5));
        assertFalse(set.remove(5));
        assertFalse(set.remove(999));
        assertEquals(2, set.size());
        SetInvariants.assertValid(set);
    }

    @Test
    void absentRemoveIsSilentButPresentRemoveNarratesThenMarks() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{10, 5, 15}) set.add(k);
        List<SetEvent> silent = new ArrayList<>();
        set.addListener(silent::add);
        set.remove(999);
        assertTrue(silent.stream().allMatch(e -> e instanceof Compare),
                "an absent remove narrates the failed walk only (no Remove marker)");
        assertFalse(silent.stream().anyMatch(e -> e instanceof Remove));

        List<SetEvent> log = new ArrayList<>();
        set.addListener(log::add);
        set.remove(5);
        assertTrue(log.stream().anyMatch(e -> e instanceof Compare), "walk narrated");
        SetEvent last = log.get(log.size() - 1);
        assertTrue(last instanceof Remove r && r.element().equals(5), "terminal Remove marker last");
    }

    @Test
    void removeFrameReportsFinalSize() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{10, 5, 15, 3, 7}) set.add(k);
        List<SetEvent> log = new ArrayList<>();
        set.addListener(log::add);
        set.remove(5);
        // Every emitted frame during the remove reports the post-removal size (4).
        for (SetEvent e : log) {
            if (!(e instanceof Compare)) {
                assertEquals(4, e.after().size(), "rebalance/terminal frames report final size");
            }
        }
    }

    @Test
    void randomAddRemoveMatchesJdkOracle() {
        TeachingTreeSet<Integer> mine = new TeachingTreeSet<>();
        TreeSet<Integer> oracle = new TreeSet<>();
        Random rng = new Random(20260716L);
        for (int i = 0; i < 3000; i++) {
            int k = rng.nextInt(200);
            if (rng.nextBoolean()) {
                assertEquals(oracle.add(k), mine.add(k), "add " + k);
            } else {
                assertEquals(oracle.remove(k), mine.remove(k), "remove " + k);
            }
            assertEquals(oracle.size(), mine.size());
            SetInvariants.assertValid(mine);
        }
        assertEquals(new ArrayList<>(oracle), new ArrayList<>(mine));
    }

    @Test
    void iteratorRemoveDeletesLastReturned() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{1, 2, 3, 4, 5}) set.add(k);
        Iterator<Integer> it = set.iterator();
        while (it.hasNext()) {
            if (it.next() % 2 == 0) it.remove();
        }
        assertEquals(List.of(1, 3, 5), new ArrayList<>(set));
        SetInvariants.assertValid(set);
    }

    @Test
    void iteratorIsFailFastAfterExternalMutation() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{1, 2, 3}) set.add(k);
        Iterator<Integer> it = set.iterator();
        it.next();
        set.add(99);
        assertThrows(ConcurrentModificationException.class, it::next);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=TeachingTreeSetRemoveTest`
Expected: FAIL — `remove` throws `UnsupportedOperationException` (inherited AbstractSet.remove uses iterator.remove, which throws).

- [ ] **Step 3: Write minimal implementation**

In `TeachingTreeSet.java`, add the import `import com.gimlism.translucent.treeset.events.Remove;` and a `remove` override (place after `add`):
```java
    @Override
    public boolean remove(Object o) {
        beginMutation();
        try {
            @SuppressWarnings("unchecked")
            E e = (E) o;
            SetNode<E> node = root;
            while (node != null) {
                int c = compare(e, node.element);
                if (c == 0) {
                    emit(new Compare(node.element, null, true, snapshot()));
                    break;
                }
                Direction went = c < 0 ? Direction.LEFT : Direction.RIGHT;
                emit(new Compare(node.element, went, false, snapshot()));
                node = c < 0 ? node.left : node.right;
            }
            if (node == null) return false;          // absent: the failed walk was narrated, no marker
            unlink(node);
            return true;
        } finally {
            dispatcher.endMutation();
        }
    }

    /** Remove an already-located node: commit size first, rebalance, then mark. */
    private void unlink(SetNode<E> node) {
        Object element = node.element;
        size--;
        modCount++;
        root = RedBlackTree.deleteFromTree(root, node, sink); // mid-fixup frames see final size
        emit(new Remove(element, snapshot()));
    }
```

Replace the iterator's `remove()` body:
```java
        @Override public void remove() {
            if (lastReturned == null) throw new IllegalStateException();
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            // The kernel's delete is pointer-based (identity-preserving): when `lastReturned`
            // has two children its successor NODE is relocated into `lastReturned`'s slot and
            // stays live, so `next` (= that successor) remains correctly positioned. No re-seat
            // is needed — unlike JDK TreeMap, which copies the successor's value and would.
            TeachingTreeSet.this.removeNode(lastReturned);
            expectedModCount = modCount;
            lastReturned = null;
        }
```

Add a package-visible `removeNode` used by the iterator (wraps `unlink` in the mutation guard, since the iterator calls outside `remove(Object)`):
```java
    /** Iterator entry point: delete an already-held node with full event + guard semantics. */
    void removeNode(SetNode<E> node) {
        beginMutation();
        try {
            unlink(node);
        } finally {
            dispatcher.endMutation();
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=TeachingTreeSetRemoveTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/treeset/core/TeachingTreeSet.java src/test/java/com/gimlism/translucent/treeset/core/TeachingTreeSetRemoveTest.java
git commit -m "feat(treeset): remove + Iterator.remove via shared delete kernel, oracle-checked"
```

---

### Task 7: Navigation — `first`/`last`/`lower`/`floor`/`ceiling`/`higher`/`pollFirst`/`pollLast`

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/treeset/core/TeachingTreeSet.java` (replace the six navigation stubs + `pollFirst`/`pollLast`)
- Test: `src/test/java/com/gimlism/translucent/treeset/core/TeachingTreeSetNavigationTest.java`

**Interfaces:**
- Consumes: `firstNode`, `compare`, `snapshot`, `emit`, `removeNode`, `Compare` (Tasks 5–6).
- Produces: working endpoint + relative-navigation methods. The four comparison-driven navigators (`lower`/`floor`/`ceiling`/`higher`) emit `Compare` narration; `first`/`last`/`pollFirst`/`pollLast` do not (no comparison occurs). `pollFirst`/`pollLast` remove and emit `Remove` via `removeNode`.

- [ ] **Step 1: Write the failing test**

```java
package com.gimlism.translucent.treeset.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.treeset.events.Compare;
import com.gimlism.translucent.treeset.events.Remove;
import com.gimlism.translucent.treeset.events.SetEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class TeachingTreeSetNavigationTest {

    private TeachingTreeSet<Integer> setOf(int... xs) {
        TeachingTreeSet<Integer> s = new TeachingTreeSet<>();
        for (int x : xs) s.add(x);
        return s;
    }

    @Test
    void firstAndLastAreMinAndMax() {
        TeachingTreeSet<Integer> s = setOf(20, 5, 40, 1, 30);
        assertEquals(1, s.first());
        assertEquals(40, s.last());
    }

    @Test
    void firstOnEmptyThrows() {
        assertThrows(NoSuchElementException.class, () -> new TeachingTreeSet<Integer>().first());
        assertThrows(NoSuchElementException.class, () -> new TeachingTreeSet<Integer>().last());
    }

    @Test
    void relativeNavigatorsMatchJdk() {
        int[] xs = {10, 20, 30, 40, 50};
        TeachingTreeSet<Integer> mine = setOf(xs);
        TreeSet<Integer> oracle = new TreeSet<>();
        for (int x : xs) oracle.add(x);
        for (int q = 5; q <= 55; q += 5) {
            assertEquals(oracle.lower(q), mine.lower(q), "lower " + q);
            assertEquals(oracle.floor(q), mine.floor(q), "floor " + q);
            assertEquals(oracle.ceiling(q), mine.ceiling(q), "ceiling " + q);
            assertEquals(oracle.higher(q), mine.higher(q), "higher " + q);
        }
    }

    @Test
    void comparisonNavigatorsNarrateCompare() {
        TeachingTreeSet<Integer> s = setOf(10, 5, 15);
        List<SetEvent> log = new ArrayList<>();
        s.addListener(log::add);
        s.ceiling(12);
        assertTrue(log.stream().allMatch(e -> e instanceof Compare), "ceiling narrates only Compare frames");
        assertTrue(log.size() > 0, "at least one comparison happened");
    }

    @Test
    void endpointOpsDoNotNarrateCompare() {
        TeachingTreeSet<Integer> s = setOf(10, 5, 15);
        List<SetEvent> log = new ArrayList<>();
        s.addListener(log::add);
        s.first();
        s.last();
        assertTrue(log.isEmpty(), "first/last are unconditional walks — no Compare");
    }

    @Test
    void pollFirstAndPollLastRemoveEndpointsAndEmitRemove() {
        TeachingTreeSet<Integer> s = setOf(10, 5, 15);
        List<SetEvent> log = new ArrayList<>();
        s.addListener(log::add);
        assertEquals(5, s.pollFirst());
        assertEquals(15, s.pollLast());
        assertEquals(1, s.size());
        assertEquals(10, s.first());
        assertTrue(log.stream().anyMatch(e -> e instanceof Remove r && r.element().equals(5)));
        assertTrue(log.stream().anyMatch(e -> e instanceof Remove r && r.element().equals(15)));
        SetInvariants.assertValid(s);
    }

    @Test
    void pollOnEmptyReturnsNull() {
        TeachingTreeSet<Integer> s = new TeachingTreeSet<>();
        assertNull(s.pollFirst());
        assertNull(s.pollLast());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=TeachingTreeSetNavigationTest`
Expected: FAIL — navigation methods throw `UnsupportedOperationException`.

- [ ] **Step 3: Write minimal implementation**

In `TeachingTreeSet.java`, add imports `import java.util.NoSuchElementException;` (already present) and replace the navigation stubs with:
```java
    @Override
    public E first() {
        SetNode<E> n = firstNode();
        if (n == null) throw new NoSuchElementException();
        return n.element;
    }

    @Override
    public E last() {
        SetNode<E> n = lastNode();
        if (n == null) throw new NoSuchElementException();
        return n.element;
    }

    @Override
    public E lower(E e) {
        return elementOrNull(bound(e, false, false));
    }

    @Override
    public E floor(E e) {
        return elementOrNull(bound(e, false, true));
    }

    @Override
    public E ceiling(E e) {
        return elementOrNull(bound(e, true, true));
    }

    @Override
    public E higher(E e) {
        return elementOrNull(bound(e, true, false));
    }

    @Override
    public E pollFirst() {
        SetNode<E> n = firstNode();
        if (n == null) return null;
        E element = n.element;
        removeNode(n);
        return element;
    }

    @Override
    public E pollLast() {
        SetNode<E> n = lastNode();
        if (n == null) return null;
        E element = n.element;
        removeNode(n);
        return element;
    }
```

Add these private helpers (near `firstNode`):
```java
    /** Rightmost (maximum) node, or null when empty. */
    private SetNode<E> lastNode() {
        SetNode<E> n = root;
        if (n == null) return null;
        while (n.right != null) n = n.right;
        return n;
    }

    private E elementOrNull(SetNode<E> n) {
        return n == null ? null : n.element;
    }

    /**
     * Narrated comparison walk for the relative navigators. Returns the node that is
     * the ceiling ({@code up=true}) or the floor ({@code up=false}) of {@code e};
     * {@code inclusive} decides whether an exact match qualifies. Emits a Compare at
     * each visited node.
     */
    private SetNode<E> bound(E e, boolean up, boolean inclusive) {
        SetNode<E> node = root;
        SetNode<E> best = null;
        while (node != null) {
            int c = compare(e, node.element);
            if (c == 0) {
                emit(new Compare(node.element, null, true, snapshot()));
                if (inclusive) return node;
                // exclusive: step to the neighbour on the requested side
                return up ? successor(node) : predecessor(node);
            }
            Direction went = c < 0 ? Direction.LEFT : Direction.RIGHT;
            emit(new Compare(node.element, went, false, snapshot()));
            if (up) {                       // ceiling/higher: smallest element > (or >=) e
                if (c < 0) { best = node; node = node.left; }
                else { node = node.right; }
            } else {                        // floor/lower: largest element < (or <=) e
                if (c > 0) { best = node; node = node.right; }
                else { node = node.left; }
            }
        }
        return best;
    }

    /** In-order predecessor of {@code n}. */
    private SetNode<E> predecessor(SetNode<E> n) {
        if (n.left != null) {
            SetNode<E> p = n.left;
            while (p.right != null) p = p.right;
            return p;
        }
        SetNode<E> p = n.parent;
        SetNode<E> c = n;
        while (p != null && c == p.left) { c = p; p = p.parent; }
        return p;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=TeachingTreeSetNavigationTest`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/treeset/core/TeachingTreeSet.java src/test/java/com/gimlism/translucent/treeset/core/TeachingTreeSetNavigationTest.java
git commit -m "feat(treeset): navigation (first/last/lower/floor/ceiling/higher/poll) — comparison walks narrate"
```

---

### Task 8: `descendingIterator` + `descendingSet`

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/treeset/core/TeachingTreeSet.java` (implement `descendingIterator`; add a descending iterator class)
- Create: `src/main/java/com/gimlism/translucent/treeset/core/DescendingSetView.java`
- Test: `src/test/java/com/gimlism/translucent/treeset/core/TeachingTreeSetDescendingTest.java`

**Interfaces:**
- Consumes: `lastNode`, `predecessor`, `successor`, `firstNode`, `modCount`, `removeNode` (Tasks 5–7). The descending view delegates every operation to the backing `TeachingTreeSet` with reversed semantics.
- Produces: `Iterator<E> descendingIterator()` (reverse in-order, fail-fast) and `NavigableSet<E> descendingSet()` returning a `DescendingSetView<E>` that reflects and writes through to the backing set.

- [ ] **Step 1: Write the failing test**

```java
package com.gimlism.translucent.treeset.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class TeachingTreeSetDescendingTest {

    private TeachingTreeSet<Integer> setOf(int... xs) {
        TeachingTreeSet<Integer> s = new TeachingTreeSet<>();
        for (int x : xs) s.add(x);
        return s;
    }

    @Test
    void descendingIteratorWalksReverseOrder() {
        TeachingTreeSet<Integer> s = setOf(3, 1, 4, 1, 5, 9, 2, 6);
        List<Integer> out = new ArrayList<>();
        s.descendingIterator().forEachRemaining(out::add);
        assertEquals(List.of(9, 6, 5, 4, 3, 2, 1), out);
    }

    @Test
    void descendingSetIteratesReverseAndReflectsFirstLast() {
        TeachingTreeSet<Integer> s = setOf(10, 20, 30);
        NavigableSet<Integer> d = s.descendingSet();
        assertEquals(List.of(30, 20, 10), new ArrayList<>(d));
        assertEquals(30, d.first());
        assertEquals(10, d.last());
        assertEquals(Integer.valueOf(20), d.ceiling(20));
        // In descending order, "higher than 20" means the next smaller real element.
        assertEquals(Integer.valueOf(10), d.higher(20));
    }

    @Test
    void descendingSetWritesThrough() {
        TeachingTreeSet<Integer> s = setOf(1, 2, 3);
        NavigableSet<Integer> d = s.descendingSet();
        d.add(4);
        assertTrue(s.contains(4));
        d.remove(2);
        assertFalse(s.contains(2));
        assertEquals(List.of(1, 3, 4), new ArrayList<>(s));
    }

    @Test
    void descendingSetMatchesJdk() {
        int[] xs = {50, 20, 80, 10, 30, 70, 90};
        TeachingTreeSet<Integer> mine = setOf(xs);
        TreeSet<Integer> oracle = new TreeSet<>();
        for (int x : xs) oracle.add(x);
        assertEquals(new ArrayList<>(oracle.descendingSet()), new ArrayList<>(mine.descendingSet()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=TeachingTreeSetDescendingTest`
Expected: FAIL — `descendingIterator`/`descendingSet` throw `UnsupportedOperationException`.

- [ ] **Step 3: Write minimal implementation**

In `TeachingTreeSet.java`, replace the two descending stubs:
```java
    @Override
    public Iterator<E> descendingIterator() {
        return new DescendingIterator();
    }

    @Override
    public NavigableSet<E> descendingSet() {
        return new DescendingSetView<>(this);
    }
```

Add the descending iterator inner class (next to `AscendingIterator`):
```java
    private final class DescendingIterator implements Iterator<E> {
        private SetNode<E> next = lastNode();
        private SetNode<E> lastReturned;
        private int expectedModCount = modCount;

        @Override public boolean hasNext() { return next != null; }

        @Override public E next() {
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            if (next == null) throw new NoSuchElementException();
            lastReturned = next;
            next = predecessor(next);
            return lastReturned.element;
        }

        @Override public void remove() {
            if (lastReturned == null) throw new IllegalStateException();
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            // Identity-preserving delete keeps `next` (the predecessor) live and positioned; no re-seat.
            TeachingTreeSet.this.removeNode(lastReturned);
            expectedModCount = modCount;
            lastReturned = null;
        }
    }
```

`DescendingSetView.java` — a view that reverses ordering and delegates through to the backing set:
```java
package com.gimlism.translucent.treeset.core;

import java.util.AbstractSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.SortedSet;

/**
 * A reversed view over a {@link TeachingTreeSet}. Reads reflect the backing set in
 * descending order; writes delegate through. Endpoint and relative navigators map to
 * the backing set's mirror ({@code first<->last}, {@code lower<->higher},
 * {@code floor<->ceiling}).
 */
final class DescendingSetView<E> extends AbstractSet<E> implements NavigableSet<E> {

    private final TeachingTreeSet<E> base;

    DescendingSetView(TeachingTreeSet<E> base) {
        this.base = base;
    }

    @Override public int size() { return base.size(); }
    @Override public boolean contains(Object o) { return base.contains(o); }
    @Override public boolean add(E e) { return base.add(e); }
    @Override public boolean remove(Object o) { return base.remove(o); }
    @Override public void clear() { base.clear(); }

    @Override public Iterator<E> iterator() { return base.descendingIterator(); }
    @Override public Iterator<E> descendingIterator() { return base.iterator(); }
    @Override public NavigableSet<E> descendingSet() { return base; }

    @Override public E first() { return base.last(); }
    @Override public E last() { return base.first(); }
    @Override public E lower(E e) { return base.higher(e); }
    @Override public E higher(E e) { return base.lower(e); }
    @Override public E floor(E e) { return base.ceiling(e); }
    @Override public E ceiling(E e) { return base.floor(e); }
    @Override public E pollFirst() { return base.pollLast(); }
    @Override public E pollLast() { return base.pollFirst(); }

    @Override public Comparator<? super E> comparator() {
        // Collections.reverseOrder(cmp) reverses cmp, or reverses natural ordering when
        // cmp is null — matching java.util.TreeMap.descendingMap().comparator(). (A bare
        // Comparator.reverseOrder() would not type-check against the unbounded E.)
        return Collections.reverseOrder(base.comparator());
    }

    @Override public NavigableSet<E> subSet(E from, boolean fromInc, E to, boolean toInc) {
        return base.subSet(to, toInc, from, fromInc).descendingSet();
    }
    @Override public NavigableSet<E> headSet(E to, boolean inclusive) {
        return base.tailSet(to, inclusive).descendingSet();
    }
    @Override public NavigableSet<E> tailSet(E from, boolean inclusive) {
        return base.headSet(from, inclusive).descendingSet();
    }
    @Override public SortedSet<E> subSet(E from, E to) { return subSet(from, true, to, false); }
    @Override public SortedSet<E> headSet(E to) { return headSet(to, false); }
    @Override public SortedSet<E> tailSet(E from) { return tailSet(from, true); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=TeachingTreeSetDescendingTest`
Expected: PASS (4 tests). (The `subSet`/`headSet`/`tailSet` delegations compile against Task 5's stubs; they are exercised in Task 9.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/treeset/core/TeachingTreeSet.java src/main/java/com/gimlism/translucent/treeset/core/DescendingSetView.java src/test/java/com/gimlism/translucent/treeset/core/TeachingTreeSetDescendingTest.java
git commit -m "feat(treeset): descendingIterator + write-through descendingSet view"
```

---

### Task 9: Range views — `subSet`/`headSet`/`tailSet` (write-through)

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/treeset/core/TeachingTreeSet.java` (implement the six range-view methods)
- Create: `src/main/java/com/gimlism/translucent/treeset/core/RangeSetView.java`
- Test: `src/test/java/com/gimlism/translucent/treeset/core/TeachingTreeSetRangeTest.java`

**Interfaces:**
- Consumes: `compare`, `contains`, `add`, `remove`, `firstNode`, `successor`, navigation methods (Tasks 5–7).
- Produces: `NavigableSet<E>` range views bounded by optional lower/upper bounds with inclusivity flags. Reads filter the backing iteration to the range; writes delegate through, rejecting out-of-range `add` with `IllegalArgumentException` (JDK semantics).
- **Known teaching simplifications (deliberate, documented):** three *compound*-view behaviours diverge from strict JDK fidelity, none exercised by tests: (a) `RangeSetView.iterator()` snapshots to a list, so it is **not fail-fast** (JDK range iterators check `modCount`); (b) `RangeSetView.descendingSet()` materializes a detached copy, so it is **not write-through** (unlike `descendingSet()` on the base set, which is); (c) `DescendingSetView`'s range views inherit (b) via delegation. The single-level views (a plain `subSet`/`headSet`/`tailSet`, and `descendingSet()` on the base) *are* live and write-through — the common teaching cases. These simplifications are called out in the spec's out-of-scope so "full NavigableSet" is honest about its edges.

- [ ] **Step 1: Write the failing test**

```java
package com.gimlism.translucent.treeset.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class TeachingTreeSetRangeTest {

    private TeachingTreeSet<Integer> setOf(int... xs) {
        TeachingTreeSet<Integer> s = new TeachingTreeSet<>();
        for (int x : xs) s.add(x);
        return s;
    }

    @Test
    void subSetIsHalfOpenLikeJdk() {
        TeachingTreeSet<Integer> s = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertEquals(List.of(3, 4, 5, 6), new ArrayList<>(s.subSet(3, 7)));
    }

    @Test
    void inclusiveBoundsMatchJdk() {
        int[] xs = {10, 20, 30, 40, 50, 60};
        TeachingTreeSet<Integer> mine = setOf(xs);
        TreeSet<Integer> oracle = new TreeSet<>();
        for (int x : xs) oracle.add(x);
        assertEquals(new ArrayList<>(oracle.subSet(20, true, 50, false)),
                new ArrayList<>(mine.subSet(20, true, 50, false)));
        assertEquals(new ArrayList<>(oracle.headSet(40, true)),
                new ArrayList<>(mine.headSet(40, true)));
        assertEquals(new ArrayList<>(oracle.tailSet(30, false)),
                new ArrayList<>(mine.tailSet(30, false)));
    }

    @Test
    void rangeViewContainsRespectsBounds() {
        TeachingTreeSet<Integer> s = setOf(1, 2, 3, 4, 5);
        NavigableSet<Integer> mid = s.subSet(2, true, 4, true);
        assertTrue(mid.contains(3));
        assertFalse(mid.contains(1));
        assertFalse(mid.contains(5));
        assertEquals(3, mid.size());
    }

    @Test
    void addInRangeWritesThroughOutOfRangeThrows() {
        TeachingTreeSet<Integer> s = setOf(10, 20, 30);
        NavigableSet<Integer> mid = s.subSet(10, true, 30, false); // [10, 30)
        assertTrue(mid.add(15));
        assertTrue(s.contains(15));
        assertThrows(IllegalArgumentException.class, () -> mid.add(30));
        assertThrows(IllegalArgumentException.class, () -> mid.add(5));
    }

    @Test
    void removeThroughRangeView() {
        TeachingTreeSet<Integer> s = setOf(1, 2, 3, 4, 5);
        NavigableSet<Integer> mid = s.subSet(2, true, 5, false);
        assertTrue(mid.remove(3));
        assertFalse(s.contains(3));
        assertFalse(mid.remove(5)); // out of range -> not present in view
        assertTrue(s.contains(5));
    }

    @Test
    void rangeFirstLastMatchJdk() {
        TeachingTreeSet<Integer> s = setOf(1, 2, 3, 4, 5, 6, 7);
        assertEquals(3, s.subSet(3, true, 6, true).first());
        assertEquals(6, s.subSet(3, true, 6, true).last());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=TeachingTreeSetRangeTest`
Expected: FAIL — range methods throw `UnsupportedOperationException`.

- [ ] **Step 3: Write minimal implementation**

In `TeachingTreeSet.java`, replace the six range stubs:
```java
    @Override
    public NavigableSet<E> subSet(E from, boolean fromInc, E to, boolean toInc) {
        if (compare(from, to) > 0) {
            throw new IllegalArgumentException("fromElement (" + from + ") > toElement (" + to + ")");
        }
        return new RangeSetView<>(this, from, true, fromInc, to, true, toInc);
    }

    @Override
    public NavigableSet<E> headSet(E to, boolean inclusive) {
        return new RangeSetView<>(this, null, false, false, to, true, inclusive);
    }

    @Override
    public NavigableSet<E> tailSet(E from, boolean inclusive) {
        return new RangeSetView<>(this, from, true, inclusive, null, false, false);
    }

    @Override
    public SortedSet<E> subSet(E from, E to) {
        return subSet(from, true, to, false);
    }

    @Override
    public SortedSet<E> headSet(E to) {
        return headSet(to, false);
    }

    @Override
    public SortedSet<E> tailSet(E from) {
        return tailSet(from, true);
    }
```

Add a package-visible `compareElements` bridge so the view can compare using the backing set's ordering:
```java
    /** Ordering bridge for range views (uses this set's comparator or natural order). */
    int compareElements(E a, E b) {
        return compare(a, b);
    }
```

`RangeSetView.java`:
```java
package com.gimlism.translucent.treeset.core;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.SortedSet;

/**
 * A bounded, write-through view over a {@link TeachingTreeSet}. Reads filter the
 * backing iteration to the range; writes delegate through, rejecting an out-of-range
 * {@code add} with {@link IllegalArgumentException} (JDK semantics). Either bound may
 * be absent ({@code hasLo}/{@code hasHi} false) for head/tail views.
 */
final class RangeSetView<E> extends AbstractSet<E> implements NavigableSet<E> {

    private final TeachingTreeSet<E> base;
    private final E lo;
    private final boolean hasLo;
    private final boolean loInc;
    private final E hi;
    private final boolean hasHi;
    private final boolean hiInc;

    RangeSetView(TeachingTreeSet<E> base, E lo, boolean hasLo, boolean loInc,
                 E hi, boolean hasHi, boolean hiInc) {
        this.base = base;
        this.lo = lo; this.hasLo = hasLo; this.loInc = loInc;
        this.hi = hi; this.hasHi = hasHi; this.hiInc = hiInc;
    }

    private boolean tooLow(E e) {
        if (!hasLo) return false;
        int c = base.compareElements(e, lo);
        return loInc ? c < 0 : c <= 0;
    }

    private boolean tooHigh(E e) {
        if (!hasHi) return false;
        int c = base.compareElements(e, hi);
        return hiInc ? c > 0 : c >= 0;
    }

    private boolean inRange(E e) {
        return !tooLow(e) && !tooHigh(e);
    }

    private List<E> elementsInRange() {
        List<E> out = new ArrayList<>();
        for (E e : base) {
            if (tooLow(e)) continue;
            if (tooHigh(e)) break; // ascending: nothing further qualifies
            out.add(e);
        }
        return out;
    }

    @Override public Iterator<E> iterator() {
        List<E> snapshot = elementsInRange();
        return new Iterator<>() {
            private final Iterator<E> it = snapshot.iterator();
            private E lastReturned;
            @Override public boolean hasNext() { return it.hasNext(); }
            @Override public E next() { lastReturned = it.next(); return lastReturned; }
            @Override public void remove() {
                if (lastReturned == null) throw new IllegalStateException();
                base.remove(lastReturned);
                lastReturned = null;
            }
        };
    }

    @Override public int size() { return elementsInRange().size(); }
    @Override public boolean isEmpty() { return elementsInRange().isEmpty(); }

    @Override @SuppressWarnings("unchecked")
    public boolean contains(Object o) {
        return inRange((E) o) && base.contains(o);
    }

    @Override public boolean add(E e) {
        if (!inRange(e)) {
            throw new IllegalArgumentException("element " + e + " out of range");
        }
        return base.add(e);
    }

    @Override @SuppressWarnings("unchecked")
    public boolean remove(Object o) {
        return inRange((E) o) && base.remove(o);
    }

    @Override public Comparator<? super E> comparator() { return base.comparator(); }

    @Override public E first() {
        List<E> es = elementsInRange();
        if (es.isEmpty()) throw new NoSuchElementException();
        return es.get(0);
    }

    @Override public E last() {
        List<E> es = elementsInRange();
        if (es.isEmpty()) throw new NoSuchElementException();
        return es.get(es.size() - 1);
    }

    @Override public E lower(E e) { return highestBelow(e, false); }
    @Override public E floor(E e) { return highestBelow(e, true); }
    @Override public E ceiling(E e) { return lowestAbove(e, true); }
    @Override public E higher(E e) { return lowestAbove(e, false); }

    private E lowestAbove(E e, boolean inclusive) {
        for (E x : elementsInRange()) {
            int c = base.compareElements(x, e);
            if (c > 0 || (inclusive && c == 0)) return x;
        }
        return null;
    }

    private E highestBelow(E e, boolean inclusive) {
        E best = null;
        for (E x : elementsInRange()) {
            int c = base.compareElements(x, e);
            if (c < 0 || (inclusive && c == 0)) best = x; else break;
        }
        return best;
    }

    @Override public E pollFirst() {
        List<E> es = elementsInRange();
        if (es.isEmpty()) return null;
        E e = es.get(0);
        base.remove(e);
        return e;
    }

    @Override public E pollLast() {
        List<E> es = elementsInRange();
        if (es.isEmpty()) return null;
        E e = es.get(es.size() - 1);
        base.remove(e);
        return e;
    }

    @Override public Iterator<E> descendingIterator() {
        List<E> es = elementsInRange();
        return new Iterator<>() {
            private int i = es.size() - 1;
            @Override public boolean hasNext() { return i >= 0; }
            @Override public E next() { return es.get(i--); }
        };
    }

    @Override public NavigableSet<E> descendingSet() { return new DescendingSetView<>(materialize()); }

    /** A standalone copy of this view's elements, for descendingSet (read-only convenience). */
    private TeachingTreeSet<E> materialize() {
        TeachingTreeSet<E> copy = base.comparator() == null
                ? new TeachingTreeSet<>() : new TeachingTreeSet<>(base.comparator());
        for (E e : elementsInRange()) copy.add(e);
        return copy;
    }

    @Override public NavigableSet<E> subSet(E from, boolean fromInc, E to, boolean toInc) {
        if (base.compareElements(from, to) > 0) {
            throw new IllegalArgumentException("fromElement (" + from + ") > toElement (" + to + ")");
        }
        requireInRange(from);
        requireInRange(to);
        return new RangeSetView<>(base, from, true, fromInc, to, true, toInc);
    }

    @Override public NavigableSet<E> headSet(E to, boolean inclusive) {
        requireInRange(to);
        return new RangeSetView<>(base, lo, hasLo, loInc, to, true, inclusive);
    }

    @Override public NavigableSet<E> tailSet(E from, boolean inclusive) {
        requireInRange(from);
        return new RangeSetView<>(base, from, true, inclusive, hi, hasHi, hiInc);
    }

    @Override public SortedSet<E> subSet(E from, E to) { return subSet(from, true, to, false); }
    @Override public SortedSet<E> headSet(E to) { return headSet(to, false); }
    @Override public SortedSet<E> tailSet(E from) { return tailSet(from, true); }

    private void requireInRange(E e) {
        if (!inRange(e)) throw new IllegalArgumentException("bound " + e + " out of range");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=TeachingTreeSetRangeTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/treeset/core/TeachingTreeSet.java src/main/java/com/gimlism/translucent/treeset/core/RangeSetView.java src/test/java/com/gimlism/translucent/treeset/core/TeachingTreeSetRangeTest.java
git commit -m "feat(treeset): write-through range views (subSet/headSet/tailSet) matching JDK bounds"
```

---

### Task 10: Consumers — `ConsoleSetEventLogger` + `SetRecordingListener`

**Files:**
- Create: `src/main/java/com/gimlism/translucent/treeset/consumer/ConsoleSetEventLogger.java`
- Create: `src/main/java/com/gimlism/translucent/treeset/consumer/SetRecordingListener.java`
- Create: `src/main/java/com/gimlism/translucent/treeset/consumer/package-info.java`
- Test: `src/test/java/com/gimlism/translucent/treeset/consumer/SetConsumerTest.java`

**Interfaces:**
- Consumes: `SetEvent`, `SetEventFormatter`, `SetEventListener` (Task 4); `TeachingTreeSet.addListener` (Task 5).
- Produces:
  - `ConsoleSetEventLogger implements SetEventListener` — prints `SetEventFormatter.format(event)` to a supplied `PrintStream` (default `System.out`).
  - `SetRecordingListener implements SetEventListener` — buffers events; `List<SetEvent> events()` returns an unmodifiable copy.

- [ ] **Step 1: Write the failing test**

```java
package com.gimlism.translucent.treeset.consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.events.Add;
import com.gimlism.translucent.treeset.events.SetEvent;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SetConsumerTest {

    @Test
    void recordingListenerBuffersEventsImmutably() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        SetRecordingListener rec = new SetRecordingListener();
        set.addListener(rec);
        set.add(1);
        set.add(2);
        assertTrue(rec.events().stream().anyMatch(e -> e instanceof Add));
        assertThrows(UnsupportedOperationException.class, () -> rec.events().add((SetEvent) null));
    }

    @Test
    void consoleLoggerWritesFormattedLines() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        set.addListener(new ConsoleSetEventLogger(new PrintStream(buf, true, StandardCharsets.UTF_8)));
        set.add(7);
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("add 7"), "logged: " + out);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=SetConsumerTest`
Expected: FAIL — consumer classes do not exist.

- [ ] **Step 3: Write minimal implementation**

`SetRecordingListener.java`:
```java
package com.gimlism.translucent.treeset.consumer;

import com.gimlism.translucent.treeset.events.SetEvent;
import com.gimlism.translucent.treeset.events.SetEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Buffers a {@code TeachingTreeSet}'s events for later replay or assertion. */
public final class SetRecordingListener implements SetEventListener {
    private final List<SetEvent> events = new ArrayList<>();

    @Override
    public void onEvent(SetEvent event) {
        events.add(event);
    }

    /** An unmodifiable view of the events recorded so far, in order. */
    public List<SetEvent> events() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }
}
```

`ConsoleSetEventLogger.java`:
```java
package com.gimlism.translucent.treeset.consumer;

import com.gimlism.translucent.treeset.events.SetEvent;
import com.gimlism.translucent.treeset.events.SetEventFormatter;
import com.gimlism.translucent.treeset.events.SetEventListener;
import java.io.PrintStream;

/** Prints each {@code TeachingTreeSet} event as a formatted one-line caption. */
public final class ConsoleSetEventLogger implements SetEventListener {
    private final PrintStream out;

    public ConsoleSetEventLogger() {
        this(System.out);
    }

    public ConsoleSetEventLogger(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onEvent(SetEvent event) {
        out.println(SetEventFormatter.format(event));
    }
}
```

`package-info.java`:
```java
/**
 * Ready-made listeners for a {@code TeachingTreeSet}'s event stream: a console logger
 * and an in-memory recorder.
 */
package com.gimlism.translucent.treeset.consumer;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=SetConsumerTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Run the full suite and commit**

Run: `mvn -q test`
Expected: PASS — all existing tests plus the new TreeSet suite green.

```bash
git add src/main/java/com/gimlism/translucent/treeset/consumer src/test/java/com/gimlism/translucent/treeset/consumer/SetConsumerTest.java
git commit -m "feat(treeset): console logger + recording listener consumers"
```

---

## Self-Review

**Spec coverage:**
- Shared `substrate/rbtree` kernel (extract-forward, HashMap untouched) → Tasks 1–3. ✓
- `TeachingTreeSet extends AbstractSet implements NavigableSet`, natural + `Comparator` ordering → Task 5 (`compare` helper, both constructors, `comparator()`). ✓
- Event vocabulary `Compare`/`Add`/`Remove`/`Rotation`/`Recolor` + no `CreateNode` → Task 4. ✓
- `Compare` narrates reads (`contains`, `lower`/`floor`/`ceiling`/`higher`) but not endpoints → Tasks 5, 7. ✓
- Recursive binary snapshot `SetSnapshot`/`SetNodeSnapshot` → Task 4; built in Task 5. ✓
- Snapshot-before-settled (mutate-then-emit; climb-to-root mid-fixup) → Task 5 (`snapshotFrom`), Task 6 (size-before-`deleteFromTree`), asserted in `everyEventSnapshotIsATrueAfterImage`, `removeFrameReportsFinalSize`. ✓
- First-event-on-empty = `Add`; `contains` on empty silent → Task 5 (`firstEventOnEmptyAddIsAddNotCompare`, `containsOnEmptySetEmitsNothing`). ✓
- Full `NavigableSet`: core+iterator (5), remove (6), navigation (7), descending (8), range views last (9). ✓
- RB invariants + JDK oracle tests → Tasks 2, 3, 5, 6, 7, 8, 9. ✓
- Shared-core reuse proven via a trivial test node → Tasks 1–3 (`IntNode`). ✓
- Console/recording consumers → Task 10. ✓
- Out of scope (HashMap migration, all viz, Comparator in front-ends) → no tasks, correctly. ✓

**Placeholder scan:** No `TODO`/`TBD`/"handle edge cases"/"similar to". The only `UnsupportedOperationException`s are Task 5's deliberate NavigableSet stubs, each replaced by a named later task; every step shows complete code. ✓

**Type consistency:** `snapshot()` / `snapshotFrom(SetNode<E>)` / `snap(SetNode<E>)`, `compare(E,E)` / `compareElements(E,E)`, `removeNode(SetNode<E>)` / `unlink(SetNode<E>)`, `firstNode()`/`lastNode()`/`successor`/`predecessor`, `rootForTest()`, event record component names (`element()`, `went()`, `found()`, `dir()`, `pivot()`, `oldColor()`, `newColor()`) are used consistently across Tasks 4–10. `RbEventSink.none()` (static factory) matches Task 1's definition and Tasks 2–3's usage. `insertFixup`/`deleteFromTree` signatures match between producer (Tasks 2–3) and consumer (Tasks 5–6). ✓
