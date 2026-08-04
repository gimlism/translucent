# Teaching HashMap — Slice 3: Red-Black Deletion + Untreeify Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the data structure with red-black deletion from tree bins and untreeify (tree → chain), so removing from a tree bin works, rebalances the tree, or collapses it back to a chain — all observable in the event stream.

**Architecture:** `TreeNode` gains a `prev` back-link, making a tree bin a doubly-linked list (insertion order) threaded through the red-black tree — O(1) unlink on delete. Removal follows the JDK shortcut: unlink from the list, then if the bin is now ≤ `untreeifyThreshold` untreeify (skip RB-delete), else pointer-based RB-delete with fixup. Untreeify also fires on resize-split for small halves (silently). The Slice-2 tree-bin remove guard is removed.

**Tech Stack:** Java 21, Maven, JUnit 5.

## Global Constraints

- Java 21 target (`maven.compiler.release=21`), Maven, built/tested on JDK 26.
- Package `com.gimlism.translucent.hashmap.core` (data structure); events/consumer/demo unchanged in shape.
- Teaching-default constants (unchanged): `treeifyThreshold=4`, `untreeifyThreshold=2`, `minTreeifyCapacity=8`, `initialCapacity=8`, `loadFactor=0.75`.
- Deletion is **pointer-based** (no value-copy — preserves node identity, `seq`, and threading).
- `prev` is a `TreeNode` field only; plain-`Node` chains keep forward-only `next`.
- A tree-bin delete emits **either** `Untreeify` **or** `Rotation`/`Recolor`, never both, then `Remove` last. `size--`/`modCount++` happen **before** the structural work (so balancing events carry the correct size).
- Split-driven untreeify is **silent** (`TreeEventSink.NONE`, no `Untreeify` event during resize).
- Untreeify converts surviving `TreeNode`s to fresh plain `Node`s so `isTreeBin` (i.e. `table[i] instanceof TreeNode`) reports `false` afterward.
- The `MapEvent` sealed hierarchy is unchanged (no new event type). `Untreeify` already exists from Slice 1.

## File structure

- Modify `core/TreeNode.java` — add `prev` field; add static `deleteFromTree` + `transplant` + `minimum` + `deleteFixup`.
- Modify `core/TeachingHashMap.java` — maintain `prev` in `treeifyBin`/`splitTreeBin`/tree-insert `put`; replace `remove` tree-bin guard with the real delete flow; add `untreeify` + `countAtMost` helpers; make `splitTreeBin` untreeify small halves.
- Modify `demo/Demo.java` + test — demonstrate remove + untreeify.
- Tests under `src/test/java/com/gimlism/translucent/hashmap/core/` and `.../demo/`.

---

### Task 1: `TreeNode.prev` and doubly-linked maintenance

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TreeNode.java`
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/TreeListLinkTest.java`

