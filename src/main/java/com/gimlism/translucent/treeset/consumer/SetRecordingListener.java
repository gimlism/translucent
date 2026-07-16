package com.gimlism.translucent.treeset.consumer;

import com.gimlism.translucent.treeset.events.SetEvent;
import com.gimlism.translucent.treeset.events.SetEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Buffers a {@code TeachingTreeSet}'s events for later replay or assertion. */
public final class SetRecordingListener implements SetEventListener {
    private final List<SetEvent> events = new ArrayList<>();

    @Override
    public void onEvent(SetEvent event) {
        events.add(event);
    }

    /** An unmodifiable view of the events recorded so far, in order. */
    public List<SetEvent> events() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }
}
