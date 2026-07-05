package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.MapRecordingListener;
import com.gimlism.translucent.hashmap.events.MapEventListener;
import com.gimlism.translucent.hashmap.events.Put;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
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

    @Test
    void replaceAllEmitsOnePutPerEntry() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(1, "a");
        map.put(2, "b");
        map.put(3, "c");
        var rec = new MapRecordingListener();
        map.addListener(rec);

        map.replaceAll((k, v) -> v.toUpperCase());

        assertEquals("A", map.get(1));
        assertEquals("B", map.get(2));
        assertEquals("C", map.get(3));
        // replaceAll iterates entrySet and setValues each -> one replacement Put per entry
        long puts = rec.events().stream()
                .filter(ev -> ev instanceof Put && !((Put) ev).newEntry()).count();
        assertEquals(3, puts);
    }

    @Test
    void setValueFromWithinAListenerIsRejected() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(1, "a");
        Map.Entry<Integer, String> captured = map.entrySet().iterator().next();
        MapEventListener reentrant = ev -> captured.setValue("X"); // re-entrant write during dispatch
        map.addListener(reentrant);

        // the next put's Put event dispatches to the listener, whose setValue calls
        // beginMutation while the map is already mutating -> ConcurrentModificationException
        assertThrows(ConcurrentModificationException.class, () -> map.put(2, "b"));
        assertEquals("a", map.get(1));   // the rejected setValue never wrote
        assertEquals(2, map.size());

        // the guard must have been released in put()'s finally: with the bad listener gone,
        // the map still accepts mutations (a stuck 'mutating' flag would make this throw)
        map.removeListener(reentrant);
        map.put(3, "c");
        assertEquals(3, map.size());
    }

    @Test
    void setValueDoesNotBumpModCountSoOpenIteratorsSurvive() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(1, "a");
        map.put(2, "b");
        Iterator<Map.Entry<Integer, String>> it = map.entrySet().iterator();
        Map.Entry<Integer, String> first = it.next();   // iterator now open
        first.setValue("A");                            // value replacement: not structural

        // a structural mod would make this next() throw ConcurrentModificationException
        Map.Entry<Integer, String> second = it.next();
        assertNotNull(second);
        assertEquals("A", map.get(1));
    }

    @Test
    void getValueReturnsValueCachedAtIterationTimeNotLive() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(1, "a");
        Map.Entry<Integer, String> e = map.entrySet().iterator().next(); // caches "a"

        map.put(1, "z");   // same key mutated through the map, behind the captured entry
        assertEquals("z", map.get(1));    // the live map moved on
        assertEquals("a", e.getValue());  // the captured entry still reports its cached value
    }
}
