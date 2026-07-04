package com.gimlism.translucent.trie.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TrieDemoTest {
    @Test
    void runShowsDescendCreateSplitPutRemoveMergePrune() {
        var buffer = new ByteArrayOutputStream();
        TrieDemo.run(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("CREATE"), out);
        assertTrue(out.contains("DESCEND"), out);
        assertTrue(out.contains("SPLIT"), out);
        assertTrue(out.contains("PUT"), out);
        assertTrue(out.contains("REMOVE"), out);
        assertTrue(out.contains("MERGE"), out);
        assertTrue(out.contains("PRUNE"), out);
    }
}
