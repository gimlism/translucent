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
