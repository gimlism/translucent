package com.gimlism.translucent.hashmap.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class VizDemoTest {
    @Test
    void runRendersAsciiFramesForTheSameCollisionStory() {
        var buffer = new ByteArrayOutputStream();
        // No console under test capture, so Palette.auto() renders PLAIN (deterministic).
        VizDemo.run(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String out = buffer.toString(StandardCharsets.UTF_8);

        // Each frame carries the event label ...
        assertTrue(out.contains("TREEIFY"), "expected treeify, got:\n" + out);
        assertTrue(out.contains("UNTREEIFY"), "expected untreeify, got:\n" + out);
        // ... plus a rendered whole-map frame (header + highlighted bucket).
        assertTrue(out.contains("map: cap="), "expected a map header, got:\n" + out);
        assertTrue(out.contains("> [0]"), "expected bucket 0 highlighted, got:\n" + out);
        // The colliding chain is drawn before it treeifies.
        assertTrue(out.contains("0=v0 -> 8=v8"), "expected a rendered chain, got:\n" + out);
    }
}
