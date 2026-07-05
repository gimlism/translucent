package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.MapRecordingListener;
import com.gimlism.translucent.hashmap.events.Untreeify;
import org.junit.jupiter.api.Test;

class TreeSplitUntreeifyTest {
    @Test
    void resizeSplitsTinyTreeHalvesIntoChains() {
        var map = new TeachingHashMap<Integer, String>(8, 100.0f, 4, 2, 8);
        // 0,8,16,24 -> bucket 0 tree; on resize to 16: 0,16 -> bucket 0, 8,24 -> bucket 8 (each half = 2 <= untreeifyThreshold)
        for (int k : new int[]{0, 8, 16, 24}) map.put(k, "v" + k);
        assertTrue(map.isTreeBin(0));
        var rec = new MapRecordingListener();
        map.addListener(rec);

        map.forceResize();

        assertEquals(16, map.capacity());
        assertFalse(map.isTreeBin(0), "small half became a chain");
        assertFalse(map.isTreeBin(8), "small half became a chain");
        for (int k : new int[]{0, 8, 16, 24}) assertEquals("v" + k, map.get(k));
        // split-driven untreeify is silent: no Untreeify event during resize
        assertEquals(0, rec.events().stream().filter(e -> e instanceof Untreeify).count());
    }

    @Test
    void resizeKeepsLargeHalvesAsTrees() {
        var map = new TeachingHashMap<Integer, String>(8, 100.0f, 4, 2, 8);
        // 8 keys all in bucket 0; after resize, evens-of-8 split so each half has 4 (> untreeifyThreshold)
        // keys 0,16,32,48 -> bucket 0 ; 8,24,40,56 -> bucket 8
        for (int k : new int[]{0, 8, 16, 24, 32, 40, 48, 56}) map.put(k, "v" + k);
        assertTrue(map.isTreeBin(0));
        map.forceResize();
        assertEquals(16, map.capacity());
        assertTrue(map.isTreeBin(0), "4-node half stays a tree");
        assertTrue(map.isTreeBin(8), "4-node half stays a tree");
        for (int k : new int[]{0, 8, 16, 24, 32, 40, 48, 56}) assertEquals("v" + k, map.get(k));
    }
}
