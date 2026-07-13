package com.gimlism.translucent.arraylist.repl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.viz.ListLiveVisualizer;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListCommandInterpreterTest {

    private final ListCommandInterpreter interp = new ListCommandInterpreter();
    private TeachingArrayList<String> list;

    @BeforeEach
    void setUp() {
        list = new TeachingArrayList<>();
    }

    private String msg(String line) {
        return interp.execute(line, list).message();
    }

    @Test
    void addAppendsAndReportsIndex() {
        assertEquals("appended \"a\" at 0", msg("add a"));
        assertEquals("appended \"b\" at 1", msg("add b"));
        assertEquals(2, list.size());
        assertEquals("b", list.get(1));
    }

    @Test
    void addPreservesInteriorSpaces() {
        assertEquals("appended \"hello world\" at 0", msg("add hello world"));
        assertEquals("hello world", list.get(0));
    }

    @Test
    void addWithoutValueIsUsage() {
        assertEquals("usage: add <value>", msg("add"));
        assertEquals("usage: add <value>", msg("add   "));
        assertEquals(0, list.size());
    }

    @Test
    void insertPlacesAtIndexAndShiftsSurvivors() {
        msg("add a");
        msg("add c");
        assertEquals("inserted \"b\" at 1", msg("insert 1 b"));
        assertEquals("b", list.get(1));
        assertEquals("c", list.get(2));
    }

    @Test
    void insertUsageAndBadIndex() {
        assertEquals("usage: insert <index> <value>", msg("insert 0"));
        assertEquals("not an integer: 'x'", msg("insert x v"));
        assertEquals("index out of range: 5 (size 0)", msg("insert 5 v"));
        assertEquals(0, list.size());
    }

    @Test
    void setReplacesAndReturnsOld() {
        msg("add a");
        assertEquals("set 0 = \"b\" (was \"a\")", msg("set 0 b"));
        assertEquals("b", list.get(0));
    }

    @Test
    void setUsageAndOutOfRange() {
        assertEquals("usage: set <index> <value>", msg("set 0"));
        assertEquals("index out of range: 0 (size 0)", msg("set 0 v"));
    }

    @Test
    void removeReturnsOldAndReportsIndex() {
        msg("add a");
        msg("add b");
        assertEquals("removed \"a\" at 0", msg("remove 0"));
        assertEquals("b", list.get(0));
        assertEquals(1, list.size());
    }

    @Test
    void removeBadAndOutOfRange() {
        assertEquals("usage: remove <index>", msg("remove"));
        assertEquals("not an integer: 'x'", msg("remove x"));
        assertEquals("index out of range: 3 (size 0)", msg("remove 3"));
    }

    @Test
    void getReadsValueWithoutMutating() {
        msg("add a");
        assertEquals("get 0 → \"a\"", msg("get 0"));
        assertEquals("index out of range: 9 (size 1)", msg("get 9"));
        assertEquals(1, list.size());
    }

    @Test
    void sizeReportsCount() {
        assertEquals("size = 0", msg("size"));
        msg("add a");
        assertEquals("size = 1", msg("size"));
    }

    @Test
    void quitAndExitTerminate() {
        assertTrue(interp.execute("quit", list).quit());
        assertEquals("bye", interp.execute("quit", list).message());
        assertTrue(interp.execute("exit", list).quit());
    }

    @Test
    void blankAndNullAreSilentNoOps() {
        assertEquals("", msg(""));
        assertEquals("", msg("   "));
        assertEquals("", interp.execute(null, list).message());
        assertFalse(interp.execute(null, list).quit());
    }

    @Test
    void unknownCommandIsReported() {
        assertEquals("unknown command: 'foo' (type 'help')", msg("foo bar"));
    }

    @Test
    void helpListsEveryCommandWord() {
        String help = msg("help");
        for (String word : new String[] {"add", "insert", "set", "remove", "get", "size", "help", "quit"}) {
            assertTrue(help.contains(word), "help mentions " + word);
        }
    }

    @Test
    void commandsAreCaseInsensitive() {
        assertEquals("appended \"a\" at 0", msg("ADD a"));
        assertEquals("size = 1", msg("Size"));
    }

    @Test
    void mutationsBroadcastOneFrameEachAndReadsBroadcastNothing() {
        var frames = new ArrayList<String>();
        list.addListener(new ListLiveVisualizer(frames::add));

        msg("add a");                       // one Grow?/Append — at least one frame
        assertFalse(frames.isEmpty(), "append broadcasts");
        frames.clear();

        msg("get 0");                       // read → no event → no frame
        msg("size");
        assertTrue(frames.isEmpty(), "reads emit no frame");

        msg("set 0 b");                     // mutation → at least one frame
        assertFalse(frames.isEmpty(), "set broadcasts");
    }
}
