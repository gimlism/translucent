package com.gimlism.translucent.trie.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.events.CreateNode;
import com.gimlism.translucent.trie.events.Descend;
import com.gimlism.translucent.trie.events.MergeEdge;
import com.gimlism.translucent.trie.events.Prune;
import com.gimlism.translucent.trie.events.Put;
import com.gimlism.translucent.trie.events.Remove;
import com.gimlism.translucent.trie.events.SplitEdge;
import com.gimlism.translucent.trie.events.TrieEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RadixTrieRemoveTest {
    private static String tag(TrieEvent e) {
        return switch (e) {
            case Descend d -> "DESC:" + d.label();
            case CreateNode c -> "CREATE:" + c.label();
            case SplitEdge s -> "SPLIT:" + s.originalLabel() + "@" + s.commonPrefix();
            case Put p -> "PUT:" + p.key();
            case Remove r -> "REMOVE:" + r.key();
            case MergeEdge m -> "MERGE:" + m.mergedLabel();
            case Prune pr -> "PRUNE:" + pr.label();
        };
    }

    private static RadixTrie<Integer> of(String... keys) {
        var t = new RadixTrie<Integer>();
        for (int i = 0; i < keys.length; i++) t.put(keys[i], i);
        return t;
    }

    @Test
    void removeAbsentReturnsNullNoEvents() {
        var t = of("she", "shore");
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        assertNull(t.remove("xyz"));   // path breaks
        assertNull(t.remove("sh"));    // node exists (split point) but isn't a key
        assertEquals(0, rec.events().size());
        assertEquals(2, t.size());
    }

    @Test
    void removeBranchKeyEmitsOnlyRemove() {
        // "sh" is a key AND an internal branch (>=2 children: she, shore)
        var t = of("she", "shore", "sh");
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        assertEquals(2, t.remove("sh"));
        assertEquals(List.of("REMOVE:sh"), rec.events().stream().map(RadixTrieRemoveTest::tag).toList());
        assertFalse(t.containsKey("sh"));
        assertEquals(0, t.get("she"));
        assertEquals(1, t.get("shore"));
    }

    @Test
    void removeLeafPrunesThenMergesParent() {
        // she, shell -> "she" node has one child "ll"; removing "shell" prunes the leaf,
        // leaving "she" (a key) with 0 children -> just pruned, no merge (she is a key).
        var t = of("she", "shell");
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        assertEquals(1, t.remove("shell"));
        assertEquals(List.of("REMOVE:shell", "PRUNE:ll"), rec.events().stream().map(RadixTrieRemoveTest::tag).toList());
        assertEquals(0, t.get("she"));
        assertEquals(1, t.size());
    }

    @Test
    void removeLeafMergesNonKeyParent() {
        // shell, shore -> "sh" is a non-key branch with children "ell","ore".
        // remove "shell": prune "ell", leaving "sh" non-key with one child "ore" -> merge to "shore".
        var t = of("shell", "shore");
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        assertEquals(0, t.remove("shell"));
        assertEquals(List.of("REMOVE:shell", "PRUNE:ell", "MERGE:shore"),
            rec.events().stream().map(RadixTrieRemoveTest::tag).toList());
        assertEquals(1, t.get("shore"));
        assertEquals(1, t.size());
    }

    @Test
    void removeKeyWithOneChildMerges() {
        // sh, shore -> "sh" is a key with one child "ore". remove "sh": unmark, then merge -> "shore".
        var t = of("sh", "shore");
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        assertEquals(0, t.remove("sh"));
        assertEquals(List.of("REMOVE:sh", "MERGE:shore"), rec.events().stream().map(RadixTrieRemoveTest::tag).toList());
        assertEquals(1, t.get("shore"));
        assertFalse(t.containsKey("sh"));
    }

    @Test
    void removeEmptyKeyKeepsRoot() {
        var t = of("", "a");
        assertEquals(0, t.remove(""));
        assertFalse(t.containsKey(""));
        assertEquals(1, t.get("a"));
        assertEquals(1, t.size());
    }

    @Test
    void iteratorRemoveDeletes() {
        var t = of("a", "ab", "b");
        Iterator<Map.Entry<String, Integer>> it = t.entrySet().iterator();
        Map.Entry<String, Integer> first = it.next(); // "a"
        it.remove();
        assertFalse(t.containsKey(first.getKey()));
        assertEquals(2, t.size());
        assertEquals(List.of("ab", "b"), new ArrayList<>(t.keySet()));
    }

    @Test
    void adversarialDeleteKeepsRadixInvariants() {
        String[] keys = {"she", "shell", "shore", "short", "shrew", "s", "sh", "romane", "romanus", "rom", "a", "ab", "abc", ""};
        int[] order = {6, 0, 13, 8, 2, 10, 4, 1, 11, 9, 3, 12, 5, 7};
        var t = new RadixTrie<Integer>();
        var present = new java.util.HashSet<String>();
        for (int i = 0; i < keys.length; i++) { t.put(keys[i], i); present.add(keys[i]); }
        RadixTrieInvariants.assertValid(t.snapshot(), present);
        for (int idx : order) {
            String k = keys[idx];
            assertEquals(idx, t.remove(k));
            present.remove(k);
            RadixTrieInvariants.assertValid(t.snapshot(), present);
        }
        assertTrue(t.isEmpty());
    }
}
