package com.gimlism.translucent.arraylist.consumer;

import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects events in order. Backbone of sequence-based tests and future replay. */
public class ListRecordingListener implements ListEventListener {
    private final List<ListEvent> events = new ArrayList<>();

    @Override
    public void onEvent(ListEvent event) {
        events.add(event);
    }

    /** Events in emission order (unmodifiable view). */
    public List<ListEvent> events() {
        return Collections.unmodifiableList(events);
    }

    public void clear() {
        events.clear();
    }
}
