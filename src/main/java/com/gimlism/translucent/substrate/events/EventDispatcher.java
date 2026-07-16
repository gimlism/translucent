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
    private boolean dispatchingRead;

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

    /**
     * Dispatch a <em>read-narration</em> event — one emitted during a query walk rather than a mutation
     * (e.g. a comparison-ordered set narrating its search descent). Marks a read dispatch in progress for
     * the callback's duration so {@link #beginMutation} rejects a listener that tries to mutate mid-walk
     * (which would corrupt the traversal), while still permitting nested reads. Save/restore keeps nested
     * read dispatch reentrant. A structure that never narrates reads never calls this, so the read-dispatch
     * flag stays {@code false} for it and {@link #beginMutation}'s behaviour is unchanged.
     */
    public void emitRead(E event) {
        boolean prev = dispatchingRead;
        dispatchingRead = true;
        try {
            for (StructureEventListener<E> listener : List.copyOf(listeners)) listener.onEvent(event);
        } finally {
            dispatchingRead = prev;
        }
    }

    /**
     * Begin a structural mutation, rejecting a re-entrant one triggered from within a listener — whether
     * that listener was fired by another mutation or by a {@linkplain #emitRead read-narration} event.
     */
    public void beginMutation() {
        if (mutating || dispatchingRead) {
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
