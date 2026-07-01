package com.gimlism.translucent.hashmap.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.events.Collision;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import com.gimlism.translucent.hashmap.events.Put;
import com.gimlism.translucent.hashmap.events.Resize;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsoleEventLoggerTest {
    private static MapSnapshot snap() {
        return new MapSnapshot(8, 1, 6, List.of());
    }

    @Test
    void formatsPutNewEntry() {
        String line = ConsoleEventLogger.format(new Put("a", 1, null, 3, true, snap()));
        assertEquals("PUT a=1 -> bucket 3 (new)", line);
    }

    @Test
    void formatsPutReplacement() {
        String line = ConsoleEventLogger.format(new Put("a", 2, 1, 3, false, snap()));
        assertEquals("PUT a=2 -> bucket 3 (replaced 1)", line);
    }

    @Test
    void formatsCollision() {
        String line = ConsoleEventLogger.format(new Collision("b", 0, 1, 2, snap()));
        assertEquals("COLLISION b -> bucket 0 (chain len 1 -> 2)", line);
    }

    @Test
    void formatsResize() {
        String line = ConsoleEventLogger.format(new Resize(8, 16, snap(), snap()));
        assertEquals("RESIZE 8 -> 16", line);
    }

    @Test
    void writesToSuppliedStream() {
        var buffer = new ByteArrayOutputStream();
        var logger = new ConsoleEventLogger(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        logger.onEvent(new Put("a", 1, null, 3, true, snap()));
        String printed = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("PUT a=1 -> bucket 3 (new)"));
    }
}
