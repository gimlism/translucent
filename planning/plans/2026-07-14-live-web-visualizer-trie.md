# Trie Web Visualizer — Slice B (live SSE mirror) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Slice A's baked static replay into a live connect-then-stream mirror: a running `RadixTrie` pushes each mutation's frame over SSE to the browser, which appends and follows the tail.

**Architecture:** Reuses the live-viz substrate verbatim (`LiveServer`, `JsonWriter`, `BrowserLauncher`, `DemoLifecycle`). New code is per-structure only: a `toFrame` on the serializer, a stateless `TrieLiveVisualizer` listener, a `liveHtml` exporter variant, the `LIVE`-branch JS on `trie-viz.html`, and a demo. No core/events/substrate change → snapshot-before-settled bug keeps zero surface.

**Tech Stack:** Java 21 (built on JDK 26, `maven.compiler.release=21`), Maven, JUnit 5, vanilla JS/SVG, SSE over the JDK `HttpServer`.

## Global Constraints

- **Target Java 21** (`maven.compiler.release=21`); build/test on JDK 26. `mvn` is the source of truth — ignore stale Eclipse-LSP "cannot be resolved" diagnostics.
- **Do NOT modify** `trie/core/**`, `trie/events/**`, `substrate/viz/LiveServer`, `substrate/viz/JsonWriter`, or `substrate/viz/WebVizTemplate`. Slice B edits only `trie/viz`, `trie/demo`, and `resources/web/trie-viz.html`.
- **No upstream/controls in this slice:** the `CONTROLS` token stays baked `false`. `trie-viz.html` must gain the `LIVE`/SSE branch but NO command box, NO `fetch`, NO `POST /command` — those are Slice D.
- **The live-listener must not swallow:** `TrieLiveVisualizer.onEvent` adds no `try/catch` — serialization is a pure read; a catch-all would hide a mid-mutation bug (matches `ListLiveVisualizer`/`MapLiveVisualizer`).
- **The SVG/live JS is untested-by-design** — verified by the controller in a browser (Task 3 final step), never by JUnit.
- **Commit footer** on every commit:
  ```
  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm
  ```

## File Structure

| File | Task | Responsibility |
|---|---|---|
| `src/main/java/com/gimlism/translucent/trie/viz/TrieJsonSerializer.java` | 1 | Add `toFrame(TrieEvent)` (single frame, no wrapper). |
| `src/main/java/com/gimlism/translucent/trie/viz/TrieLiveVisualizer.java` | 1 | Stateless listener: `onEvent` → `sink.accept(toFrame(e))`. |
| `src/test/java/com/gimlism/translucent/trie/viz/TrieJsonSerializerTest.java` | 1 | Add a `toFrame` case. |
| `src/test/java/com/gimlism/translucent/trie/viz/TrieLiveVisualizerTest.java` | 1 | One frame per event == `toFrame`. |
| `src/main/java/com/gimlism/translucent/trie/viz/TrieWebExporter.java` | 2 | Add `liveHtml()` (DATA=null, LIVE=true, CONTROLS=false). |
| `src/main/resources/web/trie-viz.html` | 2 | Wire the `LIVE`/SSE branch + follow-tail + jump-to-live badge. |
| `src/test/java/com/gimlism/translucent/trie/viz/TrieWebExporterLiveTest.java` | 2 | `liveHtml` bakes null/true/false + opens EventSource. |
| `src/main/java/com/gimlism/translucent/trie/demo/TrieLiveWebVizDemo.java` | 3 | Live student sandbox (shore/she/shell story, then park). |
| `src/test/java/com/gimlism/translucent/trie/viz/TrieLiveVizEndToEndTest.java` | 3 | Headless: a trie mutation surfaces as a live SSE frame over HTTP. |

---

