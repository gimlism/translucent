package com.gimlism.translucent.trie.events;

import com.gimlism.translucent.substrate.events.StructureEventListener;

/**
 * Consumer of the trie's event stream — a {@link StructureEventListener} named for the
 * trie's concrete {@link TrieEvent} vocabulary. A listener may read the trie but must not
 * mutate it from within {@link #onEvent} (rejected with a
 * {@link java.util.ConcurrentModificationException}).
 */
@FunctionalInterface
public interface TrieEventListener extends StructureEventListener<TrieEvent> {}
