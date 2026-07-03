package com.gimlism.translucent.arraylist.events;

/** The element at {@code index} was replaced in place (non-structural). */
public record Set(int index, Object previousElement, Object element, ListSnapshot after) implements ListEvent {}