## Task 1: toFrame + TrieLiveVisualizer

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/trie/viz/TrieJsonSerializer.java`
- Create: `src/main/java/com/gimlism/translucent/trie/viz/TrieLiveVisualizer.java`
- Modify test: `src/test/java/com/gimlism/translucent/trie/viz/TrieJsonSerializerTest.java`
- Create test: `src/test/java/com/gimlism/translucent/trie/viz/TrieLiveVisualizerTest.java`

**Interfaces:**
- Consumes: existing private `TrieJsonSerializer.writeFrame(JsonWriter, TrieEvent)`; `JsonWriter`; `TrieEventListener` (`@FunctionalInterface`, `onEvent(TrieEvent)`); `TrieRecordingListener.events() → List<TrieEvent>`; `RadixTrie<V>` (`addListener`, `put`).
- Produces: `TrieJsonSerializer.toFrame(TrieEvent) → String`; `new TrieLiveVisualizer(Consumer<String> sink)` — consumed by Tasks 2/3.

- [ ] **Step 1: Add the `toFrame` test case**

In `src/test/java/com/gimlism/translucent/trie/viz/TrieJsonSerializerTest.java`, add this method inside the class (leave the existing tests unchanged):

```java
    @Test
    void toFrameProducesASingleFrameWithNoFramesWrapper() {
        var trie = new RadixTrie<Integer>();
        var rec = new TrieRecordingListener();
        trie.addListener(rec);
        trie.put("shore", 1);

        // toFrame of the last event equals that event's frame inside the full toJson blob
        var events = rec.events();
        String frame = TrieJsonSerializer.toFrame(events.get(events.size() - 1));

        assertFalse(frame.contains("\"frames\""), "single frame carries no frames wrapper");
        assertTrue(frame.startsWith("{\"event\":{"), frame);
        assertTrue(frame.contains("\"type\":\"Put\""), frame);
        assertTrue(frame.contains("\"trie\":{\"size\":1"), frame);
        assertTrue(TrieJsonSerializer.toJson(events).contains(frame),
                "the single frame is a substring of the full frames blob");
    }
```

(The test file already imports `assertTrue`/`assertFalse`, `RadixTrie`, `TrieRecordingListener`, and `Test` from Task 1 of Slice A.)

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -Dtest=TrieJsonSerializerTest test`
Expected: FAIL — `toFrame` is not defined (compilation error).

- [ ] **Step 3: Add `toFrame` to the serializer**

In `src/main/java/com/gimlism/translucent/trie/viz/TrieJsonSerializer.java`, add this method immediately after `toJson` (it reuses the existing private `writeFrame`):

```java
    /** One event → the JSON for a single {@code { "event":…, "trie":… }} frame (no {@code frames} wrapper). */
    public static String toFrame(TrieEvent e) {
        JsonWriter w = new JsonWriter();
        writeFrame(w, e);
        return w.toString();
    }
```

- [ ] **Step 4: Run it to verify it passes**

Run: `mvn -q -Dtest=TrieJsonSerializerTest test`
Expected: PASS (6 tests: the 5 from Slice A + the new one).

- [ ] **Step 5: Write the failing live-visualizer test**

Create `src/test/java/com/gimlism/translucent/trie/viz/TrieLiveVisualizerTest.java`:

```java
package com.gimlism.translucent.trie.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.core.RadixTrie;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class TrieLiveVisualizerTest {

    @Test
    void emitsOneFramePerEventEqualToToFrame() {
        var frames = new ArrayList<String>();
        var rec = new TrieRecordingListener();
        var trie = new RadixTrie<Integer>();
        trie.addListener(new TrieLiveVisualizer(frames::add));
        trie.addListener(rec);

        trie.put("shore", 1);
        trie.put("she", 2);   // SplitEdge -> CreateNode -> Put: several events
        trie.remove("she");   // Remove (+ compression): several more events

        assertTrue(frames.size() >= 3, "at least one frame per mutation event");
        assertEquals(rec.events().size(), frames.size(), "exactly one frame per event");
        for (int i = 0; i < frames.size(); i++) {
            assertEquals(TrieJsonSerializer.toFrame(rec.events().get(i)), frames.get(i),
                    "frame " + i + " equals the serializer's toFrame of that event");
        }
    }
}
```

- [ ] **Step 6: Run it to verify it fails**

Run: `mvn -q -Dtest=TrieLiveVisualizerTest test`
Expected: FAIL — `TrieLiveVisualizer` does not exist (compilation error).

- [ ] **Step 7: Implement the live visualizer**

Create `src/main/java/com/gimlism/translucent/trie/viz/TrieLiveVisualizer.java`:

