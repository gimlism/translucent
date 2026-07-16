package com.gimlism.translucent.treeset.events;

import com.gimlism.translucent.substrate.rbtree.Direction;

/** A red-black rebalancing rotation about {@code pivot}, in direction {@code dir}. */
public record Rotation(Direction dir, Object pivot, SetSnapshot after) implements SetEvent {}
