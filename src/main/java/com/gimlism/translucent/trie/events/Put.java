package com.gimlism.translucent.trie.events;

/**
 * A key was inserted ({@code newKey}) or its value replaced; the terminal event of an insert.
 * {@code path} is the location of the affected node (equal to {@code key} — the node where the
 * key ends) — carried so every trie event, mechanical or logical, exposes a uniform locus.
 */
public record Put(String key, Object value, Object previousValue, boolean newKey, String path, TrieSnapshot after)
        implements TrieEvent {}
