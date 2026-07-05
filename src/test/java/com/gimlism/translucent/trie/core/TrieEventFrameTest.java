package com.gimlism.translucent.trie.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.events.CreateNode;
import com.gimlism.translucent.trie.events.MergeEdge;
import com.gimlism.translucent.trie.events.Prune;
import com.gimlism.translucent.trie.events.Put;
import com.gimlism.translucent.trie.events.SplitEdge;
import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieEvent;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The review's highest-value gap: the trie's event stream (the whole point of the project) was
 * only tag-checked; the {@code after()} snapshot CONTENTS and the {@code path} fields of the
 * structural events were never asserted, so a wrong-moment snapshot or an off-by-one path
 * would stay green. These pin the mid-operation frames.
 */
class TrieEventFrameTest {

    /** All edge labels in the snapshot tree, pre-order. */
    private static List<String> edgeLabels(TrieNodeSnapshot n) {
        var out = new ArrayList<String>();
        for (TrieEdge e : n.children()) {
            out.add(e.label());
            out.addAll(edgeLabels(e.target()));
        }
        return out;
    }

    private static <T extends TrieEvent> T firstOf(TrieRecordingListener rec, Class<T> type) {
        return rec.events().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    @Test
    void splitEdgeFrameShowsThePostSplitStructureAndPath() {
        var t = new RadixTrie<Integer>();
        t.put("shore", 1);
        var rec = new TrieRecordingListener();
        t.addListener(rec);

        t.put("shell", 2); // SPLIT "shore" @ "sh" -> CREATE "ell" -> PUT

        SplitEdge se = firstOf(rec, SplitEdge.class);
        assertEquals("shore", se.originalLabel());
        assertEquals("sh", se.commonPrefix());
        assertEquals("sh", se.path());
        // the frame already reflects the split: the "shore" edge is gone, replaced by "sh"->"ore"
        List<String> labels = edgeLabels(se.after().root());
        assertTrue(labels.contains("sh"), labels.toString());
        assertTrue(labels.contains("ore"), labels.toString());
        assertFalse(labels.contains("shore"), labels.toString());

        // the trailing CreateNode frame has added the divergent leaf, and its path is the full key
        CreateNode cn = firstOf(rec, CreateNode.class);
        assertEquals("ell", cn.label());
        assertEquals("shell", cn.path());
        assertTrue(edgeLabels(cn.after().root()).contains("ell"));

        // the terminal Put frame is the settled tree
        Put put = firstOf(rec, Put.class);
        assertEquals("shell", put.key());
        assertEquals(List.of("sh", "ell", "ore"), edgeLabels(put.after().root()));
    }

    @Test
    void pruneThenMergeFramesShowTheProgressiveCompression() {
        var t = new RadixTrie<Integer>();
        t.put("shell", 1);
        t.put("shore", 2); // root -"sh"-> { "ell"->shell, "ore"->shore }
        var rec = new TrieRecordingListener();
        t.addListener(rec);

        t.remove("shell"); // REMOVE -> PRUNE "ell" -> MERGE "shore"

        // Prune frame: the "ell" leaf is gone; "sh" now has its single surviving child "ore"
        Prune pr = firstOf(rec, Prune.class);
        assertEquals("ell", pr.label());
        assertEquals("shell", pr.path());
        List<String> afterPrune = edgeLabels(pr.after().root());
        assertEquals(List.of("sh", "ore"), afterPrune);

        // Merge frame: the single-child chain has compressed to one "shore" edge
        MergeEdge me = firstOf(rec, MergeEdge.class);
        assertEquals("shore", me.mergedLabel());
        assertEquals("shore", me.path());
        assertEquals(List.of("shore"), edgeLabels(me.after().root()));
    }
}
