package com.gimlism.translucent.treeset.viz;

import com.gimlism.translucent.treeset.events.SetEvent;
import com.gimlism.translucent.treeset.events.SetEventListener;
import java.util.function.Consumer;

/**
 * Live twin of {@code SetRecordingListener}: instead of buffering the stream, it serializes each
 * event to one frame ({@link TreeSetJsonSerializer#toFrame}) and hands it to a sink — normally a
 * {@code LiveServer}'s {@code broadcast}. Stateless; the server owns snapshot-on-connect.
 *
 * <p>Per the {@link SetEventListener} contract it neither mutates the set nor throws: serialization
 * is a pure read of the event's snapshot. Because the set narrates reads, this also broadcasts the
 * {@code Compare} frames a {@code contains}/navigation walk emits — so a read animates live.
 */
public final class TreeSetLiveVisualizer implements SetEventListener {

    private final Consumer<String> sink;

    public TreeSetLiveVisualizer(Consumer<String> sink) {
        this.sink = sink;
    }

    @Override
    public void onEvent(SetEvent event) {
        sink.accept(TreeSetJsonSerializer.toFrame(event));
    }
}
