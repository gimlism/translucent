# ArrayList Live REPL Command Layer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a terminal REPL for a live `TeachingArrayList<String>` — type commands, watch each mutation render live in the browser.

**Architecture:** A pure, server-agnostic `ListCommandInterpreter.execute(line, list)` (mutations fire `ListEvent`s that the already-merged `ListLiveVisualizer` listener broadcasts) plus a thin `ListLiveReplDemo` wiring it to Slice B's `LiveServer` transport. Mirrors HashMap PR #19; no core/events/substrate changes, no new deps.

**Tech Stack:** Java 21 (build on JDK 26, `maven.compiler.release=21`), Maven, JUnit 5.

## Global Constraints

- Java 21 language level; build/test with `mvn` (source of truth — ignore stale Eclipse LSP diagnostics).
- No new dependencies; reuse Slice B's `LiveServer`, `ListLiveVisualizer`, `ListWebExporter` verbatim. Core/events/substrate untouched.
- Element type is `String`; indices are `int`. `execute` **never throws** — every malformed input becomes a message.
- Values are rest-of-line (interior spaces preserved) and are **quoted** in messages so spaces/empty strings are visible.
- Grammar (user-approved): `add`/`insert`/`set`/`remove`/`get`/`size`/`help`/`quit`(alias `exit`). No `clear`, no `contains` (the list API has neither).
- Commit convention: end messages with `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

## File Structure

- Create `src/main/java/com/gimlism/translucent/arraylist/repl/CommandResult.java` — the `(message, quit)` record (verbatim copy of the map's; dedup deferred to trie Slice C).
- Create `src/main/java/com/gimlism/translucent/arraylist/repl/ListCommandInterpreter.java` — the pure interpreter.
- Create `src/test/java/com/gimlism/translucent/arraylist/repl/ListCommandInterpreterTest.java`.
- Create `src/main/java/com/gimlism/translucent/arraylist/demo/ListLiveReplDemo.java` — `main` + `runRepl` test seam.
- Create `src/test/java/com/gimlism/translucent/arraylist/demo/ListLiveReplDemoTest.java`.

Two tasks: (1) `CommandResult` + `ListCommandInterpreter` + test; (2) `ListLiveReplDemo` + `runRepl` seam test.

---

### Task 1: ListCommandInterpreter (pure command layer)

**Files:**
- Create: `src/main/java/com/gimlism/translucent/arraylist/repl/CommandResult.java`
- Create: `src/main/java/com/gimlism/translucent/arraylist/repl/ListCommandInterpreter.java`
- Test: `src/test/java/com/gimlism/translucent/arraylist/repl/ListCommandInterpreterTest.java`

**Interfaces:**
- Consumes: `com.gimlism.translucent.arraylist.core.TeachingArrayList<String>` (`add(E)`, `add(int,E)`, `set(int,E)→E`, `remove(int)→E`, `get(int)→E`, `size()→int`; index checks throw `IndexOutOfBoundsException`); `com.gimlism.translucent.arraylist.viz.ListLiveVisualizer(Consumer<String>)` (test only).
- Produces: `ListCommandInterpreter.execute(String line, TeachingArrayList<String> list) → CommandResult`; `ListCommandInterpreter.helpText() → String`; `CommandResult` record with `message()`, `quit()`, `CommandResult.of(String)`, `CommandResult.quitting(String)`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/arraylist/repl/ListCommandInterpreterTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test-compile`
Expected: FAIL — `ListCommandInterpreter` / `CommandResult` do not exist (compile error).

- [ ] **Step 3: Write CommandResult**

Create `src/main/java/com/gimlism/translucent/arraylist/repl/CommandResult.java`:

```java
package com.gimlism.translucent.arraylist.repl;

/**
 * The outcome of one command: the {@code message} to show the user, and whether the REPL should
 * {@code quit}. Returned by {@link ListCommandInterpreter#execute}; the front-end (terminal REPL
 * now, browser POST in a later slice) decides how to present the message.
 *
 * <p>A verbatim copy of the HashMap's {@code CommandResult}. The dedup into a shared
 * {@code substrate.repl} home is deferred to the trie's REPL slice (rule of three).
 */
public record CommandResult(String message, boolean quit) {

    /** A normal result — show {@code message}, keep going. */
    public static CommandResult of(String message) {
        return new CommandResult(message, false);
    }

    /** A terminating result — show {@code message}, then quit. */
    public static CommandResult quitting(String message) {
        return new CommandResult(message, true);
    }
}
```

- [ ] **Step 4: Write ListCommandInterpreter**

Create `src/main/java/com/gimlism/translucent/arraylist/repl/ListCommandInterpreter.java`:

