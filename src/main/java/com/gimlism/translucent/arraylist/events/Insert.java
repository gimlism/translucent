package com.gimlism.translucent.arraylist.events;

/** An element was inserted at {@code index}; the terminal event of an insert. */
public record Insert(Object element, int index, ListSnapshot after) implements ListEvent {}
