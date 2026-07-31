package com.gimlism.translucent.treeset.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TreeSetDemoTest {
    @Test
    void runShowsAddCompareRotationRecolorRemove() {
        var buffer = new ByteArrayOutputStream();
        TreeSetDemo.run(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("add "), out);
        assertTrue(out.contains("compare "), out);
        assertTrue(out.contains("rotate "), out);
        assertTrue(out.contains("recolor "), out);
        assertTrue(out.contains("remove "), out);
    }
}
