package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TeachingHashMapBasicsTest {
    @Test
    void putGetRoundTrip() {
        var map = new TeachingHashMap<String, Integer>();
        assertNull(map.put("a", 1));
        assertEquals(1, map.get("a"));
        assertEquals(1, map.size());
        assertTrue(map.containsKey("a"));
        assertFalse(map.containsKey("z"));
    }

    @Test
    void putReplacesExistingValueAndReturnsOld() {
        var map = new TeachingHashMap<String, Integer>();
        map.put("a", 1);
        assertEquals(1, map.put("a", 2));
        assertEquals(2, map.get("a"));
        assertEquals(1, map.size());
    }

    @Test
    void collidingKeysCoexistInSameBucketOrder() {
        // capacity 8: keys hashing to 0, 8, 16 all land in bucket 0
        var map = new TeachingHashMap<Integer, String>();
        map.put(0, "zero");
        map.put(8, "eight");
        map.put(16, "sixteen");
        assertEquals(3, map.size());
        assertEquals("zero", map.get(0));
        assertEquals("eight", map.get(8));
        assertEquals("sixteen", map.get(16));
    }

    @Test
    void supportsNullKeyAndValue() {
        var map = new TeachingHashMap<String, String>();
        map.put(null, "nullkey");
        map.put("x", null);
        assertEquals("nullkey", map.get(null));
        assertNull(map.get("x"));
        assertTrue(map.containsKey("x"));
    }

    @Test
    void entrySetReflectsContents() {
        var map = new TeachingHashMap<String, Integer>();
        map.put("a", 1);
        map.put("b", 2);
        int sum = 0;
        for (var e : map.entrySet()) {
            sum += e.getValue();
        }
        assertEquals(3, sum);
        assertEquals(2, map.entrySet().size());
    }
}
