package com.gimlism.translucent.hashmap.events;

/** Immutable copy of a single map entry at snapshot time. */
public record EntrySnapshot(Object key, Object value, int hash) {}
