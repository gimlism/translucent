package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AsciiSetVisualizerTest {
    @Test
    void liveVisualizerRendersAddRebalanceAndRemoveFrames() {
        var buf = new ByteArrayOutputStream();
        var s = new TeachingTreeSet<Integer>();
        // no console under test capture -> the convenience ctor's ColorMode.detect() is PLAIN
        s.addListener(new AsciiSetVisualizer(new PrintStream(buf, true, StandardCharsets.UTF_8)));
        s.add(10);
        s.add(20);
        s.add(30); // red-red violation at 30 -> left rotation about 10 + recolour
        s.remove(30);
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("add 10"), out);
        assertTrue(out.contains("rotate "), out);   // rebalance narrated
        assertTrue(out.contains("recolor "), out);
        assertTrue(out.contains("remove 30"), out);
        assertTrue(out.contains("set: size="), out); // trees rendered
        assertTrue(out.contains("> "), out);         // highlight present on some frame
    }
}
