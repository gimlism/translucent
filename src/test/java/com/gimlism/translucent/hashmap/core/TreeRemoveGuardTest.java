package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TreeRemoveGuardTest {
    private static TeachingHashMap<Integer, String> treeified() {
        var map = new TeachingHashMap<Integer, String>();
        for (int k : new int[]{0, 8, 16, 24}) map.put(k, "v" + k);
        assertTrue(map.isTreeBin(0));
        return map;
    }

    @Test
    void removingKeyInTreeBinThrows() {
        var map = treeified();
        assertThrows(UnsupportedOperationException.class, () -> map.remove(8));
    }

    @Test
    void removingAbsentKeyFromTreeBinReturnsNull() {
        var map = treeified(); // bucket 0 is a tree of {0, 8, 16, 24}
        // key 32 maps to bucket 0 (32 & 7 == 0) but is NOT in the tree:
        // this exercises the tree-bin "absent -> return null" branch, not an empty bin
        assertTrue(map.isTreeBin(0));
        assertNull(map.remove(32)); // no throw
    }

    @Test
    void chainRemoveStillWorks() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(1, "a");
        map.put(2, "b");
        assertEquals("a", map.remove(1));
        assertEquals(1, map.size());
    }

    @Test
    void iteratorRemoveOnTreeEntryThrows() {
        var map = treeified();
        Iterator<Map.Entry<Integer, String>> it = map.entrySet().iterator();
        it.next();
        assertThrows(UnsupportedOperationException.class, it::remove);
    }
}