```java
package com.gimlism.translucent.trie.viz;

import com.gimlism.translucent.trie.events.TrieEvent;
import com.gimlism.translucent.trie.events.TrieEventListener;
import java.util.function.Consumer;

/**
 * Live twin of {@code TrieRecordingListener}: instead of buffering the stream, it serializes each
 * event to one frame ({@link TrieJsonSerializer#toFrame}) and hands it to a sink — normally a
 * {@code LiveServer}'s {@code broadcast}. Stateless; the server owns snapshot-on-connect.
 *
 * <p>Per the {@link TrieEventListener} contract it neither mutates the trie nor throws:
 * serialization is a pure read of the event's snapshot.
 */
public final class TrieLiveVisualizer implements TrieEventListener {

    private final Consumer<String> sink;

    public TrieLiveVisualizer(Consumer<String> sink) {
        this.sink = sink;
    }

    @Override
    public void onEvent(TrieEvent event) {
        sink.accept(TrieJsonSerializer.toFrame(event));
    }
}
```

- [ ] **Step 8: Run it to verify it passes**

Run: `mvn -q -Dtest=TrieLiveVisualizerTest test`
Expected: PASS (1 test).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/viz/TrieJsonSerializer.java \
        src/main/java/com/gimlism/translucent/trie/viz/TrieLiveVisualizer.java \
        src/test/java/com/gimlism/translucent/trie/viz/TrieJsonSerializerTest.java \
        src/test/java/com/gimlism/translucent/trie/viz/TrieLiveVisualizerTest.java
git commit -m "feat(trie-viz): TrieJsonSerializer.toFrame + TrieLiveVisualizer

Single-frame serialization + a stateless listener that broadcasts each
event's frame to a sink. Mirrors ListLiveVisualizer; the server owns
snapshot-on-connect.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm"
```

---

## Task 2: liveHtml exporter + LIVE-branch template JS

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/trie/viz/TrieWebExporter.java`
- Modify: `src/main/resources/web/trie-viz.html`
- Create test: `src/test/java/com/gimlism/translucent/trie/viz/TrieWebExporterLiveTest.java`

**Interfaces:**
- Consumes: `WebVizTemplate.inject`; the existing `TrieWebExporter.TEMPLATE_RESOURCE` + `toHtml`.
- Produces: `TrieWebExporter.liveHtml() → String` — consumed by Task 3 (`LiveServer` page + e2e).

- [ ] **Step 1: Write the failing exporter-live test**

Create `src/test/java/com/gimlism/translucent/trie/viz/TrieWebExporterLiveTest.java`:

```java
package com.gimlism.translucent.trie.viz;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrieWebExporterLiveTest {

    @Test
    void liveHtmlHasNoBakedFramesAndOpensAnEventSource() {
        String html = TrieWebExporter.liveHtml();
        assertTrue(html.toLowerCase().contains("<!doctype html"), "full document");
        assertFalse(html.contains("/*__FRAMES__*/"), "frames token replaced");
        assertFalse(html.contains("/*__LIVE__*/"), "live token replaced");
        assertTrue(html.contains("const DATA = null;"), "no baked frames in live mode");
        assertTrue(html.contains("const LIVE = true;"), "live mode on");
        assertTrue(html.contains("const CONTROLS = false;"), "controls stay off in slice B");
        assertTrue(html.contains("new EventSource(\"/events\")"), "opens the SSE stream");
        assertFalse(html.contains("/command"), "no upstream command path in slice B");
    }

    @Test
    void bakedHtmlStillInjectsFramesAndTurnsLiveOff() {
        String html = TrieWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.contains("const DATA = {\"frames\":[]};"), "baked frames injected");
        assertTrue(html.contains("const LIVE = false;"), "live mode off for the baked file");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -Dtest=TrieWebExporterLiveTest test`
Expected: FAIL — `liveHtml` is not defined AND/OR the template has no `new EventSource("/events")` yet.

- [ ] **Step 3: Add `liveHtml` to the exporter**

In `src/main/java/com/gimlism/translucent/trie/viz/TrieWebExporter.java`, add this method immediately after `toHtml`:

```java
    /** Live mode (SSE), no browser controls: {@code DATA = null}, live ON, controls OFF. */
    public static String liveHtml() {
        return WebVizTemplate.inject(TEMPLATE_RESOURCE, "null", "true", "false");
    }
```

- [ ] **Step 4: Wire the LIVE branch into the template**

Make exactly these five edits to `src/main/resources/web/trie-viz.html`. (Do not touch the `layout`/`renderFrame` SVG logic.)

