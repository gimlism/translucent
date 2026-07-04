package com.gimlism.translucent.trie.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.events.CreateNode;
import com.gimlism.translucent.trie.events.Descend;
import com.gimlism.translucent.trie.events.Put;
import com.gimlism.translucent.trie.events.SplitEdge;
import com.gimlism.translucent.trie.events.TrieEvent;
import java.util.List;
import java.util.ConcurrentModificationException;
import org.junit.jupiter.api.Test;

class RadixTrieInsertTest {
    private static String tag(TrieEvent e) {
        return switch (e) {
            case Descend d -> "DESC:" + d.label();
            case CreateNode c -> "CREATE:" + c.label();
            case SplitEdge s -> "SPLIT:" + s.originalLabel() + "@" + s.commonPrefix();
            case Put p -> "PUT:" + p.key();
            default -> e.getClass().getSimpleName();
        };
    }

    @Test
    void putGetRoundTripIncludingNullValueAndEmptyKey() {
        var t = new RadixTrie<Integer>();
        assertNull(t.put("she", 1));
        assertEquals(1, t.put("she", 2));      // replace returns old
        assertEquals(2, t.get("she"));
        t.put("", 0);                          // empty key -> root is a key
        assertEquals(0, t.get(""));
        t.put("shore", null);                  // null value permitted
        assertTrue(t.containsKey("shore"));
        assertNull(t.get("shore"));
        assertEquals(3, t.size());
        assertThrows(NullPointerException.class, () -> t.put(null, 1));
    }

    @Test
    void createLeafThenDescendSharesPrefix() {
        var t = new RadixTrie<Integer>();
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        t.put("she", 1);   // CREATE "she" -> PUT
        rec.clear();
        t.put("shell", 2); // DESC "she" -> CREATE "ll" -> PUT
        assertEquals(List.of("DESC:she", "CREATE:ll", "PUT:shell"),
            rec.events().stream().map(RadixTrieInsertTest::tag).toList());
    }

    @Test
    void splitWhenKeyEndsInsideEdge() {
        var t = new RadixTrie<Integer>();
        t.put("shore", 1);
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        t.put("sh", 2); // SPLIT "shore"@"sh" -> PUT (key ends at split node)
        assertEquals(List.of("SPLIT:shore@sh", "PUT:sh"),
            rec.events().stream().map(RadixTrieInsertTest::tag).toList());
        assertEquals(1, t.get("shore"));
        assertEquals(2, t.get("sh"));
    }

    @Test
    void splitWhenKeysDivergeMidEdge() {
        var t = new RadixTrie<Integer>();
        t.put("shore", 1);
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        t.put("shell", 2); // SPLIT "shore"@"sh" -> CREATE "ell" -> PUT
        assertEquals(List.of("SPLIT:shore@sh", "CREATE:ell", "PUT:shell"),
            rec.events().stream().map(RadixTrieInsertTest::tag).toList());
        assertEquals(1, t.get("shore"));
        assertEquals(2, t.get("shell"));
    }

    @Test
    void adversarialInsertKeepsRadixInvariants() {
        String[] keys = {"she", "shell", "shore", "short", "shrew", "s", "sh", "romane", "romanus", "rom", "a", "ab", "abc", ""};
        var t = new RadixTrie<Integer>();
        var present = new java.util.HashSet<String>();
        for (int i = 0; i < keys.length; i++) {
            t.put(keys[i], i);
            present.add(keys[i]);
            RadixTrieInvariants.assertValid(t.snapshot(), present);
        }
        for (int i = 0; i < keys.length; i++) assertEquals(i, t.get(keys[i]));
    }

    @Test
    void reentrantMutationFromListenerRejected() {
        var t = new RadixTrie<Integer>();
        t.addListener(e -> t.put("x", 0));
        assertThrows(ConcurrentModificationException.class, () -> t.put("a", 1));
    }
}
