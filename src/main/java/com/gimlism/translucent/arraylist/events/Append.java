package com.gimlism.translucent.arraylist.events;

/** An element was appended at {@code index} (== the prior size). */
public record Append(Object element, int index, ListSnapshot after) implements ListEvent {}
