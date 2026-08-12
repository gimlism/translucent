package com.gimlism.translucent.trie.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RadixTrieVizDemoTest {
    @Test
    void runRendersLabelledTreeFramesWithSplitMergePruneAndHighlight() {
        var buf = new ByteArrayOutputStream();
        RadixTrieVizDemo.run(new PrintStream(buf, true, StandardCharsets.UTF_8));
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("SPLIT"), out);
        assertTrue(out.contains("PUT"), out);
        assertTrue(out.contains("DESCEND"), out);
        assertTrue(out.contains("MERGE"), out);
        assertTrue(out.contains("PRUNE"), out);
        assertTrue(out.contains("(root)"), out);   // a rendered tree frame
        assertTrue(out.contains("> "), out);        // a path highlight
    }
}
