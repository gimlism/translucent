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

    /**
     * Whether the radix trie absorbs this node: a non-root, non-key node with exactly one child
     * (part of a single-child chain radix compresses into an edge label). The single shared
     * definition used by both {@code CompressionCompareRenderer} (ASCII) and
     * {@code CompressionCompareJsonSerializer} (web) so they can never disagree on what is marked.
     */
    public static boolean isAbsorbed(TrieNodeSnapshot node, boolean root) {
        return !root && !node.key() && node.children().size() == 1;
    }
}
