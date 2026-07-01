package com.gimlism.translucent.hashmap.events;

import java.util.List;

/** Immutable whole-map state at a point in time. */
public record MapSnapshot(int capacity, int size, int threshold,
                          List<BucketSnapshot> buckets) {
    public MapSnapshot {
        buckets = List.copyOf(buckets);
    }
}
