package com.gimlism.translucent.substrate.events;

/**
 * Synchronous consumer of a structure's event stream. Invoked during a mutation,
 * sometimes mid-operation. A listener may read the structure and add/remove
 * listeners, but must not structurally mutate it from within {@link #onEvent}
 * (each structure rejects re-entrant mutation with a
 * {@link java.util.ConcurrentModificationException}).
 *
 * <p>Because events are dispatched <em>mid-operation</em>, a listener must also
 * <strong>not throw</strong>: an exception propagates out of the half-finished
 * operation and may leave the structure inconsistent (see {@link EventDispatcher}).
 * Read, record, or render — but do not fail.
 */
@FunctionalInterface
public interface StructureEventListener<E extends StructureEvent> {
    void onEvent(E event);
}
