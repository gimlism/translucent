package com.gimlism.translucent.hashmap.repl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.viz.MapLiveVisualizer;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class MapCommandInterpreterTest {

    private final MapCommandInterpreter interp = new MapCommandInterpreter();
    private final TeachingHashMap<Integer, String> map = new TeachingHashMap<>();

    @Test
    void putNewKeyInsertsAndReports() {
        CommandResult r = interp.execute("put 3 x", map);
        assertEquals("put 3 = x", r.message());
        assertFalse(r.quit());
        assertEquals("x", map.get(3));
    }

    @Test
    void putExistingKeyReportsSetWithOldValue() {
        interp.execute("put 3 x", map);
        CommandResult r = interp.execute("put 3 y", map);
        assertEquals("set 3 = y (was x)", r.message());
        assertEquals("y", map.get(3));
    }

    @Test
    void putValueKeepsInteriorSpaces() {
        CommandResult r = interp.execute("put 3 hello world", map);
        assertEquals("put 3 = hello world", r.message());
        assertEquals("hello world", map.get(3));
    }

    @Test
    void removePresentAndAbsent() {
        interp.execute("put 3 x", map);
        assertEquals("removed 3", interp.execute("remove 3", map).message());
        assertFalse(map.containsKey(3));
        assertEquals("3 not found", interp.execute("remove 3", map).message());
    }

    @Test
    void getPresentAndAbsent() {
        interp.execute("put 3 x", map);
        assertEquals("get 3 → x", interp.execute("get 3", map).message());
        assertEquals("get 9 → absent", interp.execute("get 9", map).message());
    }

    @Test
    void containsKeyWithAlias() {
        interp.execute("put 3 x", map);
        assertEquals("containsKey 3 → true", interp.execute("containsKey 3", map).message());
        assertEquals("containsKey 9 → false", interp.execute("contains 9", map).message());
    }

    @Test
    void sizeAndClear() {
        interp.execute("put 1 a", map);
        interp.execute("put 2 b", map);
        assertEquals("size = 2", interp.execute("size", map).message());
        assertEquals("cleared (2 entries removed)", interp.execute("clear", map).message());
        assertEquals(0, map.size());
        assertEquals("size = 0", interp.execute("size", map).message());
    }

    @Test
    void quitAndExitSetQuitFlag() {
        assertTrue(interp.execute("quit", map).quit());
        assertTrue(interp.execute("exit", map).quit());
        assertEquals("bye", interp.execute("quit", map).message());
    }

    @Test
    void commandWordIsCaseInsensitiveAndWhitespaceTolerant() {
        assertEquals("put 3 = x", interp.execute("  PuT   3   x  ", map).message());
        assertEquals("x", map.get(3));
    }

    @Test
    void blankLineIsANoOpWithEmptyMessage() {
        CommandResult r = interp.execute("   ", map);
        assertEquals("", r.message());
        assertFalse(r.quit());
        assertEquals(0, map.size());
    }

    @Test
    void errorPathsLeaveMapUnchangedAndReport() {
        assertEquals("usage: put <int-key> <value>", interp.execute("put 3", map).message());
        assertEquals("not an integer: 'xyz'", interp.execute("put xyz v", map).message());
        assertEquals("not an integer: 'foo'", interp.execute("get foo", map).message());
        assertEquals("unknown command: 'frobnicate' (type 'help')",
                interp.execute("frobnicate 1", map).message());
        assertFalse(interp.execute("put 3", map).quit());
        assertEquals(0, map.size(), "no error path mutated the map");
    }

    @Test
    void helpListsEveryCommandWord() {
        String help = interp.execute("help", map).message();
        for (String word : new String[] {"put", "remove", "get", "containsKey", "size", "clear", "help", "quit"}) {
            assertTrue(help.contains(word), "help mentions " + word);
        }
    }

    @Test
    void aMutationExecutedThroughTheInterpreterBroadcastsExactlyOneFrame() {
        var frames = new ArrayList<String>();
        map.addListener(new MapLiveVisualizer(frames::add));
        interp.execute("put 1 a", map); // simple insert into an empty map → one Put event
        assertEquals(1, frames.size());
        assertTrue(frames.get(0).contains("\"type\":\"Put\""));
    }

    @Test
    void aReadThroughTheInterpreterBroadcastsNothing() {
        var frames = new ArrayList<String>();
        interp.execute("put 1 a", map);
        map.addListener(new MapLiveVisualizer(frames::add));
        interp.execute("get 1", map); // read → no event → no frame
        assertTrue(frames.isEmpty(), "reads emit no frame");
    }

    @Test
    void nullLineIsANoOpWithoutThrowing() {
        CommandResult r = interp.execute(null, map);
        assertEquals("", r.message());
        assertFalse(r.quit());
        assertEquals(0, map.size());
    }

    @Test
    void errorPathsForRemoveGetContainsKey() {
        assertEquals("not an integer: 'foo'", interp.execute("remove foo", map).message());
        assertEquals("usage: remove <int-key>", interp.execute("remove", map).message());
        assertEquals("usage: get <int-key>", interp.execute("get", map).message());
        assertEquals("not an integer: 'foo'", interp.execute("containsKey foo", map).message());
        assertEquals("usage: containsKey <int-key>", interp.execute("contains", map).message());
        assertEquals(0, map.size(), "no error path mutated the map");
    }
}
