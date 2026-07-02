package com.gimlism.translucent.hashmap.events;

import java.util.List;

/** Immutable whole-map state at a point in time. */
public record MapSnapshot(int capacity, int size, int threshold,
                          List<BucketSnapshot> buckets) {
    public MapSnapshot {
        buckets = List.copyOf(buckets);
        if (buckets.size() != capacity) {
            throw new IllegalArgumentException(
                "buckets.size() (" + buckets.size() + ") must equal capacity (" + capacity + ")");
        }
    }
}
