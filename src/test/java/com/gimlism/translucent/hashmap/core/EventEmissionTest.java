package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.MapRecordingListener;
import com.gimlism.translucent.hashmap.events.Collision;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.MapEventListener;
import com.gimlism.translucent.hashmap.events.Put;
import com.gimlism.translucent.hashmap.events.Remove;
import com.gimlism.translucent.hashmap.events.Resize;
import java.util.ConcurrentModificationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventEmissionTest {
    @Test
    void newEntryEmitsPut() {
        var map = new TeachingHashMap<String, Integer>();
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.put("a", 1);
        assertEquals(1, rec.events().size());
        Put p = assertInstanceOf(Put.class, rec.events().get(0));
        assertEquals("a", p.key());
        assertEquals(1, p.value());
        assertTrue(p.newEntry());
        assertEquals(1, p.after().size());
    }

    @Test
    void replacementEmitsPutWithPreviousValue() {
        var map = new TeachingHashMap<String, Integer>();
        map.put("a", 1);
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.put("a", 2);
        assertEquals(1, rec.events().size());
        Put p = (Put) rec.events().get(0);
        assertFalse(p.newEntry());
        assertEquals(1, p.previousValue());
        assertEquals(2, p.value());
    }

    @Test
    void collisionEmitsPutThenCollision() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(0, "zero");
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.put(8, "eight"); // bucket 0 already occupied
        assertEquals(2, rec.events().size());
        assertInstanceOf(Put.class, rec.events().get(0));
        Collision c = assertInstanceOf(Collision.class, rec.events().get(1));
        assertEquals(0, c.bucketIndex());
        assertEquals(1, c.chainLengthBefore());
        assertEquals(2, c.chainLengthAfter());
    }

    @Test
    void resizeEmittedAfterThresholdBreach() {
        var map = new TeachingHashMap<Integer, Integer>();
        var rec = new MapRecordingListener();
        map.addListener(rec);
        for (int k = 1; k <= 7; k++) map.put(k, k); // 7th breaches threshold 6
        List<MapEvent> events = rec.events();
        Resize resize = (Resize) events.stream()
            .filter(e -> e instanceof Resize).findFirst().orElseThrow();
        assertEquals(8, resize.oldCapacity());
        assertEquals(16, resize.newCapacity());
        assertEquals(8, resize.before().capacity());
        assertEquals(16, resize.after().capacity());
        // resize is the last event of the 7th put
        assertInstanceOf(Resize.class, events.get(events.size() - 1));
    }

    @Test
    void removeEmitsRemove() {
        var map = new TeachingHashMap<String, Integer>();
        map.put("a", 1);
        var rec = new MapRecordingListener();
        map.addListener(rec);
        assertEquals(1, map.remove("a"));
        assertEquals(1, rec.events().size());
        Remove r = assertInstanceOf(Remove.class, rec.events().get(0));
        assertEquals("a", r.key());
        assertEquals(1, r.removedValue());
    }

    @Test
    void removeMissingKeyEmitsNothing() {
        var map = new TeachingHashMap<String, Integer>();
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.remove("nope");
        assertTrue(rec.events().isEmpty());
    }

    @Test
    void clearEmitsRemovePerEntry() {
        var map = new TeachingHashMap<Integer, Integer>();
        map.put(1, 1);
        map.put(2, 2);
        map.put(3, 3);
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.clear();
        assertEquals(3, rec.events().size());
        assertTrue(rec.events().stream().allMatch(e -> e instanceof Remove));
        assertEquals(0, map.size());
    }

    @Test
    void listenerMayUnregisterItselfDuringDispatch() {
        var map = new TeachingHashMap<Integer, Integer>();
        var survivor = new MapRecordingListener();
        MapEventListener[] selfRemoving = new MapEventListener[1];
        selfRemoving[0] = e -> map.removeListener(selfRemoving[0]);
        map.addListener(selfRemoving[0]);
        map.addListener(survivor);
        map.put(1, 1); // selfRemoving unregisters mid-dispatch; must not throw
        map.put(2, 2);
        assertEquals(2, survivor.events().size());
    }

    @Test
    void listenerMayReadMapDuringDispatch() {
        var map = new TeachingHashMap<Integer, Integer>();
        int[] observedSize = {-1};
        map.addListener(e -> observedSize[0] = map.size()); // read-only: allowed
        map.put(1, 1);
        assertEquals(1, observedSize[0]);
    }

    @Test
    void listenerMutatingMapDuringDispatchThrows() {
        var map = new TeachingHashMap<Integer, Integer>();
        map.addListener(e -> map.put(99, 99)); // re-entrant put: forbidden
        assertThrows(ConcurrentModificationException.class, () -> map.put(1, 1));
    }

    @Test
    void mapRemainsUsableAfterRejectedReentrantMutation() {
        var map = new TeachingHashMap<Integer, Integer>();
        MapEventListener bad = e -> map.remove(1); // re-entrant remove: forbidden
        map.addListener(bad);
        assertThrows(ConcurrentModificationException.class, () -> map.put(1, 1));
        // the guard must have been cleared by the finally block, so the map still works
        map.removeListener(bad);
        assertEquals(1, map.get(1)); // the outer put itself completed before dispatch failed
        map.put(2, 2);
        assertEquals(2, map.size());
    }
}
