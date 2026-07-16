package com.gimlism.translucent.treeset.events;

/** A new {@code element} was linked at its BST position (before rebalancing). */
public record Add(Object element, SetSnapshot after) implements SetEvent {}
