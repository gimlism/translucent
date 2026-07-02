package com.gimlism.translucent.hashmap.events;

/** A chain reached the treeify threshold and became a red-black tree. */
public record Treeify(int bucketIndex, MapSnapshot after) implements MapEvent {}
