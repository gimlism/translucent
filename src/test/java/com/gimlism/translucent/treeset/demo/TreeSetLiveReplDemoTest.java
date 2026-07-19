package com.gimlism.translucent.treeset.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.repl.TreeSetCommandInterpreter;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TreeSetLiveReplDemoTest {

    private String runOn(String input, TeachingTreeSet<Integer> set) throws IOException {
        var out = new ByteArrayOutputStream();
        TreeSetLiveReplDemo.runRepl(new BufferedReader(new StringReader(input)),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                set, new TreeSetCommandInterpreter());
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void loopExecutesEachLineAndStopsOnQuit() throws IOException {
        var set = new TeachingTreeSet<Integer>();
        String output = runOn("add 10\ncontains 10\nquit\nadd 20\n", set); // lines after quit must NOT run

        assertTrue(output.contains("added 10"), output);
        assertTrue(output.contains("contains 10 → true"), output);
        assertTrue(output.contains("bye"), output);
        assertEquals(1, set.size(), "processing stopped at quit — 'add 20' never ran");
        assertFalse(set.contains(20), "'add 20' after quit never ran");
    }

    @Test
    void loopEndsAtEofWithoutQuit() throws IOException {
        var set = new TeachingTreeSet<Integer>();
        String output = runOn("add 42\n", set); // no quit — EOF ends the loop

        assertTrue(output.contains("added 42"), output);
        assertTrue(set.contains(42));
    }

    @Test
    void blankLinesProduceNoOutput() throws IOException {
        var set = new TeachingTreeSet<Integer>();
        String output = runOn("\n   \nsize\n", set);
        assertEquals("size = 0" + System.lineSeparator(), output);
    }
}
