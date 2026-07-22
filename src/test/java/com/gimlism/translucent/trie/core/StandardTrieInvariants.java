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

/** Adversarial standard-trie invariant checker for tests (one char per edge, no compression). */
final class StandardTrieInvariants {
    private StandardTrieInvariants() {}

    /** Assert one-char-per-edge invariants and that the key set equals {@code expected}. */
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
            assertEquals(1, e.label().length(), "edge label must be exactly one char at \"" + prefix + "\"");
            assertTrue(firstChars.add(e.label().charAt(0)), "duplicate child char at \"" + prefix + "\"");
        }
        if (!root) {
            assertFalse(kids.isEmpty() && !n.key(), "dead non-key leaf (should have been pruned) at \"" + prefix + "\"");
        }
        for (TrieEdge e : kids) walk(e.target(), prefix + e.label(), false, keys);
    }
}