**Edit 4a** — add the jump-to-live badge to the button bar. Replace:

```html
    <span id="counter"></span>
  </div>
  <div id="stage"><svg id="svg"></svg></div>
```

with:

```html
    <span id="counter"></span>
    <button id="live" hidden></button>
  </div>
  <div id="stage"><svg id="svg"></svg></div>
```

**Edit 4b** — grab the badge element. Replace:

```javascript
const playBtn = document.getElementById("play");

const PAD = 28, ROW_H = 80, X_GAP = 76, R = 13;
let idx = 0, timer = null;
```

with:

```javascript
const playBtn = document.getElementById("play");
const liveBtn = document.getElementById("live");

const PAD = 28, ROW_H = 80, X_GAP = 76, R = 13;
let idx = 0, timer = null, followTail = true;
```

**Edit 4c** — add the behind-count + badge to `refreshMeta`. Replace:

```javascript
function refreshMeta() {
  counter.textContent = frames.length ? `frame ${idx + 1} / ${frames.length}` : "";
  prevBtn.disabled = idx <= 0;
  nextBtn.disabled = idx >= frames.length - 1;
}
```

with:

```javascript
function refreshMeta() {
  counter.textContent = frames.length ? `frame ${idx + 1} / ${frames.length}` : "";
  prevBtn.disabled = idx <= 0;
  nextBtn.disabled = idx >= frames.length - 1;
  const behind = Math.max(0, frames.length - 1 - idx);
  liveBtn.hidden = !(LIVE && behind > 0);
  liveBtn.textContent = `⏭ ${behind} new — jump to live`;
}
```

**Edit 4d** — a live-aware empty caption + follow-tail tracking in `go`. Replace:

```javascript
function render() {
  const f = frames[idx];
  caption.textContent = f ? f.event.label : "(no events to replay)";
  if (f) renderFrame(f); else svg.innerHTML = "";
  stage.style.opacity = "0";
  requestAnimationFrame(() => requestAnimationFrame(() => { stage.style.opacity = "1"; }));
  refreshMeta();
}

function go(n) { idx = Math.max(0, Math.min(frames.length - 1, n)); render(); }
```

with:

```javascript
function render() {
  const f = frames[idx];
  caption.textContent = f ? f.event.label : (LIVE ? "(waiting for live events…)" : "(no events to replay)");
  if (f) renderFrame(f); else svg.innerHTML = "";
  stage.style.opacity = "0";
  requestAnimationFrame(() => requestAnimationFrame(() => { stage.style.opacity = "1"; }));
  refreshMeta();
}

function go(n) { idx = Math.max(0, Math.min(frames.length - 1, n)); followTail = idx >= frames.length - 1; render(); }
```

**Edit 4e** — the jump-to-live handler + the SSE branch. Replace:

```javascript
document.addEventListener("keydown", e => {
  if (e.key === "ArrowRight") { stop(); go(idx + 1); }
  else if (e.key === "ArrowLeft") { stop(); go(idx - 1); }
});
render();
```

with:

```javascript
document.addEventListener("keydown", e => {
  if (e.key === "ArrowRight") { stop(); go(idx + 1); }
  else if (e.key === "ArrowLeft") { stop(); go(idx - 1); }
});
liveBtn.onclick = () => { stop(); go(frames.length - 1); };

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

- [ ] **Step 5: Run it to verify it passes**

Run: `mvn -q -Dtest=TrieWebExporterLiveTest test`
Expected: PASS (2 tests).

- [ ] **Step 6: Re-run the Slice A exporter test (guard against regressions in the template)**

Run: `mvn -q -Dtest=TrieWebExporterTest test`
Expected: PASS (2 tests — the Slice A `toHtml` injection still works after the template edits).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/viz/TrieWebExporter.java \
        src/main/resources/web/trie-viz.html \
        src/test/java/com/gimlism/translucent/trie/viz/TrieWebExporterLiveTest.java
git commit -m "feat(trie-viz): TrieWebExporter.liveHtml + LIVE/SSE template branch

trie-viz.html gains the EventSource/follow-tail/jump-to-live branch
behind the LIVE flag (CONTROLS stays off — command box is Slice D).
liveHtml bakes DATA=null, LIVE=true, CONTROLS=false.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm"
```

