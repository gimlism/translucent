package com.gimlism.translucent.trie.events;

/** A leaf node (edge {@code label}) was removed from its parent at {@code path}. */
public record Prune(String label, String path, TrieSnapshot after) implements TrieEvent {}
