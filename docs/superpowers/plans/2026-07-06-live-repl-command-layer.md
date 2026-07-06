# Live REPL Command Layer (HashMap) — Slice 2 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drive a live `TeachingHashMap` from a terminal REPL — `put 3 x`, `remove 3`, `get 3` — with every mutation rendering live in the browser, via a pure command interpreter that Slice 3's browser controls will reuse.

**Architecture:** A pure, server-agnostic `MapCommandInterpreter.execute(line, map) → CommandResult` holds all parsing and dispatch; it calls `map.put/remove/…` and the Slice-1 `MapLiveVisualizer` listener (attached by the demo) broadcasts the resulting events — so live rendering is a side-effect of the listener, not the interpreter's concern. A thin `LiveReplDemo` wires Slice 1's `LiveServer`, opens the browser, and pumps `System.in` through the interpreter via a testable read-loop seam.

**Tech Stack:** Java 21 (`release=21`, JDK 26 toolchain), Maven, JUnit 5 (Jupiter). JDK-only; reuses Slice 1's `LiveServer`/`MapLiveVisualizer`/`MapWebExporter` verbatim.

## Global Constraints

- **Zero new dependencies; no new transport.** Reuses Slice 1's `LiveServer` verbatim; JDK-only.
- **Java:** source/target `release=21`, JDK 26 toolchain.
- **The interpreter is PURE:** no I/O, no `System.out`, no reference to `LiveServer`/broadcasting; it mutates the map and returns a `CommandResult`. It must **never throw out of `execute`** — every bad input becomes an error message.
- **Reads are console-only:** `get`/`containsKey`/`size` return text; they emit no event, so the browser is intentionally unchanged. Do NOT add any probe/highlight for reads.
- **Keys `Integer`, values `String`.** `put`'s value is the rest of the line after the key, with interior spaces preserved.
- **"Intelligence in the tested layer, thin untested shell":** all logic in `MapCommandInterpreter` (exhaustively unit-tested); `LiveReplDemo`'s server/browser wiring is untested demo glue, but its read-eval-print loop is factored into a `runRepl(...)` seam that IS tested (mirrors the existing `VizDemo.run(PrintStream)` convention).
- **Testing framework:** JUnit 5 Jupiter; package-private `*Test` classes; static-imported assertions. Run with `mvn test`.
- **Git:** branch `feat/live-repl-command-layer` (already created); commit per task. Merge convention (later): `gh pr merge N --merge`, no `--delete-branch`.

---

### Task 1: `CommandResult` + `MapCommandInterpreter` (the pure core)

The whole command layer: a record for the result and a pure interpreter that parses a line, dispatches to the map, and formats the message. Exhaustively unit-tested, plus one test proving a mutation propagates to an attached `MapLiveVisualizer`.

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/repl/CommandResult.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/repl/MapCommandInterpreter.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/repl/MapCommandInterpreterTest.java`

**Interfaces:**
- Consumes: `TeachingHashMap<Integer,String>` (`put(K,V)→V`, `remove(Object)→V`, `get(Object)→V`, `containsKey(Object)→boolean`, `size()→int`, inherited `clear()`); `MapLiveVisualizer(Consumer<String>)` (Slice 1) for the integration test only.
- Produces:
  - `CommandResult` — `record CommandResult(String message, boolean quit)` with static factories `CommandResult.of(String)` (→ `{message, false}`) and `CommandResult.quitting(String)` (→ `{message, true}`).
  - `MapCommandInterpreter` — public no-arg constructor; `CommandResult execute(String line, TeachingHashMap<Integer,String> map)`; `String helpText()`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/gimlism/translucent/hashmap/repl/MapCommandInterpreterTest.java`:

```java
package com.gimlism.translucent.hashmap.repl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=MapCommandInterpreterTest`
Expected: FAIL — compile error, `CommandResult`/`MapCommandInterpreter` do not exist.

- [ ] **Step 3: Write `CommandResult`**

Create `src/main/java/com/gimlism/translucent/hashmap/repl/CommandResult.java`:

```java
package com.gimlism.translucent.hashmap.repl;

/**
 * The outcome of one command: the {@code message} to show the user, and whether the REPL should
 * {@code quit}. Returned by {@link MapCommandInterpreter#execute}; the front-end (terminal REPL
 * now, browser POST in a later slice) decides how to present the message.
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

- [ ] **Step 4: Write `MapCommandInterpreter`**

Create `src/main/java/com/gimlism/translucent/hashmap/repl/MapCommandInterpreter.java`:

```java
package com.gimlism.translucent.hashmap.repl;

import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import java.util.Locale;

/**
 * Parses one command line and applies it to a {@link TeachingHashMap}, returning a
 * {@link CommandResult}. Pure: it performs no I/O and holds no reference to the live server —
 * mutations it makes fire {@code MapEvent}s that any attached listener (e.g. {@code
 * MapLiveVisualizer}) broadcasts, so live rendering is a side-effect of the listener, not of this
 * class. {@link #execute} never throws: every malformed input becomes an error message.
 *
 * <p>Keys are {@code Integer}, values are {@code String} (the rest of the line after the key,
 * interior spaces preserved). This is the shared entry point a browser-controls front-end will
 * reuse in a later slice.
 */
