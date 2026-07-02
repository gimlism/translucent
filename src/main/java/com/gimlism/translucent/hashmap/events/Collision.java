package com.gimlism.translucent.hashmap.events;

/** A new entry landed in an already-occupied bucket (chain grew). */
public record Collision(Object key, int bucketIndex,
                        int chainLengthBefore, int chainLengthAfter, MapSnapshot after)
        implements MapEvent {}
