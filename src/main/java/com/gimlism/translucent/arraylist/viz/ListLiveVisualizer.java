package com.gimlism.translucent.arraylist.viz;

import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListEventListener;
import java.util.function.Consumer;

/**
 * Live twin of {@code ListRecordingListener}: instead of buffering the stream, it serializes each
 * event to one frame ({@link ListJsonSerializer#toFrame}) and hands it to a sink — normally a
 * {@code LiveServer}'s {@code broadcast}. Stateless; the server owns snapshot-on-connect.
 *
 * <p>Per the {@link ListEventListener} contract it neither mutates the list nor throws:
 * serialization is a pure read of the event's snapshot (it forwards the deliberate mid-slide
 * {@code Shift} and pre-placement {@code Grow} frames exactly as the baked path does).
 */
public final class ListLiveVisualizer implements ListEventListener {

    private final Consumer<String> sink;

    public ListLiveVisualizer(Consumer<String> sink) {
        this.sink = sink;
    }

    @Override
    public void onEvent(ListEvent event) {
        sink.accept(ListJsonSerializer.toFrame(event));
    }
}
