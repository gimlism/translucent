package com.gimlism.translucent.arraylist.events;

import com.gimlism.translucent.substrate.events.StructureEventListener;

/**
 * Consumer of the list's event stream — a {@link StructureEventListener} named for the
 * list's concrete {@link ListEvent} vocabulary. Invoked synchronously during a
 * mutation, sometimes mid-operation (e.g. between the {@link Shift}s of an insert). A
 * listener may freely read the list and add/remove listeners, but must not structurally
 * mutate it from within {@link #onEvent} (rejected with a
 * {@link java.util.ConcurrentModificationException}).
 */
@FunctionalInterface
public interface ListEventListener extends StructureEventListener<ListEvent> {}
