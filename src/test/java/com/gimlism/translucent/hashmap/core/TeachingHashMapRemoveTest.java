package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TeachingHashMapRemoveTest {
    @Test
    void removeReturnsOldValueAndShrinks() {
        var map = new TeachingHashMap<String, Integer>();
        map.put("a", 1);
        map.put("b", 2);
        assertEquals(1, map.remove("a"));
        assertNull(map.get("a"));
        assertEquals(1, map.size());
    }

    @Test
    void removeMiddleOfChainKeepsOthers() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(0, "zero");
        map.put(8, "eight");   // same bucket as 0
        map.put(16, "sixteen"); // same bucket as 0
        assertEquals("eight", map.remove(8));
        assertEquals("zero", map.get(0));
        assertEquals("sixteen", map.get(16));
        assertEquals(2, map.size());
    }

    @Test
    void removeMissingKeyReturnsNull() {
        var map = new TeachingHashMap<String, Integer>();
        assertNull(map.remove("nope"));
    }

    @Test
    void clearEmptiesMap() {
        var map = new TeachingHashMap<String, Integer>();
        map.put("a", 1);
        map.put("b", 2);
        map.clear();
        assertEquals(0, map.size());
        assertFalse(map.containsKey("a"));
        assertTrue(map.entrySet().isEmpty());
    }
}