public final class MapCommandInterpreter {

    /** Parse {@code line}, apply it to {@code map}, and return the result to show the user. */
    public CommandResult execute(String line, TeachingHashMap<Integer, String> map) {
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return CommandResult.of(""); // blank line: silent no-op
        }
        String[] parts = trimmed.split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "put": {
                String[] kv = rest.split("\\s+", 2);
                if (kv.length < 2) {
                    return CommandResult.of("usage: put <int-key> <value>");
                }
                Integer key = parseKey(kv[0]);
                if (key == null) {
                    return CommandResult.of("not an integer: '" + kv[0] + "'");
                }
                String old = map.put(key, kv[1]);
                return CommandResult.of(old == null
                        ? "put " + key + " = " + kv[1]
                        : "set " + key + " = " + kv[1] + " (was " + old + ")");
            }
            case "remove": {
                Integer key = parseKey(rest);
                if (key == null) {
                    return CommandResult.of(rest.isEmpty() ? "usage: remove <int-key>" : "not an integer: '" + rest + "'");
                }
                String old = map.remove(key);
                return CommandResult.of(old != null ? "removed " + key : key + " not found");
            }
            case "get": {
                Integer key = parseKey(rest);
                if (key == null) {
                    return CommandResult.of(rest.isEmpty() ? "usage: get <int-key>" : "not an integer: '" + rest + "'");
                }
                String v = map.get(key);
                return CommandResult.of("get " + key + " → " + (v != null ? v : "absent"));
            }
            case "containskey":
            case "contains": {
                Integer key = parseKey(rest);
                if (key == null) {
                    return CommandResult.of(rest.isEmpty() ? "usage: containsKey <int-key>" : "not an integer: '" + rest + "'");
                }
                return CommandResult.of("containsKey " + key + " → " + map.containsKey(key));
            }
            case "size":
                return CommandResult.of("size = " + map.size());
            case "clear": {
                int n = map.size();
                map.clear();
                return CommandResult.of("cleared (" + n + " entries removed)");
            }
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
    private static Integer parseKey(String token) {
        try {
            return Integer.valueOf(token.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** One-line overview plus the grammar, one line per command. */
    public String helpText() {
        return String.join("\n",
                "TeachingHashMap live REPL — type commands; mutations render live in the browser.",
                "  put <int> <value>   insert or update a key (value may contain spaces)",
                "  remove <int>        remove a key",
                "  get <int>           look up a key (prints the value; no viz change)",
                "  containsKey <int>   test membership (alias: contains)",
                "  size                number of entries",
                "  clear               remove all entries",
                "  help                show this help",
                "  quit                stop the server and exit (alias: exit)");
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q test -Dtest=MapCommandInterpreterTest`
Expected: PASS (all tests).

- [ ] **Step 6: Run the full suite**

Run: `mvn -q test`
Expected: PASS (no regressions).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/repl/CommandResult.java \
        src/main/java/com/gimlism/translucent/hashmap/repl/MapCommandInterpreter.java \
        src/test/java/com/gimlism/translucent/hashmap/repl/MapCommandInterpreterTest.java
git commit -m "feat(repl): MapCommandInterpreter — pure command layer for the live HashMap"
```

---

### Task 2: `LiveReplDemo` — terminal REPL front-end

The thin shell: wire Slice 1's live server + browser, then pump `System.in` through the interpreter. The read-eval-print loop is factored into a `runRepl(...)` seam that is unit-tested with canned input (no server, no real stdin); `main()`'s server/browser wiring is untested demo glue.

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/demo/LiveReplDemo.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/demo/LiveReplDemoTest.java`

**Interfaces:**
- Consumes: `LiveServer(String,String,int)` / `start()` / `port()` / `broadcast(String)` / `stop()` (Slice 1); `MapWebExporter.liveHtml()`; `MapLiveVisualizer(Consumer<String>)`; `BrowserLauncher.open(String)` (same package); `MapCommandInterpreter.execute(...)` + `CommandResult` (Task 1); `TeachingHashMap`.
- Produces: `LiveReplDemo.main(String[])`; package-private `static void runRepl(BufferedReader in, PrintStream out, TeachingHashMap<Integer,String> map, MapCommandInterpreter interpreter) throws IOException` (the tested loop seam).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/hashmap/demo/LiveReplDemoTest.java`:

```java
package com.gimlism.translucent.hashmap.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.repl.MapCommandInterpreter;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LiveReplDemoTest {

    private String runOn(String input, TeachingHashMap<Integer, String> map) throws IOException {
        var out = new ByteArrayOutputStream();
        LiveReplDemo.runRepl(new BufferedReader(new StringReader(input)),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                map, new MapCommandInterpreter());
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void loopExecutesEachLineAndStopsOnQuit() throws IOException {
        var map = new TeachingHashMap<Integer, String>();
        String output = runOn("put 1 a\nget 1\nquit\nput 2 b\n", map); // lines after quit must NOT run

        assertTrue(output.contains("put 1 = a"), output);
        assertTrue(output.contains("get 1 → a"), output);
        assertTrue(output.contains("bye"), output);
        assertEquals("a", map.get(1));
        assertFalse(map.containsKey(2), "processing stopped at quit");
    }

    @Test
    void loopEndsAtEofWithoutQuit() throws IOException {
        var map = new TeachingHashMap<Integer, String>();
        String output = runOn("put 2 b\n", map); // no quit — EOF ends the loop

        assertTrue(output.contains("put 2 = b"), output);
        assertEquals("b", map.get(2));
    }

    @Test
    void blankLinesProduceNoOutput() throws IOException {
        var map = new TeachingHashMap<Integer, String>();
        String output = runOn("\n   \nsize\n", map);
        assertEquals("size = 0" + System.lineSeparator(), output);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=LiveReplDemoTest`
Expected: FAIL — compile error, `LiveReplDemo` does not exist.

- [ ] **Step 3: Write `LiveReplDemo`**

Create `src/main/java/com/gimlism/translucent/hashmap/demo/LiveReplDemo.java`:

```java
package com.gimlism.translucent.hashmap.demo;

import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.repl.CommandResult;
import com.gimlism.translucent.hashmap.repl.MapCommandInterpreter;
import com.gimlism.translucent.hashmap.viz.MapLiveVisualizer;
import com.gimlism.translucent.hashmap.viz.MapWebExporter;
import com.gimlism.translucent.substrate.viz.LiveServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Interactive terminal REPL for a live {@link TeachingHashMap}: type commands (see {@code help})
 * and watch each mutation render live in the browser — no recompile per change. Reuses Slice 1's
 * {@link LiveServer} for the SSE stream. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.hashmap.demo.LiveReplDemo}
 * The map starts empty; the server stops when you type {@code quit} or send EOF (Ctrl-D).
 */
public class LiveReplDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        LiveServer server = new LiveServer(MapWebExporter.liveHtml(), "127.0.0.1", DEFAULT_PORT);
        server.start();
        String url = "http://localhost:" + server.port();

        var map = new TeachingHashMap<Integer, String>();
        map.addListener(new MapLiveVisualizer(server::broadcast));

        BrowserLauncher.open(url);
        System.out.println("Live REPL — serving at " + url);
        System.out.println("Type 'help' for commands, 'quit' to stop.");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            runRepl(in, System.out, map, new MapCommandInterpreter());
        } finally {
            server.stop();
        }
    }

    /**
     * Read-eval-print loop (test seam — no server or browser): read a line, execute it, print the
     * message, until a {@code quit} result or EOF. Blank-line results (empty message) print nothing.
     */
    static void runRepl(BufferedReader in, PrintStream out, TeachingHashMap<Integer, String> map,
            MapCommandInterpreter interpreter) throws IOException {
        String line;
        while ((line = in.readLine()) != null) { // EOF (Ctrl-D) ends the loop
            CommandResult r = interpreter.execute(line, map);
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

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=LiveReplDemoTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Run the full suite**

Run: `mvn -q test`
Expected: PASS.

- [ ] **Step 6: Manually verify the live REPL in a browser (thin-shell verification)**

Run: `mvn -q exec:java -Dexec.mainClass=com.gimlism.translucent.hashmap.demo.LiveReplDemo`
Then type, one per line: `help` (prints the grammar), `put 0 v0`, `put 8 v8`, `put 16 v16` (watch bucket 0 collide/treeify live in the browser), `get 8` (console prints `get 8 → v8`, browser unchanged), `remove 8`, `size`, `clear`, `quit` (server stops, process exits). Confirm the browser updates on each mutation and reads print to the console only.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/demo/LiveReplDemo.java \
        src/test/java/com/gimlism/translucent/hashmap/demo/LiveReplDemoTest.java
git commit -m "feat(repl): LiveReplDemo — terminal front-end driving the live HashMap"
```

---

## Notes for the implementer

- **The interpreter must not import anything from `substrate/viz` or reference `LiveServer`/broadcasting.** Its only dependency is `TeachingHashMap` (and the JDK). The live-rendering connection is made entirely by the demo attaching a `MapLiveVisualizer` listener to the map.
- **`split("\\s+", 2)`** is the key parsing idiom: on the whole line it separates the command word from the rest; on `put`'s rest it separates the key token from the value while preserving the value's interior spaces (limit 2 means only the first whitespace run splits).
- **`clear()` is inherited from `AbstractMap`** and removes entries via the entry-set iterator, so it emits a `Remove` event per entry — the browser will animate the map emptying out. That's intended; don't special-case it.
- **Do not unit-test `main()`** — the server/browser wiring is demo glue. The `runRepl` seam carries the loop logic and is the tested unit (mirrors `VizDemo.run`).
