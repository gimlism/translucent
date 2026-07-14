package com.gimlism.translucent.trie.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.core.RadixTrie;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class TrieLiveVisualizerTest {

    @Test
    void emitsOneFramePerEventEqualToToFrame() {
        var frames = new ArrayList<String>();
        var rec = new TrieRecordingListener();
        var trie = new RadixTrie<Integer>();
        trie.addListener(new TrieLiveVisualizer(frames::add));
        trie.addListener(rec);

        trie.put("shore", 1);
        trie.put("she", 2);   // SplitEdge -> CreateNode -> Put: several events
        trie.remove("she");   // Remove (+ compression): several more events

        assertTrue(frames.size() >= 3, "at least one frame per mutation event");
        assertEquals(rec.events().size(), frames.size(), "exactly one frame per event");
        for (int i = 0; i < frames.size(); i++) {
            assertEquals(TrieJsonSerializer.toFrame(rec.events().get(i)), frames.get(i),
                    "frame " + i + " equals the serializer's toFrame of that event");
        }
    }
}
