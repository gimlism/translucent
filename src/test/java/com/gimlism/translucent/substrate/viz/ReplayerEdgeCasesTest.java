package com.gimlism.translucent.substrate.viz;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.substrate.events.StructureEvent;
import com.gimlism.translucent.substrate.events.StructureSnapshot;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Replayer input edge cases the review flagged: empty stream, back at frame 0, next past the end. */
class ReplayerEdgeCasesTest {
    private record TestSnap() implements StructureSnapshot {}

    private record TestEvent(int id, StructureSnapshot after) implements StructureEvent {}

    private static final EventRenderer<TestEvent> RENDERER = e -> "event-" + e.id();

    private static String replay(List<TestEvent> events, String input) {
        var buf = new ByteArrayOutputStream();
        new Replayer<>(events, RENDERER).run(
            new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
            new PrintStream(buf, true, StandardCharsets.UTF_8));
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void emptyStreamReportsNothingToReplay() {
        assertTrue(replay(List.of(), "\n").contains("(no events to replay)"));
    }

    @Test
    void backAtFrameZeroClampsAndDoesNotCrash() {
        var events = List.of(new TestEvent(1, new TestSnap()), new TestEvent(2, new TestSnap()));
        String out = replay(events, "b\nb\nq\n"); // b at frame 0 clamps; never advances
        assertTrue(out.contains("frame 1/2"), out);
        assertFalse(out.contains("frame 2/2"), out); // stayed put, no IndexOutOfBounds
    }

    @Test
    void nextPastTheLastFrameEnds() {
        var events = List.of(new TestEvent(1, new TestSnap()));
        String out = replay(events, "\n"); // next on the only frame ends the session
        assertTrue(out.contains("── frame 1/1 ──"), out);
    }
}
