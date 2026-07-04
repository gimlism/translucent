package com.gimlism.translucent.trie.core;

import java.util.TreeMap;

/** A radix-trie node: a (compressed) incoming edge label, an optional value, and children by first char. */
class TrieNode<V> {
    String edgeLabel;                 // label on the edge from the parent ("" for the root)
    boolean isKey;
    V value;                          // meaningful iff isKey
    final TreeMap<Character, TrieNode<V>> children = new TreeMap<>();

    TrieNode(String edgeLabel) {
        this.edgeLabel = edgeLabel;
    }
}
