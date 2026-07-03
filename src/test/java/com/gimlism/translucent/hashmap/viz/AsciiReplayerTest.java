package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.RecordingListener;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AsciiReplayerTest {
    private static RecordingListener recordThreePuts() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new RecordingListener();
        map.addListener(rec);
        map.put(1, "a");
        map.put(2, "b");
        map.put(3, "c");
        return rec;
    }

    @Test
    void steppingForwardBackAndQuitWalksFrames() {
        var rec = recordThreePuts(); // 3 events
        var replayer = new AsciiReplayer(rec.events(), new AsciiRenderer(new Palette(Palette.Mode.PLAIN)));
        var buffer = new ByteArrayOutputStream();
        // next (0->1), back (1->0), quit
        var in = new ByteArrayInputStream("\nb\nq\n".getBytes(StandardCharsets.UTF_8));
        replayer.run(in, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("── frame 1/3 ──"), out);
        assertTrue(out.contains("── frame 2/3 ──"), out);
        // after back we are on frame 1 again -> "frame 1/3" appears at least twice
        int first = out.indexOf("frame 1/3");
        assertTrue(first >= 0 && out.indexOf("frame 1/3", first + 1) > first, "frame 1 shown again after back");
    }

    @Test
    void autoPlayRendersEveryFrameInOrder() {
        var rec = recordThreePuts();
        var replayer = new AsciiReplayer(rec.events(), new AsciiRenderer(new Palette(Palette.Mode.PLAIN)));
        var buffer = new ByteArrayOutputStream();
        replayer.autoPlay(new PrintStream(buffer, true, StandardCharsets.UTF_8), 0);
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("── frame 1/3 ──"), out);
        assertTrue(out.contains("── frame 2/3 ──"), out);
        assertTrue(out.contains("── frame 3/3 ──"), out);
        // frames are in order
        assertTrue(out.indexOf("frame 1/3") < out.indexOf("frame 2/3"), out);
        assertTrue(out.indexOf("frame 2/3") < out.indexOf("frame 3/3"), out);
    }
}
