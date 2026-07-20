# TreeSet web-viz Slice D (browser controls) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Flip on the dormant command box on `treeset-viz.html` so a student types commands into the browser, which POST to the running server and render live — completing the TreeSet's 4-slice web arc.

**Architecture:** Two tasks. Task 1 adds `TreeSetWebExporter.controlsHtml()` and the command-box HTML/CSS/JS to `treeset-viz.html` (the `CONTROLS` token already exists, baked `false`), gated tests at the exporter/template layer. Task 2 adds `TreeSetLiveControlsDemo` (a `commandHandler` seam behind `POST /command`) and an end-to-end test proving both a mutation and — the point of this slice — a browser-typed comparison read surface as live SSE frames. `TreeSetCommandInterpreter`, `LiveServer`, `WebVizTemplate`, `TreeSetLiveVisualizer`, and `TreeSetJsonSerializer` are all reused byte-unchanged.

**Tech Stack:** Java 21, Maven, JUnit 5, vanilla JS/SVG in a resource HTML file, `java.net.http` for the e2e HTTP client.

## Global Constraints

- **Java version:** `maven.compiler.release=21` — `HttpClient` implements `AutoCloseable` (JDK 21), so `client.close()` is valid.
- **Merge convention (for the human, later):** `gh pr merge N --merge` (NOT squash, NOT `--delete-branch`). Not part of these tasks.
- **Do NOT touch** `TreeSetCommandInterpreter`, `LiveServer`, `JsonWriter`, `WebVizTemplate`, `TreeSetLiveVisualizer`, `TreeSetJsonSerializer`, `BrowserLauncher`, `DemoLifecycle`, or anything in core/events/substrate. The static/live rendering JS on `treeset-viz.html` (layout, `renderFrame`, the `if (LIVE)` SSE branch) stays unchanged — only the dormant `CONTROLS`-gated box is added.
- **`mvn` is the source of truth**, not the Eclipse LSP (stale-LSP false errors are a known project gotcha). Verify with `mvn`.
- **Element highlight/serialization:** frame JSON carries `"type":"<EventClassSimpleName>"` (e.g. `"type":"Add"`, `"type":"Compare"`) and elements as strings (`"element":"30"`). Assertions rely on these exact shapes.
- **Branch:** `feat/treeset-live-browser-controls` (already created; design committed `3e556cb`). All commits land here.

---

### Task 1: `controlsHtml()` + the command box on `treeset-viz.html`

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporter.java`
- Modify: `src/main/resources/web/treeset-viz.html`
- Modify: `src/test/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporterLiveTest.java` (remove one stale assertion)
- Create: `src/test/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporterControlsTest.java`

**Interfaces:**
- Consumes: `WebVizTemplate.inject(String resource, String frames, String live, String controls)` (existing).
- Produces: `TreeSetWebExporter.controlsHtml() -> String` (new public static method). The template now unconditionally contains `id="cmd"` and `/command`, gated by `if (CONTROLS)` in JS.

- [ ] **Step 1: Write the failing exporter test**

Create `src/test/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporterControlsTest.java`:

```java
package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TreeSetWebExporterControlsTest {

    @Test
    void controlsHtmlEnablesControlsAndLive() {
        String html = TreeSetWebExporter.controlsHtml();
        assertTrue(html.contains("const CONTROLS = true;"), "controls flag on");
        assertTrue(html.contains("const LIVE = true;"), "live flag on");
        assertTrue(html.contains("const DATA = null;"), "no baked frames in live mode");
    }

    @Test
    void liveHtmlLeavesControlsOff() {
        String html = TreeSetWebExporter.liveHtml();
        assertTrue(html.contains("const CONTROLS = false;"), "controls off in plain live mode");
        assertTrue(html.contains("const LIVE = true;"), "live flag still on");
    }

    @Test
    void bakedHtmlLeavesLiveAndControlsOff() {
        String html = TreeSetWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.contains("const LIVE = false;"), "replay mode is not live");
        assertTrue(html.contains("const CONTROLS = false;"), "replay mode has no controls");
    }

    @Test
    void templateCarriesTheCommandBoxAndPostTarget() {
        // The box HTML is static in the template (CONTROLS only unhides it), so any mode carries it;
        // this guards that the Slice D command-box edit actually landed in treeset-viz.html.
        String html = TreeSetWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.contains("id=\"cmd\""), "command input present in the template");
        assertTrue(html.contains("/command"), "POST /command wiring present in the template");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q test -Dtest=TreeSetWebExporterControlsTest`
Expected: FAIL — `controlsHtml()` does not exist yet (compile error), and the template has no `id="cmd"` / `/command`.

- [ ] **Step 3: Add `controlsHtml()` to `TreeSetWebExporter`**

In `src/main/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporter.java`, insert this method immediately after `liveHtml()` (after its closing `}` on the line `return WebVizTemplate.inject(TEMPLATE_RESOURCE, "null", "true", "false"); }`):

```java
    /** Live mode with browser command controls: {@code DATA = null}, live ON, controls ON. */
    public static String controlsHtml() {
        return WebVizTemplate.inject(TEMPLATE_RESOURCE, "null", "true", "true");
    }
