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
