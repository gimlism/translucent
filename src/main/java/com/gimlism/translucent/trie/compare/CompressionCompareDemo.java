package com.gimlism.translucent.trie.compare;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.core.StandardTrie;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;
import java.util.List;

/**
 * Quantifies the radix trie's edge compression: inserts the same keys into a
 * {@link StandardTrie} (one node per character) and a {@link RadixTrie} (single-child chains
 * collapsed), then reports each one's node count. The difference is exactly the nodes the
 * compression saves.
 */
public final class CompressionCompareDemo {
    private CompressionCompareDemo() {}

    /** Result of comparing the two tries on one key set. */
    public record Comparison(int standardNodes, int radixNodes, List<String> keys,
                             TrieSnapshot standardSnapshot, TrieSnapshot radixSnapshot) {
        public Comparison {
            keys = List.copyOf(keys);
        }

        /** Nodes the radix compression saves over the standard trie. */
        public int saved() {
            return standardNodes - radixNodes;
        }
    }

    /** Insert {@code keys} (values {@code 0..n-1}) into both tries and count their nodes. */
    public static Comparison compare(List<String> keys) {
        if (keys.isEmpty()) {
            // No keys inserted -> both tries hold only the root (no events to read a snapshot from).
            // Count it through the same root-included metric so this stays consistent with the
            // non-empty path rather than hardcoding the root's contribution.
            var rootOnly = new TrieSnapshot(new TrieNodeSnapshot(false, null, List.of()), 0);
            int rootCount = TrieMetrics.nodeCount(rootOnly);
            return new Comparison(rootCount, rootCount, keys, rootOnly, rootOnly);
        }
        var standard = new StandardTrie<Integer>();
        var radix = new RadixTrie<Integer>();
        var standardRec = new TrieRecordingListener();
        var radixRec = new TrieRecordingListener();
        standard.addListener(standardRec);
        radix.addListener(radixRec);
        for (int i = 0; i < keys.size(); i++) {
            standard.put(keys.get(i), i);
            radix.put(keys.get(i), i);
        }
        var standardSnap = lastSnapshot(standardRec);
        var radixSnap = lastSnapshot(radixRec);
        return new Comparison(
            TrieMetrics.nodeCount(standardSnap),
            TrieMetrics.nodeCount(radixSnap),
            keys, standardSnap, radixSnap);
    }

    /** The whole-trie snapshot carried by the most recent event (the final Put's {@code after()}). */
    private static TrieSnapshot lastSnapshot(TrieRecordingListener rec) {
        var events = rec.events();
        return events.get(events.size() - 1).after();
    }

    public static void main(String[] args) {
        System.out.println(new CompressionCompareRenderer().render(
            compare(List.of("she", "shell", "shore", "shy"))));
    }
}
