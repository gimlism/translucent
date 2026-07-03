package com.gimlism.translucent.arraylist.events;

/**
 * Consumer of the list's event stream. Invoked synchronously during a mutation,
 * sometimes mid-operation (e.g. between the {@link Shift}s of an insert). A listener
 * may freely read the list and add/remove listeners, but must not structurally mutate
 * it from within {@link #onEvent} (rejected with a
 * {@link java.util.ConcurrentModificationException}).
 */
@FunctionalInterface
public interface ListEventListener {
    void onEvent(ListEvent event);
}
