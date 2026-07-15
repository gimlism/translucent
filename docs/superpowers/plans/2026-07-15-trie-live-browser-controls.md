# Trie live browser controls (Slice D) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Flip the baked-`false` `CONTROLS` token on and add a browser command box to `trie-viz.html`, so a student drives a live `RadixTrie<Integer>` from the page — reusing the merged `TrieCommandInterpreter` and `LiveServer`'s POST path unchanged. Completes the 4-slice trie web-viz arc.

**Architecture:** Add `TrieWebExporter.controlsHtml()` (DATA null / LIVE true / CONTROLS true), the command box in `trie-viz.html` (a grammar-agnostic `fetch("/command")` port from `list-viz.html`), and `TrieLiveControlsDemo` wiring a serialized `commandHandler` seam behind `LiveServer`'s 4-arg constructor. The SVG redraws ONLY via the existing SSE stream; the POST reply is text-only.

**Tech Stack:** Java 22, JUnit 5, Maven. Vanilla JS/SVG in the template. No new dependencies.

## Global Constraints

- Java package root: `com.gimlism.translucent`.
- **Do NOT modify** `LiveServer`, `JsonWriter`, `WebVizTemplate`, `BrowserLauncher`, `DemoLifecycle`, `TrieLiveVisualizer`, `TrieJsonSerializer`, `TrieCommandInterpreter`, trie `core`/`events`, or `trie-viz.html`'s `layout`/`renderFrame`/SSE branch. Slice D adds only: `controlsHtml()`, the command box in `trie-viz.html`, and `TrieLiveControlsDemo` + tests. This keeps the snapshot-before-settled bug at ZERO new surface.
- The command box JS is a **verbatim, grammar-agnostic** port from `list-viz.html`; the ONLY trie-specific token is the input placeholder.
- Read commands (`get`/`containsKey`/`keysWithPrefix`/`size`/`help`) fire no event → no frame → the POST reply text is their ONLY feedback. Do not "simplify" the reply away.
- `commandHandler` serializes `execute` behind a private lock (LiveServer's virtual-thread executor can dispatch overlapping POSTs onto the non-thread-safe `RadixTrie`); `quit`/`exit` return their message but do NOT stop the server (the seam holds no server reference).
- CONTROLS JS is untested-by-design → controller browser-verifies via Chrome MCP (headless demo over `http://localhost:7070` against `target/classes` after `mvn process-classes`; the extension blocks `file://`).
- Commit after each task; commit messages end with the two trailer lines used across this repo (copy from a recent commit).
- Build: `mvn -q test` (full suite). Single-class: `mvn -q -Dtest=ClassName test`.

## Baseline

The full suite is **353** tests (post-#28-merge, HEAD `151bad4`). Task 1 adds `TrieWebExporterControlsTest` (4 methods); Task 2 adds `TrieLiveControlsEndToEndTest` (2 methods). Final expected: **359**.

---

## File Structure

- **Modify** `src/main/java/com/gimlism/translucent/trie/viz/TrieWebExporter.java` — add `controlsHtml()`.
- **Modify** `src/main/resources/web/trie-viz.html` — add the command box (HTML + element refs + `if (CONTROLS){…}` block).
- **Create** `src/test/java/com/gimlism/translucent/trie/viz/TrieWebExporterControlsTest.java`.
- **Create** `src/main/java/com/gimlism/translucent/trie/demo/TrieLiveControlsDemo.java` — demo + `commandHandler` seam.
- **Create** `src/test/java/com/gimlism/translucent/trie/demo/TrieLiveControlsEndToEndTest.java`.

---

## Task 1: `TrieWebExporter.controlsHtml()` + `trie-viz.html` command box

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/trie/viz/TrieWebExporter.java`
- Modify: `src/main/resources/web/trie-viz.html`
- Test: `src/test/java/com/gimlism/translucent/trie/viz/TrieWebExporterControlsTest.java`

**Interfaces:**
- Consumes: `com.gimlism.translucent.substrate.viz.WebVizTemplate.inject(String resource, String framesJson, String live, String controls)` (already used by `toHtml`/`liveHtml`).
- Produces: `TrieWebExporter.controlsHtml()` → `String` (DATA null, LIVE true, CONTROLS true). Consumed by Task 2's demo + e2e.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/trie/viz/TrieWebExporterControlsTest.java`:

```java
package com.gimlism.translucent.trie.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrieWebExporterControlsTest {

    @Test
    void controlsHtmlEnablesControlsAndLive() {
        String html = TrieWebExporter.controlsHtml();
        assertTrue(html.contains("const CONTROLS = true;"), "controls flag on");
        assertTrue(html.contains("const LIVE = true;"), "live flag on");
        assertTrue(html.contains("const DATA = null;"), "no baked frames in live mode");
    }

    @Test
    void liveHtmlLeavesControlsOff() {
        String html = TrieWebExporter.liveHtml();
        assertTrue(html.contains("const CONTROLS = false;"), "controls off in plain live mode");
        assertTrue(html.contains("const LIVE = true;"), "live flag still on");
    }

    @Test
    void bakedHtmlLeavesLiveAndControlsOff() {
        String html = TrieWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.contains("const LIVE = false;"), "replay mode is not live");
        assertTrue(html.contains("const CONTROLS = false;"), "replay mode has no controls");
    }

    @Test
    void templateCarriesTheCommandBoxAndPostTarget() {
        // The box HTML is static in the template (CONTROLS only unhides it), so any mode carries it;
        // this guards that the Slice D command-box edit actually landed in trie-viz.html.
        String html = TrieWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.contains("id=\"cmd\""), "command input present in the template");
        assertTrue(html.contains("/command"), "POST /command wiring present in the template");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=TrieWebExporterControlsTest test`
Expected: FAIL — `controlsHtmlEnablesControlsAndLive` fails to compile (`controlsHtml()` undefined), and `templateCarriesTheCommandBoxAndPostTarget` would fail (box not yet in the template).

- [ ] **Step 3: Add `controlsHtml()` to the exporter**

In `src/main/java/com/gimlism/translucent/trie/viz/TrieWebExporter.java`, add this method immediately after `liveHtml()` (before `writeHtml`):

```java
    /** Live mode with browser command controls: {@code DATA = null}, live ON, controls ON. */
    public static String controlsHtml() {
        return WebVizTemplate.inject(TEMPLATE_RESOURCE, "null", "true", "true");
    }
```

- [ ] **Step 4: Add the command box to `trie-viz.html`**

Three edits to `src/main/resources/web/trie-viz.html`.

**Edit A — the box HTML.** After the `#live` button line (`    <button id="live" hidden></button>`) and before the `</div>` that closes `#bar`, insert:

```html
    <input id="cmd" type="text" placeholder="put shore 1 · get shore · keysWithPrefix sh · help" autocomplete="off" hidden>
    <button id="run" hidden>Run</button>
    <span id="cmdout"></span>
```

**Edit B — the element refs.** After `const liveBtn = document.getElementById("live");`, insert:

```js
const cmdInput = document.getElementById("cmd");
const runBtn = document.getElementById("run");
const cmdOut = document.getElementById("cmdout");
```

**Edit C — the CONTROLS wiring.** Inside the `if (LIVE) {` block, after the `es.onmessage = ev => { … };` handler closes and before the `}` that closes `if (LIVE)`, insert:

```js
  if (CONTROLS) {
    cmdInput.hidden = false;
    runBtn.hidden = false;
    const submit = () => {
      const line = cmdInput.value;
      if (!line.trim()) return;
      cmdInput.value = "";
      fetch("/command", { method: "POST", body: line })
        .then(r => r.text())
        .then(msg => { cmdOut.textContent = msg; })
        .catch(() => { cmdOut.textContent = "(command failed to reach the server)"; });
    };
    runBtn.onclick = submit;
    cmdInput.addEventListener("keydown", e => { if (e.key === "Enter") submit(); });
  }
```

Do not touch `layout`, `renderFrame`, `go`, `refreshMeta`, or the `EventSource`/`onmessage` handler itself.

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q -Dtest=TrieWebExporterControlsTest test`
Expected: PASS — all 4 methods green.

- [ ] **Step 6: Run the full suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS, **357** tests (353 + 4), 0 failures.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/viz/TrieWebExporter.java \
        src/main/resources/web/trie-viz.html \
        src/test/java/com/gimlism/translucent/trie/viz/TrieWebExporterControlsTest.java
git commit -m "feat(trie-viz): TrieWebExporter.controlsHtml + trie-viz.html command box

Flip the baked-false CONTROLS token on and add the grammar-agnostic command
box (input/Run/status -> POST /command; SVG redraws only via SSE, reply is
text-only) — a verbatim port of list-viz.html's controls, trie placeholder.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm"
```

---

## Task 2: `TrieLiveControlsDemo` + `commandHandler` seam + headless e2e

**Files:**
- Create: `src/main/java/com/gimlism/translucent/trie/demo/TrieLiveControlsDemo.java`
- Test: `src/test/java/com/gimlism/translucent/trie/demo/TrieLiveControlsEndToEndTest.java`

**Interfaces:**
- Consumes: `TrieWebExporter.controlsHtml()` (Task 1); `TrieCommandInterpreter.execute(String, RadixTrie<Integer>)`; `RadixTrie<Integer>`; `TrieLiveVisualizer`; from `substrate.viz`: `LiveServer` (4-arg ctor `LiveServer(String html, String host, int port, Function<String,String> handler)`, `start()`, `stop()`, `port()`, `openConnections()`, `broadcast`), `BrowserLauncher.open(String)`, `DemoLifecycle.awaitShutdown(server)`.
- Produces: `TrieLiveControlsDemo.main(String[])` and the tested seam `static Function<String,String> commandHandler(RadixTrie<Integer> trie, TrieCommandInterpreter interpreter)`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/trie/demo/TrieLiveControlsEndToEndTest.java`:

```java
package com.gimlism.translucent.trie.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.repl.TrieCommandInterpreter;
import com.gimlism.translucent.trie.viz.TrieLiveVisualizer;
import com.gimlism.translucent.trie.viz.TrieWebExporter;
import com.gimlism.translucent.substrate.viz.LiveServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Proves Slice D: a browser POST /command mutates the trie and surfaces as a live SSE frame. */
class TrieLiveControlsEndToEndTest {

    @Test
    void aPostedCommandMutatesTheTrieAndSurfacesAsALiveFrame() throws IOException {
        var trie = new RadixTrie<Integer>();
        Function<String, String> handler = TrieLiveControlsDemo.commandHandler(trie, new TrieCommandInterpreter());
        LiveServer server = new LiveServer(TrieWebExporter.controlsHtml(), "127.0.0.1", 0, handler);
        server.start();
        try {
            trie.addListener(new TrieLiveVisualizer(server::broadcast));

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                HttpClient client = HttpClient.newHttpClient();
                String base = "http://127.0.0.1:" + server.port();

                // open the SSE stream first, then wait until the server has registered it
                InputStream body = client.send(
                        HttpRequest.newBuilder(URI.create(base + "/events")).build(),
                        BodyHandlers.ofInputStream()).body();
                try (var r = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                    while (server.openConnections() < 1) Thread.sleep(10);

                    // POST a command; the response is the interpreter's text
                    var resp = client.send(HttpRequest.newBuilder(URI.create(base + "/command"))
                                    .POST(BodyPublishers.ofString("put cat 1", StandardCharsets.UTF_8)).build(),
                            BodyHandlers.ofString());
                    assertEquals("put cat = 1", resp.body());

                    // the mutation surfaces on the SSE stream. The first put into an EMPTY trie emits
                    // a leading CreateNode frame before the Put (the trie's grow-then-append analog),
                    // and the reader connected BEFORE the POST so it sees frames incrementally — so
                    // read PAST any leading frame until the Put frame itself shows up.
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String frame = line.substring("data: ".length());
                            if (frame.contains("\"type\":\"Put\"")) {
                                return; // the POST's mutation surfaced as a live Put frame
                            }
                        }
                    }
                    throw new IOException("no Put frame received");
                }
            });
        } finally {
            server.stop();
        }
    }

    @Test
    void quitReturnsItsMessageWithoutStoppingOrMutating() {
        var trie = new RadixTrie<Integer>();
        Function<String, String> handler = TrieLiveControlsDemo.commandHandler(trie, new TrieCommandInterpreter());
        handler.apply("put a 1"); // trie now has one key
        assertEquals("bye", handler.apply("quit"));
        assertEquals("bye", handler.apply("exit"));
        // the seam holds no server reference, so quit/exit cannot stop the session; and they are
        // pure reads through the interpreter, so the trie is untouched.
        assertEquals(1, trie.size(), "quit/exit leave the trie unchanged");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=TrieLiveControlsEndToEndTest test`
Expected: FAIL — compilation error, `TrieLiveControlsDemo` does not exist.

- [ ] **Step 3: Write the demo**

Create `src/main/java/com/gimlism/translucent/trie/demo/TrieLiveControlsDemo.java`:

```java
package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.repl.TrieCommandInterpreter;
import com.gimlism.translucent.trie.viz.TrieLiveVisualizer;
import com.gimlism.translucent.trie.viz.TrieWebExporter;
import java.io.IOException;
import java.util.function.Function;

/**
 * Browser-driven live demo: type commands into the page (a box wired to {@code POST /command}) and
 * watch each mutation render live. The trie lives server-side; the browser is the REPL over HTTP,
 * running the SAME {@link TrieCommandInterpreter} as {@link TrieLiveReplDemo}. No stdin. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.trie.demo.TrieLiveControlsDemo}
 * The trie starts empty; the server keeps running until Ctrl-C.
 */
public class TrieLiveControlsDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        var trie = new RadixTrie<Integer>();
        Function<String, String> handler = commandHandler(trie, new TrieCommandInterpreter());

        LiveServer server = new LiveServer(TrieWebExporter.controlsHtml(), "127.0.0.1", DEFAULT_PORT, handler);
        server.start();
        trie.addListener(new TrieLiveVisualizer(server::broadcast));

        String url = "http://localhost:" + server.port();
        BrowserLauncher.open(url);
        System.out.println("Live controls — serving at " + url + " — type commands in the browser; Ctrl-C to stop.");

        DemoLifecycle.awaitShutdown(server);
    }

    /**
     * The trie-specific command seam (tested): applies each POSTed line to {@code trie} via
     * {@code interpreter}, returning the message to show. Serialized behind a private lock because
     * {@link LiveServer}'s virtual-thread executor can dispatch overlapping POSTs onto a
     * non-thread-safe {@link RadixTrie}. {@code quit}/{@code exit} return their message but do NOT
     * stop the server — a stray POST must not kill the session (the seam holds no server).
     */
    static Function<String, String> commandHandler(RadixTrie<Integer> trie,
            TrieCommandInterpreter interpreter) {
        final Object lock = new Object();
        return line -> {
            synchronized (lock) {
                return interpreter.execute(line, trie).message();
            }
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=TrieLiveControlsEndToEndTest test`
Expected: PASS — both methods green. (The read-until-`"type":"Put"` loop robustly skips the leading `CreateNode` frame; if it ever times out, POST `put cat 1` against a running server and inspect the raw `data:` lines to confirm the emitted frame types before adjusting the target type — do NOT change `TrieCommandInterpreter` or the core.)

- [ ] **Step 5: Run the full suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS, **359** tests (357 + 2), 0 failures.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/demo/TrieLiveControlsDemo.java \
        src/test/java/com/gimlism/translucent/trie/demo/TrieLiveControlsEndToEndTest.java
git commit -m "feat(trie-demo): TrieLiveControlsDemo — browser-driven live controls

Wires TrieCommandInterpreter to LiveServer's POST /command via a serialized
commandHandler seam; each browser command mutates the trie and renders live
over SSE. Headless e2e: POST put -> reply text + live Put frame (reads past
the empty-trie CreateNode). quit/exit return their message, never stop the server.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm"
```

- [ ] **Step 7: Controller browser-verification (manual, not an automated test)**

The CONTROLS JS is untested-by-design; verify it in a real browser before the PR:

1. `mvn -q process-classes` (copies `trie-viz.html` into `target/classes`).
2. Run `TrieLiveControlsDemo` headless (`-Djava.awt.headless=true` so `BrowserLauncher` no-ops with a hint instead of opening the user's browser) against `target/classes`, serving on `http://localhost:7070`.
3. Via Chrome MCP, connect to `http://localhost:7070` and verify:
   - the command box renders (input + Run);
   - `put shore 1` then `put she 2` → SVG redraws live (nodes/edges appear), reply shows `put shore = 1` / `put she = 2`, frame counter climbs;
   - `get shore` → reply `get shore → 1` with NO SVG change and the counter FROZEN (read-no-frame proof);
   - `keysWithPrefix sh` → reply lists keys, no SVG change;
   - reload the page → snapshot-on-connect shows the current trie (late-joiner), box still works;
   - zero console errors throughout.
4. Kill the java process (`pkill -f TrieLiveControlsDemo`) and delete any scratch.

Record the observations; if anything fails, fix `trie-viz.html`/the demo (never the core/transport) and re-verify.

---

## Verification (before opening the PR)

- [ ] `mvn -q test` — full suite **359** green.
- [ ] Browser-verification (Task 2, Step 7) done and clean.

## Out of scope / deferred

- No new grammar — `TrieCommandInterpreter` reused unchanged.
- No change to static/live replay modes or the SVG renderer.
- Deferred minors (fix on a future test-touch, not here): HttpClient-unclosed in the trie + list e2e files; 2 tautological Slice-A `TrieJsonSerializerTest` Prune-test assertions.
