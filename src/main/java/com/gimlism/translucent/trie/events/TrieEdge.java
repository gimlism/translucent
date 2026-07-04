package com.gimlism.translucent.trie.events;

/** A labelled edge to a child node; {@code label} is the (compressed) String on the edge. */
public record TrieEdge(String label, TrieNodeSnapshot target) {}
