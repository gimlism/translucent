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
        // the pruned node's own path is longer than the highlighted (surviving) parent
        assertTrue(prune.path().length() > parent.length(), "expected parent shorter than pruned path");
        assertTrue(prune.path().startsWith(parent), "expected parent to be a prefix of pruned path");
        // and the serialized frame for that prune highlights the parent, not the deleted node
        String json = TrieJsonSerializer.toJson(List.of(prune));
        assertTrue(json.contains("\"highlightPath\":\"" + parent + "\""), json);
        assertFalse(json.contains("\"highlightPath\":\"" + prune.path() + "\""), json);
    }
}
