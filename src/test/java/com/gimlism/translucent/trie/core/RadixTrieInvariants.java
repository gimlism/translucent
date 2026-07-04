package com.gimlism.translucent.trie.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Adversarial radix-invariant checker for tests. */
final class RadixTrieInvariants {
    private RadixTrieInvariants() {}

    /** Assert radix invariants and that the key set equals {@code expected}; returns the key set. */
    static TreeSet<String> assertValid(TrieSnapshot snap, Set<String> expected) {
        TreeSet<String> keys = new TreeSet<>();
        walk(snap.root(), "", true, keys);
        assertEquals(new TreeSet<>(expected), keys, "key set");
        assertEquals(expected.size(), snap.size(), "size field");
        return keys;
    }

    private static void walk(TrieNodeSnapshot n, String prefix, boolean root, TreeSet<String> keys) {
        if (n.key()) keys.add(prefix);
        List<TrieEdge> kids = n.children();
        var firstChars = new TreeSet<Character>();
        for (TrieEdge e : kids) {
            assertFalse(e.label().isEmpty(), "empty edge label at \"" + prefix + "\"");
            assertTrue(firstChars.add(e.label().charAt(0)), "duplicate child first-char at \"" + prefix + "\"");
        }
        if (!root && !n.key()) {
            assertTrue(kids.size() >= 2, "non-key internal node must have >=2 children at \"" + prefix + "\"");
        }
        for (TrieEdge e : kids) walk(e.target(), prefix + e.label(), false, keys);
    }
}
