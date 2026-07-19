# TreeSet live REPL command layer (Slice C) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a terminal REPL for a live `TeachingTreeSet<Integer>` — type commands, watch each mutation (and each comparison read) render live in the browser.

**Architecture:** A pure, server-agnostic `TreeSetCommandInterpreter.execute(line, set) → CommandResult` (never throws) plus a thin `TreeSetLiveReplDemo` that wires it to the already-merged Slice B transport (`LiveServer` + `TreeSetLiveVisualizer` + `TreeSetWebExporter.liveHtml()`). Mutations fire `SetEvent`s that the attached visualizer broadcasts, so live rendering is a listener side-effect, not the interpreter's job.

**Tech Stack:** Java 21, Maven, JUnit 5.

## Global Constraints

- Java 21 / Maven; test with `mvn test` (Eclipse/LSP errors are noise — `mvn` is the source of truth).
- `execute` **never throws** — every malformed input returns a `CommandResult` message.
- **Reuse `com.gimlism.translucent.substrate.repl.CommandResult`** (already shared by map/list/trie) — do NOT create a new one.
- Element type is `Integer`, natural ordering (matches every existing TreeSet demo).
- **Touch nothing else:** no change to core, events, substrate, `treeset/viz/*`, or `treeset-viz.html`. This keeps the snapshot-before-settled bug at zero surface.
- Verb matching is case-insensitive (`Locale.ROOT` lower-case); an element is one token — a trailing extra token is a usage error (reject-extras), not a space-bearing element.
- Commit message footer (every commit):
  ```
  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm
  ```
- Branch: `feat/treeset-live-repl-command-layer` (already created; the spec is committed on it).

## File Structure

- Create `src/main/java/com/gimlism/translucent/treeset/repl/TreeSetCommandInterpreter.java` — the pure command layer.
- Create `src/main/java/com/gimlism/translucent/treeset/repl/package-info.java` — package doc (both `substrate/repl` and the sibling repl packages carry one; match the convention).
- Create `src/test/java/com/gimlism/translucent/treeset/repl/TreeSetCommandInterpreterTest.java`.
- Create `src/main/java/com/gimlism/translucent/treeset/demo/TreeSetLiveReplDemo.java` — the demo wiring + `runRepl` seam.
- Create `src/test/java/com/gimlism/translucent/treeset/demo/TreeSetLiveReplDemoTest.java`.

---

### Task 1: `TreeSetCommandInterpreter`

**Files:**
- Create: `src/main/java/com/gimlism/translucent/treeset/repl/TreeSetCommandInterpreter.java`
- Create: `src/main/java/com/gimlism/translucent/treeset/repl/package-info.java`
- Test: `src/test/java/com/gimlism/translucent/treeset/repl/TreeSetCommandInterpreterTest.java`

**Interfaces:**
- Consumes: `com.gimlism.translucent.substrate.repl.CommandResult` (`of(String)`, `quitting(String)`, `message()`, `quit()`); `com.gimlism.translucent.treeset.core.TeachingTreeSet<Integer>` (`add`, `remove`, `contains`, `isEmpty`, `first`, `last`, `lower`, `floor`, `ceiling`, `higher`, `pollFirst`, `pollLast`, `size`, `addListener`); `com.gimlism.translucent.treeset.consumer.SetRecordingListener` (`events()`); `com.gimlism.translucent.treeset.events.Compare`.
- Produces: `TreeSetCommandInterpreter` with `public CommandResult execute(String line, TeachingTreeSet<Integer> set)` and `public String helpText()` — Task 2's demo consumes both.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/treeset/repl/TreeSetCommandInterpreterTest.java`:

```java
package com.gimlism.translucent.treeset.repl;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=TreeSetCommandInterpreterTest`
Expected: FAIL — compilation error, `TreeSetCommandInterpreter` does not exist.

- [ ] **Step 3: Write the package doc**

Create `src/main/java/com/gimlism/translucent/treeset/repl/package-info.java`:

```java
/**
 * The TreeSet's terminal REPL command layer: a pure interpreter that parses one command line and
 * applies it to a {@link com.gimlism.translucent.treeset.core.TeachingTreeSet}, returning a shared
 * {@link com.gimlism.translucent.substrate.repl.CommandResult}. Server-agnostic — the browser
 * controls slice reuses the same {@code execute} entry point.
 */
package com.gimlism.translucent.treeset.repl;
```

