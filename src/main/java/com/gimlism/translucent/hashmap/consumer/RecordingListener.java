package com.gimlism.translucent.hashmap.consumer;

import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.MapEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects events in order. Backbone of sequence-based tests and future replay. */
public class RecordingListener implements MapEventListener {
    private final List<MapEvent> events = new ArrayList<>();

    @Override
    public void onEvent(MapEvent event) {
        events.add(event);
    }

    /** Events in emission order (unmodifiable view). */
    public List<MapEvent> events() {
        return Collections.unmodifiableList(events);
    }

    public void clear() {
        events.clear();
    }
}