```

Also update the class Javadoc's last sentence — change `{@code liveHtml}/{@code controlsHtml} arrive with Slices B/D.` to `{@code controlsHtml} enables the browser command box (Slice D).`

- [ ] **Step 4: Add the `#cmd` / `#cmdout` CSS to `treeset-viz.html`**

In `src/main/resources/web/treeset-viz.html`, find this line (the `#counter` rule, ~line 22):

```css
  #counter { color:var(--muted); font-variant-numeric: tabular-nums; }
```

Insert immediately after it (note: `--cellb` is the TreeSet page's border variable — the trie uses `--nodeb`, do NOT copy that name):

```css
  #cmd { font:inherit; padding:6px 10px; border:1px solid var(--cellb); border-radius:8px;
         background:var(--card); color:var(--fg); min-width:220px; }
  #cmdout { color:var(--muted); font-variant-numeric: tabular-nums; }
```

- [ ] **Step 5: Add the command box to the `#bar` markup**

In `treeset-viz.html`, find (~line 44):

```html
    <button id="live" hidden></button>
  </div>
```

Replace it with (adds the input + Run button + reply span before the `</div>` that closes `#bar`):

```html
    <button id="live" hidden></button>
    <input id="cmd" type="text" placeholder="add 30 · contains 30 · floor 25 · pollFirst · help" autocomplete="off" hidden>
    <button id="run" hidden>Run</button>
    <span id="cmdout"></span>
  </div>
```

- [ ] **Step 6: Add the element refs**

In `treeset-viz.html`, find (~line 61):

```javascript
const liveBtn = document.getElementById("live");
```

Insert immediately after it:

```javascript
const cmdInput = document.getElementById("cmd");
const runBtn = document.getElementById("run");
const cmdOut = document.getElementById("cmdout");
```

- [ ] **Step 7: Add the `if (CONTROLS)` block inside the `if (LIVE)` branch**

In `treeset-viz.html`, find the end of the `if (LIVE)` block (~lines 161–162):

```javascript
    else refreshMeta();                        // studying an older frame: badge only
  };
}
render();
```

Replace it with (insert the `if (CONTROLS)` block between the `};` that closes `es.onmessage` and the `}` that closes `if (LIVE)`):

```javascript
    else refreshMeta();                        // studying an older frame: badge only
  };
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
}
render();
```

- [ ] **Step 8: Remove the now-false stale assertion in `TreeSetWebExporterLiveTest`**

In `src/test/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporterLiveTest.java`, delete this line (the last assertion in `liveHtmlHasNoBakedFramesAndOpensAnEventSource`, ~line 20):

```java
        assertFalse(html.contains("/command"), "no command box in slice B");
```

Rationale: the box's `POST /command` is now in the template text unconditionally (dormant unless `CONTROLS`), so this assertion is false regardless of mode. The `assertTrue(html.contains("const CONTROLS = false;"), ...)` assertion on the line above still proves the box stays dormant in plain live mode. Leave the remaining `assertFalse` on frames/live tokens untouched — `assertFalse` stays imported and used.

- [ ] **Step 9: Run the exporter tests to verify they pass**

Run: `mvn -q test -Dtest=TreeSetWebExporterControlsTest+TreeSetWebExporterLiveTest`
Expected: PASS — all 4 controls-test methods green; the live test green with the stale assertion gone.

- [ ] **Step 10: Run the full suite to confirm no regressions**

Run: `mvn -q test`
Expected: BUILD SUCCESS. Baseline before this slice is 469 tests; this task adds 4 (net +4 methods, −1 assertion within an existing method) → 473.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporter.java \
        src/main/resources/web/treeset-viz.html \
        src/test/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporterControlsTest.java \
        src/test/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporterLiveTest.java
git commit -m "feat(treeset): controlsHtml() + command box on treeset-viz.html (Slice D)"
```

---

### Task 2: `TreeSetLiveControlsDemo` + end-to-end proof

**Files:**
- Create: `src/main/java/com/gimlism/translucent/treeset/demo/TreeSetLiveControlsDemo.java`
- Create: `src/test/java/com/gimlism/translucent/treeset/demo/TreeSetLiveControlsEndToEndTest.java`

**Interfaces:**
- Consumes: `TreeSetWebExporter.controlsHtml()` (Task 1); `LiveServer(String html, String host, int port, Function<String,String> handler)`, `server.start()`, `server.stop()`, `server.port()`, `server.openConnections()`, `server.broadcast` (existing); `TreeSetCommandInterpreter.execute(String, TeachingTreeSet<Integer>) -> CommandResult` and `CommandResult.message()` (existing, byte-unchanged); `TreeSetLiveVisualizer` (existing); `DemoLifecycle.awaitShutdown(LiveServer)`, `BrowserLauncher.open(String)` (existing).
- Produces: `TreeSetLiveControlsDemo.commandHandler(TeachingTreeSet<Integer> set, TreeSetCommandInterpreter interp) -> Function<String,String>` (package-private, tested seam).

- [ ] **Step 1: Write the failing end-to-end test**

Create `src/test/java/com/gimlism/translucent/treeset/demo/TreeSetLiveControlsEndToEndTest.java`:

```java
package com.gimlism.translucent.treeset.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.repl.TreeSetCommandInterpreter;
import com.gimlism.translucent.treeset.viz.TreeSetLiveVisualizer;
import com.gimlism.translucent.treeset.viz.TreeSetWebExporter;
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

/**
 * Proves Slice D: a browser POST /command mutates the set and surfaces as a live SSE frame — and,
 * the point of this slice, a POSTed comparison read narrates as a live Compare frame (no sibling
 * has this — reads are silent in map/list/trie).
 */
class TreeSetLiveControlsEndToEndTest {

    @Test
    void aPostedAddMutatesTheSetAndSurfacesAsALiveAddFrame() throws IOException {
        var set = new TeachingTreeSet<Integer>();
        Function<String, String> handler =
                TreeSetLiveControlsDemo.commandHandler(set, new TreeSetCommandInterpreter());
        LiveServer server = new LiveServer(TreeSetWebExporter.controlsHtml(), "127.0.0.1", 0, handler);
        server.start();
        HttpClient client = HttpClient.newHttpClient();
        try {
            set.addListener(new TreeSetLiveVisualizer(server::broadcast));

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                String base = "http://127.0.0.1:" + server.port();

                // open the SSE stream first, then wait until the server has registered it
                InputStream body = client.send(
                        HttpRequest.newBuilder(URI.create(base + "/events")).build(),
                        BodyHandlers.ofInputStream()).body();
                try (var r = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                    while (server.openConnections() < 1) Thread.sleep(10);

                    var resp = client.send(HttpRequest.newBuilder(URI.create(base + "/command"))
                                    .POST(BodyPublishers.ofString("add 30", StandardCharsets.UTF_8)).build(),
                            BodyHandlers.ofString());
                    assertEquals("added 30", resp.body());

                    // The reader connected BEFORE the POST, so it sees frames incrementally. The
                    // TreeSet has NO CreateNode — add IS the creation — so the Add frame appears
                    // directly (unlike the trie, which reads past a leading CreateNode to find Put).
                    // A root-blacken Recolor may follow; scan until the Add frame itself shows up.
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String frame = line.substring("data: ".length());
                            if (frame.contains("\"type\":\"Add\"")) {
                                return; // the POST's mutation surfaced as a live Add frame
                            }
                        }
                    }
                    throw new IOException("no Add frame received");
                }
            });
        } finally {
            client.close();
            server.stop();
        }
    }

    @Test
    void aPostedComparisonReadNarratesAsALiveCompareFrame() throws IOException {
        var set = new TeachingTreeSet<Integer>();
        Function<String, String> handler =
                TreeSetLiveControlsDemo.commandHandler(set, new TreeSetCommandInterpreter());
        LiveServer server = new LiveServer(TreeSetWebExporter.controlsHtml(), "127.0.0.1", 0, handler);
        server.start();
        HttpClient client = HttpClient.newHttpClient();
        try {
            set.addListener(new TreeSetLiveVisualizer(server::broadcast));

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                String base = "http://127.0.0.1:" + server.port();

                InputStream body = client.send(
                        HttpRequest.newBuilder(URI.create(base + "/events")).build(),
                        BodyHandlers.ofInputStream()).body();
                try (var r = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                    while (server.openConnections() < 1) Thread.sleep(10);

                    // Build a small tree via POSTs, then POST a comparison read. contains() walks the
                    // red-black tree emitting Compare events — the browser-typed reads-narrate path.
                    for (String cmd : new String[] {"add 30", "add 10", "add 50"}) {
                        client.send(HttpRequest.newBuilder(URI.create(base + "/command"))
                                        .POST(BodyPublishers.ofString(cmd, StandardCharsets.UTF_8)).build(),
                                BodyHandlers.ofString());
                    }
                    var resp = client.send(HttpRequest.newBuilder(URI.create(base + "/command"))
                                    .POST(BodyPublishers.ofString("contains 30", StandardCharsets.UTF_8)).build(),
                            BodyHandlers.ofString());
                    assertEquals("contains 30 → true", resp.body());

                    // Scan past the Add/Recolor/Rotation frames from the three adds until the read's
                    // Compare frame surfaces — proof that a typed comparison read animates live.
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String frame = line.substring("data: ".length());
                            if (frame.contains("\"type\":\"Compare\"")) {
                                return; // the POSTed read narrated as a live Compare frame
                            }
                        }
                    }
                    throw new IOException("no Compare frame received");
                }
            });
        } finally {
            client.close();
            server.stop();
        }
    }

    @Test
    void quitReturnsItsMessageWithoutStoppingOrMutating() {
        var set = new TeachingTreeSet<Integer>();
        Function<String, String> handler =
                TreeSetLiveControlsDemo.commandHandler(set, new TreeSetCommandInterpreter());
        handler.apply("add 1"); // set now has one element
        assertEquals("bye", handler.apply("quit"));
        assertEquals("bye", handler.apply("exit"));
        // the seam holds no server reference, so quit/exit cannot stop the session; and they are
        // pure reads through the interpreter, so the set is untouched.
        assertEquals(1, set.size(), "quit/exit leave the set unchanged");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q test -Dtest=TreeSetLiveControlsEndToEndTest`
Expected: FAIL — `TreeSetLiveControlsDemo` does not exist yet (compile error).

- [ ] **Step 3: Create `TreeSetLiveControlsDemo`**

Create `src/main/java/com/gimlism/translucent/treeset/demo/TreeSetLiveControlsDemo.java`:

```java
package com.gimlism.translucent.treeset.demo;

import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.repl.TreeSetCommandInterpreter;
import com.gimlism.translucent.treeset.viz.TreeSetLiveVisualizer;
import com.gimlism.translucent.treeset.viz.TreeSetWebExporter;
import java.io.IOException;
import java.util.function.Function;

/**
 * Browser-driven live demo: type commands into the page (a box wired to {@code POST /command}) and
 * watch each mutation render live — and, because the set narrates comparison reads, watch a
 * {@code contains}/{@code floor} walk animate the comparison cursor live. The set lives server-side;
 * the browser is the REPL over HTTP, running the SAME {@link TreeSetCommandInterpreter} as
 * {@link TreeSetLiveReplDemo}. No stdin. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.treeset.demo.TreeSetLiveControlsDemo}
 * The set starts empty; the server keeps running until Ctrl-C.
 */
public class TreeSetLiveControlsDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        var set = new TeachingTreeSet<Integer>();
        Function<String, String> handler = commandHandler(set, new TreeSetCommandInterpreter());

        LiveServer server = new LiveServer(TreeSetWebExporter.controlsHtml(), "127.0.0.1", DEFAULT_PORT, handler);
        server.start();
        set.addListener(new TreeSetLiveVisualizer(server::broadcast));

        String url = "http://localhost:" + server.port();
        BrowserLauncher.open(url);
        System.out.println("Live controls — serving at " + url + " — type commands in the browser; Ctrl-C to stop.");

        DemoLifecycle.awaitShutdown(server);
    }

    /**
     * The set-specific command seam (tested): applies each POSTed line to {@code set} via
     * {@code interpreter}, returning the message to show. Serialized behind a private lock because
     * {@link LiveServer}'s virtual-thread executor can dispatch overlapping POSTs onto a
     * non-thread-safe {@link TeachingTreeSet}. {@code quit}/{@code exit} return their message but do
     * NOT stop the server — a stray POST must not kill the session (the seam holds no server).
     */
    static Function<String, String> commandHandler(TeachingTreeSet<Integer> set,
            TreeSetCommandInterpreter interpreter) {
        final Object lock = new Object();
        return line -> {
            synchronized (lock) {
                return interpreter.execute(line, set).message();
            }
        };
    }
}
```

- [ ] **Step 4: Run the e2e test to verify it passes**

Run: `mvn -q test -Dtest=TreeSetLiveControlsEndToEndTest`
Expected: PASS — all 3 methods green (Add frame surfaces, Compare frame surfaces, quit leaves the set unchanged).

- [ ] **Step 5: Run the full suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS. After Task 1's 473, this task adds 3 e2e methods → 476.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/treeset/demo/TreeSetLiveControlsDemo.java \
        src/test/java/com/gimlism/translucent/treeset/demo/TreeSetLiveControlsEndToEndTest.java
git commit -m "feat(treeset): TreeSetLiveControlsDemo — browser POST /command over the live mirror (Slice D)"
```

