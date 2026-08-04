# Trie live REPL command layer (Slice C) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a terminal REPL for a live `RadixTrie<Integer>` — type commands, watch each mutation render live in the browser — and consolidate the twice-copied `CommandResult` record into a shared `substrate/repl` home (rule of three).

**Architecture:** A pure, server-agnostic `TrieCommandInterpreter.execute(line, trie) → CommandResult` (never throws; mutations fire `TrieEvent`s that the already-merged `TrieLiveVisualizer` broadcasts), plus a thin `TrieLiveReplDemo` that wires it to the Slice B live transport (`LiveServer` + `TrieWebExporter.liveHtml()`). Task 1 is a pure refactor moving `CommandResult` to `substrate/repl`, gated on the existing suite still passing before any trie code is written.

**Tech Stack:** Java 22, JUnit 5, Maven. No new dependencies.

## Global Constraints

- Java package root: `com.gimlism.translucent`.
- **`execute` never throws** — every malformed input returns an error `CommandResult`, never an exception.
- The interpreter is **pure**: no I/O, no reference to the server. Live rendering is a side-effect of an attached listener, not of the interpreter.
- **Do NOT touch** `core` / `events` / `LiveServer` / `JsonWriter` / `TrieLiveVisualizer` / `TrieWebExporter` / `WebVizTemplate` / any `*.html` or live JS. Slice B's transport is reused verbatim; this keeps the snapshot-before-settled bug at zero new surface.
- Keys are `String` (first token, no spaces); values are `Integer`. Only `put`'s value is parsed as an int — keys and the `get`/`remove`/`containsKey` arguments are raw tokens (no `parseKey`).
- `keysWithPrefix` with no argument is **not** an error — the empty prefix is passed as `""`, which lists all keys.
- Commit after each task. Commit messages end with the two trailer lines used across this repo (`Co-Authored-By:` and `Claude-Session:` — copy them from a recent commit).
- Build command: `mvn -q test` (full suite). Single-class: `mvn -q -Dtest=ClassName test`.

---

## File Structure

- **Create** `src/main/java/com/gimlism/translucent/substrate/repl/CommandResult.java` — shared result record (moved from the two `*/repl` copies).
- **Create** `src/main/java/com/gimlism/translucent/substrate/repl/package-info.java` — package doc, matching `substrate/events` and `substrate/viz`.
- **Delete** `src/main/java/com/gimlism/translucent/hashmap/repl/CommandResult.java` and `src/main/java/com/gimlism/translucent/arraylist/repl/CommandResult.java`.
- **Modify** four consumers' imports: `hashmap/repl/MapCommandInterpreter`, `hashmap/demo/LiveReplDemo`, `arraylist/repl/ListCommandInterpreter`, `arraylist/demo/ListLiveReplDemo`.
- **Modify** one test import: `hashmap/repl/MapCommandInterpreterTest` (`arraylist/repl/ListCommandInterpreterTest` needs no change — verified below).
- **Create** `src/main/java/com/gimlism/translucent/trie/repl/TrieCommandInterpreter.java` — the command layer.
- **Create** `src/test/java/com/gimlism/translucent/trie/repl/TrieCommandInterpreterTest.java`.
- **Create** `src/main/java/com/gimlism/translucent/trie/demo/TrieLiveReplDemo.java` — the demo + `runRepl` seam.
- **Create** `src/test/java/com/gimlism/translucent/trie/demo/TrieLiveReplDemoTest.java`.

---

## Task 1: Consolidate `CommandResult` into `substrate/repl` (pure move, no behaviour change)

**Files:**
- Create: `src/main/java/com/gimlism/translucent/substrate/repl/CommandResult.java`
- Create: `src/main/java/com/gimlism/translucent/substrate/repl/package-info.java`
- Delete: `src/main/java/com/gimlism/translucent/hashmap/repl/CommandResult.java`
- Delete: `src/main/java/com/gimlism/translucent/arraylist/repl/CommandResult.java`
- Modify: `src/main/java/com/gimlism/translucent/hashmap/repl/MapCommandInterpreter.java` (add import)
- Modify: `src/main/java/com/gimlism/translucent/arraylist/repl/ListCommandInterpreter.java` (add import)
- Modify: `src/main/java/com/gimlism/translucent/hashmap/demo/LiveReplDemo.java` (change import, line 4)
- Modify: `src/main/java/com/gimlism/translucent/arraylist/demo/ListLiveReplDemo.java` (change import, line 4)
- Modify: `src/test/java/com/gimlism/translucent/hashmap/repl/MapCommandInterpreterTest.java` (add import)

