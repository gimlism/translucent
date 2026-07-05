package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.MapRecordingListener;
import com.gimlism.translucent.hashmap.events.Put;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pins {@code setValue} observability/write-through and the inherited bulk ops. */
class MapEntryAndBulkTest {

    @Test
    void setValueOnAChainEntryEmitsPutAndWritesThrough() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(1, "a");
        var rec = new MapRecordingListener();
        map.addListener(rec);

        Map.Entry<Integer, String> e = map.entrySet().iterator().next();
        assertEquals("a", e.setValue("A"));    // returns the old value
        assertEquals("A", map.get(1));         // live node -> write-through
        assertEquals("A", e.getValue());       // wrapper's cached view is updated

        assertEquals(1, rec.events().size());
        Put p = assertInstanceOf(Put.class, rec.events().get(0));
        assertEquals(1, p.key());
        assertEquals("A", p.value());
        assertEquals("a", p.previousValue());
        assertFalse(p.newEntry());
    }

    @Test
    void setValueOnARemovedKeyThrowsIllegalState() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(1, "a");
        Map.Entry<Integer, String> e = map.entrySet().iterator().next();
        map.remove(1);   // the captured entry's key is now gone
        assertThrows(IllegalStateException.class, () -> e.setValue("A"));
    }

    @Test
    void setValueSurvivesTreeifyOfItsBucket() {
        var map = new TeachingHashMap<Integer, String>();      // cap 8, treeify at 4
        map.put(1, "a");
        Map.Entry<Integer, String> e = map.entrySet().iterator().next(); // captured pre-treeify
        assertEquals(1, e.getKey());

        map.put(9, "i");    // 1, 9, 17, 25 all share bucket 1 (key & 7 == 1)...
        map.put(17, "q");
        map.put(25, "y");   // ...and the 4th insertion treeifies the bucket
        assertTrue(map.isTreeBin(1));   // key-1's node was copied into a fresh TreeNode

        var rec = new MapRecordingListener();
        map.addListener(rec);
        assertEquals("a", e.setValue("A"));   // re-finds the LIVE tree node, not the orphan
        assertEquals("A", map.get(1));        // write is not lost
        long puts = rec.events().stream()
                .filter(ev -> ev instanceof Put && !((Put) ev).newEntry()).count();
        assertEquals(1, puts);
    }

    @Test
    void putAllEmitsOnePutPerEntry() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new MapRecordingListener();
        map.addListener(rec);

        var source = new LinkedHashMap<Integer, String>();
        source.put(1, "a");
        source.put(2, "b");
        source.put(3, "c");
        map.putAll(source);

        assertEquals(3, map.size());
        // putAll funnels through put(): one new-entry Put per source entry
        long puts = rec.events().stream().filter(ev -> ev instanceof Put && ((Put) ev).newEntry()).count();
        assertEquals(3, puts);
    }
}
