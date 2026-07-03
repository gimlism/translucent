package com.gimlism.translucent.hashmap.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DemoTest {
    @Test
    void runShowsCollisionTreeifyResizeRemoveAndUntreeify() {
        var buffer = new ByteArrayOutputStream();
        Demo.run(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("COLLISION"), "expected collision, got:\n" + out);
        assertTrue(out.contains("TREEIFY"), "expected treeify, got:\n" + out);
        assertTrue(out.contains("ROTATE"), "expected rotation, got:\n" + out);
        assertTrue(out.contains("RESIZE"), "expected resize, got:\n" + out);
        assertTrue(out.contains("REMOVE"), "expected remove, got:\n" + out);
        assertTrue(out.contains("UNTREEIFY"), "expected untreeify, got:\n" + out);
    }
}
