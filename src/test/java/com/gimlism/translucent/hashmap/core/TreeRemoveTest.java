package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.MapRecordingListener;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.Recolor;
import com.gimlism.translucent.hashmap.events.Remove;
import com.gimlism.translucent.hashmap.events.Rotation;
import com.gimlism.translucent.hashmap.events.TreeSnapshot;
import com.gimlism.translucent.hashmap.events.Untreeify;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TreeRemoveTest {
    // high load factor -> no auto-resize; keys k*8 all land in bucket 0
    private static TeachingHashMap<Integer, String> treeBin(int n) {
        var map = new TeachingHashMap<Integer, String>(8, 100.0f, 4, 2, 8);
        for (int i = 0; i < n; i++) map.put(i * 8, "v" + (i * 8));
        return map;
    }

    @Test
    void removeFromLargeTreeKeepsItATreeAndReturnsValue() {
        var map = treeBin(8); // 8-node tree in bucket 0
        assertTrue(map.isTreeBin(0));
        assertEquals("v24", map.remove(24));
        assertTrue(map.isTreeBin(0), "still a tree (7 > untreeifyThreshold)");
        assertNull(map.get(24));
        assertEquals(7, map.size());
        for (int i = 0; i < 8; i++) {
            if (i * 8 != 24) assertEquals("v" + (i * 8), map.get(i * 8));
        }
    }

    @Test
    void shrinkingBelowThresholdUntreeifiesToChain() {
        var map = treeBin(4); // 4-node tree
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.remove(0);  // 4 -> 3, still a tree (RB-delete)
        assertTrue(map.isTreeBin(0));
        rec.clear();
        map.remove(8);  // 3 -> 2 (<= untreeifyThreshold) -> untreeify
        assertFalse(map.isTreeBin(0), "bucket 0 is now a chain");
        assertEquals(1, rec.events().stream().filter(e -> e instanceof Untreeify).count());
        // an untreeify delete emits no Rotation/Recolor
        assertEquals(0, rec.events().stream().filter(e -> e instanceof Rotation || e instanceof Recolor).count());
        // remaining entries intact and iterable
        assertEquals("v16", map.get(16));
        assertEquals("v24", map.get(24));
        assertEquals(2, map.size());
    }

    @Test
    void balancingDeleteEmitsRotationOrRecolorButNoUntreeify() {
        var map = treeBin(8);
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.remove(0);
        assertTrue(map.isTreeBin(0));
        assertEquals(0, rec.events().stream().filter(e -> e instanceof Untreeify).count());
    }

    @Test
    void removingLastTreeEntryEmptiesTheBucket() {
        var map = treeBin(4);
        for (int k : new int[]{0, 8, 16, 24}) map.remove(k);
        assertEquals(0, map.size());
        assertNull(map.get(0));
        assertFalse(map.isTreeBin(0));
        assertNull(map.table[0]);
    }

    @Test
    void deleteEventsCarryConsistentSize() {
        var map = treeBin(8);
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.remove(32);
        int expected = map.size();
        for (MapEvent e : rec.events()) {
            if (e instanceof Rotation || e instanceof Recolor || e instanceof Remove) {
                assertEquals(expected, e.after().size(), "delete event size must match post-delete size");
            }
        }
    }

    @Test
    void iteratorRemoveOnTreeEntryDeletes() {
        var map = treeBin(8);
        Iterator<Map.Entry<Integer, String>> it = map.entrySet().iterator();
        Map.Entry<Integer, String> first = it.next();
        it.remove(); // deletes a tree-bin entry (previously threw)
        assertEquals(7, map.size());
        assertNull(map.get(first.getKey()));
    }

    @Test
    void rbDeleteOfRootHeadFramesAreRootedAtSurvivor() {
        // insertion order makes the treeify root (16) also the list head; then delete it.
        // 3 survivors > untreeifyThreshold(2) => RB-delete path with fixup events.
        var map = new TeachingHashMap<Integer, String>(8, 100.0f, 4, 2, 8);
        for (int k : new int[]{16, 8, 24, 0}) map.put(k, "v" + k);
        var rec = new com.gimlism.translucent.hashmap.consumer.MapRecordingListener();
        map.addListener(rec);
        map.remove(16);
        boolean sawBalancing = false;
        for (MapEvent e : rec.events()) {
            if (e instanceof Rotation || e instanceof Recolor || e instanceof Remove) {
                TreeSnapshot tree = (TreeSnapshot) e.after().buckets().get(0);
                assertNotEquals(16, tree.root().key(),
                    "delete frame must be rooted at a survivor, not the detached deleted node");
                if (e instanceof Rotation || e instanceof Recolor) sawBalancing = true;
            }
        }
        assertTrue(sawBalancing, "expected balancing events during a root RB-delete");
    }

    @Test
    void removingAbsentKeyFromTreeBinReturnsNull() {
        var map = treeBin(4); // bucket 0 tree of {0,8,16,24}
        assertTrue(map.isTreeBin(0));
        assertNull(map.remove(1000)); // 1000 & 7 == 0 -> bucket 0 (the tree bin), but absent -> tree find returns null
    }
}
