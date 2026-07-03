package com.gimlism.translucent.arraylist.events;

/** A slot holding a live element (which may itself be {@code null}). */
public record FilledSlot(Object element) implements SlotSnapshot {}
