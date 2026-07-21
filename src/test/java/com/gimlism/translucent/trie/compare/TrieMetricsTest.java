package com.gimlism.translucent.trie.compare;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrieMetricsTest {
    @Test
    void countsRootOnlyForEmptyTrie() {
        var snap = new TrieSnapshot(new TrieNodeSnapshot(false, null, List.of()), 0);
        assertEquals(1, TrieMetrics.nodeCount(snap));   // just the root
    }

    @Test
    void countsEveryNodeIncludingRoot() {
        // root -> a -> b (key), root -> c (key): 4 nodes total.
        var b = new TrieNodeSnapshot(true, 1, List.of());
        var a = new TrieNodeSnapshot(false, null, List.of(new TrieEdge("b", b)));
        var c = new TrieNodeSnapshot(true, 2, List.of());
        var root = new TrieNodeSnapshot(false, null, List.of(new TrieEdge("a", a), new TrieEdge("c", c)));
        assertEquals(4, TrieMetrics.nodeCount(new TrieSnapshot(root, 2)));
    }
}
