package com.gimlism.translucent.trie.events;

import com.gimlism.translucent.substrate.events.StructureSnapshot;

/** Immutable whole-trie state at a point in time. */
public record TrieSnapshot(TrieNodeSnapshot root, int size) implements StructureSnapshot {}
