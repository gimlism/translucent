package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.gimlism.translucent.substrate.rbtree.RbEventSink;
import org.junit.jupiter.api.Test;

class TreeFindTest {
    private static TreeNode<Integer, String> tree(int... keys) {
        TreeNode<Integer, String> root = null;
        for (int k : keys) {
            root = TreeNode.insert(root, new TreeNode<>(k, k, "v" + k, null, k), RbEventSink.none());
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
            root = TreeNode.insert(root, new TreeNode<>(7, keys[i], "V" + keys[i], null, i), RbEventSink.none());
        }
        for (String k : keys) {
            assertEquals("V" + k, TreeNode.find(root, 7, k).value);
        }
        assertNull(TreeNode.find(root, 7, "z"));
    }
}
