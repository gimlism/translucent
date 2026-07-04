package com.gimlism.translucent.trie.events;

import java.util.List;

/**
 * Immutable snapshot of a trie node: whether a key ends here (with its {@code value}),
 * and its children as {@code String}-labelled edges (sorted by label first character).
 * The N-ary, string-labelled shape is what a binary tree-node snapshot cannot express.
 */
public record TrieNodeSnapshot(boolean key, Object value, List<TrieEdge> children) {
    public TrieNodeSnapshot {
        children = List.copyOf(children);
    }
}
