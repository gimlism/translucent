package com.gimlism.translucent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.substrate.events.RecordingListener;
import com.gimlism.translucent.substrate.events.StructureEvent;
import com.gimlism.translucent.substrate.events.StructureEventListener;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The guides under {@code docs/guide/} tell a student which events each method produces. That claim
 * is only worth making if it stays true, so every documented row is driven for real here and
 * compared in both directions: nothing may fire that the guide omits, and no row may name an event
 * that cannot happen.
 *
 * <p>Events are captured at runtime rather than read out of the source, because they are not
 * emitted where they are caused — the ArrayList's {@code Append} fires inside a private helper, and
 * the HashMap's {@code Rotation}/{@code Recolor} arrive from red-black sink callbacks that appear
 * nowhere in {@code put}.
 */
class GuideEventMapTest {

    static final Path GUIDE_DIR = Path.of("docs", "guide");

    private static final String TABLE_HEADING = "## What each method makes you see";
    private static final Pattern CALL = Pattern.compile("`(\\w+)\\(([^)]*)\\)`");
    private static final Pattern EVENT = Pattern.compile("`(\\w+)`");

    /** Collects the event type names produced by each call, keyed {@code name/arity}. */
    static final class Runner<E extends StructureEvent> {
        private final RecordingListener<E> rec = new RecordingListener<>();
        private final Map<String, Set<String>> emitted = new LinkedHashMap<>();

        Runner(Consumer<StructureEventListener<E>> attach) {
            attach.accept(rec);
        }

        /** Attach to a second structure of the same event type, unioning into the same tally. */
        Runner<E> also(Consumer<StructureEventListener<E>> attach) {
            attach.accept(rec);
            return this;
        }

        void call(String key, Runnable body) {
            rec.clear();
            body.run();
            Set<String> seen = emitted.computeIfAbsent(key, k -> new TreeSet<>());
            for (E event : rec.events()) seen.add(event.getClass().getSimpleName());
        }

        Map<String, Set<String>> emitted() {
            return emitted;
        }
    }

    static Map<String, Set<String>> parseGuide(Path md) throws IOException {
        Map<String, Set<String>> rows = new LinkedHashMap<>();
        boolean inTable = false;
        for (String line : Files.readAllLines(md, StandardCharsets.UTF_8)) {
            String text = line.strip();
            if (text.startsWith("## ")) {
                inTable = text.equals(TABLE_HEADING);
                continue;
            }
            if (!inTable || !text.startsWith("|")) continue;
            if (text.startsWith("| ---") || text.startsWith("| you call")) continue;

            String[] cells = text.split("\\|");
            if (cells.length < 3) fail(md + ": unparseable table row: " + text);
            Matcher call = CALL.matcher(cells[1]);
            if (!call.find()) fail(md + ": first cell is not `method(args)`: " + text);

            String args = call.group(2).strip();
            int arity = args.isEmpty() ? 0 : args.split(",").length;

            Set<String> events = new TreeSet<>();
            Matcher event = EVENT.matcher(cells[2]);
            while (event.find()) events.add(event.group(1));
            if (events.isEmpty() && !cells[2].contains("nothing"))
                fail(md + ": second cell names no events and does not say \"nothing\": " + text);

            rows.put(call.group(1) + "/" + arity, events);
        }
        // A parser that silently skips rows looks identical to one that passes them.
        if (rows.isEmpty()) fail(md + ": no rows parsed under \"" + TABLE_HEADING + "\"");
        return rows;
    }

    static void assertGuideMatches(Path md, Class<?> type, Map<String, Set<String>> emitted)
            throws IOException {
        Map<String, Set<String>> documented = parseGuide(md);
        assertEquals(documented.keySet(), emitted.keySet(),
                md + ": documented rows and exercised calls disagree");
        for (Map.Entry<String, Set<String>> row : documented.entrySet()) {
            assertMethodExists(type, row.getKey(), md);
            assertEquals(row.getValue(), emitted.get(row.getKey()),
                    md + ": " + row.getKey() + " — documented events vs what it actually emitted");
        }
    }

    private static void assertMethodExists(Class<?> type, String key, Path md) {
        String[] parts = key.split("/");
        int arity = Integer.parseInt(parts[1]);
        boolean found = false;
        for (Method m : type.getMethods())
            if (m.getName().equals(parts[0]) && m.getParameterCount() == arity) {
                found = true;
                break;
            }
        // Matched on arity, not exact types: generics erase to Object and the guide writes
        // put(key, value), so arity keeps the docs readable without encoding erasure into them.
        assertTrue(found, md + " names " + parts[0] + "/" + arity + " but "
                + type.getSimpleName() + " has no such public method");
    }

    private static Runner<ListEvent> listScenario() {
        TeachingArrayList<Integer> list = new TeachingArrayList<>(2);
        Runner<ListEvent> runner = new Runner<>(list::addListener);
        runner.call("add/1", () -> list.add(1));
        runner.call("add/1", () -> list.add(2));
        runner.call("add/1", () -> list.add(3));      // capacity 2 exceeded — expect a Grow here
        runner.call("add/2", () -> list.add(0, 9));
        runner.call("set/2", () -> list.set(0, 8));
        runner.call("remove/1", () -> list.remove(0));
        runner.call("get/1", () -> list.get(0));
        return runner;
    }

    @Test
    void listGuideMatchesWhatTheListEmits() throws IOException {
        assertGuideMatches(GUIDE_DIR.resolve("list.md"), TeachingArrayList.class,
                listScenario().emitted());
    }
}
