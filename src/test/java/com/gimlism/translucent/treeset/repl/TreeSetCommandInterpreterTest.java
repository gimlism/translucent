package com.gimlism.translucent.treeset.repl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.substrate.repl.CommandResult;
import com.gimlism.translucent.treeset.consumer.SetRecordingListener;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.events.Compare;
import org.junit.jupiter.api.Test;

class TreeSetCommandInterpreterTest {

    private final TreeSetCommandInterpreter interp = new TreeSetCommandInterpreter();

    private TeachingTreeSet<Integer> setOf(int... elements) {
        var set = new TeachingTreeSet<Integer>();
        for (int e : elements) {
            set.add(e);
        }
        return set;
    }

    private String run(String line, TeachingTreeSet<Integer> set) {
        return interp.execute(line, set).message();
    }

    // --- add / remove / contains ---

    @Test
    void addNewElementReportsAdded() {
        var set = setOf();
        assertEquals("added 30", run("add 30", set));
        assertTrue(set.contains(30));
    }

    @Test
    void addDuplicateReportsAlreadyPresent() {
        var set = setOf(30);
        assertEquals("30 already present", run("add 30", set));
        assertEquals(1, set.size());
    }

    @Test
    void removeHitAndMiss() {
        var set = setOf(30);
        assertEquals("removed 30", run("remove 30", set));
        assertEquals("30 not found", run("remove 30", set));
    }

    @Test
    void containsTrueAndFalse() {
        var set = setOf(10, 20);
        assertEquals("contains 20 → true", run("contains 20", set));
        assertEquals("contains 99 → false", run("contains 99", set));
    }

    // --- first / last, populated and empty ---

    @Test
    void firstAndLastOnPopulatedSet() {
        var set = setOf(30, 10, 20);
        assertEquals("first → 10", run("first", set));
        assertEquals("last → 30", run("last", set));
    }

    @Test
    void firstAndLastOnEmptySetSayEmpty() {
        var set = setOf();
        assertEquals("first → (empty)", run("first", set));
        assertEquals("last → (empty)", run("last", set));
    }

    // --- navigation queries ---

    @Test
    void lowerFloorCeilingHigher() {
        var set = setOf(10, 20, 30);
        assertEquals("lower 20 → 10", run("lower 20", set));   // strictly <
        assertEquals("floor 20 → 20", run("floor 20", set));   // ≤, exact member
        assertEquals("floor 25 → 20", run("floor 25", set));
        assertEquals("ceiling 20 → 20", run("ceiling 20", set)); // ≥, exact member
        assertEquals("ceiling 25 → 30", run("ceiling 25", set));
        assertEquals("higher 20 → 30", run("higher 20", set)); // strictly >
    }

    @Test
    void navigationReturnsNoneWhenNoSuchElement() {
        var set = setOf(10, 20, 30);
        assertEquals("lower 10 → none", run("lower 10", set));   // nothing < 10
        assertEquals("higher 30 → none", run("higher 30", set)); // nothing > 30
        assertEquals("floor 5 → none", run("floor 5", set));
        assertEquals("ceiling 99 → none", run("ceiling 99", set));
    }

    // --- poll*, populated and empty ---

    @Test
    void pollFirstAndPollLastRemoveAndReturn() {
        var set = setOf(10, 20, 30);
        assertEquals("pollFirst → 10", run("pollFirst", set));
        assertEquals("pollLast → 30", run("pollLast", set));
        assertEquals(1, set.size()); // only 20 remains
    }

    @Test
    void pollOnEmptySetSaysEmpty() {
        var set = setOf();
        assertEquals("pollFirst → (empty)", run("pollFirst", set));
        assertEquals("pollLast → (empty)", run("pollLast", set));
    }

    // --- size / help / quit ---

    @Test
    void sizeReportsCount() {
        assertEquals("size = 3", run("size", setOf(10, 20, 30)));
    }

    @Test
    void helpListsEveryVerb() {
        String help = interp.helpText();
        for (String verb : new String[] {"add", "remove", "contains", "first", "last",
                "lower", "floor", "ceiling", "higher", "pollFirst", "pollLast", "size",
                "help", "quit"}) {
            assertTrue(help.contains(verb), "help missing verb: " + verb + "\n" + help);
        }
    }

    @Test
    void quitAndExitTerminate() {
        CommandResult q = interp.execute("quit", setOf());
        assertEquals("bye", q.message());
        assertTrue(q.quit());
        assertTrue(interp.execute("exit", setOf()).quit(), "exit is an alias for quit");
    }

    @Test
    void verbsAreCaseInsensitive() {
        assertEquals("added 5", run("ADD 5", setOf()));
        assertEquals("pollFirst → (empty)", run("POLLFIRST", setOf()));
    }

    // --- error paths ---

    @Test
    void blankAndNullAreSilentNoOps() {
        assertEquals("", run("", setOf()));
        assertEquals("", run("   ", setOf()));
        assertEquals("", interp.execute(null, setOf()).message());
    }

    @Test
    void missingArgumentIsAUsageError() {
        assertEquals("usage: add <int>", run("add", setOf()));
        assertEquals("usage: floor <int>", run("floor", setOf()));
    }

    @Test
    void nonIntegerArgumentIsRejected() {
        assertEquals("not an integer: 'abc'", run("add abc", setOf()));
    }

    @Test
    void trailingExtraTokenIsRejected() {
        assertEquals("usage: add <int>", run("add 3 4", setOf()));   // element is one token
        assertEquals("usage: first", run("first now", setOf()));      // no-arg verb rejects args
        assertEquals("usage: help", run("help me", setOf()));
    }

    @Test
    void quitWithTrailingTokenIsRejectedAndDoesNotTerminate() {
        CommandResult q = interp.execute("quit now", setOf());
        assertEquals("usage: quit", q.message());
        assertFalse(q.quit(), "a stray token must not trigger an accidental quit");
        assertFalse(interp.execute("exit please", setOf()).quit(), "exit also rejects extras");
    }

    @Test
    void unknownCommand() {
        assertEquals("unknown command: 'frobnicate' (type 'help')", run("frobnicate", setOf()));
    }

    // --- the reads-narrate divergence: comparison reads broadcast Compare frames ---

    private long compareFrames(String line, TeachingTreeSet<Integer> set) {
        var rec = new SetRecordingListener();
        set.addListener(rec);
        interp.execute(line, set);
        return rec.events().stream().filter(e -> e instanceof Compare).count();
    }

    @Test
    void comparisonReadsNarrateWithCompareFrames() {
        assertTrue(compareFrames("contains 20", setOf(10, 20, 30)) > 0,
                "contains walks the tree and must emit Compare frames");
        assertTrue(compareFrames("floor 25", setOf(10, 20, 30)) > 0,
                "floor walks the tree and must emit Compare frames");
    }

    @Test
    void nonComparingVerbsDoNotNarrate() {
        assertEquals(0, compareFrames("size", setOf(10, 20, 30)));
        assertEquals(0, compareFrames("first", setOf(10, 20, 30)));
        assertEquals(0, compareFrames("help", setOf(10, 20, 30)));
    }
}
