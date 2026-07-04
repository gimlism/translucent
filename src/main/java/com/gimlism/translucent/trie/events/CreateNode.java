package com.gimlism.translucent.trie.events;

/** A new leaf edge (label) was created — a new branch — reaching {@code path}. */
public record CreateNode(String label, String path, TrieSnapshot after) implements TrieEvent {}
