package com.gimlism.translucent.trie.events;

/** A key was inserted ({@code newKey}) or its value replaced; the terminal event of an insert. */
public record Put(String key, Object value, Object previousValue, boolean newKey, TrieSnapshot after)
        implements TrieEvent {}
