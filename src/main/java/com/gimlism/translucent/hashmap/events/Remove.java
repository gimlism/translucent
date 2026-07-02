package com.gimlism.translucent.hashmap.events;

/** A key was removed, carrying the value that had been mapped. */
public record Remove(Object key, Object removedValue, int bucketIndex, MapSnapshot after)
        implements MapEvent {}
