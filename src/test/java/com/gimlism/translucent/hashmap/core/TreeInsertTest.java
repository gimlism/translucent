package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.substrate.rbtree.RbEventSink;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeInsertTest {
    /** Records rotations/recolours reported by the shared RB kernel, for event-emission assertions. */
    static final class Rec implements RbEventSink<TreeNode<Integer, String>> {
        final List<String> log = new ArrayList<>();
        @Override
        public void rotated(com.gimlism.translucent.substrate.rbtree.Direction d, TreeNode<Integer, String> p) {
            log.add("ROT " + d + " " + p.getKey());
        }
        @Override
        public void recolored(TreeNode<Integer, String> n,
                com.gimlism.translucent.substrate.rbtree.Color o,
                com.gimlism.translucent.substrate.rbtree.Color newColor) {
            log.add("COL " + n.getKey() + " " + o + "->" + newColor);
        }
    }

    @Test
    void insertingAscendingKeysStaysBalancedAndComplete() {
        TreeNode<Integer, String> root = null;
        for (int k = 1; k <= 20; k++) {
            root = TreeNode.insert(root, new TreeNode<>(k, k, "v" + k, null, k), RbEventSink.none());
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
        var rec = new Rec();
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
        root = TreeNode.insert(root, new TreeNode<>(7, 100, "a", null, 0), RbEventSink.none());
        root = TreeNode.insert(root, new TreeNode<>(7, 200, "b", null, 1), RbEventSink.none());
        root = TreeNode.insert(root, new TreeNode<>(7, 300, "c", null, 2), RbEventSink.none());
        RedBlackInvariants.assertValid(root);
        List<Object> keys = new ArrayList<>();
        RedBlackInvariants.collectKeys(root, keys);
        assertEquals(List.of(100, 200, 300), keys); // seq order
    }

    @Test
    void descendingInsertsStayBalanced() {
        // descending keys make every new node a leftmost child -> exercises the
        // p == g.left branch and outer rotateRight, the mirror of the ascending test
        TreeNode<Integer, String> root = null;
        for (int k = 20; k >= 1; k--) {
            root = TreeNode.insert(root, new TreeNode<>(k, k, "v" + k, null, 20 - k), RbEventSink.none());
            RedBlackInvariants.assertValid(root);
        }
        List<Object> keys = new ArrayList<>();
        RedBlackInvariants.collectKeys(root, keys);
        List<Object> expected = new ArrayList<>();
        for (int k = 1; k <= 20; k++) expected.add(k);
        assertEquals(expected, keys);
    }

    @Test
    void zigzagInsertsForceInnerRotationsAndStayValid() {
        // a mixed order that forces inner (opposite-side) pre-rotations on both sides
        int[] order = {50, 40, 45, 30, 35, 60, 55, 10, 20, 15, 5, 25};
        TreeNode<Integer, String> root = null;
        for (int i = 0; i < order.length; i++) {
            root = TreeNode.insert(root, new TreeNode<>(order[i], order[i], "v" + order[i], null, i), RbEventSink.none());
            RedBlackInvariants.assertValid(root);
        }
        List<Object> keys = new ArrayList<>();
        RedBlackInvariants.collectKeys(root, keys);
        List<Object> sorted = new ArrayList<>(keys);
        sorted.sort(null);
        assertEquals(sorted, keys, "in-order traversal must be sorted by hash");
        assertEquals(order.length, keys.size());
        for (int k : order) assertTrue(keys.contains(k), "missing key " + k);
    }
}
