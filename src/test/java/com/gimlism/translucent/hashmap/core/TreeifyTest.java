package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.RecordingListener;
import com.gimlism.translucent.hashmap.events.Recolor;
import com.gimlism.translucent.hashmap.events.Rotation;
import com.gimlism.translucent.hashmap.events.Treeify;
import org.junit.jupiter.api.Test;

class TreeifyTest {
    // keys 0, 8, 16, 24 all land in bucket 0 at capacity 8 ((cap-1)&hash == 0)
    private static TeachingHashMap<Integer, String> collidingMap() {
        return new TeachingHashMap<>();
    }

    @Test
    void chainTreeifiesAtThreshold() {
        var map = collidingMap();
        map.put(0, "a");
        map.put(8, "b");
        map.put(16, "c");
        assertFalse(map.isTreeBin(0), "still a chain at length 3");
        map.put(24, "d"); // 4th -> treeifyThreshold(4)
        assertTrue(map.isTreeBin(0), "bucket 0 is now a tree");
        assertEquals(4, map.size());
        // map was loaded with put(0,"a"), put(8,"b"), put(16,"c"), put(24,"d")
        for (int k : new int[]{0, 8, 16, 24}) {
            assertEquals("abcd".substring(k / 8, k / 8 + 1), map.get(k));
        }
    }

    @Test
    void treeifyEmitsTreeifyThenBalancingEvents() {
        var map = collidingMap();
        var rec = new RecordingListener();
        map.addListener(rec);
        map.put(0, "a");
        map.put(8, "b");
        map.put(16, "c");
        map.put(24, "d");
        // exactly one Treeify, and it precedes the rotation/recolour burst
        long treeifyCount = rec.events().stream().filter(e -> e instanceof Treeify).count();
        assertEquals(1, treeifyCount);
        int treeifyIdx = -1;
        for (int i = 0; i < rec.events().size(); i++) {
            if (rec.events().get(i) instanceof Treeify) { treeifyIdx = i; break; }
        }
        boolean balancingAfter = rec.events().subList(treeifyIdx + 1, rec.events().size()).stream()
            .anyMatch(e -> e instanceof Rotation || e instanceof Recolor);
        assertTrue(balancingAfter, "expected rotation/recolour after Treeify");
    }

    @Test
    void treeBinLookupAndReplaceWork() {
        var map = collidingMap();
        for (int k : new int[]{0, 8, 16, 24, 32}) map.put(k, "v" + k);
        assertTrue(map.isTreeBin(0));
        for (int k : new int[]{0, 8, 16, 24, 32}) assertEquals("v" + k, map.get(k));
        assertEquals("v8", map.put(8, "v8b")); // replace returns old
        assertEquals("v8b", map.get(8));
        assertEquals(5, map.size()); // replace does not grow
    }

    @Test
    void belowMinTreeifyCapacityResizesInsteadOfTreeifying() {
        // capacity 4 < minTreeifyCapacity 8: a 4-long chain must resize, not treeify
        var map = new TeachingHashMap<Integer, String>(4, 0.75f, 4, 2, 8);
        // keys 0,4,8,12 collide at cap 4 ((cap-1)&hash == 0); but resize fires first
        map.put(0, "a");
        map.put(4, "b");
        map.put(8, "c"); // size 3 > threshold(3) -> resize happens along the way
        map.put(12, "d");
        assertFalse(map.isTreeBin(0), "should have resized rather than treeified");
        assertTrue(map.capacity() > 4);
    }
}
