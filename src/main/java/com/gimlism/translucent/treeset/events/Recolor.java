package com.gimlism.translucent.treeset.events;

import com.gimlism.translucent.substrate.rbtree.Color;

/** A red-black rebalancing recolour of the node holding {@code element}. */
public record Recolor(Object element, Color oldColor, Color newColor, SetSnapshot after) implements SetEvent {}
