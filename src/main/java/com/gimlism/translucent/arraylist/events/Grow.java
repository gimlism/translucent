package com.gimlism.translucent.arraylist.events;

/** The backing array grew (or was first allocated); {@code before}/{@code after} bracket the copy. */
public record Grow(int oldCapacity, int newCapacity, ListSnapshot before, ListSnapshot after) implements ListEvent {}