**Interfaces:**
- Consumes: existing `TreeNode`, `treeifyBin`, `splitTreeBin`, tree-insert `put`.
- Produces: `TreeNode` field `TreeNode<K,V> prev;`. After treeify, tree-insert, and resize-split, each tree bin's `next`/`prev` form a consistent doubly-linked list (head `prev == null`; every `e.next.prev == e`).

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/TreeListLinkTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TreeListLinkTest {
    // Walk the bin's next thread and assert prev links are consistent; return count.
    private static int assertDoublyLinked(TreeNode<Integer, String> head) {
        assertNull(head.prev, "head.prev must be null");
        int count = 0;
        TreeNode<Integer, String> e = head;
        while (e != null) {
            @SuppressWarnings("unchecked")
            TreeNode<Integer, String> next = (TreeNode<Integer, String>) e.next;
            if (next != null) assertSame(e, next.prev, "next.prev must point back to e");
            count++;
            e = next;
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    @Test
    void treeifyAndInsertBuildConsistentDoublyLinkedList() {
        var map = new TeachingHashMap<Integer, String>(8, 100.0f, 4, 2, 8); // high LF: no resize
        for (int k : new int[]{0, 8, 16, 24, 32, 40}) map.put(k, "v" + k); // treeify at 4, then inserts
        assertTrue(map.isTreeBin(0));
        int count = assertDoublyLinked((TreeNode<Integer, String>) map.table[0]);
        assertEquals(6, count);
    }

    @SuppressWarnings("unchecked")
    @Test
    void resizeSplitPreservesConsistentDoublyLinkedList() {
        var map = new TeachingHashMap<Integer, String>(8, 100.0f, 4, 2, 8);
        // keys 0,8,16,24 -> bucket 0; after resize to 16: 0,16 -> bucket 0, 8,24 -> bucket 8
        for (int k : new int[]{0, 8, 16, 24}) map.put(k, "v" + k);
        assertTrue(map.isTreeBin(0));
        map.forceResize();
        // both halves have 2 nodes (<= untreeifyThreshold is not yet wired; still trees here in Task 1)
        // verify whichever bins remain trees are doubly-linked-consistent
        for (int i = 0; i < map.table.length; i++) {
            if (map.isTreeBin(i)) {
                assertDoublyLinked((TreeNode<Integer, String>) map.table[i]);
            }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — `TreeNode` has no `prev` field (compile error: cannot find symbol `prev`).

- [ ] **Step 3: Write minimal implementation**

In `TreeNode.java`, add the field (next to `parent/left/right`):
```java
    TreeNode<K, V> parent;
    TreeNode<K, V> left;
    TreeNode<K, V> right;
    TreeNode<K, V> prev;
    boolean red;
    final long seq;
```

In `TeachingHashMap.treeifyBin`, set `prev` while threading. Replace the conversion loop:
```java
        TreeNode<K, V> first = null;
        TreeNode<K, V> prev = null;
        for (Node<K, V> e = table[i]; e != null; e = e.next) {
            TreeNode<K, V> t = new TreeNode<>(e.hash, e.key, e.value, null, nextSeq++);
            t.prev = prev;
            if (prev == null) first = t; else prev.next = t;
            prev = t;
        }
        table[i] = first;
```

In `TeachingHashMap.splitTreeBin`, set `prev` when appending to each half and clear it on detach. Replace the partition loop:
```java
        TreeNode<K, V> loHead = null, loTail = null, hiHead = null, hiTail = null;
        for (Node<K, V> e = head; e != null; ) {
            @SuppressWarnings("unchecked")
            TreeNode<K, V> t = (TreeNode<K, V>) e;
            Node<K, V> next = e.next;
            t.parent = null;
            t.left = null;
            t.right = null;
            t.next = null;
            t.prev = null;
            if ((t.hash & oldCap) == 0) {
                if (loTail == null) { loHead = t; } else { loTail.next = t; t.prev = loTail; }
                loTail = t;
            } else {
                if (hiTail == null) { hiHead = t; } else { hiTail.next = t; t.prev = hiTail; }
                hiTail = t;
            }
            e = next;
        }
```

In `TeachingHashMap.put` (tree-insert branch), set the new node's `prev`. Replace the append lines:
```java
            TreeNode<K, V> node = new TreeNode<>(h, key, value, null, nextSeq++);
            @SuppressWarnings("unchecked")
            TreeNode<K, V> tail = (TreeNode<K, V>) head;
            while (tail.next != null) tail = (TreeNode<K, V>) tail.next;
            tail.next = node;
            node.prev = tail;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `TreeListLinkTest` green; all prior tests still pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TreeNode.java src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java src/test/java/com/gimlism/translucent/hashmap/core/TreeListLinkTest.java
git commit -m "feat(core): add TreeNode.prev and maintain the doubly-linked bin list"
```

---

### Task 2: Red-black delete (`TreeNode.deleteFromTree`)

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TreeNode.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/TreeDeleteTest.java`

**Interfaces:**
- Consumes: `TreeNode.insert`/`rotateLeft`/`rotateRight`/`setColor` (Slice 2), `RedBlackInvariants` (test helper, Slice 2).
- Produces: `static <K,V> TreeNode<K,V> deleteFromTree(TreeNode<K,V> root, TreeNode<K,V> z, TreeEventSink sink)` — removes `z` from the red-black tree (pointer-based, no value-copy), restores invariants (emitting `Rotation`/`Recolor`), and returns the new root (or `null` if the tree is now empty). Operates on tree links only (`parent`/`left`/`right`/`red`); does NOT touch `next`/`prev`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/TreeDeleteTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeDeleteTest {
    private static TreeNode<Integer, String> build(int[] keys) {
        TreeNode<Integer, String> root = null;
        for (int i = 0; i < keys.length; i++) {
            root = TreeNode.insert(root, new TreeNode<>(keys[i], keys[i], "v" + keys[i], null, i), TreeEventSink.NONE);
        }
        return root;
    }

    private static List<Object> inOrder(TreeNode<?, ?> root) {
        List<Object> keys = new ArrayList<>();
        RedBlackInvariants.collectKeys(root, keys);
        return keys;
    }

    // delete each key in the given deletion order; after every delete assert RB validity + exact remaining set
    private static void deleteAllChecking(int[] insertKeys, int[] deleteOrder) {
        TreeNode<Integer, String> root = build(insertKeys);
        List<Integer> remaining = new ArrayList<>();
        for (int k : insertKeys) remaining.add(k);
        for (int k : deleteOrder) {
            TreeNode<Integer, String> victim = TreeNode.find(root, k, k);
            assertNotNull(victim, "key " + k + " should be present before delete");
            root = TreeNode.deleteFromTree(root, victim, TreeEventSink.NONE);
            remaining.remove((Integer) k);
            RedBlackInvariants.assertValid(root);
            List<Object> expected = new ArrayList<>(remaining);
            expected.sort(null);
            assertEquals(expected, inOrder(root), "in-order keys after deleting " + k);
            assertNull(TreeNode.find(root, k, k), "deleted key " + k + " must be absent");
        }
        assertNull(root, "tree empty after deleting all");
    }

    @Test
    void deleteAscendingKeepsInvariants() {
        int[] keys = new int[20];
        for (int i = 0; i < 20; i++) keys[i] = i + 1;
        int[] order = keys.clone();
        deleteAllChecking(keys, order);
    }

    @Test
    void deleteDescendingKeepsInvariants() {
        int[] keys = new int[20];
        for (int i = 0; i < 20; i++) keys[i] = i + 1;
        int[] order = new int[20];
        for (int i = 0; i < 20; i++) order[i] = 20 - i;
        deleteAllChecking(keys, order);
    }

    @Test
    void deleteMixedOrderKeepsInvariants() {
        int[] keys = {50, 40, 45, 30, 35, 60, 55, 10, 20, 15, 5, 25, 70, 65, 80, 75, 33, 37, 42, 47};
        int[] order = {45, 50, 5, 80, 33, 40, 60, 10, 47, 30, 70, 20, 35, 75, 15, 55, 25, 42, 37, 65};
        deleteAllChecking(keys, order);
    }

    @Test
    void deleteEmitsBalancingEventsOnAtLeastOneDelete() {
        var rec = new TreeRotationTest.Rec();
        TreeNode<Integer, String> root = build(new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        // delete a handful; a red-black delete from a tree this size forces fixups
        for (int k : new int[]{1, 10, 5, 3, 8}) {
            TreeNode<Integer, String> victim = TreeNode.find(root, k, k);
            root = TreeNode.deleteFromTree(root, victim, rec);
            RedBlackInvariants.assertValid(root);
        }
        // deletes that trigger the double-black cases emit rotations and/or recolours
        boolean balanced = rec.log.stream().anyMatch(s -> s.startsWith("ROT") || s.startsWith("COL"));
        org.junit.jupiter.api.Assertions.assertTrue(balanced, "expected balancing events during deletes");
    }

    @Test
    void deleteSingleNodeEmptiesTree() {
        TreeNode<Integer, String> root = build(new int[]{42});
        root = TreeNode.deleteFromTree(root, root, TreeEventSink.NONE);
        assertNull(root);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `deleteFromTree`.

- [ ] **Step 3: Write minimal implementation**

Add to `TreeNode` (standard null-based CLRS red-black delete with explicit `xParent` tracking):
```java
    /**
     * Remove {@code z} from the red-black tree (pointer-based, preserving node
     * identity), restoring invariants. Returns the new root, or null if empty.
     * Touches only tree links (parent/left/right/red), never next/prev.
     */
    static <K, V> TreeNode<K, V> deleteFromTree(TreeNode<K, V> root, TreeNode<K, V> z, TreeEventSink sink) {
        TreeNode<K, V> y = z;               // node removed or moved
        boolean yWasBlack = !y.red;
        TreeNode<K, V> x;                   // replaces y in the tree (may be null)
        TreeNode<K, V> xParent;             // parent of x (tracked because x may be null)

        if (z.left == null) {
            x = z.right;
            xParent = z.parent;
            root = transplant(root, z, z.right);
        } else if (z.right == null) {
            x = z.left;
            xParent = z.parent;
            root = transplant(root, z, z.left);
        } else {
            y = minimum(z.right);           // in-order successor
            yWasBlack = !y.red;
            x = y.right;
            if (y.parent == z) {
                xParent = y;                // x may be null; its parent becomes y
            } else {
                xParent = y.parent;
                root = transplant(root, y, y.right);
                y.right = z.right;
                y.right.parent = y;
            }
            root = transplant(root, z, y);
            y.left = z.left;
            y.left.parent = y;
            setColor(y, z.red, sink);        // y takes z's colour
        }

        if (yWasBlack) {
            root = deleteFixup(root, x, xParent, sink);
        }
        z.parent = null;
        z.left = null;
        z.right = null;
        return root;
    }

    private static <K, V> TreeNode<K, V> transplant(TreeNode<K, V> root, TreeNode<K, V> u, TreeNode<K, V> v) {
        if (u.parent == null) root = v;
        else if (u == u.parent.left) u.parent.left = v;
        else u.parent.right = v;
        if (v != null) v.parent = u.parent;
        return root;
    }

    private static <K, V> TreeNode<K, V> minimum(TreeNode<K, V> n) {
        while (n.left != null) n = n.left;
        return n;
    }

    // Restore the black-height after removing a black node. x is the (possibly
    // null) node that now carries an extra black; xParent is its parent. When x
    // is null the sibling is guaranteed non-null (removing a black means the
    // sibling subtree has black-height >= 1), so `x == xParent.left` is unambiguous.
    private static <K, V> TreeNode<K, V> deleteFixup(
            TreeNode<K, V> root, TreeNode<K, V> x, TreeNode<K, V> xParent, TreeEventSink sink) {
        while (x != root && (x == null || !x.red)) {
            if (x == xParent.left) {
                TreeNode<K, V> w = xParent.right;                 // sibling
                if (w != null && w.red) {                         // case 1
                    setColor(w, false, sink);
                    setColor(xParent, true, sink);
                    root = rotateLeft(root, xParent, sink);
                    w = xParent.right;
                }
                if (w == null
                        || ((w.left == null || !w.left.red) && (w.right == null || !w.right.red))) { // case 2
                    if (w != null) setColor(w, true, sink);
                    x = xParent;
                    xParent = x.parent;
                } else {
                    if (w.right == null || !w.right.red) {        // case 3
                        if (w.left != null) setColor(w.left, false, sink);
                        setColor(w, true, sink);
                        root = rotateRight(root, w, sink);
                        w = xParent.right;
                    }
                    setColor(w, xParent.red, sink);               // case 4
                    setColor(xParent, false, sink);
                    if (w.right != null) setColor(w.right, false, sink);
                    root = rotateLeft(root, xParent, sink);
                    x = root;
                    xParent = null;
                }
            } else {                                              // mirror image
                TreeNode<K, V> w = xParent.left;
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

Run: `mvn -q test`
Expected: PASS — `TreeDeleteTest` green (RB invariants hold after every delete across ascending/descending/mixed orders; tree empties correctly; balancing events emitted).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TreeNode.java src/test/java/com/gimlism/translucent/hashmap/core/TreeDeleteTest.java
git commit -m "feat(core): red-black delete with fixup and balancing events"
```

---

### Task 3: Map tree-bin remove (JDK shortcut) + untreeify

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/TreeRemoveTest.java`
- Delete: `src/test/java/com/gimlism/translucent/hashmap/core/TreeRemoveGuardTest.java` (the guard it tested is gone)

**Interfaces:**
- Consumes: `TreeNode.{find,deleteFromTree,root}`, `Untreeify`, `Remove`, `sinkFor`.
- Produces: `remove(Object)` now deletes from tree bins (no guard); helpers `private Node<K,V> untreeify(TreeNode<K,V> first)` and `private boolean countAtMost(Node<K,V> head, int max)`.

- [ ] **Step 1: Write the failing test**

Delete the obsolete guard test:
```bash
git rm src/test/java/com/gimlism/translucent/hashmap/core/TreeRemoveGuardTest.java
```

`src/test/java/com/gimlism/translucent/hashmap/core/TreeRemoveTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.RecordingListener;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.Recolor;
import com.gimlism.translucent.hashmap.events.Remove;
import com.gimlism.translucent.hashmap.events.Rotation;
import com.gimlism.translucent.hashmap.events.Untreeify;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TreeRemoveTest {
    // high load factor -> no auto-resize; keys k*8 all land in bucket 0
    private static TeachingHashMap<Integer, String> treeBin(int n) {
        var map = new TeachingHashMap<Integer, String>(8, 100.0f, 4, 2, 8);
        for (int i = 0; i < n; i++) map.put(i * 8, "v" + (i * 8));
        return map;
    }

    @Test
    void removeFromLargeTreeKeepsItATreeAndReturnsValue() {
        var map = treeBin(8); // 8-node tree in bucket 0
        assertTrue(map.isTreeBin(0));
        assertEquals("v24", map.remove(24));
        assertTrue(map.isTreeBin(0), "still a tree (7 > untreeifyThreshold)");
        assertNull(map.get(24));
        assertEquals(7, map.size());
        for (int i = 0; i < 8; i++) {
            if (i * 8 != 24) assertEquals("v" + (i * 8), map.get(i * 8));
        }
    }

    @Test
    void shrinkingBelowThresholdUntreeifiesToChain() {
        var map = treeBin(4); // 4-node tree
        var rec = new RecordingListener();
        map.addListener(rec);
        map.remove(0);  // 4 -> 3, still a tree (RB-delete)
        assertTrue(map.isTreeBin(0));
        rec.clear();
        map.remove(8);  // 3 -> 2 (<= untreeifyThreshold) -> untreeify
        assertFalse(map.isTreeBin(0), "bucket 0 is now a chain");
        assertEquals(1, rec.events().stream().filter(e -> e instanceof Untreeify).count());
        // an untreeify delete emits no Rotation/Recolor
        assertEquals(0, rec.events().stream().filter(e -> e instanceof Rotation || e instanceof Recolor).count());
        // remaining entries intact and iterable
        assertEquals("v16", map.get(16));
        assertEquals("v24", map.get(24));
        assertEquals(2, map.size());
    }

    @Test
    void balancingDeleteEmitsRotationOrRecolorButNoUntreeify() {
        var map = treeBin(8);
        var rec = new RecordingListener();
        map.addListener(rec);
        map.remove(0);
        assertTrue(map.isTreeBin(0));
        assertEquals(0, rec.events().stream().filter(e -> e instanceof Untreeify).count());
    }

    @Test
    void removingLastTreeEntryEmptiesTheBucket() {
        var map = treeBin(4);
        for (int k : new int[]{0, 8, 16, 24}) map.remove(k);
        assertEquals(0, map.size());
        assertNull(map.get(0));
        assertFalse(map.isTreeBin(0));
        assertNull(map.table[0]);
    }

    @Test
    void deleteEventsCarryConsistentSize() {
        var map = treeBin(8);
        var rec = new RecordingListener();
        map.addListener(rec);
        map.remove(32);
        int expected = map.size();
        for (MapEvent e : rec.events()) {
            if (e instanceof Rotation || e instanceof Recolor || e instanceof Remove) {
                assertEquals(expected, e.after().size(), "delete event size must match post-delete size");
            }
        }
    }

    @Test
    void iteratorRemoveOnTreeEntryDeletes() {
        var map = treeBin(8);
        Iterator<Map.Entry<Integer, String>> it = map.entrySet().iterator();
        Map.Entry<Integer, String> first = it.next();
        it.remove(); // deletes a tree-bin entry (previously threw)
        assertEquals(7, map.size());
        assertNull(map.get(first.getKey()));
    }

    @Test
    void removingAbsentKeyFromTreeBinReturnsNull() {
        var map = treeBin(4); // bucket 0 tree of {0,8,16,24}
        assertTrue(map.isTreeBin(0));
        assertNull(map.remove(1000)); // 1000 & 7 == 0 -> bucket 0 (the tree bin), but absent -> tree find returns null
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — `TreeRemoveTest` fails because tree-bin `remove` still throws `UnsupportedOperationException`.

- [ ] **Step 3: Write minimal implementation**

Add the import to `TeachingHashMap`:
```java
import com.gimlism.translucent.hashmap.events.Untreeify;
```

Replace the tree-bin branch at the top of `remove(Object key)` (the `if (head instanceof TreeNode) { ... throw ... }` block) with the real delete flow:
```java
        if (head instanceof TreeNode) {
            @SuppressWarnings("unchecked")
            TreeNode<K, V> treeHead = (TreeNode<K, V>) head;
            TreeNode<K, V> p = TreeNode.find(treeHead.root(), h, key);
            if (p == null) return null;
            V old = p.value;
            size--;
            modCount++;
            // unlink p from the doubly-linked list (O(1) via prev)
            @SuppressWarnings("unchecked")
            TreeNode<K, V> pNext = (TreeNode<K, V>) p.next;
            TreeNode<K, V> pPrev = p.prev;
            if (pPrev != null) pPrev.next = pNext;
            if (pNext != null) pNext.prev = pPrev;
            TreeNode<K, V> newHead = (pPrev == null) ? pNext : treeHead;
            if (newHead == null) {
                table[i] = null; // bin now empty
            } else if (countAtMost(newHead, untreeifyThreshold)) {
                table[i] = untreeify(newHead); // small: convert survivors to a chain
                emit(new Untreeify(i, snapshot()));
            } else {
                TreeNode.deleteFromTree(p.root(), p, sinkFor(i)); // RB-delete; keep survivor head
                table[i] = newHead;
            }
            p.next = null;
            p.prev = null;
            emit(new Remove(key, old, i, snapshot()));
            return old;
        }
```

Add the two helpers (place them near `treeifyBin`):
```java
    /** True if the chain/list from {@code head} has at most {@code max} nodes. */
    private boolean countAtMost(Node<K, V> head, int max) {
        int c = 0;
        for (Node<K, V> e = head; e != null; e = e.next) {
            if (++c > max) return false;
        }
        return true;
    }

    /** Convert a tree bin's surviving nodes (walked via next) into a plain-Node chain. */
    private Node<K, V> untreeify(TreeNode<K, V> first) {
        Node<K, V> head = null, tail = null;
        for (TreeNode<K, V> t = first; t != null; ) {
            @SuppressWarnings("unchecked")
            TreeNode<K, V> next = (TreeNode<K, V>) t.next;
            Node<K, V> plain = new Node<>(t.hash, t.key, t.value, null);
            if (tail == null) head = plain; else tail.next = plain;
            tail = plain;
            t = next;
        }
        return head;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `TreeRemoveTest` green; Slice-1 `TeachingHashMapRemoveTest` still passes; the deleted guard test no longer exists.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(core): red-black delete + untreeify for tree-bin remove"
```

---

### Task 4: Split-driven untreeify

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/TreeSplitUntreeifyTest.java`

**Interfaces:**
- Consumes: `untreeify`, `countAtMost` (Task 3), `TreeNode.build`, `TreeEventSink.NONE`.
- Produces: `splitTreeBin` untreeifies a half with `≤ untreeifyThreshold` nodes (silently) instead of leaving a tiny tree.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/TreeSplitUntreeifyTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.RecordingListener;
import com.gimlism.translucent.hashmap.events.Untreeify;
import org.junit.jupiter.api.Test;

class TreeSplitUntreeifyTest {
    @Test
    void resizeSplitsTinyTreeHalvesIntoChains() {
        var map = new TeachingHashMap<Integer, String>(8, 100.0f, 4, 2, 8);
        // 0,8,16,24 -> bucket 0 tree; on resize to 16: 0,16 -> bucket 0, 8,24 -> bucket 8 (each half = 2 <= untreeifyThreshold)
        for (int k : new int[]{0, 8, 16, 24}) map.put(k, "v" + k);
        assertTrue(map.isTreeBin(0));
        var rec = new RecordingListener();
        map.addListener(rec);

        map.forceResize();

        assertEquals(16, map.capacity());
        assertFalse(map.isTreeBin(0), "small half became a chain");
        assertFalse(map.isTreeBin(8), "small half became a chain");
        for (int k : new int[]{0, 8, 16, 24}) assertEquals("v" + k, map.get(k));
        // split-driven untreeify is silent: no Untreeify event during resize
        assertEquals(0, rec.events().stream().filter(e -> e instanceof Untreeify).count());
    }

    @Test
    void resizeKeepsLargeHalvesAsTrees() {
        var map = new TeachingHashMap<Integer, String>(8, 100.0f, 4, 2, 8);
        // 8 keys all in bucket 0; after resize, evens-of-8 split so each half has 4 (> untreeifyThreshold)
        // keys 0,16,32,48 -> bucket 0 ; 8,24,40,56 -> bucket 8
        for (int k : new int[]{0, 8, 16, 24, 32, 40, 48, 56}) map.put(k, "v" + k);
        assertTrue(map.isTreeBin(0));
        map.forceResize();
        assertEquals(16, map.capacity());
        assertTrue(map.isTreeBin(0), "4-node half stays a tree");
        assertTrue(map.isTreeBin(8), "4-node half stays a tree");
        for (int k : new int[]{0, 8, 16, 24, 32, 40, 48, 56}) assertEquals("v" + k, map.get(k));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — `resizeSplitsTinyTreeHalvesIntoChains` fails: the halves are still trees (`isTreeBin` true) because `splitTreeBin` always rebuilds a tree.

- [ ] **Step 3: Write minimal implementation**

In `splitTreeBin`, replace the two build-and-assign blocks so a small half untreeifies instead:
```java
        if (loHead != null) {
            if (countAtMost(loHead, untreeifyThreshold)) {
                newTab[j] = untreeify(loHead);
            } else {
                TreeNode.build(loHead, TreeEventSink.NONE);
                newTab[j] = loHead;
            }
        }
        if (hiHead != null) {
            if (countAtMost(hiHead, untreeifyThreshold)) {
                newTab[j + oldCap] = untreeify(hiHead);
            } else {
                TreeNode.build(hiHead, TreeEventSink.NONE);
                newTab[j + oldCap] = hiHead;
            }
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `TreeSplitUntreeifyTest` green; Slice-2 `TreeResizeTest` still passes (its trees have 3-node halves > untreeifyThreshold, so they stay trees).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java src/test/java/com/gimlism/translucent/hashmap/core/TreeSplitUntreeifyTest.java
git commit -m "feat(core): untreeify small halves on resize split"
```

---

### Task 5: Demo remove + untreeify

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/demo/Demo.java`
- Modify: `src/test/java/com/gimlism/translucent/hashmap/demo/DemoTest.java`

**Interfaces:**
- Consumes: `TeachingHashMap`, `ConsoleEventLogger`.
- Produces: `Demo.run` additionally removes colliding keys from the treeified bin so the output contains `REMOVE` and `UNTREEIFY` lines.

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
    void runShowsCollisionTreeifyResizeRemoveAndUntreeify() {
        var buffer = new ByteArrayOutputStream();
        Demo.run(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("COLLISION"), "expected collision, got:\n" + out);
        assertTrue(out.contains("TREEIFY"), "expected treeify, got:\n" + out);
        assertTrue(out.contains("ROTATE"), "expected rotation, got:\n" + out);
        assertTrue(out.contains("RESIZE"), "expected resize, got:\n" + out);
        assertTrue(out.contains("REMOVE"), "expected remove, got:\n" + out);
        assertTrue(out.contains("UNTREEIFY"), "expected untreeify, got:\n" + out);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — output has no `REMOVE`/`UNTREEIFY` (the demo never removes).

- [ ] **Step 3: Write minimal implementation**

Replace `Demo.run` with a version that also removes to force an untreeify. Use a high-load-factor map so the treeified bin persists, then remove down past the untreeify threshold:
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

        out.println("== removing colliding keys until bucket 0 untreeifies back to a chain ==");
        for (int k : new int[]{0, 8, 16}) { // shrink the tree bin below the untreeify threshold
            map.remove(k);
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `DemoTest` green (output shows COLLISION, TREEIFY, ROTATE, RESIZE, REMOVE, UNTREEIFY). Note: after the initial 4 keys + resize, bucket 0 remains a tree; removing 3 of its 4 members drives an untreeify.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/demo/Demo.java src/test/java/com/gimlism/translucent/hashmap/demo/DemoTest.java
git commit -m "feat(demo): demonstrate remove and untreeify"
```

---

## Self-Review

**Spec coverage:**
- `TreeNode.prev`, doubly-linked list, maintained in treeify/split/tree-insert → Task 1. ✓
- Pointer-based RB delete with fixup, fine-grained events, node identity preserved → Task 2. ✓
- JDK shortcut (unlink-first, then untreeify-if-small else RB-delete), `size--`/`modCount++` before structural work, `Untreeify` XOR balancing then `Remove` last, delete-to-empty → Task 3. ✓
- Removal-driven untreeify → Task 3; split-driven untreeify (silent) → Task 4. ✓
- Guard removed; `Iterator.remove` on tree entry deletes → Task 3. ✓
- No new event type (uses existing `Untreeify`/`Remove`/`Rotation`/`Recolor`) → all tasks. ✓
- Adversarial RB-delete invariants + size-consistency + list-consistency tests → Tasks 1/2/3. ✓
- Demo → Task 5. ✓

**Placeholder scan:** None — every step has complete, runnable code and real assertions.

**Type consistency:** `deleteFromTree(root, z, sink)` / `transplant` / `minimum` / `deleteFixup(root, x, xParent, sink)` on `TreeNode`; map helpers `untreeify(TreeNode)` returns `Node`, `countAtMost(Node, int)` returns boolean; `TreeNode.prev` field; test hook access via package-private `table` field. Names consistent across tasks. `Untreeify(bucketIndex, after)` and `Remove(key, removedValue, bucketIndex, after)` match the Slice-1 record definitions.

## Notes for the reviewer / final review

- Task 2's `deleteFromTree` is the correctness crux — the adversarial test (delete-all in three orders, RB-invariant + exact-remaining assertions after every delete) is the gate. Recommend the strong model for Task 2 (implement) and its review.
- Task 3's `remove` flow is intricate (list-unlink math + branch on remaining count + event order). Recommend the strong model here too.
- Known non-goal this slice: an `Iterator.remove` that triggers an untreeify *mid-iteration* leaves the iterator traversing the now-detached survivor `TreeNode`s (correct data, but the bin is a fresh chain). This is an extreme edge; the common single-remove case is covered. Flag if the final review wants it hardened.
