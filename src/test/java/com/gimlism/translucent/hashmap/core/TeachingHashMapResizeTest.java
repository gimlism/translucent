package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TeachingHashMapResizeTest {
    @Test
    void doublesCapacityWhenThresholdExceeded() {
        // capacity 8, loadFactor 0.75 -> threshold 6; 7th insert triggers resize to 16
        var map = new TeachingHashMap<Integer, Integer>();
        assertEquals(8, map.capacity());
        for (int k = 1; k <= 6; k++) map.put(k, k);
        assertEquals(8, map.capacity());
        map.put(7, 7);
        assertEquals(16, map.capacity());
    }

    @Test
    void resizePreservesAllEntries() {
        var map = new TeachingHashMap<Integer, Integer>();
        for (int k = 0; k < 50; k++) map.put(k, k * 10);
        assertEquals(50, map.size());
        for (int k = 0; k < 50; k++) assertEquals(k * 10, map.get(k));
    }

    @Test
    void tableSizeForClampsAtMaximumCapacityWithoutOverflow() {
        // Without the cap this loops forever (n overflows past 2^31). Pure int
        // math — does NOT allocate a table, so it's safe to assert directly.
        assertEquals(1 << 30, TeachingHashMap.tableSizeFor(Integer.MAX_VALUE));
        assertEquals(1 << 30, TeachingHashMap.tableSizeFor((1 << 30) + 1));
        assertEquals(1 << 30, TeachingHashMap.tableSizeFor(1 << 30));
    }
}
