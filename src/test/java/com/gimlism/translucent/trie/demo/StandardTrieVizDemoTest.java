package com.gimlism.translucent.trie.demo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StandardTrieVizDemoTest {
    @Test
    void runRendersLabelledTreeFramesWithCreatePruneAndHighlight() {
        var buf = new ByteArrayOutputStream();
        StandardTrieVizDemo.run(new PrintStream(buf, true, StandardCharsets.UTF_8));
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("CREATE"), out);
        assertTrue(out.contains("DESCEND"), out);
        assertTrue(out.contains("PUT"), out);
        assertTrue(out.contains("PRUNE"), out);
        assertTrue(out.contains("(root)"), out);   // a rendered tree frame
        assertTrue(out.contains("> "), out);        // a path highlight
        assertFalse(out.contains("SPLIT"), out);
        assertFalse(out.contains("MERGE"), out);
    }
}
