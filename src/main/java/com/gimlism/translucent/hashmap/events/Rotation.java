package com.gimlism.translucent.hashmap.events;

/** A single red-black tree rotation about {@code pivotKey}. */
public record Rotation(int bucketIndex, Direction direction, Object pivotKey, MapSnapshot after)
        implements MapEvent {}
