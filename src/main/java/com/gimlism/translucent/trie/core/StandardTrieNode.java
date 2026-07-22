package com.gimlism.translucent.trie.core;

import java.util.TreeMap;

/**
 * A standard-trie node: one character per edge, so a node carries no edge label of its own
 * (its incoming character is the key under which its parent holds it). Contrast with
 * {@code TrieNode}, whose {@code edgeLabel} compresses a whole single-child chain into one edge.
 */
class StandardTrieNode<V> {
    boolean isKey;
    V value;                          // meaningful iff isKey
    final TreeMap<Character, StandardTrieNode<V>> children = new TreeMap<>();
}
