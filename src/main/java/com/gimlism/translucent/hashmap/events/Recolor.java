package com.gimlism.translucent.hashmap.events;

/** A single red-black tree node changed colour. */
public record Recolor(int bucketIndex, Object nodeKey, Color oldColor, Color newColor, MapSnapshot after)
        implements MapEvent {}
