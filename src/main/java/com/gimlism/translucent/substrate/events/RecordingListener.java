package com.gimlism.translucent.substrate.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects events in emission order. Backbone of sequence-based tests and replay. */
public class RecordingListener<E extends StructureEvent> implements StructureEventListener<E> {
    private final List<E> events = new ArrayList<>();

    @Override
    public void onEvent(E event) {
        events.add(event);
    }

    /** Events in emission order (unmodifiable view). */
    public List<E> events() {
        return Collections.unmodifiableList(events);
    }

    public void clear() {
        events.clear();
    }
}
