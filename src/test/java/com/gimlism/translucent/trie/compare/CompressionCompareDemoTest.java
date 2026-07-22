package com.gimlism.translucent.trie.compare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.core.StandardTrie;
import com.gimlism.translucent.trie.events.TrieSnapshot;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class CompressionCompareDemoTest {
    private static final List<String> CANON = List.of("she", "shell", "shore", "shy");

    @Test
    void bothTriesHoldTheSameEntriesSoTheCompareIsFair() {
        var std = new StandardTrie<Integer>();
        var rad = new RadixTrie<Integer>();
        for (int i = 0; i < CANON.size(); i++) {
            std.put(CANON.get(i), i);
            rad.put(CANON.get(i), i);
        }
        // Same Map contract -> identical entry sets (ordering-independent map equality).
        assertEquals(new TreeMap<>(rad), new TreeMap<>(std));
    }

    @Test
    void standardTrieHasStrictlyMoreNodesOnAPrefixSharingSet() {
        var c = CompressionCompareDemo.compare(CANON);
        assertTrue(c.standardNodes() > c.radixNodes(),
            "expected compression: standard " + c.standardNodes() + " vs radix " + c.radixNodes());
        assertEquals(c.standardNodes() - c.radixNodes(), c.saved());
    }

    @Test
    void pinnedNodeCountsForTheCanonicalSet() {
        // she,shell,shore,shy:
        //   standard: root,s,h,e,l,l,o,r,e,y            = 10 nodes
        //   radix:    root,sh,e("she"),ll,ore,y         =  6 nodes
        var c = CompressionCompareDemo.compare(CANON);
        assertEquals(10, c.standardNodes());
        assertEquals(6, c.radixNodes());
    }

    @Test
    void emptyKeySetYieldsRootOnlyCountsWithNoException() {
        // No keys -> both tries are just the root; nodeCount is root-included, so 1 each, 0 saved.
        var c = CompressionCompareDemo.compare(List.of());
        assertEquals(1, c.standardNodes());
        assertEquals(1, c.radixNodes());
        assertEquals(0, c.saved());
    }

    @Test
    void comparisonCarriesEachTriesFinalSnapshot() {
        var c = CompressionCompareDemo.compare(CANON);
        TrieSnapshot std = c.standardSnapshot();
        TrieSnapshot rad = c.radixSnapshot();
        assertNotNull(std);
        assertNotNull(rad);
        // The carried snapshots are the very ones the counts were derived from.
        assertEquals(c.standardNodes(), TrieMetrics.nodeCount(std));
        assertEquals(c.radixNodes(), TrieMetrics.nodeCount(rad));
    }
}
