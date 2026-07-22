package com.gimlism.translucent.trie.compare;

import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;

/** Consumer-side node-count metric over a whole-trie {@link TrieSnapshot} (root included). */
public final class TrieMetrics {
    private TrieMetrics() {}

    /** Total number of nodes in the trie the snapshot describes, counting the root. */
    public static int nodeCount(TrieSnapshot snapshot) {
        return count(snapshot.root());
    }

    private static int count(TrieNodeSnapshot n) {
        int total = 1;
        for (TrieEdge e : n.children()) total += count(e.target());
        return total;
    }
}
