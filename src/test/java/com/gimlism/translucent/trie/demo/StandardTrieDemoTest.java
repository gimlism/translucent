package com.gimlism.translucent.trie.demo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StandardTrieDemoTest {

    /**
     * The absences are the point: a standard trie never splits or merges an edge, so a demo that
     * quietly constructed a RadixTrie would still print CREATE/DESCEND/PUT/REMOVE/PRUNE and pass a
     * presence-only check. SPLIT and MERGE are what separate the two implementations.
     */
    @Test
    void runShowsTheChainGrowingAndPruningWithNoSplitOrMerge() {
        var buffer = new ByteArrayOutputStream();
        StandardTrieDemo.run(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("CREATE"), out);
        assertTrue(out.contains("DESCEND"), out);
        assertTrue(out.contains("PUT"), out);
        assertTrue(out.contains("REMOVE"), out);
        assertTrue(out.contains("PRUNE"), out);
        assertFalse(out.contains("SPLIT"), "a standard trie never splits an edge: " + out);
        assertFalse(out.contains("MERGE"), "a standard trie never merges an edge: " + out);
    }
}
