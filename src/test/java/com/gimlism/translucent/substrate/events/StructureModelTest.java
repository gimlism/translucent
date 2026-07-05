package com.gimlism.translucent.substrate.events;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListSnapshot;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import org.junit.jupiter.api.Test;

class StructureModelTest {
    @Test
    void mapEventIsAStructureEventWithAStructureSnapshot() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new com.gimlism.translucent.hashmap.consumer.MapRecordingListener();
        map.addListener(rec);
        map.put(1, "a");
        MapEvent e = rec.events().get(0);
        StructureEvent se = assertInstanceOf(StructureEvent.class, e);
        StructureSnapshot snap = assertInstanceOf(StructureSnapshot.class, se.after());
        assertInstanceOf(MapSnapshot.class, snap);      // covariant after() still narrows
        assertSame(e.after(), se.after());
    }

    @Test
    void listEventIsAStructureEventWithAStructureSnapshot() {
        var list = new TeachingArrayList<String>(4);
        var rec = new com.gimlism.translucent.arraylist.consumer.ListRecordingListener();
        list.addListener(rec);
        list.add("a");
        ListEvent e = rec.events().get(0);
        StructureEvent se = assertInstanceOf(StructureEvent.class, e);
        assertInstanceOf(ListSnapshot.class, assertInstanceOf(StructureSnapshot.class, se.after()));
    }
}
