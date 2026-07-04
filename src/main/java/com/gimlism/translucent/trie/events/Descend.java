package com.gimlism.translucent.trie.events;

/** The walk followed an existing edge (label) fully, reaching {@code path} (shared prefix). */
public record Descend(String label, String path, TrieSnapshot after) implements TrieEvent {}
