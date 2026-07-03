package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TreeListLinkTest {
    // Walk the bin's next thread and assert prev links are consistent; return count.
    private static int assertDoublyLinked(TreeNode<Integer, String> head) {
        assertNull(head.prev, "head.prev must be null");
        int count = 0;
        TreeNode<Integer, String> e = head;
        while (e != null) {
            @SuppressWarnings("unchecked")
            TreeNode<Integer, String> next = (TreeNode<Integer, String>) e.next;
            if (next != null) assertSame(e, next.prev, "next.prev must point back to e");
            count++;
            e = next;
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    @Test
    void treeifyAndInsertBuildConsistentDoublyLinkedList() {
        var map = new TeachingHashMap<Integer, String>(8, 100.0f, 4, 2, 8); // high LF: no resize
        for (int k : new int[]{0, 8, 16, 24, 32, 40}) map.put(k, "v" + k); // treeify at 4, then inserts
        assertTrue(map.isTreeBin(0));
        int count = assertDoublyLinked((TreeNode<Integer, String>) map.table[0]);
        assertEquals(6, count);
    }

    @SuppressWarnings("unchecked")
    @Test
    void resizeSplitPreservesConsistentDoublyLinkedList() {
        var map = new TeachingHashMap<Integer, String>(8, 100.0f, 4, 2, 8);
        // 8 keys all land in bucket 0; after resize to 16 each half has 4 nodes
        // (> untreeifyThreshold 2), so both halves stay trees and their doubly-linked
        // lists must remain consistent after the split.
        for (int k : new int[]{0, 8, 16, 24, 32, 40, 48, 56}) map.put(k, "v" + k);
        assertTrue(map.isTreeBin(0));
        map.forceResize();
        int treeBinsChecked = 0;
        for (int i = 0; i < map.table.length; i++) {
            if (map.isTreeBin(i)) {
                assertDoublyLinked((TreeNode<Integer, String>) map.table[i]);
                treeBinsChecked++;
            }
        }
        assertEquals(2, treeBinsChecked, "both 4-node halves must remain trees and be checked");
    }
}
