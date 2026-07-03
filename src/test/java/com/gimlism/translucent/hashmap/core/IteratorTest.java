package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IteratorTest {
    @Test
    void iteratorRemoveDeletesEntry() {
        var map = new TeachingHashMap<Integer, Integer>();
        for (int k = 0; k < 5; k++) map.put(k, k);
        Iterator<Map.Entry<Integer, Integer>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getKey() % 2 == 0) it.remove();
        }
        assertEquals(2, map.size()); // 1 and 3 remain
        assertFalse(map.containsKey(0));
        assertEquals(1, map.get(1));
        assertEquals(3, map.get(3));
    }

    @Test
    void structuralModificationDuringIterationThrows() {
        var map = new TeachingHashMap<Integer, Integer>();
        for (int k = 0; k < 5; k++) map.put(k, k);
        Iterator<Map.Entry<Integer, Integer>> it = map.entrySet().iterator();
        it.next();
        map.put(99, 99); // structural change
        assertThrows(ConcurrentModificationException.class, it::next);
    }

    @Test
    void resizeDuringIterationThrows() {
        var map = new TeachingHashMap<Integer, Integer>();
        for (int k = 0; k < 5; k++) map.put(k, k);
        Iterator<Map.Entry<Integer, Integer>> it = map.entrySet().iterator();
        it.next();
        map.forceResize(); // rehash relocates every entry; the live iterator must fail fast
        assertThrows(ConcurrentModificationException.class, it::next);
    }
}
