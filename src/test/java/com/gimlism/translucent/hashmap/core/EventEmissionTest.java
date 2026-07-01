package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.RecordingListener;
import com.gimlism.translucent.hashmap.events.Collision;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.Put;
import com.gimlism.translucent.hashmap.events.Remove;
import com.gimlism.translucent.hashmap.events.Resize;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventEmissionTest {
    @Test
    void newEntryEmitsPut() {
        var map = new TeachingHashMap<String, Integer>();
        var rec = new RecordingListener();
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
        var rec = new RecordingListener();
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
        var rec = new RecordingListener();
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
        var rec = new RecordingListener();
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
        var rec = new RecordingListener();
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
        var rec = new RecordingListener();
        map.addListener(rec);
        map.remove("nope");
        assertTrue(rec.events().isEmpty());
    }
}
