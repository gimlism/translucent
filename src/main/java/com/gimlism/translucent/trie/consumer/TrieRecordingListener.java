package com.gimlism.translucent.trie.consumer;

import com.gimlism.translucent.trie.events.TrieEvent;

/**
 * Records {@link TrieEvent}s — the trie-typed
 * {@link com.gimlism.translucent.substrate.events.RecordingListener}. Kept as a named
 * subclass so existing {@code new TrieRecordingListener()} call sites read naturally.
 */
public class TrieRecordingListener extends com.gimlism.translucent.substrate.events.RecordingListener<TrieEvent> {}