**Interfaces:**
- Produces: `com.gimlism.translucent.substrate.repl.CommandResult` — `record CommandResult(String message, boolean quit)` with static `CommandResult of(String message)` (quit=false) and `CommandResult quitting(String message)` (quit=true). Consumed by all three interpreters (Tasks 2 onward and the two existing REPLs).

This task adds **no new tests**. It is a pure relocation; correctness is proven by the existing 333-test suite still passing unchanged.

- [ ] **Step 1: Create the shared record**

Create `src/main/java/com/gimlism/translucent/substrate/repl/CommandResult.java`:

```java
package com.gimlism.translucent.substrate.repl;

/**
 * The outcome of one REPL command: the {@code message} to show the user, and whether the REPL
 * should {@code quit}. The shared result type for every teaching structure's command interpreter
 * (HashMap, ArrayList, Trie); the front-end (terminal REPL now, browser POST in a later slice)
 * decides how to present the message.
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

- [ ] **Step 2: Create the package doc**

Create `src/main/java/com/gimlism/translucent/substrate/repl/package-info.java`:

```java
/**
 * Structure-agnostic REPL plumbing shared by every teaching structure's command interpreter.
 * Currently the {@link com.gimlism.translucent.substrate.repl.CommandResult} record returned by
 * each {@code *CommandInterpreter.execute}.
 */
package com.gimlism.translucent.substrate.repl;
```

- [ ] **Step 3: Delete the two copies**

```bash
git rm src/main/java/com/gimlism/translucent/hashmap/repl/CommandResult.java \
       src/main/java/com/gimlism/translucent/arraylist/repl/CommandResult.java
```

- [ ] **Step 4: Repoint the four main-source consumers**

In `src/main/java/com/gimlism/translucent/hashmap/repl/MapCommandInterpreter.java`, add this import (the file is in package `hashmap.repl`, so it referenced `CommandResult` with no import before). Place it with the existing imports, before `import java.util.Locale;`:

```java
import com.gimlism.translucent.substrate.repl.CommandResult;
```

In `src/main/java/com/gimlism/translucent/arraylist/repl/ListCommandInterpreter.java`, add the same import, before `import java.util.Locale;`:

```java
import com.gimlism.translucent.substrate.repl.CommandResult;
```

In `src/main/java/com/gimlism/translucent/hashmap/demo/LiveReplDemo.java`, change the existing line 4 import:

```java
// from:
import com.gimlism.translucent.hashmap.repl.CommandResult;
// to:
import com.gimlism.translucent.substrate.repl.CommandResult;
```

In `src/main/java/com/gimlism/translucent/arraylist/demo/ListLiveReplDemo.java`, change the existing line 4 import:

```java
// from:
import com.gimlism.translucent.arraylist.repl.CommandResult;
// to:
import com.gimlism.translucent.substrate.repl.CommandResult;
```

- [ ] **Step 5: Repoint the one affected test**

`MapCommandInterpreterTest` (package `hashmap.repl`) references `CommandResult` by simple name (e.g. `CommandResult r = interp.execute(...)`), so after the move it needs the import. In `src/test/java/com/gimlism/translucent/hashmap/repl/MapCommandInterpreterTest.java`, add after the existing `import com.gimlism.translucent.hashmap.viz.MapLiveVisualizer;` (line 8):

```java
import com.gimlism.translucent.substrate.repl.CommandResult;
```

**Do not modify `ListCommandInterpreterTest`** — it references the result only fluently (`interp.execute(line, list).message()` / `.quit()`) and never names `CommandResult`, so it needs no import. Confirm with:

```bash
grep -c CommandResult src/test/java/com/gimlism/translucent/arraylist/repl/ListCommandInterpreterTest.java
```
Expected: `0`

- [ ] **Step 6: Run the full suite — the regression gate**

Run: `mvn -q test`
Expected: BUILD SUCCESS, **333 tests, 0 failures** (unchanged from before this task). If the count or pass state differs, a consumer was missed — fix the import before proceeding.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor(repl): hoist CommandResult to substrate/repl (rule of three)

Trie Slice C is the third consumer of the (message, quit) result record;
consolidate the two verbatim copies (hashmap/repl, arraylist/repl) into a
shared substrate/repl home. Pure move — 333 tests pass unchanged.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm"
```

---

