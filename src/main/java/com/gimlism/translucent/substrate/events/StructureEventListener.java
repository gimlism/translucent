package com.gimlism.translucent.substrate.events;

/**
 * Synchronous consumer of a structure's event stream. Invoked during a mutation,
 * sometimes mid-operation. A listener may read the structure and add/remove
 * listeners, but must not structurally mutate it from within {@link #onEvent}
 * (each structure rejects re-entrant mutation with a
 * {@link java.util.ConcurrentModificationException}).
 */
@FunctionalInterface
public interface StructureEventListener<E extends StructureEvent> {
    void onEvent(E event);
}
