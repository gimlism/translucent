package com.gimlism.translucent.trie.events;

/**
 * An edge labelled {@code originalLabel} was split at {@code commonPrefix}, inserting an
 * intermediate node at {@code path}. Radix-specific.
 */
public record SplitEdge(String originalLabel, String commonPrefix, String path, TrieSnapshot after)
        implements TrieEvent {}