## Task 2: `TrieCommandInterpreter` + tests

**Files:**
- Create: `src/main/java/com/gimlism/translucent/trie/repl/TrieCommandInterpreter.java`
- Test: `src/test/java/com/gimlism/translucent/trie/repl/TrieCommandInterpreterTest.java`

**Interfaces:**
- Consumes: `com.gimlism.translucent.substrate.repl.CommandResult` (Task 1); `com.gimlism.translucent.trie.core.RadixTrie<V>` — public API `V put(String, V)`, `V get(Object)`, `V remove(Object)`, `boolean containsKey(Object)`, `int size()`, `List<String> keysWithPrefix(String)`; `com.gimlism.translucent.trie.consumer.TrieRecordingListener` (test only) — `List<TrieEvent> events()`, `void clear()`, attaches via `trie.addListener(...)`.
- Produces: `com.gimlism.translucent.trie.repl.TrieCommandInterpreter` — `CommandResult execute(String line, RadixTrie<Integer> trie)` and `String helpText()`. Reused unchanged by Task 3 and the future Slice D browser controls.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/trie/repl/TrieCommandInterpreterTest.java`:

```java
package com.gimlism.translucent.trie.repl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.substrate.repl.CommandResult;
import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.core.RadixTrie;
import org.junit.jupiter.api.Test;

class TrieCommandInterpreterTest {

    private final TrieCommandInterpreter interp = new TrieCommandInterpreter();
    private final RadixTrie<Integer> trie = new RadixTrie<>();

    @Test
    void putNewKeyInsertsAndReports() {
        CommandResult r = interp.execute("put shore 1", trie);
        assertEquals("put shore = 1", r.message());
        assertFalse(r.quit());
        assertEquals(1, trie.get("shore"));
    }

    @Test
    void putExistingKeyReportsSetWithOldValue() {
        interp.execute("put shore 1", trie);
        CommandResult r = interp.execute("put shore 9", trie);
        assertEquals("set shore = 9 (was 1)", r.message());
        assertEquals(9, trie.get("shore"));
    }

    @Test
    void removePresentAndAbsent() {
        interp.execute("put shore 1", trie);
        assertEquals("removed shore", interp.execute("remove shore", trie).message());
        assertFalse(trie.containsKey("shore"));
        assertEquals("shore not found", interp.execute("remove shore", trie).message());
    }

    @Test
    void getPresentAndAbsent() {
        interp.execute("put shore 1", trie);
        assertEquals("get shore → 1", interp.execute("get shore", trie).message());
        assertEquals("get gone → absent", interp.execute("get gone", trie).message());
    }

    @Test
    void containsKeyWithAlias() {
        interp.execute("put shore 1", trie);
        assertEquals("containsKey shore → true", interp.execute("containsKey shore", trie).message());
        assertEquals("containsKey gone → false", interp.execute("contains gone", trie).message());
    }

    @Test
    void keysWithPrefixMatchesEmptyListsAllAndNoMatch() {
        interp.execute("put she 1", trie);
        interp.execute("put shell 2", trie);
        interp.execute("put shore 3", trie);
        // a real prefix → matching keys, lexicographic (matches RadixTrie.keysWithPrefix order)
        assertEquals("keysWithPrefix \"sh\" → [she, shell, shore]",
                interp.execute("keysWithPrefix sh", trie).message());
        // empty prefix → all keys (alias 'keys')
        assertEquals("keysWithPrefix \"\" → [she, shell, shore]",
                interp.execute("keys", trie).message());
        // no match → empty list
        assertEquals("keysWithPrefix \"xyz\" → []",
                interp.execute("keysWithPrefix xyz", trie).message());
    }

    @Test
    void sizeCountsKeys() {
        interp.execute("put she 1", trie);
        interp.execute("put shore 2", trie);
        assertEquals("size = 2", interp.execute("size", trie).message());
    }

    @Test
    void quitAndExitSetQuitFlag() {
        assertTrue(interp.execute("quit", trie).quit());
        assertTrue(interp.execute("exit", trie).quit());
        assertEquals("bye", interp.execute("quit", trie).message());
    }

    @Test
    void commandWordIsCaseInsensitiveAndWhitespaceTolerant() {
        assertEquals("put shore = 1", interp.execute("  PuT   shore   1  ", trie).message());
        assertEquals(1, trie.get("shore"));
    }

