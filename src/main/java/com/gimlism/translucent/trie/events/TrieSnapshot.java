package com.gimlism.translucent.trie.events;

import com.gimlism.translucent.substrate.events.StructureSnapshot;
import java.util.Objects;

/** Immutable whole-trie state at a point in time. */
public record TrieSnapshot(TrieNodeSnapshot root, int size) implements StructureSnapshot {
    public TrieSnapshot {
        Objects.requireNonNull(root, "root");
        if (size < 0) {
            throw new IllegalArgumentException("size (" + size + ") must be >= 0");
        }
    }
}