- [ ] **Step 4: Write the interpreter**

Create `src/main/java/com/gimlism/translucent/treeset/repl/TreeSetCommandInterpreter.java`:

```java
package com.gimlism.translucent.treeset.repl;

import com.gimlism.translucent.substrate.repl.CommandResult;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import java.util.Locale;

/**
 * Parses one command line and applies it to a {@link TeachingTreeSet}, returning a
 * {@link CommandResult}. Pure: it performs no I/O and holds no reference to the live server —
 * mutations it makes fire {@code SetEvent}s that any attached listener (e.g.
 * {@code TreeSetLiveVisualizer}) broadcasts, so live rendering is a side-effect of the listener,
 * not of this class. {@link #execute} never throws: every malformed input becomes an error message.
 *
 * <p>Elements are {@code Integer} — a single token, natural ordering; a set has no key/value, so
 * every verb parses exactly one integer (or takes no argument). Unlike the map/list/trie REPLs,
 * the comparison reads ({@code contains}, {@code lower}/{@code floor}/{@code ceiling}/{@code higher})
 * DO narrate: they emit {@code Compare} events as they walk the red-black tree, so the browser
 * animates the comparison cursor live. This is the shared entry point a browser-controls front-end
 * will reuse in a later slice.
 */
public final class TreeSetCommandInterpreter {

    /** Parse {@code line}, apply it to {@code set}, and return the result to show the user. */
    public CommandResult execute(String line, TeachingTreeSet<Integer> set) {
        if (line == null) {
            return CommandResult.of(""); // null (e.g. an empty POST body) — treat as a blank no-op
        }
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return CommandResult.of(""); // blank line: silent no-op
        }
        String[] parts = trimmed.split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "add": {
                Integer e = soleInt(rest);
                if (e == null) {
                    return usageOrParse(rest, "add");
                }
                return CommandResult.of(set.add(e) ? "added " + e : e + " already present");
            }
            case "remove": {
                Integer e = soleInt(rest);
                if (e == null) {
                    return usageOrParse(rest, "remove");
                }
                return CommandResult.of(set.remove(e) ? "removed " + e : e + " not found");
            }
            case "contains": {
                Integer e = soleInt(rest);
                if (e == null) {
                    return usageOrParse(rest, "contains");
                }
                return CommandResult.of("contains " + e + " → " + set.contains(e));
            }
            case "lower":
            case "floor":
            case "ceiling":
            case "higher":
                return bound(set, rest, cmd);
            case "first":
                if (!rest.isEmpty()) {
                    return CommandResult.of("usage: first");
                }
                return CommandResult.of("first → " + (set.isEmpty() ? "(empty)" : set.first()));
            case "last":
                if (!rest.isEmpty()) {
                    return CommandResult.of("usage: last");
                }
                return CommandResult.of("last → " + (set.isEmpty() ? "(empty)" : set.last()));
            case "pollfirst": {
                if (!rest.isEmpty()) {
                    return CommandResult.of("usage: pollFirst");
                }
                Integer e = set.pollFirst();
                return CommandResult.of("pollFirst → " + (e != null ? e : "(empty)"));
            }
            case "polllast": {
                if (!rest.isEmpty()) {
                    return CommandResult.of("usage: pollLast");
                }
                Integer e = set.pollLast();
                return CommandResult.of("pollLast → " + (e != null ? e : "(empty)"));
            }
            case "size":
                if (!rest.isEmpty()) {
                    return CommandResult.of("usage: size");
                }
                return CommandResult.of("size = " + set.size());
            case "help":
                return CommandResult.of(helpText());
            case "quit":
            case "exit":
                return CommandResult.quitting("bye");
            default:
                return CommandResult.of("unknown command: '" + cmd + "' (type 'help')");
        }
    }

    /** Apply the navigation query {@code verb} (lower/floor/ceiling/higher) to {@code set}. */
    private static CommandResult bound(TeachingTreeSet<Integer> set, String rest, String verb) {
        Integer e = soleInt(rest);
        if (e == null) {
            return usageOrParse(rest, verb);
        }
        Integer r = switch (verb) {
            case "lower" -> set.lower(e);
            case "floor" -> set.floor(e);
            case "ceiling" -> set.ceiling(e);
            default -> set.higher(e);
        };
        return CommandResult.of(verb + " " + e + " → " + (r != null ? r : "none"));
    }

    /**
     * The single {@code Integer} in {@code rest}, or {@code null} if {@code rest} is empty, carries
     * more than one whitespace-separated token, or does not parse as an int. An element is one
     * token, so a trailing token is malformed input, not a space-bearing element.
     */
    private static Integer soleInt(String rest) {
        if (rest.isEmpty()) {
            return null;
        }
        String[] tokens = rest.split("\\s+", 2);
        if (tokens.length > 1) {
            return null; // trailing extra token
        }
        try {
            return Integer.valueOf(tokens[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The specific message for a single-int verb whose {@code soleInt} came back {@code null}. */
    private static CommandResult usageOrParse(String rest, String verb) {
        if (rest.isEmpty() || rest.split("\\s+", 2).length > 1) {
            return CommandResult.of("usage: " + verb + " <int>");
        }
        return CommandResult.of("not an integer: '" + rest + "'");
    }

    /** One-line overview plus the grammar, one line per command. */
    public String helpText() {
        return String.join("\n",
                "TeachingTreeSet live REPL — type commands; mutations render live in the browser.",
                "  add <int>        insert an element (integer)",
                "  remove <int>     remove an element",
                "  contains <int>   test membership (the comparison walk animates live)",
                "  first            smallest element (no viz change)",
                "  last             largest element (no viz change)",
                "  lower <int>      greatest element < arg (walk animates live)",
                "  floor <int>      greatest element ≤ arg (walk animates live)",
                "  ceiling <int>    least element ≥ arg (walk animates live)",
                "  higher <int>     least element > arg (walk animates live)",
                "  pollFirst        remove and return the smallest element",
                "  pollLast         remove and return the largest element",
                "  size             number of elements (no viz change)",
                "  help             show this help",
                "  quit             end the session (alias: exit)");
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q test -Dtest=TreeSetCommandInterpreterTest`
Expected: PASS (all tests green).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/treeset/repl/ \
        src/test/java/com/gimlism/translucent/treeset/repl/TreeSetCommandInterpreterTest.java
