package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gimlism.translucent.hashmap.consumer.RecordingListener;
import com.gimlism.translucent.hashmap.events.Put;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pins the documented {@code setValue} behaviour and the inherited {@code putAll}. */
class MapEntryAndBulkTest {

    @Test
    void setValueOnAChainEntryWritesThroughButEmitsNoEvent() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(1, "a");
        var rec = new RecordingListener();
        map.addListener(rec);

        Map.Entry<Integer, String> e = map.entrySet().iterator().next();
        assertEquals("a", e.setValue("A"));   // returns the old value
        assertEquals("A", map.get(1));         // live node -> write-through
        assertEquals(0, rec.events().size());  // documented limitation: no event emitted
    }

    @Test
    void putAllEmitsOnePutPerEntry() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new RecordingListener();
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
