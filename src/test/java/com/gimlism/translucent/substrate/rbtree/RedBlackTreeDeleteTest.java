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
