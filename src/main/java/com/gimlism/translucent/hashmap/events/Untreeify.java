package com.gimlism.translucent.hashmap.events;

/**
 * A tree bin shrank below the untreeify threshold and became a chain again.
 *
 * <p>Emitted <em>after</em> conversion, so its {@link #after()} already shows
 * {@code bucketIndex} as a chain. (This is the settle-after dual of
 * {@link Treeify}, which announces before conversion.)
 */
public record Untreeify(int bucketIndex, MapSnapshot after) implements MapEvent {}
