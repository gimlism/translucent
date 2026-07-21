package com.gimlism.translucent.trie.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.events.CreateNode;
import com.gimlism.translucent.trie.events.Descend;
import com.gimlism.translucent.trie.events.Put;
import com.gimlism.translucent.trie.events.TrieEvent;
import java.util.ConcurrentModificationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class StandardTrieInsertTest {
    private static String tag(TrieEvent e) {
        return switch (e) {
            case Descend d -> "DESC:" + d.label();
            case CreateNode c -> "CREATE:" + c.label();
            case Put p -> "PUT:" + p.key();
            default -> e.getClass().getSimpleName();
        };
    }

    @Test
    void putGetRoundTripIncludingNullValueAndEmptyKey() {
        var t = new StandardTrie<Integer>();
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
    void insertGrowsOneNodePerCharThenDescendsSharedChars() {
        var t = new StandardTrie<Integer>();
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        t.put("she", 1);   // CREATE s,h,e -> PUT
        assertEquals(List.of("CREATE:s", "CREATE:h", "CREATE:e", "PUT:she"),
            rec.events().stream().map(StandardTrieInsertTest::tag).toList());
        rec.clear();
        t.put("shell", 2); // DESC s,h,e -> CREATE l,l -> PUT
        assertEquals(List.of("DESC:s", "DESC:h", "DESC:e", "CREATE:l", "CREATE:l", "PUT:shell"),
            rec.events().stream().map(StandardTrieInsertTest::tag).toList());
        assertEquals(1, t.get("she"));
        assertEquals(2, t.get("shell"));
    }

    @Test
    void emptyKeyPutIsSilentWalkJustPut() {
        var t = new StandardTrie<Integer>();
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        t.put("", 7);
        assertEquals(List.of("PUT:"), rec.events().stream().map(StandardTrieInsertTest::tag).toList());
    }

    @Test
    void putEventCarriesPathEqualToKey() {
        var t = new StandardTrie<Integer>();
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        t.put("shore", 1);
        Put p = (Put) rec.events().get(rec.events().size() - 1);
        assertEquals("shore", p.key());
        assertEquals("shore", p.path());
    }

    @Test
    void keysWithPrefixReturnsLexicographicSnapshot() {
        var t = new StandardTrie<Integer>();
        t.put("she", 1); t.put("shell", 2); t.put("shore", 3); t.put("shy", 4); t.put("other", 5);
        assertEquals(List.of("she", "shell", "shore", "shy"), t.keysWithPrefix("sh"));
        assertEquals(List.of(), t.keysWithPrefix("zzz"));
        assertThrows(NullPointerException.class, () -> t.keysWithPrefix(null));
    }

    @Test
    void nonStringAndNullArgumentsAreTolerated() {
        var t = new StandardTrie<Integer>();
        t.put("she", 1);
        assertNull(t.get(42));
        assertNull(t.get(null));
        assertFalse(t.containsKey(42));
        assertFalse(t.containsKey(null));
        assertEquals(1, t.size());
    }

    @Test
    void valueReplaceIsNonStructuralAndDoesNotInvalidateIterators() {
        var t = new StandardTrie<Integer>();
        t.put("a", 0);
        t.put("b", 1);
        var it = t.entrySet().iterator();
        it.next();
        t.put("a", 99);                 // value replace: non-structural, no modCount bump
        assertEquals(99, t.get("a"));
        assertEquals(2, t.size());
        assertDoesNotThrow(it::next);
    }

    @Test
    void reentrantMutationFromListenerRejected() {
        var t = new StandardTrie<Integer>();
        t.addListener(e -> t.put("x", 0));
        assertThrows(ConcurrentModificationException.class, () -> t.put("a", 1));
    }
}
