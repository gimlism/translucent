package com.gimlism.translucent.hashmap.events;

/** A key/value was inserted ({@code newEntry}) or its value replaced. */
public record Put(Object key, Object value, Object previousValue,
                  int bucketIndex, boolean newEntry, MapSnapshot after)
        implements MapEvent {}
