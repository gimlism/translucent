package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.MapRecordingListener;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class MapLiveVisualizerTest {

    @Test
    void emitsOneFramePerEventEqualToToFrame() {
        var frames = new ArrayList<String>();
        var rec = new MapRecordingListener();
        var map = new TeachingHashMap<Integer, String>();
        map.addListener(new MapLiveVisualizer(frames::add));
        map.addListener(rec);

        map.put(1, "a");
        map.put(9, "b"); // collides in bucket 1 at cap 8: Collision + Put

        assertTrue(frames.size() >= 2, "at least one frame per mutation event");
        assertEquals(rec.events().size(), frames.size(), "exactly one frame per event");
        for (int i = 0; i < frames.size(); i++) {
            assertEquals(MapJsonSerializer.toFrame(rec.events().get(i)), frames.get(i),
                    "frame " + i + " equals the serializer's toFrame of that event");
        }
    }
}
