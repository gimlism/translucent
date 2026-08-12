package com.gimlism.translucent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LauncherMenuTest {

    /** Drives the launcher over {@code input}, recording which entries it chose to launch. */
    private record Session(String out, List<Launcher.Entry> launched) { }

    private static Session drive(String input) throws IOException {
        var buffer = new ByteArrayOutputStream();
        var out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        var launched = new ArrayList<Launcher.Entry>();
        Launcher.run(new BufferedReader(new StringReader(input)), out, e -> {
            launched.add(e);
            out.println("<<LAUNCHED>>"); // marker: lets us assert the command printed first
        });
        return new Session(buffer.toString(StandardCharsets.UTF_8), launched);
    }

    @Test
    void menuListsEveryEntryWithItsNumber() throws IOException {
        String out = drive("q\n").out();
        for (int i = 0; i < Launcher.CATALOG.size(); i++) {
            // The whole rendered row, not just the number: bare "1" is a substring of "12", "21",
            // and every other line, so a looser check passes for a menu that dropped rows.
            String row = String.format("%2d  %s", i + 1, Launcher.CATALOG.get(i).mode());
            assertTrue(out.contains(row), "missing menu row: " + row);
        }
    }

    @Test
    void menuNamesEachStructureOnce() throws IOException {
        String out = drive("q\n").out();
        for (String s : List.of("ArrayList", "HashMap", "TreeSet", "Trie (radix)")) {
            assertTrue(out.contains(s), "missing structure heading " + s);
        }
    }

    @Test
    void choosingANumberLaunchesThatEntry() throws IOException {
        Session s = drive("7\n");
        assertEquals(List.of(Launcher.CATALOG.get(6)), s.launched());
    }

    /** The menu is training wheels: a Reader should graduate to the direct command. */
    @Test
    void theDirectCommandIsPrintedBeforeTheDemoRuns() throws IOException {
        Session s = drive("7\n");
        String cmd = Launcher.commandFor(Launcher.CATALOG.get(6));
        assertTrue(s.out().contains(cmd), s.out());
        assertTrue(s.out().indexOf(cmd) < s.out().indexOf("<<LAUNCHED>>"), s.out());
    }

    @Test
    void rejectsOutOfRangeAndGarbageThenReprompts() throws IOException {
        Session s = drive("0\n26\nbanana\n1\n");
        assertEquals(List.of(Launcher.CATALOG.get(0)), s.launched(), "only the valid choice runs");
        assertTrue(s.out().contains("banana"), "should echo what was rejected");
    }

    @Test
    void quitsOnQWithoutLaunchingAnything() throws IOException {
        assertTrue(drive("q\n").launched().isEmpty());
    }

    @Test
    void quitsOnEofWithoutLaunchingAnything() throws IOException {
        assertTrue(drive("").launched().isEmpty());
    }

    @Test
    void blankLinesAreIgnoredRatherThanTreatedAsErrors() throws IOException {
        Session s = drive("\n   \nq\n");
        assertFalse(s.out().contains("not a choice"), s.out());
    }

    /**
     * A reader that snapshots whatever has actually reached the byte sink at the moment the
     * launcher blocks for input. {@code PrintStream} auto-flushes on {@code println} but not on a
     * newline-free {@code print}, so an unflushed prompt sits in the buffer and the terminal looks
     * hung — invisible when stdin is piped, because JVM exit flushes it after the fact.
     */
    private static final class PeekingReader extends BufferedReader {
        private final ByteArrayOutputStream sink;
        private final List<String> visibleAtEachRead = new ArrayList<>();

        PeekingReader(String input, ByteArrayOutputStream sink) {
            super(new StringReader(input));
            this.sink = sink;
        }

        @Override
        public String readLine() throws IOException {
            visibleAtEachRead.add(sink.toString(StandardCharsets.UTF_8));
            return super.readLine();
        }
    }

    private static PeekingReader driveBuffered(String input) throws IOException {
        var sink = new ByteArrayOutputStream();
        // Buffered exactly like System.out (8K, autoflush) so print-without-newline really is held.
        var out = new PrintStream(new BufferedOutputStream(sink, 8192), true, StandardCharsets.UTF_8);
        var in = new PeekingReader(input, sink);
        Launcher.run(in, out, e -> { });
        return in;
    }

    @Test
    void thePromptIsFlushedBeforeTheLauncherBlocksForInput() throws IOException {
        List<String> visible = driveBuffered("q\n").visibleAtEachRead;
        assertTrue(visible.get(0).contains("Pick a number"),
                "prompt was still sitting in the buffer when we blocked on stdin:\n" + visible.get(0));
    }

    @Test
    void repromptsAfterAnInvalidChoice() throws IOException {
        List<String> visible = driveBuffered("banana\nq\n").visibleAtEachRead;
        assertTrue(visible.get(1).contains("not a choice"), visible.get(1));
        assertTrue(visible.get(1).lastIndexOf("Pick a number") > visible.get(1).indexOf("not a choice"),
                "the prompt should be re-shown after the error, not left off-screen:\n" + visible.get(1));
    }

    @Test
    void repromptsAfterABlankLine() throws IOException {
        List<String> visible = driveBuffered("\nq\n").visibleAtEachRead;
        // Counted as occurrences, not lines: the prompt ends without a newline (the terminal's own
        // echo of the user's Enter supplies it), so two prompts land on one captured line.
        int prompts = visible.get(1).split("Pick a number", -1).length - 1;
        assertTrue(prompts >= 2, "a bare Enter should redraw the prompt, not leave a blank terminal");
    }
}
