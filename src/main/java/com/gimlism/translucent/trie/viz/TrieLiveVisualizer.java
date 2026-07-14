package com.gimlism.translucent.trie.viz;

import com.gimlism.translucent.trie.events.TrieEvent;
import com.gimlism.translucent.trie.events.TrieEventListener;
import java.util.function.Consumer;

/**
 * Live twin of {@code TrieRecordingListener}: instead of buffering the stream, it serializes each
 * event to one frame ({@link TrieJsonSerializer#toFrame}) and hands it to a sink — normally a
 * {@code LiveServer}'s {@code broadcast}. Stateless; the server owns snapshot-on-connect.
 *
 * <p>Per the {@link TrieEventListener} contract it neither mutates the trie nor throws:
 * serialization is a pure read of the event's snapshot.
 */
public final class TrieLiveVisualizer implements TrieEventListener {

    private final Consumer<String> sink;

    public TrieLiveVisualizer(Consumer<String> sink) {
        this.sink = sink;
    }

    @Override
    public void onEvent(TrieEvent event) {
        sink.accept(TrieJsonSerializer.toFrame(event));
    }
}
