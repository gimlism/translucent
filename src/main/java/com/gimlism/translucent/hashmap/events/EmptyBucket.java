package com.gimlism.translucent.hashmap.events;

/** A table slot with no entries. */
public record EmptyBucket() implements BucketSnapshot {
    /** Shared instance — the record is a stateless value, so snapshots reuse one rather than reallocating. */
    public static final EmptyBucket INSTANCE = new EmptyBucket();
}
