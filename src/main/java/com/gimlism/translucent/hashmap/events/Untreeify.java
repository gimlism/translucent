package com.gimlism.translucent.hashmap.events;

/** A tree bin shrank below the untreeify threshold and became a chain again. */
public record Untreeify(int bucketIndex, MapSnapshot after) implements MapEvent {}
