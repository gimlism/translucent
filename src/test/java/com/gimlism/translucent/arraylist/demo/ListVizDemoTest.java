package com.gimlism.translucent.arraylist.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ListVizDemoTest {
    @Test
    void runRendersLabelledFramesWithGrowShiftAndHighlight() {
        var buffer = new ByteArrayOutputStream();
        ListVizDemo.run(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("GROW cap 4 -> 6"), out);
        assertTrue(out.contains("SHIFT"), out);
        assertTrue(out.contains("INSERT X @ 1"), out);
        assertTrue(out.contains("REMOVE @ 2"), out);
        assertTrue(out.contains(">X<"), out); // the inserted element highlighted in its row
    }
}
