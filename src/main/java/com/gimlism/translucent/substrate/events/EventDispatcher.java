package com.gimlism.translucent.substrate.events;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

/**
 * The shared event-dispatch transport: holds a structure's listeners, dispatches events
 * synchronously, and enforces the no-re-entrant-mutation guard. Each instrumented structure
 * owns one by composition — its public {@code addListener}/{@code removeListener} delegate here —
 * so the subtlest transport invariants (copy-on-dispatch and guard-in-{@code finally}) live in
 * exactly one place instead of being hand-transcribed per structure.
 *
 * <p><b>Listener exceptions.</b> Events are dispatched synchronously and, by design, sometimes
 * <em>mid-operation</em> (between the rotations of a treeify, the shifts of an insert, the
 * split of a trie edge…). {@link #emit} does <em>not</em> isolate listener exceptions: a listener
 * that throws aborts the in-progress operation and may leave the structure inconsistent.
 * <strong>Listeners must not throw</strong> — read, record, or render, but do not fail. (This is
 * the exception-safety counterpart of the re-entrant-mutation ban {@link #beginMutation} enforces.)
 */
public final class EventDispatcher<E extends StructureEvent> {
    private final List<StructureEventListener<E>> listeners = new ArrayList<>();
    private final String structureNoun;
    private boolean mutating;

    /** @param structureNoun the structure's name for the re-entrancy message ("map", "list", "trie"). */
    public EventDispatcher(String structureNoun) {
        this.structureNoun = structureNoun;
    }

    public void addListener(StructureEventListener<E> listener) {
        listeners.add(listener);
    }

    public void removeListener(StructureEventListener<E> listener) {
        listeners.remove(listener);
    }

    /** Dispatch synchronously. Copies the listener list so a listener may add/remove listeners during dispatch. */
    public void emit(E event) {
        for (StructureEventListener<E> listener : List.copyOf(listeners)) listener.onEvent(event);
    }

    /** Begin a structural mutation, rejecting a re-entrant one triggered from within a listener. */
    public void beginMutation() {
        if (mutating) {
            throw new ConcurrentModificationException(
                structureNoun + " mutated from within an event listener; listeners may read the "
                + structureNoun + " but must not mutate it during event dispatch");
        }
        mutating = true;
    }

    /** End the current mutation. Call from a {@code finally} so the guard always resets. */
    public void endMutation() {
        mutating = false;
    }
}
