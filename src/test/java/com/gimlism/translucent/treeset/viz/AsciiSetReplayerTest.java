package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.substrate.viz.ColorMode;
import com.gimlism.translucent.treeset.consumer.SetRecordingListener;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AsciiSetReplayerTest {
    private static SetRecordingListener recordThreeAdds() {
        var s = new TeachingTreeSet<Integer>();
        var rec = new SetRecordingListener();
        s.addListener(rec);
        s.add(2);
        s.add(1);
        s.add(3);
        return rec;
    }

    @Test
    void steppingForwardBackAndQuitWalksFrames() {
        var rec = recordThreeAdds();
        int n = rec.events().size();
        var replayer = new AsciiSetReplayer(rec.events(), new AsciiSetRenderer(ColorMode.PLAIN));
        var buf = new ByteArrayOutputStream();
        var in = new ByteArrayInputStream("\nb\nq\n".getBytes(StandardCharsets.UTF_8)); // next, back, quit
        replayer.run(in, new PrintStream(buf, true, StandardCharsets.UTF_8));
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("── frame 1/" + n + " ──"), out);
        assertTrue(out.contains("── frame 2/" + n + " ──"), out);
        int first = out.indexOf("frame 1/" + n);
        assertTrue(first >= 0 && out.indexOf("frame 1/" + n, first + 1) > first, "frame 1 re-shown after back");
    }

    @Test
    void autoPlayRendersEveryFrameInOrder() {
        var rec = recordThreeAdds();
        int n = rec.events().size();
        var replayer = new AsciiSetReplayer(rec.events(), new AsciiSetRenderer(ColorMode.PLAIN));
        var buf = new ByteArrayOutputStream();
        replayer.autoPlay(new PrintStream(buf, true, StandardCharsets.UTF_8), 0);
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("── frame 1/" + n + " ──"), out);
        assertTrue(out.indexOf("frame 1/" + n) < out.indexOf("frame 2/" + n), out);
        assertTrue(out.contains("set: size="), out);
    }
}
