package com.gimlism.translucent.trie.events;

/** Two edges merged into one compressed edge ({@code mergedLabel}) at {@code path}. Radix-specific. */
public record MergeEdge(String mergedLabel, String path, TrieSnapshot after) implements TrieEvent {}