---

## Task 3: TrieLiveWebVizDemo + headless SSE end-to-end

**Files:**
- Create: `src/main/java/com/gimlism/translucent/trie/demo/TrieLiveWebVizDemo.java`
- Create test: `src/test/java/com/gimlism/translucent/trie/viz/TrieLiveVizEndToEndTest.java`

**Interfaces:**
- Consumes: `LiveServer` (`new LiveServer(pageHtml, bindAddr, port)`, `start`, `port`, `broadcast`, `stop`); `BrowserLauncher.open`; `DemoLifecycle.awaitShutdown`; `TrieWebExporter.liveHtml` (Task 2); `TrieLiveVisualizer` (Task 1); `RadixTrie<V>`.
- Produces: a runnable demo (browser-verified) + a headless e2e.

- [ ] **Step 1: Write the failing end-to-end test**

Create `src/test/java/com/gimlism/translucent/trie/viz/TrieLiveVizEndToEndTest.java`:

```java
package com.gimlism.translucent.trie.viz;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.trie.core.RadixTrie;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Proves the full slice: a RadixTrie mutation surfaces as a live SSE frame over HTTP. */
class TrieLiveVizEndToEndTest {

    @Test
    void aMutationSurfacesAsALiveFrameOverHttp() throws IOException {
        LiveServer server = new LiveServer(TrieWebExporter.liveHtml(), "127.0.0.1", 0);
        server.start();
        try {
            var trie = new RadixTrie<Integer>();
            trie.addListener(new TrieLiveVisualizer(server::broadcast));
            trie.put("cat", 1); // last event (Put) becomes the cached snapshot-on-connect frame

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                HttpClient client = HttpClient.newHttpClient();
                InputStream bodyStream = client.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/events")).build(),
                        BodyHandlers.ofInputStream()).body();
                try (var r = new BufferedReader(new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String frame = line.substring("data: ".length());
                            assertTrue(frame.contains("\"type\":\"Put\""), "frame carries the Put event");
                            assertTrue(frame.contains("\"highlightPath\":\"cat\""), "frame carries the highlight path");
                            assertTrue(frame.contains("\"trie\":{"), "frame carries the whole-trie snapshot");
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
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -Dtest=TrieLiveVizEndToEndTest test`
Expected: FAIL — compiles only after Tasks 1 & 2 (which are done), but this test file compiles now; it will PASS immediately since the production code already exists. If it PASSES here, that is acceptable — this test guards the integration; proceed to Step 3. (If Tasks 1–2 were somehow incomplete it would fail to compile.)

- [ ] **Step 3: Create the live demo**

Create `src/main/java/com/gimlism/translucent/trie/demo/TrieLiveWebVizDemo.java`:

```java
package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.viz.TrieLiveVisualizer;
import com.gimlism.translucent.trie.viz.TrieWebExporter;
import java.io.IOException;

/**
 * The student sandbox: a running {@link RadixTrie} whose every mutation renders live in the browser.
 * Write your own put/remove calls in the marked block, then run:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.trie.demo.TrieLiveWebVizDemo}
 * and watch the trie change as your code runs. The server keeps running after your code finishes so
 * the final state stays live and scrubbable — stop it with Ctrl-C.
 */
public class TrieLiveWebVizDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        LiveServer server = new LiveServer(TrieWebExporter.liveHtml(), "127.0.0.1", DEFAULT_PORT);
        server.start();
        String url = "http://localhost:" + server.port();

        var trie = new RadixTrie<Integer>();
        trie.addListener(new TrieLiveVisualizer(server::broadcast));

        BrowserLauncher.open(url);
        System.out.println("Serving live at " + url + " — Ctrl-C to stop.");

        // ---------------------------------------------------------------
        // TODO: your mutations here — each renders live in the browser.
        int v = 1;
        for (String key : new String[] {"shore", "she", "shell"}) { // edges split and branch
            trie.put(key, v++);
        }
        for (String key : new String[] {"shell", "she"}) { // leaves prune and edges merge
            trie.remove(key);
        }
        // ---------------------------------------------------------------

        DemoLifecycle.awaitShutdown(server);
    }
}
```

- [ ] **Step 4: Run the end-to-end test (now with everything in place)**

Run: `mvn -q -Dtest=TrieLiveVizEndToEndTest test`
Expected: PASS (1 test).