git commit -m "feat(treeset): TreeSetCommandInterpreter — pure REPL command layer

$(printf 'Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm')"
```

---

### Task 2: `TreeSetLiveReplDemo`

**Files:**
- Create: `src/main/java/com/gimlism/translucent/treeset/demo/TreeSetLiveReplDemo.java`
- Test: `src/test/java/com/gimlism/translucent/treeset/demo/TreeSetLiveReplDemoTest.java`

**Interfaces:**
- Consumes: `TreeSetCommandInterpreter` (Task 1); `com.gimlism.translucent.substrate.viz.LiveServer`, `.BrowserLauncher`; `com.gimlism.translucent.treeset.viz.TreeSetLiveVisualizer`, `.TreeSetWebExporter`; `com.gimlism.translucent.treeset.core.TeachingTreeSet`; `com.gimlism.translucent.substrate.repl.CommandResult`.
- Produces: `TreeSetLiveReplDemo` with `public static void main(String[])` and a package-private static seam `runRepl(BufferedReader in, PrintStream out, TeachingTreeSet<Integer> set, TreeSetCommandInterpreter interpreter)`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/treeset/demo/TreeSetLiveReplDemoTest.java`:

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=TreeSetLiveReplDemoTest`
Expected: FAIL — compilation error, `TreeSetLiveReplDemo` does not exist.

- [ ] **Step 3: Write the demo**

Create `src/main/java/com/gimlism/translucent/treeset/demo/TreeSetLiveReplDemo.java`:

```java
package com.gimlism.translucent.treeset.demo;

import com.gimlism.translucent.substrate.repl.CommandResult;
import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.repl.TreeSetCommandInterpreter;
import com.gimlism.translucent.treeset.viz.TreeSetLiveVisualizer;
import com.gimlism.translucent.treeset.viz.TreeSetWebExporter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Interactive terminal REPL for a live {@link TeachingTreeSet}: type commands (see {@code help}) and
 * watch each mutation render live in the browser — and, because the set narrates comparison reads,
 * watch a {@code contains}/{@code floor} walk animate the comparison cursor live. No recompile per
 * change. Reuses Slice B's {@link LiveServer} for the SSE stream. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.treeset.demo.TreeSetLiveReplDemo}
 * The set starts empty; the server stops when you type {@code quit} or send EOF (Ctrl-D).
 */
