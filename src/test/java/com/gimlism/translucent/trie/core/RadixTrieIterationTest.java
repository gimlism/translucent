package com.gimlism.translucent.trie.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ConcurrentModificationException;
import org.junit.jupiter.api.Test;

class RadixTrieIterationTest {
    private static RadixTrie<Integer> of(String... keys) {
        var t = new RadixTrie<Integer>();
        for (int i = 0; i < keys.length; i++) t.put(keys[i], i);
        return t;
    }

    @Test
    void iteratesKeysInLexicographicOrder() {
        var t = of("shore", "she", "short", "a", "shell", "ab");
        assertEquals(List.of("a", "ab", "she", "shell", "shore", "short"), new ArrayList<>(t.keySet()));
        // entrySet mirrors it, values intact
        var seen = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : t.entrySet()) {
            seen.add(e.getKey());
            assertEquals(t.get(e.getKey()), e.getValue());
        }
        assertEquals(List.of("a", "ab", "she", "shell", "shore", "short"), seen);
    }

    @Test
    void keysWithPrefixReturnsSortedMatches() {
        var t = of("she", "shell", "shore", "short", "a", "ab");
        assertEquals(List.of("she", "shell", "shore", "short"), t.keysWithPrefix("sh"));
        assertEquals(List.of("she", "shell"), t.keysWithPrefix("she")); // "she" is itself a key + prefix
        assertEquals(List.of("a", "ab", "she", "shell", "shore", "short"), t.keysWithPrefix(""));
        assertEquals(List.of(), t.keysWithPrefix("zzz"));
        assertEquals(List.of(), t.keysWithPrefix("shx")); // diverges mid-edge
    }

    @Test
    void emptyStringKeyIsIterated() {
        var t = of("", "a");
        assertEquals(List.of("", "a"), new ArrayList<>(t.keySet()));
    }

    @Test
    void failFastOnStructuralChangeDuringIteration() {
        var t = of("a", "b", "c");
        Iterator<Map.Entry<String, Integer>> it = t.entrySet().iterator();
        it.next();
        t.put("d", 3); // structural change
        assertThrows(ConcurrentModificationException.class, it::next);
    }
}
