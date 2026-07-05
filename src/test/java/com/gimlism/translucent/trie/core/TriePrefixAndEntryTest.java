package com.gimlism.translucent.trie.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.events.Put;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Residual trie edge cases the review flagged: empty/"" prefixes, entry setValue, putAll. */
class TriePrefixAndEntryTest {

    @Test
    void keysWithPrefixOnEmptyTrieIsEmpty() {
        var t = new RadixTrie<Integer>();
        assertEquals(List.of(), t.keysWithPrefix("x"));
        assertEquals(List.of(), t.keysWithPrefix(""));
    }

    @Test
    void keysWithPrefixEmptyStringIncludesTheEmptyKey() {
        var t = new RadixTrie<Integer>();
        t.put("", 0);
        t.put("a", 1);
        t.put("ab", 2);
        assertEquals(List.of("", "a", "ab"), t.keysWithPrefix("")); // the "" key is itself a match
    }

    @Test
    void entrySetEntriesRejectSetValue() {
        var t = new RadixTrie<Integer>();
        t.put("a", 1);
        Map.Entry<String, Integer> e = t.entrySet().iterator().next();
        // immutable snapshot entries: fail fast rather than silently update a throwaway
        assertThrows(UnsupportedOperationException.class, () -> e.setValue(2));
        assertEquals(1, t.get("a")); // unchanged
    }

    @Test
    void putAllEmitsOnePutPerEntry() {
        var t = new RadixTrie<Integer>();
        var rec = new TrieRecordingListener();
        t.addListener(rec);

        var source = new LinkedHashMap<String, Integer>();
        source.put("she", 1);
        source.put("shell", 2);
        source.put("shore", 3);
        t.putAll(source);

        assertEquals(3, t.size());
        long puts = rec.events().stream().filter(ev -> ev instanceof Put && ((Put) ev).newKey()).count();
        assertEquals(3, puts);
        assertEquals(List.of("she", "shell", "shore"), t.keysWithPrefix("sh"));
    }
}
