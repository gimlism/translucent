package com.gimlism.translucent.arraylist.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.consumer.ListRecordingListener;
import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AsciiListReplayerTest {
    private static ListRecordingListener recordThreeAppends() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add("a");
        list.add("b");
        list.add("c");
        return rec;
    }

    @Test
    void steppingForwardBackAndQuitWalksFrames() {
        var rec = recordThreeAppends(); // 3 events
        var replayer = new AsciiListReplayer(rec.events(), new AsciiListRenderer());
        var buffer = new ByteArrayOutputStream();
        var in = new ByteArrayInputStream("\nb\nq\n".getBytes(StandardCharsets.UTF_8)); // next, back, quit
        replayer.run(in, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("── frame 1/3 ──"), out);
        assertTrue(out.contains("── frame 2/3 ──"), out);
        int first = out.indexOf("frame 1/3");
        assertTrue(first >= 0 && out.indexOf("frame 1/3", first + 1) > first, "frame 1 shown again after back");
    }

    @Test
    void autoPlayRendersEveryFrameInOrder() {
        var rec = recordThreeAppends();
        var replayer = new AsciiListReplayer(rec.events(), new AsciiListRenderer());
        var buffer = new ByteArrayOutputStream();
        replayer.autoPlay(new PrintStream(buffer, true, StandardCharsets.UTF_8), 0);
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("── frame 1/3 ──"), out);
        assertTrue(out.contains("── frame 3/3 ──"), out);
        assertTrue(out.indexOf("frame 1/3") < out.indexOf("frame 2/3"), out);
        assertTrue(out.indexOf("frame 2/3") < out.indexOf("frame 3/3"), out);
    }
}
