package com.gimlism.translucent.arraylist.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AsciiListVisualizerTest {
    @Test
    void liveVisualizerRendersAppendGrowInsertRemoveFrames() {
        var buffer = new ByteArrayOutputStream();
        var list = new TeachingArrayList<String>(4);
        list.addListener(new AsciiListVisualizer(new PrintStream(buffer, true, StandardCharsets.UTF_8)));

        for (String s : new String[]{"a", "b", "c", "d", "e"}) list.add(s); // grow 4 -> 6
        list.add(1, "X");   // shift right + insert
        list.remove(2);     // shift left + remove

        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("GROW cap 4 -> 6"), out);
        assertTrue(out.contains("APPEND e @ 4"), out);
        assertTrue(out.contains("INSERT X @ 1"), out);
        assertTrue(out.contains("SHIFT"), out);
        assertTrue(out.contains("REMOVE @ 2"), out);
        // a rendered row appears (highlight marker present)
        assertTrue(out.contains(">X<"), out);
    }
}
