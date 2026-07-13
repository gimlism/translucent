package com.gimlism.translucent.arraylist.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.repl.ListCommandInterpreter;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ListLiveReplDemoTest {

    private String runOn(String input, TeachingArrayList<String> list) throws IOException {
        var out = new ByteArrayOutputStream();
        ListLiveReplDemo.runRepl(new BufferedReader(new StringReader(input)),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                list, new ListCommandInterpreter());
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void loopExecutesEachLineAndStopsOnQuit() throws IOException {
        var list = new TeachingArrayList<String>();
        String output = runOn("add a\nget 0\nquit\nadd b\n", list); // lines after quit must NOT run

        assertTrue(output.contains("appended \"a\" at 0"), output);
        assertTrue(output.contains("get 0 → \"a\""), output);
        assertTrue(output.contains("bye"), output);
        assertEquals(1, list.size(), "processing stopped at quit — 'add b' never ran");
    }

    @Test
    void loopEndsAtEofWithoutQuit() throws IOException {
        var list = new TeachingArrayList<String>();
        String output = runOn("add b\n", list); // no quit — EOF ends the loop

        assertTrue(output.contains("appended \"b\" at 0"), output);
        assertEquals("b", list.get(0));
    }

    @Test
    void blankLinesProduceNoOutput() throws IOException {
        var list = new TeachingArrayList<String>();
        String output = runOn("\n   \nsize\n", list);
        assertEquals("size = 0" + System.lineSeparator(), output);
    }
}
