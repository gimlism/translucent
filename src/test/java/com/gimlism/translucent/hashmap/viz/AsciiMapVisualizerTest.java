package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AsciiMapVisualizerTest {
    @Test
    void livePrintsAFramePerMutation() {
        var buffer = new ByteArrayOutputStream();
        var out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        var map = new TeachingHashMap<Integer, String>();
        map.addListener(new AsciiMapVisualizer(out, new AsciiMapRenderer(new Palette(Palette.Mode.PLAIN))));

        map.put(0, "zero");
        map.put(8, "eight"); // collision in bucket 0

        String printed = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("PUT 0=zero -> bucket 0 (new)"), printed);
        assertTrue(printed.contains("map: cap=8"), printed);
        // the collision frame shows both entries chained in bucket 0
        assertTrue(printed.contains("0=zero -> 8=eight"), printed);
    }
}
