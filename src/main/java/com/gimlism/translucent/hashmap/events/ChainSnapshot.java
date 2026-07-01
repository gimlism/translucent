package com.gimlism.translucent.hashmap.events;

import java.util.List;

/** A table slot holding a separate-chaining list, head first. */
public record ChainSnapshot(List<EntrySnapshot> entries) implements BucketSnapshot {
    public ChainSnapshot {
        entries = List.copyOf(entries);
    }
}
