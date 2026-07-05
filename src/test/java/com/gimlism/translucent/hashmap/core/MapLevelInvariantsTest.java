package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Correctness gates at the MAP level (the review found the red-black invariants were only
 * checked at the {@code TreeNode} unit level, and the tree-bin-put resize path was untested).
 */
class MapLevelInvariantsTest {

    @SuppressWarnings("unchecked")
    private static TreeNode<Integer, String> rbRoot(TeachingHashMap<Integer, String> m, int i) {
        return ((TreeNode<Integer, String>) m.table[i]).root();
    }

    @Test
    void liveTreeBinSatisfiesRedBlackInvariantsThroughPutRemoveAndSplit() {
        // high load factor -> no auto-resize; keys k*8 all land in bucket 0
        var map = new TeachingHashMap<Integer, String>(8, 100.0f, 4, 2, 8);
        for (int k = 0; k < 14; k++) map.put(k * 8, "v" + (k * 8));
        assertTrue(map.isTreeBin(0));
        RedBlackInvariants.assertValid(rbRoot(map, 0)); // live bin, not a hand-built TreeNode

        int[] removed = {0, 24, 40, 56};
        for (int key : removed) map.remove(key); // tree-bin removes
        if (map.isTreeBin(0)) RedBlackInvariants.assertValid(rbRoot(map, 0));

        map.forceResize(); // split the tree bin; the color-only integrity of each half is the point
        for (int i = 0; i < map.capacity(); i++) {
            if (map.isTreeBin(i)) RedBlackInvariants.assertValid(rbRoot(map, i));
        }
        // data survived the whole sequence
        for (int k = 0; k < 14; k++) {
            int key = k * 8;
            boolean gone = key == 0 || key == 24 || key == 40 || key == 56;
            assertEquals(gone ? null : "v" + key, map.get(key));
        }
    }

    @Test
    void autoResizeFiresFromThePutIntoAnAlreadyTreeifiedBin() {
        // default: cap 8, threshold 6, treeify 4, minTreeify 8
        var map = new TeachingHashMap<Integer, String>();
        for (int k : new int[]{0, 8, 16, 24, 32, 40}) map.put(k, "v" + k); // treeify at 24; size 6, cap 8
        assertTrue(map.isTreeBin(0), "bucket 0 is a tree");
        assertEquals(8, map.capacity());

        map.put(48, "v48"); // tree-bin put; size 7 > threshold 6 -> resize from the tree branch

        assertEquals(16, map.capacity(), "the tree-bin put must have triggered the resize");
        for (int k : new int[]{0, 8, 16, 24, 32, 40, 48}) assertEquals("v" + k, map.get(k));
    }

    @Test
    void nullKeyInsideATreeifiedBucketIsFindableAndRemovable() {
        var map = new TeachingHashMap<Integer, String>(8, 100.0f, 4, 2, 8);
        map.put(null, "nv");           // hash(null) == 0 -> bucket 0
        map.put(8, "v8");
        map.put(16, "v16");
        map.put(24, "v24");            // 4 keys in bucket 0 -> treeify
        assertTrue(map.isTreeBin(0));
        RedBlackInvariants.assertValid(rbRoot(map, 0)); // a null key lives in the tree
        assertEquals("nv", map.get(null));
        assertTrue(map.containsKey(null));
        assertEquals("nv", map.remove(null));
        assertFalse(map.containsKey(null));
        assertEquals("v8", map.get(8)); // siblings intact
    }
}
