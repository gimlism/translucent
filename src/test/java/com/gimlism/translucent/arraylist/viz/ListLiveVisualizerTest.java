package com.gimlism.translucent.arraylist.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.consumer.ListRecordingListener;
import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ListLiveVisualizerTest {

    @Test
    void emitsOneFramePerEventEqualToToFrame() {
        var frames = new ArrayList<String>();
        var rec = new ListRecordingListener();
        var list = new TeachingArrayList<String>(4);
        list.addListener(new ListLiveVisualizer(frames::add));
        list.addListener(rec);

        list.add("a");
        list.add("b");
        list.add(0, "c"); // insert at 0: a Shift burst then an Insert -> several events

        assertTrue(frames.size() >= 3, "at least one frame per mutation event");
        assertEquals(rec.events().size(), frames.size(), "exactly one frame per event");
        for (int i = 0; i < frames.size(); i++) {
            assertEquals(ListJsonSerializer.toFrame(rec.events().get(i)), frames.get(i),
                    "frame " + i + " equals the serializer's toFrame of that event");
        }
    }
}
