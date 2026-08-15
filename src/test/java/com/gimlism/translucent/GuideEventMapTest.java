package com.gimlism.translucent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.substrate.events.RecordingListener;
import com.gimlism.translucent.substrate.events.StructureEvent;
import com.gimlism.translucent.substrate.events.StructureEventListener;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.events.SetEvent;
import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.core.StandardTrie;
import com.gimlism.translucent.trie.events.TrieEvent;
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
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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

    // This assertion fires only when keySet equality passes in assertGuideMatches, so it catches
    // the case where a scenario call() key literal names a method that does not exist. It guards
    // against the scenario key drifting from the method it invokes after a src/main rename.
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

    /**
     * One guide and the scenario that proves it.
     *
     * <p>The supplier is lazy on purpose. A scenario that throws must fail its own case only,
     * leaving the directory guards free to report what is actually missing — eager evaluation would
     * take them down alongside it and describe the wrong problem.
     */
    record GuideCase(String file, Class<?> type, Supplier<Map<String, Set<String>>> emitted) {
        @Override
        public String toString() {
            // Not for Maven's console/XML output, which never shows this — it names invocations
            // as guideMatchesWhatTheStructureEmits(GuideCase)[1..4], and assertGuideMatches already
            // embeds the guide path in every assertion message. This override matters for IDE test
            // runners, which do render {0}, and it replaces the record's default toString(), whose
            // Supplier field would otherwise print a nondeterministic lambda identity.
            return file;
        }
    }

    /**
     * Every guide and how it is proven. Deliberately the single source of truth: the parameterized
     * case below runs exactly these, and the directory guards compare exactly these against disk, so
     * a single guide's coverage cannot be dropped without the registry shrinking and the guards
     * noticing that guide.
     */
    static Stream<GuideCase> cases() {
        return Stream.of(
                new GuideCase("list.md", TeachingArrayList.class, () -> listScenario().emitted()),
                new GuideCase("map.md", TeachingHashMap.class, () -> mapScenario().emitted()),
                new GuideCase("treeset.md", TeachingTreeSet.class, () -> treeSetScenario().emitted()),
                new GuideCase("trie.md", RadixTrie.class, () -> trieScenario().emitted()),
                new GuideCase("standard-trie.md", StandardTrie.class,
                        () -> standardTrieScenario().emitted()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void guideMatchesWhatTheStructureEmits(GuideCase c) throws IOException {
        assertGuideMatches(GUIDE_DIR.resolve(c.file()), c.type(), c.emitted().get());
    }

    private static Runner<ListEvent> listScenario() {
        TeachingArrayList<Integer> list = new TeachingArrayList<>(2);
        Runner<ListEvent> runner = new Runner<>(list::addListener);
        runner.call("add/1", () -> list.add(1));
        runner.call("add/1", () -> list.add(2));
        runner.call("add/1", () -> list.add(3));      // capacity 2 exceeded — expect a Grow here
        runner.call("add/2", () -> list.add(0, 9));
        runner.call("add/2", () -> list.add(list.size(), 7)); // index == size -> delegates to append
        runner.call("set/2", () -> list.set(0, 8));
        runner.call("remove/1", () -> list.remove(0));
        runner.call("get/1", () -> list.get(0));
        return runner;
    }

    /** Every instance lands in one bucket, so a chain forms and then treeifies. */
    private record CollidingKey(int id) {
        @Override
        public int hashCode() {
            return 42;
        }
    }

    private static Runner<MapEvent> mapScenario() {
        // treeifyThreshold 8, untreeifyThreshold 2, minTreeifyCapacity 8: the 8th colliding put
        // builds the tree; capacity 16 with 8 entries stays under 0.75 load, so nothing resizes.
        TeachingHashMap<CollidingKey, String> colliding =
                new TeachingHashMap<>(16, 0.75f, 8, 2, 8);
        Runner<MapEvent> runner = new Runner<>(colliding::addListener);
        for (int i = 0; i < 8; i++) {
            int n = i;
            runner.call("put/2", () -> colliding.put(new CollidingKey(n), "v" + n));
        }
        // Drain the tree back down past untreeifyThreshold to reach Untreeify.
        for (int i = 7; i >= 1; i--) {
            int n = i;
            runner.call("remove/1", () -> colliding.remove(new CollidingKey(n)));
        }
        // Absent key -> doRemove finds nothing and emits nothing; unions into the same tally as the
        // removes above, so it can only shrink the documented set if it fires something new.
        runner.call("remove/1", () -> colliding.remove(new CollidingKey(99)));
        runner.call("get/1", () -> colliding.get(new CollidingKey(0)));

        // A second, deliberately cramped map: distinct keys crossing the load factor force Resize,
        // which the colliding map never does. Same event type, so it unions into the same tally.
        TeachingHashMap<Integer, String> cramped = new TeachingHashMap<>(2, 0.75f, 8, 2, 8);
        runner.also(cramped::addListener);
        for (int i = 0; i < 6; i++) {
            int n = i;
            runner.call("put/2", () -> cramped.put(n, "v" + n));
        }
        return runner;
    }

    private static Runner<SetEvent> treeSetScenario() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        Runner<SetEvent> runner = new Runner<>(set::addListener);
        // An ascending run is the worst case for a red-black tree: every insert leans right, so
        // rotations and recolours are forced rather than incidental.
        for (int value : new int[] {10, 20, 30, 40, 50}) {
            runner.call("add/1", () -> set.add(value));
        }
        runner.call("add/1", () -> set.add(20));   // already present -> Compare only, no Add
        runner.call("contains/1", () -> set.contains(20));
        runner.call("floor/1", () -> set.floor(35));
        // remove(30) turns out to be quiet on this tree (no fixup needed); remove(10) is the
        // deletion that forces a rebalance, so it is the one that exercises Rotation/Recolor.
        runner.call("remove/1", () -> set.remove(10));
        runner.call("remove/1", () -> set.remove(99)); // absent -> narrated Compares, no Remove
        return runner;
    }

    private static Runner<TrieEvent> trieScenario() {
        RadixTrie<Integer> trie = new RadixTrie<>();
        Runner<TrieEvent> runner = new Runner<>(trie::addListener);
        // "shore" diverges from "shell" partway along its edge, which is the only way to reach
        // SplitEdge; the split leaves a non-key "sh" node with three children ('e' -> the old
        // "shell" chain, 'o' -> "shore", 'y' -> "shy" once inserted).
        runner.call("put/2", () -> trie.put("shell", 1));
        runner.call("put/2", () -> trie.put("shore", 2));
        runner.call("put/2", () -> trie.put("shy", 3));
        runner.call("get/1", () -> trie.get("shell"));
        runner.call("containsKey/1", () -> trie.containsKey("shore"));
        // Removing "shy" first only prunes it — "sh" still has two children afterwards, so it
        // cannot merge. Removing "shore" next prunes it too, but that's the child that drops "sh"
        // to exactly one remaining child ("shell"'s chain), which is what triggers the merge back
        // into a single edge. Both calls are tagged remove/1, so their events union together.
        runner.call("remove/1", () -> trie.remove("shy"));
        runner.call("remove/1", () -> trie.remove("shore"));
        // Absent key -> remove bails out before emitting anything; unions into the same tally as the
        // removes above, so it can only shrink the documented set if it fires something new.
        runner.call("remove/1", () -> trie.remove("nope"));
        return runner;
    }

    /**
     * Deliberately the same keys as {@link #trieScenario()}: the two guides then differ only where
     * the structures do. An uncompressed trie reaches its whole vocabulary on this story — the first
     * put creates a chain from an empty root, later puts descend the shared "sh" prefix before
     * creating, and the removes prune back up it — while SplitEdge and MergeEdge remain unreachable,
     * which is the lesson standard-trie.md is written to teach.
     */
    private static Runner<TrieEvent> standardTrieScenario() {
        StandardTrie<Integer> trie = new StandardTrie<>();
        Runner<TrieEvent> runner = new Runner<>(trie::addListener);
        // Empty root: every character of "shell" is a fresh node, so this call is all CreateNode.
        runner.call("put/2", () -> trie.put("shell", 1));
        // "sh" already exists: Descend twice, then create the rest of the chain.
        runner.call("put/2", () -> trie.put("shore", 2));
        runner.call("put/2", () -> trie.put("shy", 3));
        runner.call("get/1", () -> trie.get("shell"));
        runner.call("containsKey/1", () -> trie.containsKey("shore"));
        // "shy"'s 'y' is a childless non-key leaf once unset, so exactly one Prune fires; "sh" keeps
        // two children, so the cascade stops there. Removing "shore" then prunes 'e','r','o' in turn.
        runner.call("remove/1", () -> trie.remove("shy"));
        runner.call("remove/1", () -> trie.remove("shore"));
        // Absent key -> remove bails out before narrating the buffered walk; unions into the same
        // tally as the removes above, so it can only shrink the documented set if it fires something.
        runner.call("remove/1", () -> trie.remove("nope"));
        return runner;
    }

    /**
     * Direction one: every guide on disk is claimed by a case. A guide added without a scenario
     * behind it would otherwise ship unverified — the direction PR #48 already covered, restated
     * against the registry rather than against a hardcoded set that had to be edited by hand.
     */
    @Test
    void everyGuideFileHasACase() throws IOException {
        Set<String> claimed = cases().map(GuideCase::file).collect(Collectors.toSet());
        try (Stream<Path> files = Files.list(GUIDE_DIR)) {
            files.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".md"))
                    .forEach(n -> assertTrue(claimed.contains(n),
                            GUIDE_DIR.resolve(n) + " has no GuideCase — add one, with a scenario, "
                                    + "so the guide is checked against the code it describes"));
        }
    }

    /**
     * Direction two, and the hole PR #48 left open: a case may only name a guide that is really
     * there. Together with direction one this makes per-guide coverage drift unrepresentable: one
     * guide cannot go unverified while the others stay checked, because dropping its coverage means
     * dropping its case, and direction one then reports the orphaned file. (Deleting the single
     * parameterized driver below still drops all four guides' coverage at once — proving otherwise
     * would mean reflecting over {@code @Test} methods, which this design does not do.)
     *
     * <p>{@code isRegularFile}, never {@code exists}: a DIRECTORY named {@code trie.md} satisfies
     * {@code exists} and produced a real false green in {@code SiteIndexTest} on #49.
     *
     * <p>The emptiness check is not ceremony. Both directions iterate, so both pass over an empty
     * registry, agreeing perfectly about nothing.
     */
    @Test
    void everyCaseNamesARegularGuideFile() {
        var all = cases().toList();
        assertFalse(all.isEmpty(),
                "the case registry is empty — every other check in this class would pass vacuously");
        for (GuideCase c : all) {
            Path md = GUIDE_DIR.resolve(c.file());
            assertTrue(Files.isRegularFile(md),
                    md + " is named by a GuideCase but is not a regular file");
        }
    }
}
