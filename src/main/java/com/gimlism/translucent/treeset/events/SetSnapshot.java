package com.gimlism.translucent.treeset.events;

import com.gimlism.translucent.substrate.events.StructureSnapshot;

/** Immutable whole-set state: the tree {@code root} (null when empty) and element count. */
public record SetSnapshot(SetNodeSnapshot root, int size) implements StructureSnapshot {
    public SetSnapshot {
        if (size < 0) {
            throw new IllegalArgumentException("size (" + size + ") must be >= 0");
        }
    }
}