```java
package com.gimlism.translucent.arraylist.repl;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import java.util.Locale;

/**
 * Parses one command line and applies it to a {@link TeachingArrayList}, returning a
 * {@link CommandResult}. Pure: it performs no I/O and holds no reference to the live server —
 * mutations it makes fire {@code ListEvent}s that any attached listener (e.g. {@code
 * ListLiveVisualizer}) broadcasts, so live rendering is a side-effect of the listener, not of
 * this class. {@link #execute} never throws: every malformed input becomes an error message.
 *
 * <p>Elements are {@code String} (the rest of the line after the verb/index, interior spaces
 * preserved), indices are {@code int}. The grammar reflects the list's real API — two insertion
 * ops as distinct verbs ({@code add} appends, {@code insert} places at an index), no
 * {@code clear}/{@code contains} (the structure has neither). This is the shared entry point a
 * browser-controls front-end will reuse in a later slice.
 */
public final class ListCommandInterpreter {

    /** Parse {@code line}, apply it to {@code list}, and return the result to show the user. */
    public CommandResult execute(String line, TeachingArrayList<String> list) {
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
                if (rest.isEmpty()) {
                    return CommandResult.of("usage: add <value>");
                }
                list.add(rest);
                return CommandResult.of("appended \"" + rest + "\" at " + (list.size() - 1));
            }
            case "insert": {
                String[] iv = rest.split("\\s+", 2);
                if (iv.length < 2) {
                    return CommandResult.of("usage: insert <index> <value>");
                }
                Integer index = parseIndex(iv[0]);
                if (index == null) {
                    return CommandResult.of("not an integer: '" + iv[0] + "'");
                }
                try {
                    list.add(index, iv[1]);
                } catch (IndexOutOfBoundsException e) {
                    return outOfRange(index, list);
                }
                return CommandResult.of("inserted \"" + iv[1] + "\" at " + index);
            }
            case "set": {
                String[] iv = rest.split("\\s+", 2);
                if (iv.length < 2) {
                    return CommandResult.of("usage: set <index> <value>");
                }
                Integer index = parseIndex(iv[0]);
                if (index == null) {
                    return CommandResult.of("not an integer: '" + iv[0] + "'");
                }
                try {
                    String old = list.set(index, iv[1]);
                    return CommandResult.of("set " + index + " = \"" + iv[1] + "\" (was \"" + old + "\")");
                } catch (IndexOutOfBoundsException e) {
                    return outOfRange(index, list);
                }
            }
            case "remove": {
                if (rest.isEmpty()) {
                    return CommandResult.of("usage: remove <index>");
                }
                Integer index = parseIndex(rest);
                if (index == null) {
                    return CommandResult.of("not an integer: '" + rest + "'");
                }
                try {
                    String old = list.remove((int) index);
                    return CommandResult.of("removed \"" + old + "\" at " + index);
                } catch (IndexOutOfBoundsException e) {
                    return outOfRange(index, list);
                }
            }
            case "get": {
                if (rest.isEmpty()) {
                    return CommandResult.of("usage: get <index>");
                }
                Integer index = parseIndex(rest);
                if (index == null) {
                    return CommandResult.of("not an integer: '" + rest + "'");
                }
                try {
                    return CommandResult.of("get " + index + " → \"" + list.get(index) + "\"");
                } catch (IndexOutOfBoundsException e) {
                    return outOfRange(index, list);
                }
            }
            case "size":
                return CommandResult.of("size = " + list.size());
            case "help":
                return CommandResult.of(helpText());
            case "quit":
            case "exit":
                return CommandResult.quitting("bye");
            default:
                return CommandResult.of("unknown command: '" + cmd + "' (type 'help')");
        }
    }

    /** {@code Integer} value of {@code token}, or {@code null} if it isn't a valid int. */
    private static Integer parseIndex(String token) {
        try {
            return Integer.valueOf(token.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Uniform out-of-range message including the offending index and the current size. */
    private static CommandResult outOfRange(int index, TeachingArrayList<String> list) {
        return CommandResult.of("index out of range: " + index + " (size " + list.size() + ")");
    }

    /** One-line overview plus the grammar, one line per command. */
    public String helpText() {
        return String.join("\n",
                "TeachingArrayList live REPL — type commands; mutations render live in the browser.",
                "  add <value>             append a value to the end (value may contain spaces)",
                "  insert <index> <value>  insert a value, shifting survivors right",
                "  set <index> <value>     overwrite the value at an index",
                "  remove <index>          remove the value at an index, shifting survivors left",
                "  get <index>             look up a value (prints it; no viz change)",
                "  size                    number of elements",
                "  help                    show this help",
                "  quit                    stop the server and exit (alias: exit)");
    }
}
```

- [ ] **Step 5: Run the tests and make sure they pass**

Run: `mvn -q test -Dtest=ListCommandInterpreterTest`
Expected: PASS (all tests green).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/arraylist/repl/ src/test/java/com/gimlism/translucent/arraylist/repl/
git commit -m "feat(repl): ListCommandInterpreter — pure ArrayList command layer

Mirrors MapCommandInterpreter. Distinct verbs add/insert/set/remove/get/
size/help/quit; execute never throws (bad-int, out-of-range, missing args,
unknown all become messages). Values quoted to surface spaces/empties.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: ListLiveReplDemo (terminal front-end wiring)

