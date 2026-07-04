package com.gimlism.translucent.substrate.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.events.MapEvent;
import org.junit.jupiter.api.Test;

class RecordingListenerTest {
    // the SAME generic recorder type serves both structures
    @Test
    void genericRecorderRecordsMapEvents() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new RecordingListener<MapEvent>();
        map.addListener(rec);
        map.put(1, "a");
        map.put(2, "b");
        assertEquals(2, rec.events().size());
        rec.clear();
        assertEquals(0, rec.events().size());
    }

    @Test
    void genericRecorderRecordsListEvents() {
        var list = new TeachingArrayList<String>(4);
        var rec = new RecordingListener<ListEvent>();
        list.addListener(rec);
        list.add("a");
        assertEquals(1, rec.events().size());
    }

    @Test
    void eventsViewIsUnmodifiable() {
        var rec = new RecordingListener<MapEvent>();
        assertFalse(rec.events() instanceof java.util.ArrayList);
    }
}
