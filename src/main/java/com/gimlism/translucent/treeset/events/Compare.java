package com.gimlism.translucent.treeset.events;

import com.gimlism.translucent.substrate.rbtree.Direction;

/**
 * The comparison walk visited a node holding {@code element}. Traversal narration: the
 * snapshot is unchanged from the previous frame; the focus advances. {@code went} is the
 * branch taken ({@code LEFT}/{@code RIGHT}); on the terminal comparison that lands on an
 * equal element, {@code found} is true and {@code went} is {@code null}.
 */
public record Compare(Object element, Direction went, boolean found, SetSnapshot after) implements SetEvent {}