**Files:**
- Create: `src/main/java/com/gimlism/translucent/arraylist/demo/ListLiveReplDemo.java`
- Test: `src/test/java/com/gimlism/translucent/arraylist/demo/ListLiveReplDemoTest.java`

**Interfaces:**
- Consumes: `ListCommandInterpreter.execute` / `CommandResult` (Task 1); `com.gimlism.translucent.substrate.viz.LiveServer(String html, String host, int port)` with `start()`, `stop()`, `int port()`, `void broadcast(String)`; `com.gimlism.translucent.arraylist.viz.ListWebExporter.liveHtml() → String`; `com.gimlism.translucent.arraylist.viz.ListLiveVisualizer(Consumer<String>)`; `com.gimlism.translucent.substrate.viz.BrowserLauncher.open(String url)`.
- Produces: `ListLiveReplDemo.main(String[])`; package-private test seam `static void runRepl(BufferedReader in, PrintStream out, TeachingArrayList<String> list, ListCommandInterpreter interpreter) throws IOException`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/arraylist/demo/ListLiveReplDemoTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test-compile`
Expected: FAIL — `ListLiveReplDemo` does not exist (compile error).

- [ ] **Step 3: Write ListLiveReplDemo**

Create `src/main/java/com/gimlism/translucent/arraylist/demo/ListLiveReplDemo.java`:

```java
package com.gimlism.translucent.arraylist.demo;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.repl.CommandResult;
import com.gimlism.translucent.arraylist.repl.ListCommandInterpreter;
import com.gimlism.translucent.arraylist.viz.ListLiveVisualizer;
import com.gimlism.translucent.arraylist.viz.ListWebExporter;
import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.LiveServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Interactive terminal REPL for a live {@link TeachingArrayList}: type commands (see {@code help})
 * and watch each mutation render live in the browser — no recompile per change. Reuses Slice B's
 * {@link LiveServer} for the SSE stream. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListLiveReplDemo}
 * The list starts empty; the server stops when you type {@code quit} or send EOF (Ctrl-D).
 */
public class ListLiveReplDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        LiveServer server = new LiveServer(ListWebExporter.liveHtml(), "127.0.0.1", DEFAULT_PORT);
        server.start();
        String url = "http://localhost:" + server.port();

        var list = new TeachingArrayList<String>();
        list.addListener(new ListLiveVisualizer(server::broadcast));

        BrowserLauncher.open(url);
        System.out.println("Live REPL — serving at " + url);
        System.out.println("Type 'help' for commands, 'quit' to stop.");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            runRepl(in, System.out, list, new ListCommandInterpreter());
        } finally {
            server.stop();
        }
    }

    /**
     * Read-eval-print loop (test seam — no server or browser): read a line, execute it, print the
     * message, until a {@code quit} result or EOF. Blank-line results (empty message) print nothing.
     */
    static void runRepl(BufferedReader in, PrintStream out, TeachingArrayList<String> list,
            ListCommandInterpreter interpreter) throws IOException {
        String line;
        while ((line = in.readLine()) != null) { // EOF (Ctrl-D) ends the loop
            CommandResult r = interpreter.execute(line, list);
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

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `mvn -q test -Dtest=ListLiveReplDemoTest`
Expected: PASS (all tests green).

- [ ] **Step 5: Run the full suite**

Run: `mvn -q test`
Expected: PASS — all prior tests plus the new ones (the suite was 290 before this slice; expect 290 + the new test methods).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/arraylist/demo/ListLiveReplDemo.java src/test/java/com/gimlism/translucent/arraylist/demo/ListLiveReplDemoTest.java
git commit -m "feat(demo): ListLiveReplDemo — terminal REPL over Slice B live transport

Wires ListCommandInterpreter to LiveServer + ListLiveVisualizer +
ListWebExporter.liveHtml(). runRepl is a server/browser-free test seam.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

- **Spec coverage:** CommandResult (Task 1 Step 3); ListCommandInterpreter grammar incl. distinct verbs, bounds via caught `IndexOutOfBoundsException`, never-throws, quoted values, read-no-frame (Task 1); ListLiveReplDemo + runRepl seam reusing Slice B transport (Task 2). No `clear`/`contains` — absent by design. All spec sections mapped.
- **Placeholder scan:** none — every step has full code/commands.
- **Type consistency:** `execute(String, TeachingArrayList<String>) → CommandResult`, `helpText() → String`, `runRepl(BufferedReader, PrintStream, TeachingArrayList<String>, ListCommandInterpreter)` used identically across tasks and tests. `remove((int) index)` cast avoids the `remove(Object)` overload trap. `list.set` returns the old element (used in the `set` message).

## Post-implementation (controller, after both tasks)

- Whole-branch review (opus) per project workflow.
- Optional live smoke test per the recurring recipe: `mvn -q process-classes`, run `ListLiveReplDemo` headless against `target/classes`, pipe timed commands, connect via Chrome MCP to `http://localhost:7070`, confirm live frames + zero console errors. (Verification, not an automated test; the live JS is unchanged from Slice B.)
- PR via `gh`, Copilot triage, then `gh pr merge N --merge` (no `--delete-branch`) on user go-ahead.
