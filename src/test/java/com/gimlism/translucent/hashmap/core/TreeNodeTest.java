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
