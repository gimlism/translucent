package com.gimlism.translucent.trie.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.events.CreateNode;
import com.gimlism.translucent.trie.events.Descend;
import com.gimlism.translucent.trie.events.Prune;
import com.gimlism.translucent.trie.events.Put;
import com.gimlism.translucent.trie.events.Remove;
import com.gimlism.translucent.trie.events.TrieEvent;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StandardTrieRemoveTest {
    private static String tag(TrieEvent e) {
        return switch (e) {
            case Descend d -> "DESC:" + d.label();
            case CreateNode c -> "CREATE:" + c.label();
            case Put p -> "PUT:" + p.key();
            case Remove r -> "REMOVE:" + r.key();
            case Prune pr -> "PRUNE:" + pr.label();
            default -> e.getClass().getSimpleName();
        };
    }

    private static StandardTrie<Integer> of(String... keys) {
        var t = new StandardTrie<Integer>();
        for (int i = 0; i < keys.length; i++) t.put(keys[i], i);
        return t;
    }

    @Test
    void removeAbsentReturnsNullNoEvents() {
        var t = of("she", "shore");
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        assertNull(t.remove("xyz"));   // path breaks
        assertNull(t.remove("sh"));    // node exists but isn't a key
        assertNull(t.remove(42));      // non-String
        assertNull(t.remove(null));    // null
        assertEquals(0, rec.events().size());
        assertEquals(2, t.size());
    }

    @Test
    void removeLeafPrunesCascadingChainUpToAKeyNode() {
        // she, shell share s-h-e; e is a key (she), then l-l (shell).
        // remove shell: prune the two l nodes; stop at e (a key).
        var t = of("she", "shell");
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        assertEquals(1, t.remove("shell"));
        assertEquals(List.of("DESC:s", "DESC:h", "DESC:e", "DESC:l", "DESC:l",
                "REMOVE:shell", "PRUNE:l", "PRUNE:l"),
            rec.events().stream().map(StandardTrieRemoveTest::tag).toList());
        assertEquals(0, t.get("she"));
        assertFalse(t.containsKey("shell"));
        assertEquals(1, t.size());
    }

    @Test
    void removeLeafCascadesPastNonKeyNodesToABranch() {
        // shell, shore branch at h (non-key, children e/o). remove shell:
        // prune l,l,e; stop at h (still has child o) -> shore survives, no merge event.
        var t = of("shell", "shore");
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        assertEquals(0, t.remove("shell"));
        assertEquals(List.of("DESC:s", "DESC:h", "DESC:e", "DESC:l", "DESC:l",
                "REMOVE:shell", "PRUNE:l", "PRUNE:l", "PRUNE:e"),
            rec.events().stream().map(StandardTrieRemoveTest::tag).toList());
        assertEquals(1, t.get("shore"));
        assertEquals(1, t.size());
    }

    @Test
    void removeBranchKeyUnmarksWithoutPruning() {
        // sh is a key AND a branch (children e, o). remove sh: unmark only, no prune.
        var t = of("she", "shore", "sh");
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        assertEquals(2, t.remove("sh"));
        assertEquals(List.of("DESC:s", "DESC:h", "REMOVE:sh"),
            rec.events().stream().map(StandardTrieRemoveTest::tag).toList());
        assertFalse(t.containsKey("sh"));
        assertEquals(0, t.get("she"));
        assertEquals(1, t.get("shore"));
    }

    @Test
    void removeEmptyKeyKeepsRoot() {
        var t = of("", "a");
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        assertEquals(0, t.remove(""));
        assertEquals(List.of("REMOVE:"), rec.events().stream().map(StandardTrieRemoveTest::tag).toList());
        assertFalse(t.containsKey(""));
        assertEquals(1, t.get("a"));
        assertEquals(1, t.size());
    }

    @Test
    void removeEventCarriesPathEqualToKey() {
        var t = of("she", "shore");
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        t.remove("she");
        Remove r = rec.events().stream().filter(e -> e instanceof Remove)
            .map(e -> (Remove) e).findFirst().orElseThrow();
        assertEquals("she", r.key());
        assertEquals("she", r.path());
    }

    @Test
    void iteratorRemoveDeletesAndContinues() {
        var t = of("a", "ab", "b", "c");
        Iterator<Map.Entry<String, Integer>> it = t.entrySet().iterator();
        it.next();     // "a"
        it.remove();   // deletes "a"; modCount resynced
        var rest = new ArrayList<String>();
        while (it.hasNext()) rest.add(it.next().getKey());
        assertEquals(List.of("ab", "b", "c"), rest);
        assertFalse(t.containsKey("a"));
        assertEquals(3, t.size());
    }

    @Test
    void clearEmptiesTheTrie() {
        var t = of("a", "ab", "abc", "b", "");
        t.clear();
        assertTrue(t.isEmpty());
        assertEquals(0, t.size());
        assertFalse(t.containsKey("abc"));
        assertNull(t.get(""));
    }

    @Test
    void reentrantRemoveFromListenerRejected() {
        var t = of("a", "b");
        t.addListener(e -> t.remove("a"));
        assertThrows(ConcurrentModificationException.class, () -> t.remove("b"));
    }
}
