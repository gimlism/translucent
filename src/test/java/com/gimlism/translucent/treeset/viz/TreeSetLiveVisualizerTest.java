package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.treeset.consumer.SetRecordingListener;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class TreeSetLiveVisualizerTest {

    @Test
    void emitsOneFramePerEventEqualToToFrame() {
        var frames = new ArrayList<String>();
        var rec = new SetRecordingListener();
        var set = new TeachingTreeSet<Integer>();
        set.addListener(new TreeSetLiveVisualizer(frames::add));
        set.addListener(rec);

        set.add(10);
        set.add(20);   // compare walk + add (+ any rebalance events)
        set.remove(10);

        assertTrue(frames.size() >= 3, "at least one frame per event");
        assertEquals(rec.events().size(), frames.size(), "exactly one frame per event");
        for (int i = 0; i < frames.size(); i++) {
            assertEquals(TreeSetJsonSerializer.toFrame(rec.events().get(i)), frames.get(i),
                    "frame " + i + " equals the serializer's toFrame of that event");
        }
    }

    @Test
    void readsNarrateLive_containsBroadcastsCompareFrames() {
        var set = new TeachingTreeSet<Integer>();
        set.add(10);
        set.add(20);
        set.add(30);

        var frames = new ArrayList<String>();
        set.addListener(new TreeSetLiveVisualizer(frames::add)); // attach AFTER the inserts

        set.contains(25); // a read: narrates the comparison walk, mutates nothing

        assertTrue(frames.size() >= 1, "a contains walk broadcasts Compare frames");
        assertTrue(frames.stream().allMatch(f -> f.contains("\"type\":\"Compare\"")),
                "every frame from a read is a Compare frame");
    }
}
