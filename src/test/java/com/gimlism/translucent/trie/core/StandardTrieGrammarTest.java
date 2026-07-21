package com.gimlism.translucent.trie.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.events.MergeEdge;
import com.gimlism.translucent.trie.events.SplitEdge;
import com.gimlism.translucent.trie.events.TrieEvent;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class StandardTrieGrammarTest {
    private static final String[] KEYS = {
        "she", "shell", "shore", "short", "shrew", "s", "sh",
        "romane", "romanus", "rom", "a", "ab", "abc", ""
    };

    @Test
    void adversarialInsertThenDeleteKeepsInvariants() {
        int[] order = {6, 0, 13, 8, 2, 10, 4, 1, 11, 9, 3, 12, 5, 7};
        var t = new StandardTrie<Integer>();
        var present = new HashSet<String>();
        for (int i = 0; i < KEYS.length; i++) {
            t.put(KEYS[i], i);
            present.add(KEYS[i]);
            StandardTrieInvariants.assertValid(t.snapshot(), present);
        }
        for (int i = 0; i < KEYS.length; i++) assertEquals(i, t.get(KEYS[i]));
        for (int idx : order) {
            assertEquals(idx, t.remove(KEYS[idx]));
            present.remove(KEYS[idx]);
            StandardTrieInvariants.assertValid(t.snapshot(), present);
        }
        assertTrue(t.isEmpty());
    }

    @Test
    void neverEmitsSplitOrMergeEvents() {
        // The uncompressed trie has no edge compression, so these radix-only events must never fire,
        // across a full adversarial insert+delete workload.
        var t = new StandardTrie<Integer>();
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        for (int i = 0; i < KEYS.length; i++) t.put(KEYS[i], i);
        for (String k : KEYS) t.remove(k);
        for (TrieEvent e : rec.events()) {
            assertTrue(!(e instanceof SplitEdge) && !(e instanceof MergeEdge),
                "unexpected compression event: " + e.getClass().getSimpleName());
        }
        assertTrue(t.isEmpty());
    }
}