public class TreeSetLiveReplDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        LiveServer server = new LiveServer(TreeSetWebExporter.liveHtml(), "127.0.0.1", DEFAULT_PORT);
        server.start();
        String url = "http://localhost:" + server.port();

        var set = new TeachingTreeSet<Integer>();
        set.addListener(new TreeSetLiveVisualizer(server::broadcast));

        BrowserLauncher.open(url);
        System.out.println("Live REPL — serving at " + url);
        System.out.println("Type 'help' for commands, 'quit' to stop.");

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            runRepl(in, System.out, set, new TreeSetCommandInterpreter());
        } finally {
            server.stop();
        }
    }

    /**
     * Read-eval-print loop (test seam — no server or browser): read a line, execute it, print the
     * message, until a {@code quit} result or EOF. Blank-line results (empty message) print nothing.
     */
    static void runRepl(BufferedReader in, PrintStream out, TeachingTreeSet<Integer> set,
            TreeSetCommandInterpreter interpreter) throws IOException {
        String line;
        while ((line = in.readLine()) != null) { // EOF (Ctrl-D) ends the loop
            CommandResult r = interpreter.execute(line, set);
            if (!r.message().isEmpty()) {
                out.println(r.message());
            }
            if (r.quit()) {
                return;
            }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q test -Dtest=TreeSetLiveReplDemoTest`
Expected: PASS.

- [ ] **Step 5: Run the full suite (no regressions)**

Run: `mvn -q test`
Expected: BUILD SUCCESS — the previous 444 tests plus the new interpreter and demo tests, all green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/treeset/demo/TreeSetLiveReplDemo.java \
        src/test/java/com/gimlism/translucent/treeset/demo/TreeSetLiveReplDemoTest.java
git commit -m "feat(treeset): TreeSetLiveReplDemo — terminal REPL over the live SSE mirror

$(printf 'Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm')"
```

---

## After the tasks

- Whole-branch opus review (verify core/events/substrate/viz are byte-unchanged — the snapshot-before-settled bug must have zero surface).
- Optional controller browser smoke test: `mvn process-classes`, run `TreeSetLiveReplDemo` against `target/classes`, pipe timed commands, confirm a `contains`/`floor` walk animates in Chrome. Verification, not an automated test.
- Open PR → Copilot triage → `gh pr merge N --merge` (NOT squash, NOT `--delete-branch`) on user go-ahead.
- Then Slice D (browser controls) completes the TreeSet's 4-slice arc.
```

## Self-Review

**1. Spec coverage:**
- Grammar table (add/remove/contains/first/last/lower/floor/ceiling/higher/poll*/size/help/quit) → Task 1 interpreter + tests. ✓
- Never-throws + empty-set `first`/`last` guard → `isEmpty()` branch + `firstAndLastOnEmptySetSayEmpty` test. ✓
- Reject-extras + non-int + usage errors → `soleInt`/`usageOrParse` + error-path tests. ✓
- Reads-narrate divergence → `comparisonReadsNarrateWithCompareFrames` / `nonComparingVerbsDoNotNarrate`. ✓
- Reuse shared `CommandResult`, touch nothing else → imports only; no core/events/viz files in any task. ✓
- Demo wiring + `runRepl` seam → Task 2. ✓
- Out-of-scope (views, `clear`, Slice D) → correctly absent from tasks. ✓

**2. Placeholder scan:** No TBD/TODO; every code step shows complete code; every run step shows the exact command and expected result. ✓

**3. Type consistency:** `execute(String, TeachingTreeSet<Integer>) → CommandResult` and `helpText() → String` are used identically in Task 2's demo and both test files; `runRepl(BufferedReader, PrintStream, TeachingTreeSet<Integer>, TreeSetCommandInterpreter)` matches between the demo and its test; `soleInt`/`usageOrParse`/`bound` are all defined and used within Task 1. ✓