    @Test
    void blankLineIsANoOpWithEmptyMessage() {
        CommandResult r = interp.execute("   ", trie);
        assertEquals("", r.message());
        assertFalse(r.quit());
        assertEquals(0, trie.size());
    }

    @Test
    void nullLineIsANoOpWithoutThrowing() {
        CommandResult r = interp.execute(null, trie);
        assertEquals("", r.message());
        assertFalse(r.quit());
        assertEquals(0, trie.size());
    }

    @Test
    void errorPathsLeaveTrieUnchangedAndReport() {
        assertEquals("usage: put <key> <int-value>", interp.execute("put shore", trie).message());
        assertEquals("not an integer: 'x'", interp.execute("put shore x", trie).message());
        // multi-token value: the whole rest-after-key must parse as one int, so this fails
        assertEquals("not an integer: '1 2'", interp.execute("put shore 1 2", trie).message());
        assertEquals("unknown command: 'frobnicate' (type 'help')",
                interp.execute("frobnicate shore", trie).message());
        assertFalse(interp.execute("put shore", trie).quit());
        assertEquals(0, trie.size(), "no error path mutated the trie");
    }

    @Test
    void errorPathsForRemoveGetContainsKey() {
        assertEquals("usage: remove <key>", interp.execute("remove", trie).message());
        assertEquals("usage: get <key>", interp.execute("get", trie).message());
        assertEquals("usage: containsKey <key>", interp.execute("contains", trie).message());
        assertEquals(0, trie.size(), "no error path mutated the trie");
    }

    @Test
    void helpListsEveryCommandWord() {
        String help = interp.execute("help", trie).message();
        for (String word : new String[] {
                "put", "remove", "get", "containsKey", "keysWithPrefix", "size", "help", "quit"}) {
            assertTrue(help.contains(word), "help mentions " + word);
        }
    }

    @Test
    void aMutationThroughTheInterpreterEmitsAtLeastOneEvent() {
        var rec = new TrieRecordingListener();
        trie.addListener(rec);
        interp.execute("put shore 1", trie); // first insert into an empty trie → ≥1 event
        assertFalse(rec.events().isEmpty(), "a put emits at least one event");
    }

