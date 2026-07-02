package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeInsertTest {
    @Test
    void insertingAscendingKeysStaysBalancedAndComplete() {
        TreeNode<Integer, String> root = null;
        for (int k = 1; k <= 20; k++) {
            root = TreeNode.insert(root, new TreeNode<>(k, k, "v" + k, null, k), TreeEventSink.NONE);
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
        var rec = new TreeRotationTest.Rec();
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
        root = TreeNode.insert(root, new TreeNode<>(7, 100, "a", null, 0), TreeEventSink.NONE);
        root = TreeNode.insert(root, new TreeNode<>(7, 200, "b", null, 1), TreeEventSink.NONE);
        root = TreeNode.insert(root, new TreeNode<>(7, 300, "c", null, 2), TreeEventSink.NONE);
        RedBlackInvariants.assertValid(root);
        List<Object> keys = new ArrayList<>();
        RedBlackInvariants.collectKeys(root, keys);
        assertEquals(List.of(100, 200, 300), keys); // seq order
    }
}
