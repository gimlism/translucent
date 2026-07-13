# ArrayList Live Browser Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add POST-driven browser controls to the ArrayList live viz, and consolidate both web exporters' template-inject into a shared substrate helper (which fixes the MapWebExporter token-ordering bug, Issue #23).

**Architecture:** A new `substrate/viz/WebVizTemplate.inject(resource, frames, live, controls)` centralizes template reading + 3-token substitution with the correct ordering (fixed LIVE/CONTROLS first, user FRAMES last). Both `MapWebExporter` and `ListWebExporter` delegate to it; `ListWebExporter` gains `controlsHtml()`; `list-viz.html` gains a CONTROLS-gated command box POSTing to the existing `/command` endpoint; `ListLiveControlsDemo` wires the merged `ListCommandInterpreter` into a serialized command handler. `LiveServer` and `JsonWriter` are untouched.

**Tech Stack:** Java 21 (build on JDK 26, `maven.compiler.release=21`), Maven, JUnit 5, vanilla JS/SVG in the HTML template.

## Global Constraints

- Java 21 language level; build/test with `mvn` (source of truth — ignore stale Eclipse LSP diagnostics).
- No new dependencies. `LiveServer` (POST /command + 405 handler, from HashMap Slice 3) and `JsonWriter` are reused verbatim and MUST NOT be modified — so the recurring "snapshot-before-settled" after()-timing bug has zero new surface.
- The three shared template tokens are exactly `/*__FRAMES__*/`, `/*__LIVE__*/`, `/*__CONTROLS__*/`. Substitution order is load-bearing: replace the FIXED `LIVE` and `CONTROLS` first, inject the USER-controlled `FRAMES` last.
- `MapWebExporter` / `ListWebExporter` keep their public surface: `toHtml`=frames/false/false, `liveHtml`=null/true/false, `controlsHtml`=null/true/true, `writeHtml` writes `toHtml`. The injected flag literals are the strings `"true"`/`"false"` and `"null"` (so the page reads `const LIVE = true;`, `const DATA = null;`, etc.).
- The `list-viz.html` CONTROLS branch mirrors `map-viz.html`: same element ids (`cmd`/`run`/`cmdout`), same `if (CONTROLS)` submit logic, POST body is the raw line, response is TEXT ONLY (the SVG redraws only via the existing SSE `onmessage` path — never off the POST reply). Read commands (`get`/`size`/`help`) fire no event, so the POST reply is their only feedback.
- The demo `commandHandler` seam serializes `interpreter.execute` behind a private lock (the virtual-thread executor can dispatch overlapping POSTs onto the non-thread-safe `TeachingArrayList`); lock order is one-directional `commandLock → LiveServer.lock`. `quit`/`exit` return their message but the seam holds NO server reference, so they cannot stop the session.
- Commit convention: end messages with `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.

## File Structure

- Create `src/main/java/com/gimlism/translucent/substrate/viz/WebVizTemplate.java` — shared read + 3-token inject.
- Create `src/test/resources/web/webviztemplate-test.html` and `…/webviztemplate-missing.html` — isolated fixtures for the helper test.
- Create `src/test/java/com/gimlism/translucent/substrate/viz/WebVizTemplateTest.java`.
- Modify `src/main/java/com/gimlism/translucent/hashmap/viz/MapWebExporter.java` — delegate to the helper (fixes #23).
- Modify `src/main/java/com/gimlism/translucent/arraylist/viz/ListWebExporter.java` — delegate + add `controlsHtml()`.
- Modify `src/main/resources/web/list-viz.html` — CONTROLS token, command box, CONTROLS branch.
- Create `src/test/java/com/gimlism/translucent/arraylist/viz/ListWebExporterControlsTest.java`.
- Modify `src/test/java/com/gimlism/translucent/hashmap/viz/MapWebExporterLiveTest.java` — add the #23 regression.
- Create `src/main/java/com/gimlism/translucent/arraylist/demo/ListLiveControlsDemo.java`.
- Create `src/test/java/com/gimlism/translucent/arraylist/demo/ListLiveControlsEndToEndTest.java`.

Two tasks: (1) the shared helper + both exporters + template CONTROLS + all exporter/template tests (one coherent, independently-testable unit — the helper's 3-token requirement forces template and exporters to move together); (2) the demo + its seam/e2e tests.

---

### Task 1: WebVizTemplate consolidation + exporters + list-viz CONTROLS

**Files:**
- Create: `src/main/java/com/gimlism/translucent/substrate/viz/WebVizTemplate.java`
- Create: `src/test/resources/web/webviztemplate-test.html`
- Create: `src/test/resources/web/webviztemplate-missing.html`
- Create: `src/test/java/com/gimlism/translucent/substrate/viz/WebVizTemplateTest.java`
- Modify: `src/main/java/com/gimlism/translucent/hashmap/viz/MapWebExporter.java`
- Modify: `src/main/java/com/gimlism/translucent/arraylist/viz/ListWebExporter.java`
- Modify: `src/main/resources/web/list-viz.html`
- Create: `src/test/java/com/gimlism/translucent/arraylist/viz/ListWebExporterControlsTest.java`
- Modify: `src/test/java/com/gimlism/translucent/hashmap/viz/MapWebExporterLiveTest.java`

**Interfaces:**
- Produces: `WebVizTemplate.inject(String resource, String framesReplacement, String liveReplacement, String controlsReplacement) → String` (static); `ListWebExporter.controlsHtml() → String` (static); `MapWebExporter`/`ListWebExporter` public methods unchanged in signature.
- Consumes: existing templates `/web/map-viz.html` (already has all 3 tokens) and `/web/list-viz.html` (gains the CONTROLS token in this task).

- [ ] **Step 1: Write the helper's failing test + fixtures**

Create `src/test/resources/web/webviztemplate-test.html` (single line, all three tokens):

```
LIVE=/*__LIVE__*/ CONTROLS=/*__CONTROLS__*/ DATA=/*__FRAMES__*/
```

Create `src/test/resources/web/webviztemplate-missing.html` (no CONTROLS token):

```
LIVE=/*__LIVE__*/ DATA=/*__FRAMES__*/
```

Create `src/test/java/com/gimlism/translucent/substrate/viz/WebVizTemplateTest.java`:

```java
package com.gimlism.translucent.substrate.viz;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebVizTemplateTest {

    private static final String T = "/web/webviztemplate-test.html";

    @Test
    void substitutesAllThreeTokens() {
        String out = WebVizTemplate.inject(T, "{\"frames\":[]}", "true", "false");
        assertTrue(out.contains("LIVE=true CONTROLS=false DATA={\"frames\":[]}"), out);
        assertFalse(out.contains("/*__"), "no token left behind");
    }

    @Test
    void missingTokenThrowsNamingTheToken() {
        var e = assertThrows(IllegalStateException.class,
                () -> WebVizTemplate.inject("/web/webviztemplate-missing.html", "x", "true", "true"));
        assertTrue(e.getMessage().contains("/*__CONTROLS__*/"), e.getMessage());
    }

    @Test
    void missingResourceThrows() {
        var e = assertThrows(IllegalStateException.class,
                () -> WebVizTemplate.inject("/web/does-not-exist.html", "x", "true", "true"));
        assertTrue(e.getMessage().contains("not found"), e.getMessage());
    }

    @Test
    void userFramesContainingATokenLiteralAreNotCorrupted() {
        // Issue #23 at the helper level: a frames blob literally containing the LIVE/CONTROLS
        // token must be injected verbatim because FRAMES is substituted LAST.
        String frames = "{\"e\":\"/*__LIVE__*/ and /*__CONTROLS__*/\"}";
        String out = WebVizTemplate.inject(T, frames, "true", "true");
        assertTrue(out.contains(frames), "user token literals survive intact");
        assertTrue(out.startsWith("LIVE=true CONTROLS=true "), "the real flag tokens are still replaced");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test-compile`
Expected: FAIL — `WebVizTemplate` does not exist (compile error).

- [ ] **Step 3: Write WebVizTemplate**

Create `src/main/java/com/gimlism/translucent/substrate/viz/WebVizTemplate.java`:

```java
package com.gimlism.translucent.substrate.viz;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Shared assembler for the self-contained live-viz HTML pages. Reads a classpath template and
 * substitutes the three tokens every structure's page carries: a user-controlled {@code FRAMES}
 * data blob and two fixed mode flags {@code LIVE} and {@code CONTROLS}.
 *
 * <p>The fixed flags are replaced FIRST and the user-controlled {@code FRAMES} blob LAST, so a
 * frame whose serialized data happens to contain a token literal can never be rewritten by a
 * later substitution. Centralizing the order here makes it the single source of truth for every
 * per-structure exporter (map/list/trie), and is the fix for the earlier map-side ordering bug.
 */
public final class WebVizTemplate {

    private static final String FRAMES_TOKEN = "/*__FRAMES__*/";
    private static final String LIVE_TOKEN = "/*__LIVE__*/";
    private static final String CONTROLS_TOKEN = "/*__CONTROLS__*/";

    private WebVizTemplate() {}

    /**
     * Read {@code resource}, require all three tokens, and substitute them — fixed {@code LIVE}
     * and {@code CONTROLS} first, user-controlled {@code FRAMES} last.
     *
     * @param resource absolute classpath path, e.g. {@code /web/list-viz.html}
     */
    public static String inject(String resource, String framesReplacement,
            String liveReplacement, String controlsReplacement) {
        String template = read(resource);
        require(template, resource, FRAMES_TOKEN);
        require(template, resource, LIVE_TOKEN);
        require(template, resource, CONTROLS_TOKEN);
        return template
                .replace(LIVE_TOKEN, liveReplacement)
                .replace(CONTROLS_TOKEN, controlsReplacement)
                .replace(FRAMES_TOKEN, framesReplacement); // user data LAST
    }

    private static void require(String template, String resource, String token) {
        if (!template.contains(token)) {
            throw new IllegalStateException("template " + resource + " is missing token " + token);
        }
    }

    private static String read(String resource) {
        try (InputStream in = WebVizTemplate.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("template resource not found on classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

- [ ] **Step 4: Run the helper test to green**

Run: `mvn -q test -Dtest=WebVizTemplateTest`
Expected: PASS (4/4).

- [ ] **Step 5: Add the CONTROLS token, command box, and CONTROLS branch to list-viz.html**

Make four edits to `src/main/resources/web/list-viz.html`.

Edit 5a — CSS. After the line `  #counter { color:var(--muted); font-variant-numeric: tabular-nums; }` add:

```css
  #cmd { font:inherit; padding:6px 10px; border:1px solid var(--cellb); border-radius:8px;
         background:var(--card); color:var(--fg); min-width:220px; }
  #cmdout { color:var(--muted); font-variant-numeric: tabular-nums; }
```

Edit 5b — bar markup. Replace:

```html
    <button id="live" hidden></button>
  </div>
```

with:

```html
    <button id="live" hidden></button>
    <input id="cmd" type="text" placeholder="add hello · insert 1 x · remove 1 · help" autocomplete="off" hidden>
    <button id="run" hidden>Run</button>
    <span id="cmdout"></span>
  </div>
```

Edit 5c — the CONTROLS const + element refs. Replace:

```js
const DATA = /*__FRAMES__*/;
const LIVE = /*__LIVE__*/;
const frames = (DATA && DATA.frames) || [];
```

with:

```js
const DATA = /*__FRAMES__*/;
const LIVE = /*__LIVE__*/;
const CONTROLS = /*__CONTROLS__*/;
const frames = (DATA && DATA.frames) || [];
```

and replace:

```js
const liveBtn = document.getElementById("live");
```

with:

```js
const liveBtn = document.getElementById("live");
const cmdInput = document.getElementById("cmd");
const runBtn = document.getElementById("run");
const cmdOut = document.getElementById("cmdout");
```

Edit 5d — the CONTROLS submit branch inside `if (LIVE)`. Replace:

```js
if (LIVE) {
  const es = new EventSource("/events");
  es.onmessage = ev => {
    let f;
    try { f = JSON.parse(ev.data); } catch (_) { return; }
    frames.push(f);
    if (followTail) go(frames.length - 1);   // full render + cross-fade
    else refreshMeta();                        // studying an older frame: badge only
  };
}
render();
```

with:

```js
if (LIVE) {
  const es = new EventSource("/events");
  es.onmessage = ev => {
    let f;
    try { f = JSON.parse(ev.data); } catch (_) { return; }
    frames.push(f);
    if (followTail) go(frames.length - 1);   // full render + cross-fade
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

- [ ] **Step 6: Write the failing ListWebExporter controls test**

Create `src/test/java/com/gimlism/translucent/arraylist/viz/ListWebExporterControlsTest.java`:

```java
package com.gimlism.translucent.arraylist.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ListWebExporterControlsTest {

    @Test
    void controlsHtmlEnablesControlsAndLive() {
        String html = ListWebExporter.controlsHtml();
        assertTrue(html.contains("const CONTROLS = true;"), "controls flag on");
        assertTrue(html.contains("const LIVE = true;"), "live flag on");
        assertTrue(html.contains("const DATA = null;"), "no baked frames in live mode");
    }

    @Test
    void liveHtmlLeavesControlsOff() {
        String html = ListWebExporter.liveHtml();
        assertTrue(html.contains("const CONTROLS = false;"), "controls off in plain live mode");
        assertTrue(html.contains("const LIVE = true;"), "live flag still on");
    }

    @Test
    void bakedHtmlLeavesLiveAndControlsOff() {
        String html = ListWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.contains("const LIVE = false;"), "replay mode is not live");
        assertTrue(html.contains("const CONTROLS = false;"), "replay mode has no controls");
    }
}
```

- [ ] **Step 7: Run it to verify it fails**

Run: `mvn -q test-compile`
Expected: FAIL — `ListWebExporter.controlsHtml()` does not exist (compile error).

- [ ] **Step 8: Rewrite ListWebExporter to delegate + add controlsHtml**

Replace the entire body of `src/main/java/com/gimlism/translucent/arraylist/viz/ListWebExporter.java` with:

```java
package com.gimlism.translucent.arraylist.viz;

import com.gimlism.translucent.substrate.viz.WebVizTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assembles the self-contained ArrayList HTML page from {@code /web/list-viz.html} by delegating
 * token substitution to {@link WebVizTemplate}: a baked {@code FRAMES} JSON blob (from
 * {@link ListJsonSerializer}) plus the {@code LIVE} and {@code CONTROLS} mode flags. The template
 * carries the whole vanilla-JS/SVG renderer; this class only chooses the three replacement values.
 */
public final class ListWebExporter {

    private static final String TEMPLATE_RESOURCE = "/web/list-viz.html";

    private ListWebExporter() {}

    /** The self-contained baked HTML with {@code framesJson} injected; live + controls OFF. */
    public static String toHtml(String framesJson) {
        return WebVizTemplate.inject(TEMPLATE_RESOURCE, framesJson, "false", "false");
    }

    /** Live mode (SSE), no browser controls: {@code DATA = null}, live ON, controls OFF. */
    public static String liveHtml() {
        return WebVizTemplate.inject(TEMPLATE_RESOURCE, "null", "true", "false");
    }

    /** Live mode with browser command controls: {@code DATA = null}, live ON, controls ON. */
    public static String controlsHtml() {
        return WebVizTemplate.inject(TEMPLATE_RESOURCE, "null", "true", "true");
    }

    /** Write {@link #toHtml(String)} to {@code out} (UTF-8). */
    public static void writeHtml(String framesJson, Path out) throws IOException {
        Files.writeString(out, toHtml(framesJson));
    }
}
```

- [ ] **Step 9: Run the list exporter tests to green**

Run: `mvn -q test -Dtest=ListWebExporterControlsTest,ListWebExporterTest,ListWebExporterLiveTest`
Expected: PASS. (Existing `ListWebExporterLiveTest.bakedFrameDataContainingTheLiveTokenIsNotCorrupted` still passes — the shared helper injects FRAMES last.)

- [ ] **Step 10: Add the map #23 regression test (RED before the map reroute)**

Add this test method to `src/test/java/com/gimlism/translucent/hashmap/viz/MapWebExporterLiveTest.java` (inside the class):

```java
    @Test
    void bakedFrameDataContainingTheLiveTokenIsNotCorrupted() {
        // Issue #23: a serialized map key/value can contain the literal token as user data. Token
        // substitution must inject the user frames LAST (via the shared WebVizTemplate) so it never
        // rewrites their content.
        String frames = "{\"frames\":[{\"highlightKey\":\"/*__LIVE__*/\"}]}";
        String html = MapWebExporter.toHtml(frames);
        assertTrue(html.contains("\"highlightKey\":\"/*__LIVE__*/\""), "user data containing the LIVE token survives intact");
        assertTrue(html.contains("const LIVE = false;"), "the real LIVE token is still replaced");
    }
```

Run: `mvn -q test -Dtest=MapWebExporterLiveTest`
Expected: FAIL on the new test — the current `MapWebExporter.inject` replaces FRAMES first, so `/*__LIVE__*/` inside the frames is rewritten to `false` (`"highlightKey":"false"`), and the `assertTrue(... survives intact)` fails.

- [ ] **Step 11: Rewrite MapWebExporter to delegate (fixes #23)**

Replace the entire body of `src/main/java/com/gimlism/translucent/hashmap/viz/MapWebExporter.java` with:

```java
package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.substrate.viz.WebVizTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assembles the self-contained HashMap HTML page from {@code /web/map-viz.html} by delegating token
 * substitution to {@link WebVizTemplate}: a baked {@code FRAMES} JSON blob (from
 * {@link MapJsonSerializer}) plus the {@code LIVE} and {@code CONTROLS} mode flags. The template
 * carries the whole vanilla-JS/SVG renderer; this class only chooses the three replacement values.
 */
public final class MapWebExporter {

    private static final String TEMPLATE_RESOURCE = "/web/map-viz.html";

    private MapWebExporter() {}

    /** The self-contained baked HTML with {@code framesJson} injected; live + controls OFF. */
    public static String toHtml(String framesJson) {
        return WebVizTemplate.inject(TEMPLATE_RESOURCE, framesJson, "false", "false");
    }

    /** Live mode (SSE), no browser controls: {@code DATA = null}, live ON, controls OFF. */
    public static String liveHtml() {
        return WebVizTemplate.inject(TEMPLATE_RESOURCE, "null", "true", "false");
    }

    /** Live mode with browser command controls: {@code DATA = null}, live ON, controls ON. */
    public static String controlsHtml() {
        return WebVizTemplate.inject(TEMPLATE_RESOURCE, "null", "true", "true");
    }

    /** Write {@link #toHtml(String)} to {@code out} (UTF-8). */
    public static void writeHtml(String framesJson, Path out) throws IOException {
        Files.writeString(out, toHtml(framesJson));
    }
}
```

- [ ] **Step 12: Run the full suite**

Run: `mvn -q test`
Expected: PASS — all prior tests plus the new helper (4), list controls (3), and map #23 regression (1). Confirms the map exporter tests (`MapWebExporterTest`, `MapWebExporterLiveTest`, `MapWebExporterControlsTest`, `LiveControlsEndToEndTest`) still pass through the shared helper.

- [ ] **Step 13: Commit**

```bash
git add src/main/java/com/gimlism/translucent/substrate/viz/WebVizTemplate.java \
  src/test/resources/web/webviztemplate-test.html src/test/resources/web/webviztemplate-missing.html \
  src/test/java/com/gimlism/translucent/substrate/viz/WebVizTemplateTest.java \
  src/main/java/com/gimlism/translucent/hashmap/viz/MapWebExporter.java \
  src/main/java/com/gimlism/translucent/arraylist/viz/ListWebExporter.java \
  src/main/resources/web/list-viz.html \
  src/test/java/com/gimlism/translucent/arraylist/viz/ListWebExporterControlsTest.java \
  src/test/java/com/gimlism/translucent/hashmap/viz/MapWebExporterLiveTest.java
git commit -m "feat(viz): shared WebVizTemplate + ListWebExporter.controlsHtml + #23 fix

Extract both web exporters' read+inject into substrate/viz/WebVizTemplate
(fixed LIVE/CONTROLS replaced first, user FRAMES last). MapWebExporter now
routes through it, fixing the token-ordering bug (Issue #23) where a map
key/value containing a token literal was corrupted in baked toHtml.
ListWebExporter gains controlsHtml(); list-viz.html gains a CONTROLS-gated
command box POSTing to /command.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: ListLiveControlsDemo + seam / end-to-end tests

**Files:**
- Create: `src/main/java/com/gimlism/translucent/arraylist/demo/ListLiveControlsDemo.java`
- Create: `src/test/java/com/gimlism/translucent/arraylist/demo/ListLiveControlsEndToEndTest.java`

**Interfaces:**
- Consumes: `ListWebExporter.controlsHtml()` (Task 1); `ListCommandInterpreter.execute` / `CommandResult` (Slice C); `LiveServer(String, String, int, Function<String,String>)` with `start()`/`stop()`/`port()`/`openConnections()`/`broadcast` (Slice 3); `ListLiveVisualizer(Consumer<String>)`; `BrowserLauncher.open`; `DemoLifecycle.awaitShutdown`.
- Produces: `ListLiveControlsDemo.main(String[])`; tested seam `static Function<String,String> commandHandler(TeachingArrayList<String> list, ListCommandInterpreter interpreter)`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/gimlism/translucent/arraylist/demo/ListLiveControlsEndToEndTest.java`:

```java
package com.gimlism.translucent.arraylist.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.repl.ListCommandInterpreter;
import com.gimlism.translucent.arraylist.viz.ListLiveVisualizer;
import com.gimlism.translucent.arraylist.viz.ListWebExporter;
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

/** Proves Slice D: a browser POST /command mutates the list and surfaces as a live SSE frame. */
class ListLiveControlsEndToEndTest {

    @Test
    void aPostedCommandMutatesTheListAndSurfacesAsALiveFrame() throws IOException {
        var list = new TeachingArrayList<String>();
        Function<String, String> handler = ListLiveControlsDemo.commandHandler(list, new ListCommandInterpreter());
        LiveServer server = new LiveServer(ListWebExporter.controlsHtml(), "127.0.0.1", 0, handler);
        server.start();
        try {
            list.addListener(new ListLiveVisualizer(server::broadcast));

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
                                    .POST(BodyPublishers.ofString("add hi", StandardCharsets.UTF_8)).build(),
                            BodyHandlers.ofString());
                    assertEquals("appended \"hi\" at 0", resp.body());

                    // the mutation's frame arrives on the SSE stream
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String frame = line.substring("data: ".length());
                            assertTrue(frame.contains("\"type\":\"Append\""), "frame carries the Append event");
                            return;
                        }
                    }
                    throw new IOException("no data line received");
                }
            });
        } finally {
            server.stop();
        }
    }

    @Test
    void quitReturnsItsMessageWithoutStoppingOrMutating() {
        var list = new TeachingArrayList<String>();
        Function<String, String> handler = ListLiveControlsDemo.commandHandler(list, new ListCommandInterpreter());
        handler.apply("add a"); // list now has one element
        assertEquals("bye", handler.apply("quit"));
        assertEquals("bye", handler.apply("exit"));
        // the seam holds no server reference, so quit/exit cannot stop the session; and they are
        // pure reads through the interpreter, so the list is untouched.
        assertEquals(1, list.size(), "quit/exit leave the list unchanged");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test-compile`
Expected: FAIL — `ListLiveControlsDemo` does not exist (compile error).

- [ ] **Step 3: Write ListLiveControlsDemo**

Create `src/main/java/com/gimlism/translucent/arraylist/demo/ListLiveControlsDemo.java`:

```java
package com.gimlism.translucent.arraylist.demo;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.repl.ListCommandInterpreter;
import com.gimlism.translucent.arraylist.viz.ListLiveVisualizer;
import com.gimlism.translucent.arraylist.viz.ListWebExporter;
import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
import com.gimlism.translucent.substrate.viz.LiveServer;
import java.io.IOException;
import java.util.function.Function;

/**
 * Browser-driven live demo: type commands into the page (a box wired to {@code POST /command}) and
 * watch each mutation render live. The list lives server-side; the browser is the REPL over HTTP,
 * running the SAME {@link ListCommandInterpreter} as {@link ListLiveReplDemo}. No stdin. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListLiveControlsDemo}
 * The list starts empty; the server keeps running until Ctrl-C.
 */
public class ListLiveControlsDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        var list = new TeachingArrayList<String>();
        Function<String, String> handler = commandHandler(list, new ListCommandInterpreter());

        LiveServer server = new LiveServer(ListWebExporter.controlsHtml(), "127.0.0.1", DEFAULT_PORT, handler);
        server.start();
        list.addListener(new ListLiveVisualizer(server::broadcast));

        String url = "http://localhost:" + server.port();
        BrowserLauncher.open(url);
        System.out.println("Live controls — serving at " + url + " — type commands in the browser; Ctrl-C to stop.");

        DemoLifecycle.awaitShutdown(server);
    }

    /**
     * The list-specific command seam (tested): applies each POSTed line to {@code list} via
     * {@code interpreter}, returning the message to show. Serialized behind a private lock because
     * {@link LiveServer}'s virtual-thread executor can dispatch overlapping POSTs onto a
     * non-thread-safe {@link TeachingArrayList}. {@code quit}/{@code exit} return their message but
     * do NOT stop the server — a stray POST must not kill the session (the seam holds no server).
     */
    static Function<String, String> commandHandler(TeachingArrayList<String> list,
            ListCommandInterpreter interpreter) {
        final Object lock = new Object();
        return line -> {
            synchronized (lock) {
                return interpreter.execute(line, list).message();
            }
        };
    }
}
```

- [ ] **Step 4: Run the tests to green**

Run: `mvn -q test -Dtest=ListLiveControlsEndToEndTest`
Expected: PASS (2/2).

- [ ] **Step 5: Run the full suite**

Run: `mvn -q test`
Expected: PASS — the full suite including Task 1's and Task 2's additions.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/arraylist/demo/ListLiveControlsDemo.java \
  src/test/java/com/gimlism/translucent/arraylist/demo/ListLiveControlsEndToEndTest.java
git commit -m "feat(demo): ListLiveControlsDemo — browser command controls for the ArrayList

Wires the merged ListCommandInterpreter into a serialized commandHandler
seam behind LiveServer's POST /command, serving ListWebExporter.controlsHtml().
Headless e2e proves POST -> mutation -> Append frame over SSE; a seam test
proves quit/exit return their message without stopping or mutating.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

- **Spec coverage:** `WebVizTemplate` consolidation (Task 1 Steps 1-4); `MapWebExporter` reroute + #23 fix (Steps 10-11); `ListWebExporter` reroute + `controlsHtml()` (Steps 6-9); `list-viz.html` CONTROLS token + command box + submit branch (Step 5); `ListLiveControlsDemo` + serialized seam + `quit`-doesn't-stop (Task 2); load-bearing read-no-frame reply exercised by the e2e's text assertion + the seam test. `LiveServer`/`JsonWriter` untouched (no task modifies them). All spec sections mapped.
- **Placeholder scan:** none — every step has full code/commands.
- **Type consistency:** `WebVizTemplate.inject(String, String, String, String)` is called identically by both exporters and the test. `commandHandler(TeachingArrayList<String>, ListCommandInterpreter) → Function<String,String>` matches the map's `LiveControlsDemo` shape and the e2e/seam tests. Flag literals are the strings `"true"`/`"false"`/`"null"`, producing `const LIVE = true;` etc. as the controls tests assert. The Append frame's `"type":"Append"` matches the Slice-B `ListLiveVizEndToEndTest` and `ListJsonSerializer` output.

## Post-implementation (controller, after both tasks)

- Whole-branch review (opus) per project workflow — special attention: `LiveServer`/`JsonWriter` truly untouched; both exporters behavior-preserved except the intended #23 fix; the demo lock order is `commandLock → LiveServer.lock` with no reverse path.
- Live browser-verification per the recurring recipe (Chrome MCP over `http://localhost:7070`, run `ListLiveControlsDemo` headless against `target/classes` after `mvn -q process-classes`): controls render; `add`/`insert`/`remove` redraw the SVG + show the reply text; a read (`get`/`size`) shows a reply with NO SVG change (the read-no-frame proof); snapshot-on-connect survives reload; zero console errors.
- PR via `gh`, Copilot triage (close Issue #23 in the PR), then `gh pr merge N --merge` (no `--delete-branch`) on user go-ahead.
