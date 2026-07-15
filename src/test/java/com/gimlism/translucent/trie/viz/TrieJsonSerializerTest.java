package com.gimlism.translucent.trie.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.events.Prune;
import com.gimlism.translucent.trie.events.TrieEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrieJsonSerializerTest {

    /** Record the demo story: 3 inserts (split/branch) then 2 removes (prune/merge). */
    private static List<TrieEvent> story() {
        var trie = new RadixTrie<Integer>();
        var rec = new TrieRecordingListener();
        trie.addListener(rec);
        int v = 1;
        for (String key : new String[] {"shore", "she", "shell"}) trie.put(key, v++);
        for (String key : new String[] {"shell", "she"}) trie.remove(key);
        return rec.events();
    }

    @Test
    void emptyStreamProducesEmptyFramesArray() {
        assertEquals("{\"frames\":[]}", TrieJsonSerializer.toJson(List.of()));
    }

    @Test
    void frameCarriesEventFieldsAndRecursiveTrie() {
        var trie = new RadixTrie<Integer>();
        var rec = new TrieRecordingListener();
        trie.addListener(rec);
        trie.put("shore", 1);

        String json = TrieJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"frames\":["), json);
        // event object: type + label + highlightPath
        assertTrue(json.contains("\"type\":\"Put\""), json);
        assertTrue(json.contains("\"highlightPath\":\"shore\""), json);
        assertTrue(json.contains("\"label\":"), json);
        // recursive trie: size, an edge labelled with the (compressed) key, a key node value
        assertTrue(json.contains("\"trie\":{\"size\":1"), json);
        assertTrue(json.contains("\"label\":\"shore\""), json);
        assertTrue(json.contains("\"key\":true"), json);
        assertTrue(json.contains("\"value\":\"1\""), json);
        // no template token could ever appear; and children arrays are present
        assertTrue(json.contains("\"children\":["), json);
    }

    @Test
    void branchAndRootNodesSerializeKeyFalseValueNull() {
        var trie = new RadixTrie<Integer>();
        var rec = new TrieRecordingListener();
        trie.addListener(rec);
        trie.put("shore", 1);
        String json = TrieJsonSerializer.toJson(rec.events());
        // the root is not a key node
        assertTrue(json.contains("\"key\":false,\"value\":null"), json);
    }

    @Test
    void nullElementValueSerializesAsJsonNull() {
        var trie = new RadixTrie<Integer>();
        var rec = new TrieRecordingListener();
        trie.addListener(rec);
        trie.put("a", null);
        String json = TrieJsonSerializer.toJson(rec.events());
        // the "a" key node ends the word but carries a null value
        assertTrue(json.contains("\"key\":true,\"value\":null"), json);
    }

    @Test
    void pruneHighlightPathIsSurvivingParentNotDeletedNode() {
        List<TrieEvent> events = story();
        Prune prune = events.stream()
                .filter(Prune.class::isInstance).map(Prune.class::cast)
                .findFirst().orElseThrow();
        String parent = AsciiTrieRenderer.affectedPath(prune);
        // the story removes "shell" first; that leaf is the pruned node, and the node that lost the
        // child (and survives in after()) is "she" — so the highlight must be "she", not "shell"
        assertEquals("shell", prune.path(), "first prune removes the 'shell' leaf");
        assertEquals("she", parent, "highlight the surviving parent, not the deleted node");
        // and the serialized frame for that prune highlights the parent, not the deleted node
        String json = TrieJsonSerializer.toJson(List.of(prune));
        assertTrue(json.contains("\"highlightPath\":\"she\""), json);
        assertFalse(json.contains("\"highlightPath\":\"shell\""), json);
    }

    @Test
    void toFrameProducesASingleFrameWithNoFramesWrapper() {
        var trie = new RadixTrie<Integer>();
        var rec = new TrieRecordingListener();
        trie.addListener(rec);
        trie.put("shore", 1);

        // toFrame of the last event equals that event's frame inside the full toJson blob
        var events = rec.events();
        String frame = TrieJsonSerializer.toFrame(events.get(events.size() - 1));

        assertFalse(frame.contains("\"frames\""), "single frame carries no frames wrapper");
        assertTrue(frame.startsWith("{\"event\":{"), frame);
        assertTrue(frame.contains("\"type\":\"Put\""), frame);
        assertTrue(frame.contains("\"trie\":{\"size\":1"), frame);
        assertTrue(TrieJsonSerializer.toJson(events).contains(frame),
                "the single frame is a substring of the full frames blob");
    }
}