- [ ] **Step 5: Run the full suite**

Run: `mvn -q test`
Expected: PASS — full suite green (328 from Slice A + 6 new = 334).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/demo/TrieLiveWebVizDemo.java \
        src/test/java/com/gimlism/translucent/trie/viz/TrieLiveVizEndToEndTest.java
git commit -m "feat(trie-demo): TrieLiveWebVizDemo + headless SSE end-to-end

Live student sandbox (shore/she/shell then prune/merge, then parks) and
a headless e2e proving a RadixTrie mutation surfaces as an SSE frame over
HTTP. Reuses LiveServer/BrowserLauncher/DemoLifecycle verbatim.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm"
```

- [ ] **Step 7: Browser verification (controller-run — the live JS is untested-by-design)**

Run by the controller, not a subagent (subagents cannot drive a browser). Per the live-viz recipe (headless demo/driver so `BrowserLauncher` no-ops instead of opening the real browser; the Chrome extension blocks `file://`, so serve over http):

1. `mvn -q process-classes` so `/web/trie-viz.html` is on `target/classes`.
2. Run a timed throwaway driver (or `TrieLiveWebVizDemo` with a trailing sleep between puts) headless (`-Djava.awt.headless=true`) on `127.0.0.1:7070`, streaming puts on a timer so the counter can be watched climbing.
3. Connect via the claude-in-chrome MCP to `http://localhost:7070` DURING streaming and verify:
   - the frame counter climbs live as keys are put (incremental push, not one baked blob);
   - the tree redraws per frame, including `Descend` walk-highlight frames (highlight moves down the shared prefix), split/branch on insert, and prune/merge on remove;
   - snapshot-on-connect: a browser connecting after a run immediately shows the latest frame;
   - scrub back with ◀ prev, then confirm the "⏭ N new — jump to live" badge shows the correct behind-count and clicking it snaps to the tail and resumes follow-tail;
   - zero console errors.
   - Note the rAF cross-fade caveat from Slice A: in a background tab the stage can sit at opacity 0 until focus; pin `stage.style.opacity='1'` if needed to inspect a frame. Not a regression.
4. Tear down: kill the java process (`pkill -f TrieLiveWebVizDemo` or the driver) and remove scratch.

---

## Self-Review

**1. Spec coverage.** Every spec section maps to a task:
- `toFrame` → Task 1 Step 3 (+ test Step 1). `TrieLiveVisualizer` (stateless, no swallow) → Task 1 Step 7 (+ test Step 5).
- `TrieWebExporter.liveHtml` (null/true/false) → Task 2 Step 3 (+ test Step 1). `LIVE`-branch JS (EventSource, follow-tail, jump-to-live badge, no CONTROLS) → Task 2 Step 4 edits 4a–4e; `TrieWebExporterLiveTest` asserts `const CONTROLS = false;` and no `/command`.
- `TrieLiveWebVizDemo` (sandbox, shore/she/shell, park) → Task 3 Step 3. Headless SSE e2e → Task 3 Step 1.
- Zero-surface guarantee → no task touches `trie/core`, `trie/events`, `LiveServer`, `JsonWriter`, `WebVizTemplate` (verify in review).
- "Verify, don't assume" first event → the e2e reads until a `data:` line and asserts on the cached last frame (`Put` of a single `put`), so a leading `CreateNode` cannot break it; browser step observes the live `Descend`/`CreateNode`/`Put` order directly.
- Live JS untested-by-design → Task 3 Step 7 browser verification.

**2. Placeholder scan.** No TBD/TODO (the demo's `TODO:` marker is the student-facing sandbox comment, mirroring `ListLiveWebVizDemo`, not a plan gap). Every code step shows complete code; every test step shows real assertions.

**3. Type consistency.** `toFrame(TrieEvent) → String`, `new TrieLiveVisualizer(Consumer<String>)`, `liveHtml() → String`, `LiveServer(String,String,int)`/`broadcast`/`port`/`stop`, and the JSON field names (`event.type`/`event.highlightPath`/`trie`) are used identically across tasks and match the Slice A serializer output and the `LiveServer`/`ListLiveVizEndToEndTest` signatures confirmed in the codebase. The template `const` names (`LIVE`, `frames`, `followTail`, `liveBtn`) are consistent between edits 4a–4e.