    @Test
    void readsThroughTheInterpreterEmitNothing() {
        interp.execute("put shore 1", trie);
        var rec = new TrieRecordingListener();
        trie.addListener(rec);
        interp.execute("get shore", trie);
        interp.execute("containsKey shore", trie);
        interp.execute("keysWithPrefix sh", trie);
        interp.execute("size", trie);
        assertTrue(rec.events().isEmpty(), "reads emit no event");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=TrieCommandInterpreterTest test`
Expected: FAIL — compilation error, `TrieCommandInterpreter` does not exist.

- [ ] **Step 3: Write the interpreter**

Create `src/main/java/com/gimlism/translucent/trie/repl/TrieCommandInterpreter.java`:

```java
package com.gimlism.translucent.trie.repl;

import com.gimlism.translucent.substrate.repl.CommandResult;
import com.gimlism.translucent.trie.core.RadixTrie;
import java.util.List;
import java.util.Locale;

/**
 * Parses one command line and applies it to a {@link RadixTrie}, returning a {@link CommandResult}.
 * Pure: it performs no I/O and holds no reference to the live server — mutations it makes fire
 * {@code TrieEvent}s that any attached listener (e.g. {@code TrieLiveVisualizer}) broadcasts, so
 * live rendering is a side-effect of the listener, not of this class. {@link #execute} never
 * throws: every malformed input becomes an error message.
 *
 * <p>Keys are {@code String} (the first token — no spaces, no parsing), values are {@code Integer}.
 * This inverts the HashMap REPL (Integer key, String value): here only {@code put}'s value is
 * parsed, and {@code get}/{@code remove}/{@code containsKey} take the raw key token. This is the
 * shared entry point a browser-controls front-end will reuse in a later slice.
 */
public final class TrieCommandInterpreter {

    /** Parse {@code line}, apply it to {@code trie}, and return the result to show the user. */
    public CommandResult execute(String line, RadixTrie<Integer> trie) {
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
            case "put": {
                String[] kv = rest.split("\\s+", 2);
                if (kv.length < 2) {
                    return CommandResult.of("usage: put <key> <int-value>");
                }
                Integer value = parseValue(kv[1]);
                if (value == null) {
                    return CommandResult.of("not an integer: '" + kv[1] + "'");
                }
                Integer old = trie.put(kv[0], value);
                return CommandResult.of(old == null
                        ? "put " + kv[0] + " = " + value
                        : "set " + kv[0] + " = " + value + " (was " + old + ")");
            }
            case "remove": {
                if (rest.isEmpty()) {
                    return CommandResult.of("usage: remove <key>");
                }
                Integer old = trie.remove(rest);
                return CommandResult.of(old != null ? "removed " + rest : rest + " not found");
            }
            case "get": {
                if (rest.isEmpty()) {
                    return CommandResult.of("usage: get <key>");
                }
                Integer v = trie.get(rest);
                return CommandResult.of("get " + rest + " → " + (v != null ? v : "absent"));
            }
            case "containskey":
            case "contains": {
                if (rest.isEmpty()) {
                    return CommandResult.of("usage: containsKey <key>");
                }
                return CommandResult.of("containsKey " + rest + " → " + trie.containsKey(rest));
            }
            case "keyswithprefix":
            case "keys": {
                List<String> keys = trie.keysWithPrefix(rest); // empty rest → "" → all keys
                return CommandResult.of("keysWithPrefix \"" + rest + "\" → " + keys);
            }
            case "size":
                return CommandResult.of("size = " + trie.size());
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
    private static Integer parseValue(String token) {
        try {
            return Integer.valueOf(token.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** One-line overview plus the grammar, one line per command. */
    public String helpText() {
        return String.join("\n",
                "RadixTrie live REPL — type commands; mutations render live in the browser.",
                "  put <key> <int-value>    insert or update a key (String key, integer value)",
                "  remove <key>             remove a key",
                "  get <key>                look up a key (prints the value; no viz change)",
                "  containsKey <key>        test membership (alias: contains; no viz change)",
                "  keysWithPrefix [prefix]  list keys under a prefix, all keys if omitted "
                        + "(alias: keys; no viz change)",
                "  size                     number of keys (no viz change)",
                "  help                     show this help",
                "  quit                     stop the server and exit (alias: exit)");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=TrieCommandInterpreterTest test`
Expected: PASS — all test methods green.

Note on the `keysWithPrefix` ordering assertion: `RadixTrie.keysWithPrefix` collects via a recursive walk over `children.values()`. If the class Javadoc's "lexicographically" claim and the actual `[she, shell, shore]` ordering disagree at runtime, trust the runtime output and adjust the expected string in the test — do **not** modify `RadixTrie` (core is out of scope).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/repl/TrieCommandInterpreter.java \
        src/test/java/com/gimlism/translucent/trie/repl/TrieCommandInterpreterTest.java
git commit -m "feat(trie-repl): TrieCommandInterpreter — pure command layer

put/remove/get/containsKey/keysWithPrefix/size/help/quit over
RadixTrie<Integer>. String keys (raw token), Integer values (only put
parses). Never throws; reads emit no event.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm"
```

---

## Task 3: `TrieLiveReplDemo` + `runRepl` seam test

**Files:**
- Create: `src/main/java/com/gimlism/translucent/trie/demo/TrieLiveReplDemo.java`
- Test: `src/test/java/com/gimlism/translucent/trie/demo/TrieLiveReplDemoTest.java`

**Interfaces:**
- Consumes: `TrieCommandInterpreter` (Task 2); `CommandResult` (Task 1); `RadixTrie<Integer>`; from `substrate.viz`: `LiveServer`, `BrowserLauncher`; from `trie.viz`: `TrieLiveVisualizer`, `TrieWebExporter.liveHtml()`.
- Produces: `TrieLiveReplDemo.main(String[])` and the package-private seam `static void runRepl(BufferedReader in, PrintStream out, RadixTrie<Integer> trie, TrieCommandInterpreter interpreter) throws IOException`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/trie/demo/TrieLiveReplDemoTest.java`:

```java
package com.gimlism.translucent.trie.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.repl.TrieCommandInterpreter;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TrieLiveReplDemoTest {

    private String runOn(String input, RadixTrie<Integer> trie) throws IOException {
        var out = new ByteArrayOutputStream();
        TrieLiveReplDemo.runRepl(new BufferedReader(new StringReader(input)),
                new PrintStream(out, true, StandardCharsets.UTF_8),
                trie, new TrieCommandInterpreter());
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void loopExecutesEachLineAndStopsOnQuit() throws IOException {
        var trie = new RadixTrie<Integer>();
        String output = runOn("put shore 1\nget shore\nquit\nput she 2\n", trie); // lines after quit must NOT run

        assertTrue(output.contains("put shore = 1"), output);
        assertTrue(output.contains("get shore → 1"), output);
        assertTrue(output.contains("bye"), output);
        assertEquals(1, trie.size(), "processing stopped at quit — 'put she 2' never ran");
    }

    @Test
    void loopEndsAtEofWithoutQuit() throws IOException {
        var trie = new RadixTrie<Integer>();
        String output = runOn("put she 2\n", trie); // no quit — EOF ends the loop

        assertTrue(output.contains("put she = 2"), output);
        assertEquals(2, trie.get("she"));
    }

    @Test
    void blankLinesProduceNoOutput() throws IOException {
        var trie = new RadixTrie<Integer>();
        String output = runOn("\n   \nsize\n", trie);
        assertEquals("size = 0" + System.lineSeparator(), output);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=TrieLiveReplDemoTest test`
Expected: FAIL — compilation error, `TrieLiveReplDemo` does not exist.

- [ ] **Step 3: Write the demo**

Create `src/main/java/com/gimlism/translucent/trie/demo/TrieLiveReplDemo.java`:

```java
package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.substrate.repl.CommandResult;
import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.repl.TrieCommandInterpreter;
import com.gimlism.translucent.trie.viz.TrieLiveVisualizer;
import com.gimlism.translucent.trie.viz.TrieWebExporter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Interactive terminal REPL for a live {@link RadixTrie}: type commands (see {@code help}) and
 * watch each mutation render live in the browser — no recompile per change. Reuses Slice B's
 * {@link LiveServer} for the SSE stream. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.trie.demo.TrieLiveReplDemo}
 * The trie starts empty; the server stops when you type {@code quit} or send EOF (Ctrl-D).
 */
public class TrieLiveReplDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        LiveServer server = new LiveServer(TrieWebExporter.liveHtml(), "127.0.0.1", DEFAULT_PORT);
        server.start();
        String url = "http://localhost:" + server.port();

        var trie = new RadixTrie<Integer>();
        trie.addListener(new TrieLiveVisualizer(server::broadcast));

        BrowserLauncher.open(url);
        System.out.println("Live REPL — serving at " + url);
        System.out.println("Type 'help' for commands, 'quit' to stop.");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            runRepl(in, System.out, trie, new TrieCommandInterpreter());
        } finally {
            server.stop();
        }
    }

    /**
     * Read-eval-print loop (test seam — no server or browser): read a line, execute it, print the
     * message, until a {@code quit} result or EOF. Blank-line results (empty message) print nothing.
     */
    static void runRepl(BufferedReader in, PrintStream out, RadixTrie<Integer> trie,
            TrieCommandInterpreter interpreter) throws IOException {
        String line;
        while ((line = in.readLine()) != null) { // EOF (Ctrl-D) ends the loop
            CommandResult r = interpreter.execute(line, trie);
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

Run: `mvn -q -Dtest=TrieLiveReplDemoTest test`
Expected: PASS — all three test methods green.

- [ ] **Step 5: Run the full suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS, **352 tests** (333 pre-existing + 16 in `TrieCommandInterpreterTest` + 3 in `TrieLiveReplDemoTest`), 0 failures. If you added or merged assertions and the total differs, confirm the delta equals your actual new-method count — don't accept an unexplained drop below 352.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/demo/TrieLiveReplDemo.java \
        src/test/java/com/gimlism/translucent/trie/demo/TrieLiveReplDemoTest.java
git commit -m "feat(trie-demo): TrieLiveReplDemo — terminal REPL over the live SSE stream

Wires TrieCommandInterpreter to stdin and the Slice B LiveServer; each
mutation renders live in the browser. Package-private runRepl seam is
driven by TrieLiveReplDemoTest without a server.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm"
```

---

## Verification (before opening the PR)

- [ ] `mvn -q test` — full suite green.
- [ ] Optional live smoke test (verification, not an automated test): `mvn -q process-classes`, then pipe timed commands into the REPL against `target/classes` and browser-verify the SSE renders via Chrome MCP over `http://localhost:7070` (the extension blocks `file://`). This mirrors the Slice A/B recipe; the live JS is untested-by-design and unchanged here.

## Out of scope / deferred

- `clear` command — the trie exposes no `clear()`; a remove-loop would emit N events and invent API the trie lacks.
- Browser controls (POST /command + CONTROLS token) — Slice D, reusing this interpreter unchanged.
- Web-exporter-template consolidation (roadmap item E) — still deferred; this slice touches only `CommandResult`.