---

## After both tasks: browser-verify (controller, not an automated test)

The `if (CONTROLS)` JS is untested-by-design (browser JS). Per the recurring live-viz recipe, browser-verify via Chrome MCP over `http://localhost:7070` (the extension blocks `file://`):

1. `mvn -q process-classes` to compile to `target/classes`.
2. Start `TreeSetLiveControlsDemo` serving on 7070 (a throwaway driver, since the demo blocks on `awaitShutdown`).
3. Drive the **real** box by setting `cmd.value` and clicking `#run` (or dispatching Enter):
   - **CONTROLS on:** the input + Run button are unhidden.
   - **Mutation renders live:** `add 30`, `add 10`, `add 50`, `add 20` → counter climbs, the RB tree grows with visible rotations/recolours.
   - **The distinctive check — type a comparison read:** `floor 25` (or `contains 30`) → the comparison cursor animates down the tree (Compare frames) AND the `#cmdout` reply shows (`floor 25 → 20` / `contains 30 → true`), with the tree otherwise settled. This is the gap Slice B's script-driven verify left open.
   - **Read-no-mutation:** `size` → reply shows, tree unchanged.
   - **Reload → snapshot-on-connect:** the last frame is restored.
   - **Zero console errors** throughout.

Capture the outcome in the PR / memory. Then: whole-branch opus review → PR → Copilot triage → `gh pr merge N --merge` (NOT squash, NOT `--delete-branch`) on user go-ahead.

