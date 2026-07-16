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
