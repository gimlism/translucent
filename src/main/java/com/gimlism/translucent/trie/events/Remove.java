package com.gimlism.translucent.trie.events;

/** A key was removed (its node unmarked); leads the remove burst. */
public record Remove(String key, Object removedValue, TrieSnapshot after) implements TrieEvent {}
