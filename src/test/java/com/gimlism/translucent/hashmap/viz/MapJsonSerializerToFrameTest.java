package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.MapRecordingListener;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import org.junit.jupiter.api.Test;

class MapJsonSerializerToFrameTest {

    @Test
    void toFrameEmitsOneFrameObjectForOneEvent() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.put(1, "a");

        String frame = MapJsonSerializer.toFrame(rec.events().get(0));

        assertTrue(frame.startsWith("{"), "one JSON object");
        assertTrue(frame.contains("\"event\":{"), "carries the event descriptor");
        assertTrue(frame.contains("\"map\":{"), "carries the whole-map snapshot");
        assertTrue(frame.contains("\"type\":\"Put\""), "the event type");
        // NOT wrapped in a frames array
        assertTrue(!frame.contains("\"frames\""), "a single frame is not the batch wrapper");
    }

    @Test
    void toJsonIsTheFramesWrapperOfEachToFrame() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.put(1, "a");
        map.put(9, "b"); // collides in bucket 1 at cap 8: Collision + Put

        var events = rec.events();
        var expected = new StringBuilder("{\"frames\":[");
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) expected.append(',');
            expected.append(MapJsonSerializer.toFrame(events.get(i)));
        }
        expected.append("]}");

        assertEquals(expected.toString(), MapJsonSerializer.toJson(events));
    }
}
