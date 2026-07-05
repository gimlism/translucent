package com.gimlism.translucent.substrate.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.viz.AsciiListRenderer;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.viz.AsciiMapRenderer;
import com.gimlism.translucent.hashmap.viz.Palette;
import com.gimlism.translucent.substrate.events.RecordingListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GenericDriversTest {
    @Test
    void genericVisualizerRendersMapFramesLive() {
        var buffer = new ByteArrayOutputStream();
        var map = new TeachingHashMap<Integer, String>();
        map.addListener(new Visualizer<MapEvent>(
            new PrintStream(buffer, true, StandardCharsets.UTF_8),
            new AsciiMapRenderer(new Palette(Palette.Mode.PLAIN))));
        map.put(1, "a");
        assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("PUT 1=a"), buffer.toString());
    }

    @Test
    void genericReplayerStepsThroughListFrames() {
        var list = new TeachingArrayList<String>(4);
        var rec = new RecordingListener<ListEvent>();
        list.addListener(rec);
        list.add("a");
        list.add("b");
        var replayer = new Replayer<ListEvent>(rec.events(), new AsciiListRenderer());
        var buffer = new ByteArrayOutputStream();
        replayer.autoPlay(new PrintStream(buffer, true, StandardCharsets.UTF_8), 0);
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("── frame 1/2 ──"), out);
        assertTrue(out.contains("── frame 2/2 ──"), out);
    }

    @Test
    void genericReplayerRunHandlesQuit() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new RecordingListener<MapEvent>();
        map.addListener(rec);
        map.put(1, "a");
        var replayer = new Replayer<MapEvent>(rec.events(), new AsciiMapRenderer(new Palette(Palette.Mode.PLAIN)));
        var buffer = new ByteArrayOutputStream();
        replayer.run(new ByteArrayInputStream("q\n".getBytes(StandardCharsets.UTF_8)),
                     new PrintStream(buffer, true, StandardCharsets.UTF_8));
        assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("── frame 1/1 ──"), buffer.toString());
    }
}
