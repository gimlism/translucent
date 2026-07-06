package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.MapEventListener;
import java.util.function.Consumer;

/**
 * Live twin of {@code MapRecordingListener}: instead of buffering the stream, it serializes each
 * event to one frame ({@link MapJsonSerializer#toFrame}) and hands it to a sink — normally a
 * {@code LiveServer}'s {@code broadcast}. Stateless; the server owns snapshot-on-connect.
 *
 * <p>Per the {@link MapEventListener} contract it neither mutates the map nor throws:
 * serialization is a pure read of the event's settled snapshot.
 */
public final class MapLiveVisualizer implements MapEventListener {

    private final Consumer<String> sink;

    public MapLiveVisualizer(Consumer<String> sink) {
        this.sink = sink;
    }

    @Override
    public void onEvent(MapEvent event) {
        sink.accept(MapJsonSerializer.toFrame(event));
    }
}
