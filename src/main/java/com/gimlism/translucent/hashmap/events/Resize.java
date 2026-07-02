package com.gimlism.translucent.hashmap.events;

/** The table doubled and all entries were rehashed. */
public record Resize(int oldCapacity, int newCapacity,
                     MapSnapshot before, MapSnapshot after)
        implements MapEvent {}
