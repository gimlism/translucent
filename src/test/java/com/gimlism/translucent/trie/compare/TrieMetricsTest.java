package com.gimlism.translucent.trie.compare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void isAbsorbedOnlyForNonRootNonKeySingleChild() {
        var leaf = new TrieNodeSnapshot(false, null, List.of());
        var singleChild = new TrieNodeSnapshot(false, null, List.of(new TrieEdge("a", leaf)));
        var twoChild = new TrieNodeSnapshot(false, null,
            List.of(new TrieEdge("a", leaf), new TrieEdge("b", leaf)));
        var keySingleChild = new TrieNodeSnapshot(true, 1, List.of(new TrieEdge("a", leaf)));

        assertFalse(TrieMetrics.isAbsorbed(singleChild, true), "root is never absorbed");
        assertTrue(TrieMetrics.isAbsorbed(singleChild, false), "non-root single-child non-key is absorbed");
        assertFalse(TrieMetrics.isAbsorbed(twoChild, false), "a 2-child branch is kept");
        assertFalse(TrieMetrics.isAbsorbed(keySingleChild, false), "a key node is kept");
        assertFalse(TrieMetrics.isAbsorbed(leaf, false), "a leaf (no children) is kept");
    }

    @Test
    void savedPctRoundsShareOfStandardNodes() {
        assertEquals(40L, CompressionCompareDemo.compare(List.of("she","shell","shore","shy")).savedPct());
        assertEquals(0L, CompressionCompareDemo.compare(List.of()).savedPct());
    }
}
