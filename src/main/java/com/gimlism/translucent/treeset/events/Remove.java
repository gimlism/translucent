package com.gimlism.translucent.treeset.events;

/** {@code element} was removed (terminal marker, after any rebalancing). */
public record Remove(Object element, SetSnapshot after) implements SetEvent {}
