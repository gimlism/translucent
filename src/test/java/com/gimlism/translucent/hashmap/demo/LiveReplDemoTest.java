package com.gimlism.translucent.hashmap.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.repl.MapCommandInterpreter;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LiveReplDemoTest {

    private String runOn(String input, TeachingHashMap<Integer, String> map) throws IOException {
        var out = new ByteArrayOutputStream();
        LiveReplDemo.runRepl(new BufferedReader(new StringReader(input)),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                map, new MapCommandInterpreter());
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void loopExecutesEachLineAndStopsOnQuit() throws IOException {
        var map = new TeachingHashMap<Integer, String>();
        String output = runOn("put 1 a\nget 1\nquit\nput 2 b\n", map); // lines after quit must NOT run

        assertTrue(output.contains("put 1 = a"), output);
        assertTrue(output.contains("get 1 → a"), output);
        assertTrue(output.contains("bye"), output);
        assertEquals("a", map.get(1));
        assertFalse(map.containsKey(2), "processing stopped at quit");
    }

    @Test
    void loopEndsAtEofWithoutQuit() throws IOException {
        var map = new TeachingHashMap<Integer, String>();
        String output = runOn("put 2 b\n", map); // no quit — EOF ends the loop

        assertTrue(output.contains("put 2 = b"), output);
        assertEquals("b", map.get(2));
    }

    @Test
    void blankLinesProduceNoOutput() throws IOException {
        var map = new TeachingHashMap<Integer, String>();
        String output = runOn("\n   \nsize\n", map);
        assertEquals("size = 0" + System.lineSeparator(), output);
    }
}
