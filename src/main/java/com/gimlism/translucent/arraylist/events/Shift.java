package com.gimlism.translucent.arraylist.events;

/** One element slid one slot (right on insert, left on remove); snapshot is mid-slide. */
public record Shift(int fromIndex, int toIndex, Object element, ListSnapshot after) implements ListEvent {}
