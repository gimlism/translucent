package com.gimlism.translucent.trie.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.core.StandardTrie;
import com.gimlism.translucent.trie.repl.TrieCommandInterpreter;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StandardTrieLiveReplDemoTest {

    private String runOn(String input, StandardTrie<Integer> trie) throws IOException {
        var out = new ByteArrayOutputStream();
        StandardTrieLiveReplDemo.runRepl(new BufferedReader(new StringReader(input)),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                trie, new TrieCommandInterpreter());
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void loopExecutesEachLineAndStopsOnQuit() throws IOException {
        var trie = new StandardTrie<Integer>();
        String output = runOn("put shore 1\nget shore\nquit\nput she 2\n", trie); // lines after quit must NOT run

        assertTrue(output.contains("put shore = 1"), output);
        assertTrue(output.contains("get shore → 1"), output);
        assertTrue(output.contains("bye"), output);
        assertEquals(1, trie.size(), "processing stopped at quit — 'put she 2' never ran");
    }

    @Test
    void loopEndsAtEofWithoutQuit() throws IOException {
        var trie = new StandardTrie<Integer>();
        String output = runOn("put she 2\n", trie); // no quit — EOF ends the loop

        assertTrue(output.contains("put she = 2"), output);
        assertEquals(2, trie.get("she"));
    }

    @Test
    void blankLinesProduceNoOutput() throws IOException {
        var trie = new StandardTrie<Integer>();
        String output = runOn("\n   \nsize\n", trie);
        assertEquals("size = 0" + System.lineSeparator(), output);
    }
}
