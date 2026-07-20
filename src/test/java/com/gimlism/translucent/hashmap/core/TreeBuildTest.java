package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gimlism.translucent.substrate.rbtree.RbEventSink;
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

        TreeNode<Integer, String> root = TreeNode.build(first, RbEventSink.none());

        RedBlackInvariants.assertValid(root);
        // tree holds all keys, in-order == sorted by hash
        List<Object> inOrder = new ArrayList<>();
        RedBlackInvariants.collectKeys(root, inOrder);
        assertEquals(List.of(1, 2, 3, 4, 5), inOrder);
        // next thread still in original insertion order
        List<Object> threaded = new ArrayList<>();
        for (Node<Integer, String> e = first; e != null; e = e.next()) threaded.add(e.getKey());
        assertEquals(List.of(4, 2, 5, 1, 3), threaded);
    }
}
