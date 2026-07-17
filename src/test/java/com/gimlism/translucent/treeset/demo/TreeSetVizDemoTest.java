package com.gimlism.translucent.treeset.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TreeSetVizDemoTest {
    @Test
    void runRendersInsertsRebalancesReadsAndRemoval() {
        var buf = new ByteArrayOutputStream();
        TreeSetVizDemo.run(new PrintStream(buf, true, StandardCharsets.UTF_8));
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("add 10"), out);
        assertTrue(out.contains("rotate "), out);     // rebalancing visible
        assertTrue(out.contains("recolor "), out);
        assertTrue(out.contains("compare 40 -> found"), out);   // read narration (a hit: visited node 40)
        assertTrue(out.contains("remove 30"), out);
        assertTrue(out.contains("set: size="), out);   // trees rendered
        assertTrue(out.contains("> "), out);           // highlight present
    }
}