## Self-Review notes (already reconciled)

- **Spec coverage:** `controlsHtml()` (Task 1 Step 3) ✓; four `treeset-viz.html` edits — CSS/box/refs/CONTROLS block (Task 1 Steps 4–7) ✓; placeholder `add 30 · contains 30 · floor 25 · pollFirst · help` (Step 5) ✓; `TreeSetWebExporterControlsTest` (Step 1) ✓; stale-assertion removal (Step 8, confirmed present at `TreeSetWebExporterLiveTest:20`) ✓; `TreeSetLiveControlsDemo` + `commandHandler` seam (Task 2 Step 3) ✓; parity Add-frame e2e + novel Compare-frame e2e + quit test (Task 2 Step 1) ✓; browser-verify with a typed comparison read (above) ✓.
- **Not touched:** interpreter, LiveServer, WebVizTemplate, live visualizer, serializer, core/events/substrate, and the render JS — diff is exactly the ~3 production files (+ 2 test files) above → snapshot-before-settled bug has zero surface.
- **Type consistency:** `controlsHtml()` / `commandHandler(TeachingTreeSet<Integer>, TreeSetCommandInterpreter)` / `execute(...).message()` used identically across tasks; frame assertions match the serializer's `"type":"Add"` / `"type":"Compare"` and string elements.
