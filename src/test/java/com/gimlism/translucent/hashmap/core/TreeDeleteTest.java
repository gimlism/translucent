package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.gimlism.translucent.substrate.rbtree.RbEventSink;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeDeleteTest {
    private static TreeNode<Integer, String> build(int[] keys) {
        TreeNode<Integer, String> root = null;
        for (int i = 0; i < keys.length; i++) {
            root = TreeNode.insert(root, new TreeNode<>(keys[i], keys[i], "v" + keys[i], null, i), RbEventSink.none());
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
            root = TreeNode.deleteFromTree(root, victim, RbEventSink.none());
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
        var rec = new TreeInsertTest.Rec();
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
        root = TreeNode.deleteFromTree(root, root, RbEventSink.none());
        assertNull(root);
    }
}
