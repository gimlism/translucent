package com.gimlism.translucent.hashmap.events;

/**
 * A chain reached the treeify threshold and is about to become a red-black tree.
 *
 * <p><b>Announce semantics:</b> unlike its dual {@link Untreeify} (emitted after
 * conversion), this event is emitted <em>before</em> conversion, so its
 * {@link #after()} still shows {@code bucketIndex} as a chain. The tree is then
 * assembled by the following {@link Rotation}/{@link Recolor} burst; watch those
 * frames to see the bucket become a tree.
 */
public record Treeify(int bucketIndex, MapSnapshot after) implements MapEvent {}
