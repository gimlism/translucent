package com.gimlism.translucent.trie.events;

/**
 * A key was removed (its node unmarked); leads the remove burst. {@code path} is the location of
 * the affected node (equal to {@code key}), for a uniform locus across all trie events.
 */
public record Remove(String key, Object removedValue, String path, TrieSnapshot after) implements TrieEvent {}
