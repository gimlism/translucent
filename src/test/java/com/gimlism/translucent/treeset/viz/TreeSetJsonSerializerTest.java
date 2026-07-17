package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.treeset.consumer.SetRecordingListener;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.events.Add;
import com.gimlism.translucent.treeset.events.Remove;
import com.gimlism.translucent.treeset.events.SetEvent;
import com.gimlism.translucent.treeset.events.SetNodeSnapshot;
import com.gimlism.translucent.treeset.events.SetSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeSetJsonSerializerTest {

    // A deterministic hand-built tree: 20(black) { left 10(red), right 30(red) }.
    private static SetSnapshot threeNodeTree() {
        var n10 = new SetNodeSnapshot(10, true, null, null);
        var n30 = new SetNodeSnapshot(30, true, null, null);
        return new SetSnapshot(new SetNodeSnapshot(20, false, n10, n30), 3);
    }

    @Test
    void emptyStreamProducesEmptyFramesArray() {
        assertEquals("{\"frames\":[]}", TreeSetJsonSerializer.toJson(List.of()));
    }

    @Test
    void frameCarriesEventFieldsAndRecursiveSet() {
        var set = new TeachingTreeSet<Integer>();
        var rec = new SetRecordingListener();
        set.addListener(rec);
        set.add(10);

        String json = TreeSetJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"frames\":["), json);
        assertTrue(json.contains("\"type\":\"Add\""), json);
        assertTrue(json.contains("\"label\":\"add 10\""), json);
        assertTrue(json.contains("\"set\":{\"size\":1"), json);
        assertTrue(json.contains("\"element\":\"10\""), json);
        assertTrue(json.contains("\"left\":null"), json);   // a leaf
        assertTrue(json.contains("\"right\":null"), json);
    }

    @Test
    void highlightMarksTheAffectedNodeInJavaNotOthers() {
        // Add(30) -> affectedElement is 30 -> only the "30" node is hl:true.
        String frame = TreeSetJsonSerializer.toFrame(new Add(30, threeNodeTree()));
        assertTrue(frame.contains("\"element\":\"30\",\"red\":true,\"hl\":true"), frame);
        assertTrue(frame.contains("\"element\":\"20\",\"red\":false,\"hl\":false"), frame);
        assertTrue(frame.contains("\"element\":\"10\",\"red\":true,\"hl\":false"), frame);
    }

    @Test
    void removeFrameHighlightsNothing() {
        // affectedElement(Remove) is null -> no node is hl:true.
        String frame = TreeSetJsonSerializer.toFrame(new Remove(10, threeNodeTree()));
        assertFalse(frame.contains("\"hl\":true"), frame);
        assertTrue(frame.contains("\"label\":\"remove 10\""), frame);
    }

    @Test
    void emptySetSerializesNullRoot() {
        String frame = TreeSetJsonSerializer.toFrame(new Remove(10, new SetSnapshot(null, 0)));
        assertTrue(frame.contains("\"set\":{\"size\":0,\"root\":null}"), frame);
    }

    @Test
    void toFrameProducesASingleFrameWithNoFramesWrapper() {
        var set = new TeachingTreeSet<Integer>();
        var rec = new SetRecordingListener();
        set.addListener(rec);
        set.add(7);
        List<SetEvent> events = rec.events();
        String frame = TreeSetJsonSerializer.toFrame(events.get(events.size() - 1));
        assertFalse(frame.contains("\"frames\""), "single frame carries no frames wrapper");
        assertTrue(frame.startsWith("{\"event\":{"), frame);
        assertTrue(TreeSetJsonSerializer.toJson(events).contains(frame),
                "the single frame is a substring of the full frames blob");
    }
}
