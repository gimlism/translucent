package com.gimlism.translucent.arraylist.events;

/** The element at {@code index} was removed; the terminal event of a remove. */
public record RemoveAt(int index, Object removedElement, ListSnapshot after) implements ListEvent {}
