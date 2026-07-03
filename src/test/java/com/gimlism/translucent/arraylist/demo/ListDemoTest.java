package com.gimlism.translucent.arraylist.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ListDemoTest {
    @Test
    void runShowsGrowAppendInsertShiftAndRemove() {
        var buffer = new ByteArrayOutputStream();
        ListDemo.run(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("GROW"), "expected grow, got:\n" + out);
        assertTrue(out.contains("APPEND"), "expected append, got:\n" + out);
        assertTrue(out.contains("INSERT"), "expected insert, got:\n" + out);
        assertTrue(out.contains("SHIFT"), "expected shift, got:\n" + out);
        assertTrue(out.contains("REMOVE"), "expected remove, got:\n" + out);
    }
}
