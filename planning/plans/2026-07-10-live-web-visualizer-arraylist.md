# ArrayList Live Web Visualizer (Slice B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Slice A's baked ArrayList replay into a live connect-then-stream mirror — every `TeachingArrayList` mutation is serialized to one frame and pushed to connected browsers over SSE, redrawing the existing two-tier SVG. Exactly parallel to HashMap PR #18.

**Architecture:** Reuse the generic `substrate/viz/LiveServer` transport and Slice A's `ListJsonSerializer` / `ListWebExporter` / `list-viz.html` unchanged except for a live-mode branch. A new `ListLiveVisualizer` (a `ListEventListener` that forwards `toFrame(e)` to a sink) plus a `ListLiveWebVizDemo` student sandbox. Two structure-agnostic demo helpers (`BrowserLauncher`, `DemoLifecycle`) are promoted from `hashmap/demo` into `substrate/viz` so both structures share one copy.

**Tech Stack:** Java 21 (built on JDK 26, `maven.compiler.release=21`), Maven, JUnit 5 (`org.junit.jupiter`), JDK `com.sun.net.httpserver` + SSE (already in `LiveServer`), vanilla JS/SVG.

## Global Constraints

- Target Java 21 (`maven.compiler.release=21`); build/test on JDK 26. `mvn` is the source of truth for compilation — ignore stale Eclipse/LSP "cannot be resolved" diagnostics.
- No new dependencies.
- **LIVE token only.** This slice adds a `/*__LIVE__*/` token and live branch. It must NOT add a `/*__CONTROLS__*/` token, a command input, a `/command` POST, or any browser-controls machinery — those are Slice D.
- Reuse `substrate/viz/LiveServer` and `substrate/viz/JsonWriter` verbatim (no edits). Reuse Slice A's `ListJsonSerializer.toFrame` and the `list-viz.html` renderer; make the smallest possible additions.
- `ListLiveVisualizer` must neither mutate the list nor throw (the `StructureEventListener` contract): serialization is a pure read. It forwards whatever `toFrame` produces, including the deliberate mid-slide `Shift` and pre-placement `Grow` frames — never "corrects" them.
- Promoted helpers move **verbatim** (logic byte-for-byte unchanged), gaining only `public` visibility. All existing HashMap demos must still compile and the full suite stay green after the move.
- The static (baked) path must keep working after the `/*__LIVE__*/` token is added: `toHtml` injects `LIVE=false`, so the token is never left unreplaced in shipped HTML. Slice A's `ListWebVizDemo` + its test must stay green.
- The template's LIVE/EventSource branch is untested-by-design JS; it is browser-verified by the controller (Task 4), never unit-tested.
- Merge convention (after all tasks): PR on branch `feat/arraylist-live-web-visualizer`, `gh pr merge N --merge` (no squash, no `--delete-branch`).

## File Structure

| File | Status | Responsibility |
|---|---|---|
| `src/main/java/com/gimlism/translucent/substrate/viz/BrowserLauncher.java` | Create (moved from `hashmap/demo`) | best-effort open-URL-in-browser; now shared |
| `src/main/java/com/gimlism/translucent/substrate/viz/DemoLifecycle.java` | Create (moved from `hashmap/demo`) | park JVM until Ctrl-C, stop the `LiveServer`; now shared |
| `src/main/java/com/gimlism/translucent/hashmap/demo/BrowserLauncher.java` | Delete | moved to substrate |
| `src/main/java/com/gimlism/translucent/hashmap/demo/DemoLifecycle.java` | Delete | moved to substrate |
| `src/main/java/com/gimlism/translucent/hashmap/demo/LiveWebVizDemo.java` | Modify (imports) | import helpers from substrate |
| `src/main/java/com/gimlism/translucent/hashmap/demo/LiveControlsDemo.java` | Modify (imports) | import helpers from substrate |
| `src/main/java/com/gimlism/translucent/hashmap/demo/LiveReplDemo.java` | Modify (imports) | import `BrowserLauncher` from substrate |
| `src/main/java/com/gimlism/translucent/arraylist/viz/ListLiveVisualizer.java` | Create | `ListEventListener` → `toFrame` → sink |
| `src/test/java/com/gimlism/translucent/arraylist/viz/ListLiveVisualizerTest.java` | Create | one frame per event == `toFrame` |
| `src/main/resources/web/list-viz.html` | Modify | add `/*__LIVE__*/` token + EventSource/follow-tail branch |
| `src/main/java/com/gimlism/translucent/arraylist/viz/ListWebExporter.java` | Modify | add `liveHtml()` + `/*__LIVE__*/` token |
| `src/test/java/com/gimlism/translucent/arraylist/viz/ListWebExporterLiveTest.java` | Create | `liveHtml()` / `toHtml` live-mode assertions |
| `src/main/java/com/gimlism/translucent/arraylist/demo/ListLiveWebVizDemo.java` | Create | student sandbox live demo |
| `src/test/java/com/gimlism/translucent/arraylist/viz/ListLiveVizEndToEndTest.java` | Create | headless mutation → SSE frame round-trip |

---

### Task 1: Promote BrowserLauncher + DemoLifecycle to substrate/viz

**Files:**
- Create: `src/main/java/com/gimlism/translucent/substrate/viz/BrowserLauncher.java`
- Create: `src/main/java/com/gimlism/translucent/substrate/viz/DemoLifecycle.java`
- Delete: `src/main/java/com/gimlism/translucent/hashmap/demo/BrowserLauncher.java`
- Delete: `src/main/java/com/gimlism/translucent/hashmap/demo/DemoLifecycle.java`
- Modify: `src/main/java/com/gimlism/translucent/hashmap/demo/LiveWebVizDemo.java`
- Modify: `src/main/java/com/gimlism/translucent/hashmap/demo/LiveControlsDemo.java`
- Modify: `src/main/java/com/gimlism/translucent/hashmap/demo/LiveReplDemo.java`

**Interfaces:**
- Produces (later tasks + the HashMap demos rely on these): `com.gimlism.translucent.substrate.viz.BrowserLauncher.open(String url)` (public static, void) and `com.gimlism.translucent.substrate.viz.DemoLifecycle.awaitShutdown(LiveServer server)` (public static, void).

This is a pure refactor of merged code — no behavior change. There is no new test; the gate is a clean compile plus the unchanged full suite.

- [ ] **Step 1: Move the two helper files with git mv**

```bash
git mv src/main/java/com/gimlism/translucent/hashmap/demo/BrowserLauncher.java \
       src/main/java/com/gimlism/translucent/substrate/viz/BrowserLauncher.java
git mv src/main/java/com/gimlism/translucent/hashmap/demo/DemoLifecycle.java \
       src/main/java/com/gimlism/translucent/substrate/viz/DemoLifecycle.java
```

- [ ] **Step 2: Rewrite `substrate/viz/BrowserLauncher.java` (package + public)**

Replace the whole file with:

```java
package com.gimlism.translucent.substrate.viz;

import java.awt.Desktop;
import java.net.URI;

/** Best-effort "open this URL in the default browser"; a silent no-op (prints instead) when unavailable. */
public final class BrowserLauncher {

    private BrowserLauncher() {}

    public static void open(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception headlessOrUnsupported) {
            // fall through to the printed hint
        }
        System.out.println("Open " + url + " in your browser.");
    }
}
```

(Only changes vs the original: `package` line, `public final class`, `public static void open`.)

- [ ] **Step 3: Rewrite `substrate/viz/DemoLifecycle.java` (package + public, drop LiveServer import)**

Replace the whole file with:

```java
package com.gimlism.translucent.substrate.viz;

import java.util.concurrent.CountDownLatch;

/** Shared demo lifecycle: keep the JVM alive so a live {@link LiveServer} stays serving until Ctrl-C. */
public final class DemoLifecycle {

    private DemoLifecycle() {}

    /** Park the calling thread until Ctrl-C (or interrupt); stop the server cleanly on the way out. */
    public static void awaitShutdown(LiveServer server) {
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        try {
            new CountDownLatch(1).await(); // never counted down — Ctrl-C runs the shutdown hook and exits
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.stop(); // interrupted (e.g. IDE stop) — the shutdown hook won't fire, so release the port here
        }
    }
}
```

(Changes vs the original: `package` line, `public final class`, `public static void awaitShutdown`, and the `import com.gimlism.translucent.substrate.viz.LiveServer;` line is removed because `LiveServer` is now in the same package.)

- [ ] **Step 4: Add imports to the three HashMap demos**

In `src/main/java/com/gimlism/translucent/hashmap/demo/LiveWebVizDemo.java`, add these two imports (place them with the existing `import com.gimlism.translucent.substrate.viz.LiveServer;`, keeping the import block sorted):

```java
import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
```

In `src/main/java/com/gimlism/translucent/hashmap/demo/LiveControlsDemo.java`, add the same two imports (sorted into the existing `com.gimlism.translucent.substrate.viz.*` import group).

In `src/main/java/com/gimlism/translucent/hashmap/demo/LiveReplDemo.java`, add only:

```java
import com.gimlism.translucent.substrate.viz.BrowserLauncher;
```

(These demos call `BrowserLauncher.open(...)` / `DemoLifecycle.awaitShutdown(...)` unqualified; they compiled without imports only because the helpers were same-package. After the move they need the imports. Do NOT change any other line in these demos.)

- [ ] **Step 5: Compile and verify no stale references**

Run: `mvn -q test-compile`
Expected: BUILD SUCCESS (everything compiles).

Run: `grep -rn "hashmap.demo.BrowserLauncher\|hashmap.demo.DemoLifecycle" src/ ; echo "exit=$?"`
Expected: no matches (`exit=1`) — nothing still references the old package location.

- [ ] **Step 6: Run the full suite (no test count change)**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, .*Skipped: [0-9]+$|BUILD'`
Expected: `Tests run: 285, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS` — a pure refactor changes no test outcome.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor(viz): promote BrowserLauncher + DemoLifecycle to substrate/viz"
```

---

### Task 2: ListLiveVisualizer

**Files:**
- Create: `src/main/java/com/gimlism/translucent/arraylist/viz/ListLiveVisualizer.java`
- Test: `src/test/java/com/gimlism/translucent/arraylist/viz/ListLiveVisualizerTest.java`

**Interfaces:**
- Consumes (existing): `ListEventListener` (a `@FunctionalInterface extends StructureEventListener<ListEvent>` with `void onEvent(ListEvent)`); `ListJsonSerializer.toFrame(ListEvent) → String` (Slice A); `TeachingArrayList.addListener(StructureEventListener<ListEvent>)`; `ListRecordingListener` (`.events() → List<ListEvent>`).
- Produces (Task 4 relies on this): `new ListLiveVisualizer(Consumer<String> sink)` — a `ListEventListener` that, per event, calls `sink.accept(ListJsonSerializer.toFrame(event))`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/arraylist/viz/ListLiveVisualizerTest.java`:

```java
package com.gimlism.translucent.arraylist.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.consumer.ListRecordingListener;
import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ListLiveVisualizerTest {

    @Test
    void emitsOneFramePerEventEqualToToFrame() {
        var frames = new ArrayList<String>();
        var rec = new ListRecordingListener();
        var list = new TeachingArrayList<String>(4);
        list.addListener(new ListLiveVisualizer(frames::add));
        list.addListener(rec);

        list.add("a");
        list.add("b");
        list.add(0, "c"); // insert at 0: a Shift burst then an Insert -> several events

        assertTrue(frames.size() >= 3, "at least one frame per mutation event");
        assertEquals(rec.events().size(), frames.size(), "exactly one frame per event");
        for (int i = 0; i < frames.size(); i++) {
            assertEquals(ListJsonSerializer.toFrame(rec.events().get(i)), frames.get(i),
                    "frame " + i + " equals the serializer's toFrame of that event");
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ListLiveVisualizerTest test`
Expected: FAIL — compilation error, `ListLiveVisualizer` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/gimlism/translucent/arraylist/viz/ListLiveVisualizer.java`:

```java
package com.gimlism.translucent.arraylist.viz;

import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListEventListener;
import java.util.function.Consumer;

/**
 * Live twin of {@code ListRecordingListener}: instead of buffering the stream, it serializes each
 * event to one frame ({@link ListJsonSerializer#toFrame}) and hands it to a sink — normally a
 * {@code LiveServer}'s {@code broadcast}. Stateless; the server owns snapshot-on-connect.
 *
 * <p>Per the {@link ListEventListener} contract it neither mutates the list nor throws:
 * serialization is a pure read of the event's snapshot (it forwards the deliberate mid-slide
 * {@code Shift} and pre-placement {@code Grow} frames exactly as the baked path does).
 */
public final class ListLiveVisualizer implements ListEventListener {

    private final Consumer<String> sink;

    public ListLiveVisualizer(Consumer<String> sink) {
        this.sink = sink;
    }

    @Override
    public void onEvent(ListEvent event) {
        sink.accept(ListJsonSerializer.toFrame(event));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ListLiveVisualizerTest test`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/arraylist/viz/ListLiveVisualizer.java \
        src/test/java/com/gimlism/translucent/arraylist/viz/ListLiveVisualizerTest.java
git commit -m "feat(viz): ListLiveVisualizer — stream each ArrayList event as a live frame"
```

---

### Task 3: LIVE mode — list-viz.html branch + ListWebExporter.liveHtml()

**Files:**
- Modify: `src/main/resources/web/list-viz.html`
- Modify: `src/main/java/com/gimlism/translucent/arraylist/viz/ListWebExporter.java`
- Test: `src/test/java/com/gimlism/translucent/arraylist/viz/ListWebExporterLiveTest.java`

**Interfaces:**
- Produces (Task 4 relies on this): `ListWebExporter.liveHtml() → String` (self-contained HTML with `DATA=null`, `LIVE=true`); `ListWebExporter.toHtml(String)` unchanged in signature but now also injects `LIVE=false`.
- The template gains exactly one new token, `/*__LIVE__*/`. No `/*__CONTROLS__*/`.

**Why template + exporter are one task:** the moment the template has a `/*__LIVE__*/` token, any HTML produced without replacing it contains `const LIVE = /*__LIVE__*/;` → `const LIVE = ;` (a syntax error that breaks the whole script). So the exporter must learn to replace the token in the same change that adds it. Do the template edits, then the exporter, before running anything.

- [ ] **Step 1: Add the live button to the template's control bar**

In `src/main/resources/web/list-viz.html`, replace:

```html
    <button id="next">next ▶</button>
    <span id="counter"></span>
  </div>
```

with:

```html
    <button id="next">next ▶</button>
    <span id="counter"></span>
    <button id="live" hidden></button>
  </div>
```

- [ ] **Step 2: Add the `LIVE` constant and `liveBtn` element reference**

Replace:

```javascript
const DATA = /*__FRAMES__*/;
const frames = (DATA && DATA.frames) || [];
```

with:

```javascript
const DATA = /*__FRAMES__*/;
const LIVE = /*__LIVE__*/;
const frames = (DATA && DATA.frames) || [];
```

Replace:

```javascript
const playBtn = document.getElementById("play");
```

with:

```javascript
const playBtn = document.getElementById("play");
const liveBtn = document.getElementById("live");
```

- [ ] **Step 3: Add the `followTail` state flag**

Replace:

```javascript
let idx = 0, timer = null;
```

with:

```javascript
let idx = 0, timer = null, followTail = true;
```

- [ ] **Step 4: Extend `refreshMeta` with the behind-count / live button**

Replace:

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

- [ ] **Step 5: Make the empty-caption live-aware**

Replace:

```javascript
  caption.textContent = f ? f.event.label : "(no events to replay)";
```

with:

```javascript
  caption.textContent = f ? f.event.label : (LIVE ? "(waiting for live events…)" : "(no events to replay)");
```

- [ ] **Step 6: Track `followTail` in `go`**

Replace:

```javascript
function go(n) { idx = Math.max(0, Math.min(frames.length - 1, n)); render(); }
```

with:

```javascript
function go(n) { idx = Math.max(0, Math.min(frames.length - 1, n)); followTail = idx >= frames.length - 1; render(); }
```

- [ ] **Step 7: Add the live button handler and the EventSource stream**

Replace:

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

- [ ] **Step 8: Update the exporter to inject both tokens**

In `src/main/java/com/gimlism/translucent/arraylist/viz/ListWebExporter.java`, replace:

```java
    private static final String FRAMES_TOKEN = "/*__FRAMES__*/";

    private ListWebExporter() {}

    /** The self-contained baked HTML with {@code framesJson} injected. */
    public static String toHtml(String framesJson) {
        String template = readTemplate();
        requireToken(template, FRAMES_TOKEN);
        return template.replace(FRAMES_TOKEN, framesJson);
    }
```

with:

```java
    private static final String FRAMES_TOKEN = "/*__FRAMES__*/";
    private static final String LIVE_TOKEN = "/*__LIVE__*/";

    private ListWebExporter() {}

    /** The self-contained baked HTML with {@code framesJson} injected; live mode OFF. */
    public static String toHtml(String framesJson) {
        return inject(framesJson, "false");
    }

    /** Live mode (SSE): {@code DATA = null}, {@code LIVE = true} — no baked frames; frames arrive over /events. */
    public static String liveHtml() {
        return inject("null", "true");
    }

    private static String inject(String framesReplacement, String liveReplacement) {
        String template = readTemplate();
        requireToken(template, FRAMES_TOKEN);
        requireToken(template, LIVE_TOKEN);
        return template
                .replace(FRAMES_TOKEN, framesReplacement)
                .replace(LIVE_TOKEN, liveReplacement);
    }
```

Also update the class Javadoc's last sentence (currently "Slice A has a single token; the live/controls tokens arrive with later slices.") to: "The `FRAMES` token carries the baked data; the `LIVE` token selects baked-replay vs. live (SSE) mode. The controls token arrives with a later slice."

- [ ] **Step 9: Write the exporter live-mode tests**

Create `src/test/java/com/gimlism/translucent/arraylist/viz/ListWebExporterLiveTest.java`:

```java
package com.gimlism.translucent.arraylist.viz;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ListWebExporterLiveTest {

    @Test
    void liveHtmlHasNoBakedFramesAndOpensAnEventSource() {
        String html = ListWebExporter.liveHtml();
        assertTrue(html.toLowerCase().contains("<!doctype html"), "full document");
        assertFalse(html.contains("/*__FRAMES__*/"), "frames token replaced");
        assertFalse(html.contains("/*__LIVE__*/"), "live token replaced");
        assertTrue(html.contains("const DATA = null;"), "no baked frames in live mode");
        assertTrue(html.contains("const LIVE = true;"), "live mode on");
        assertTrue(html.contains("new EventSource(\"/events\")"), "opens the SSE stream");
    }

    @Test
    void bakedHtmlInjectsFramesAndTurnsLiveOff() {
        String html = ListWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.contains("const DATA = {\"frames\":[]};"), "baked frames injected");
        assertTrue(html.contains("const LIVE = false;"), "live mode off for the baked file");
    }
}
```

- [ ] **Step 10: Run the exporter tests (new + existing) and the smoke gate**

Run: `mvn -q -Dtest='ListWebExporterTest,ListWebExporterLiveTest,ListWebVizDemoTest' test`
Expected: PASS — the 2 new live tests, the 2 existing Slice-A exporter tests (still green: `toHtml` now also replaces `LIVE`→`false`, the frames marker + no-external-URL assertions still hold), and the Slice-A demo smoke test.

Run: `mvn -q process-classes && grep -c '/\*__FRAMES__\*/\|/\*__LIVE__\*/' target/classes/web/list-viz.html && grep -c '__CONTROLS__' target/classes/web/list-viz.html`
Expected: first grep prints `2` (both tokens present, one line each); second prints `0` (no controls token).

- [ ] **Step 11: Commit**

```bash
git add src/main/resources/web/list-viz.html \
        src/main/java/com/gimlism/translucent/arraylist/viz/ListWebExporter.java \
        src/test/java/com/gimlism/translucent/arraylist/viz/ListWebExporterLiveTest.java
git commit -m "feat(viz): live mode — list-viz LIVE/EventSource branch + ListWebExporter.liveHtml()"
```

---

### Task 4: ListLiveWebVizDemo + headless SSE end-to-end test + browser verification

**Files:**
- Create: `src/main/java/com/gimlism/translucent/arraylist/demo/ListLiveWebVizDemo.java`
- Test: `src/test/java/com/gimlism/translucent/arraylist/viz/ListLiveVizEndToEndTest.java`

**Interfaces:**
- Consumes: `LiveServer(String pageHtml, String bindAddr, int port)` + `start()`/`port()`/`openConnections()`/`broadcast(String)`/`stop()`; `ListWebExporter.liveHtml()` (Task 3); `ListLiveVisualizer` (Task 2); `substrate.viz.BrowserLauncher`/`DemoLifecycle` (Task 1); `TeachingArrayList`.
- Produces: `ListLiveWebVizDemo.main` (student sandbox). The demo class itself is untested-by-design (a `main` + student mutation block that parks until Ctrl-C — like the HashMap `LiveWebVizDemo`); the end-to-end test proves the live wiring, and the controller browser-verifies the JS.

- [ ] **Step 1: Write the end-to-end test**

Create `src/test/java/com/gimlism/translucent/arraylist/viz/ListLiveVizEndToEndTest.java`:

```java
package com.gimlism.translucent.arraylist.viz;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.substrate.viz.LiveServer;
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

/** Proves the full slice: a TeachingArrayList mutation surfaces as a live SSE frame over HTTP. */
class ListLiveVizEndToEndTest {

    @Test
    void aMutationSurfacesAsALiveFrameOverHttp() throws IOException {
        LiveServer server = new LiveServer(ListWebExporter.liveHtml(), "127.0.0.1", 0);
        server.start();
        try {
            var list = new TeachingArrayList<String>(4);
            list.addListener(new ListLiveVisualizer(server::broadcast));
            list.add("z"); // becomes the cached last frame (an Append)

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
                            assertTrue(frame.contains("\"type\":\"Append\""), "frame carries the Append event");
                            assertTrue(frame.contains("\"label\":\"APPEND z @ 0\""), "frame carries the caption");
                            assertTrue(frame.contains("\"list\":{"), "frame carries the whole-list snapshot");
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

- [ ] **Step 2: Run the end-to-end test — it should pass (proves Task 2 + Task 3 wiring)**

Run: `mvn -q -Dtest=ListLiveVizEndToEndTest test`
Expected: PASS (1 test). The production code it exercises (`ListLiveVisualizer`, `ListWebExporter.liveHtml`, `LiveServer`) already exists, so this is an integration proof, not a red-then-green cycle. If it fails, stop and report — the live wiring is broken.

- [ ] **Step 3: Write the demo**

Create `src/main/java/com/gimlism/translucent/arraylist/demo/ListLiveWebVizDemo.java`:

```java
package com.gimlism.translucent.arraylist.demo;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.viz.ListLiveVisualizer;
import com.gimlism.translucent.arraylist.viz.ListWebExporter;
import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
import com.gimlism.translucent.substrate.viz.LiveServer;
import java.io.IOException;

/**
 * The student sandbox: a running {@link TeachingArrayList} whose every mutation renders live in the
 * browser. Write your own add/remove/set calls in the marked block, then run:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListLiveWebVizDemo}
 * and watch the structure change as your code runs. The server keeps running after your code
 * finishes so the final state stays live and scrubbable — stop it with Ctrl-C.
 */
public class ListLiveWebVizDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        LiveServer server = new LiveServer(ListWebExporter.liveHtml(), "127.0.0.1", DEFAULT_PORT);
        server.start();
        String url = "http://localhost:" + server.port();

        var list = new TeachingArrayList<String>(4);
        list.addListener(new ListLiveVisualizer(server::broadcast));

        BrowserLauncher.open(url);
        System.out.println("Serving live at " + url + " — Ctrl-C to stop.");

        // ---------------------------------------------------------------
        // TODO: your mutations here — each renders live in the browser.
        for (String s : new String[] {"a", "b", "c", "d", "e"}) { // 5th append grows 4 -> 6
            list.add(s);
        }
        list.add(2, "x"); // insert in the middle: a shift burst then place
        list.remove(1);   // remove in the middle: shift survivors left
        // ---------------------------------------------------------------

        DemoLifecycle.awaitShutdown(server);
    }
}
```

- [ ] **Step 4: Run the full suite**

Run: `mvn test 2>&1 | grep -E 'Tests run: [0-9]+, .*Skipped: [0-9]+$|BUILD'`
Expected: `Tests run: 289, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS` — 285 from before plus the 4 new tests (`ListLiveVisualizerTest` ×1, `ListWebExporterLiveTest` ×2, `ListLiveVizEndToEndTest` ×1). The binding assertion is zero failures/errors with the count risen by 4.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/arraylist/demo/ListLiveWebVizDemo.java \
        src/test/java/com/gimlism/translucent/arraylist/viz/ListLiveVizEndToEndTest.java
git commit -m "feat(demo): ListLiveWebVizDemo — student sandbox + headless SSE end-to-end test"
```

- [ ] **Step 6: Browser-verify the untested LIVE renderer (controller task)**

The EventSource/live JS is the untested-by-design, Slice-B-specific surface, and it is the *only* thing neither test above covers: `ListLiveVizEndToEndTest` reads the SSE stream with a raw `HttpClient` and never runs `es.onmessage`, `followTail`, the behind-count, or jump-to-live. **The verification must exercise incremental streaming, not just the connect-time snapshot.** The shipped `ListLiveWebVizDemo` fires all its mutations synchronously before it parks, so connecting to *it* only ever shows the cached last frame (`behind` is always 0, the jump-to-live badge never appears) — that would leave the whole streaming path unverified. So drive the live path with a throwaway timed driver instead.

1. Build classes so the template is on the classpath:

```bash
mvn -q process-classes    # copies list-viz.html into target/classes
```

2. Write a throwaway driver (scratch, **not committed**) to the scratchpad, e.g. `LiveDriver.java`, that streams mutations on a timer and parks:

```java
import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.viz.ListLiveVisualizer;
import com.gimlism.translucent.arraylist.viz.ListWebExporter;
import com.gimlism.translucent.substrate.viz.LiveServer;

public class LiveDriver {
    public static void main(String[] a) throws Exception {
        LiveServer s = new LiveServer(ListWebExporter.liveHtml(), "127.0.0.1", 7070);
        s.start();
        var list = new TeachingArrayList<String>(4);
        list.addListener(new ListLiveVisualizer(s::broadcast));
        System.out.println("live at http://localhost:" + s.port());
        Thread.sleep(8000);                    // give the controller time to connect first
        for (String x : new String[]{"a","b","c","d","e"}) { list.add(x); Thread.sleep(1500); }
        list.add(2, "x"); Thread.sleep(1500);  // shift burst
        list.remove(1);   Thread.sleep(1500);  // shift burst
        Thread.sleep(600000);                  // park so the final state stays live/scrubbable
    }
}
```

Compile and run it headless against the project classpath (adjust the scratch path):

```bash
SP="<scratchpad-dir>"
javac -cp target/classes -d "$SP" "$SP/LiveDriver.java"
java -Djava.awt.headless=true -cp "target/classes:$SP" LiveDriver &
```

3. Connect the Chrome MCP to `http://localhost:7070` **during the initial 8s wait** (before frames start), so you watch them arrive. The extension blocks `file://`, but this serves over HTTP. Confirm, as frames stream in:
   - the frame **counter climbs** 1→N live as each mutation arrives (the core proof the EventSource path works);
   - the two-tier render is correct across the stream — Grow (4→6) extends the backing row, Shift bursts freeze the top row + drop connectors, Insert/Remove settle;
   - **scrub back** (◀ prev) mid-stream: the "⏭ N new — jump to live" badge appears and its count **rises** as further frames arrive while you're behind;
   - **click the badge** (or press it): it jumps to the latest frame and resumes following the tail;
   - the console shows **zero errors**.
4. Verify snapshot-on-connect separately: reload the tab after several frames have streamed and confirm the latest frame renders immediately on connect (no blank).
5. Eyeball (no code change): if the cached last frame on a fresh connect happens to be a `Shift`, the Slice-A `settledLogical` fallback shows that mid-slide frame's own row until the next frame; confirm it self-corrects on the next streamed frame (this is the deferred Slice-A Minor, more reachable live).

Kill the java process (`pkill -f LiveDriver`) and delete the scratch driver when done. Record the verification result (counter-climb, badge, jump-to-live, zero console errors) in the ledger.

---

## Self-Review

**Spec coverage:**
- Live one-way SSE mirror reusing `LiveServer` → `ListLiveVisualizer` (Task 2) + the template EventSource branch (Task 3) + the e2e test (Task 4). ✓
- `ListWebExporter.liveHtml()` + `/*__LIVE__*/` token, `toHtml` stays valid (`LIVE=false`) → Task 3 (+ existing Slice-A tests re-run). ✓
- Student-sandbox demo, server stays up until Ctrl-C → Task 4 (`ListLiveWebVizDemo` + `DemoLifecycle.awaitShutdown`). ✓
- Promote `BrowserLauncher` + `DemoLifecycle` to `substrate/viz`, update the 3 HashMap demos → Task 1. ✓
- LIVE token only, no CONTROLS → Task 3 smoke gate asserts `__CONTROLS__` count is 0. ✓
- `ListLiveVisualizer` neither mutates nor throws, forwards `toFrame` verbatim → Task 2 impl + Javadoc; verified by the one-frame-per-event test. ✓
- Headless SSE end-to-end test → Task 4. ✓
- Live JS untested-by-design, browser-verified → Task 4 Step 6. ✓
- No core/event change (recurring timing bug has zero surface) → no task touches `arraylist/core` or `arraylist/events`. ✓

**Placeholder scan:** none — every step has the exact code/edit and an exact command with expected output. (Task 4 Step 4's total count is given as "all pass, count rose by the new tests" rather than a hard number to avoid over-precision on the runner's per-class tallying; the binding assertion is zero failures.)

**Type consistency:** `ListLiveVisualizer(Consumer<String>)`, `ListWebExporter.liveHtml()`/`toHtml(String)`, `LiveServer` ctor/`broadcast`/`openConnections`/`stop`, `BrowserLauncher.open`/`DemoLifecycle.awaitShutdown`, and `ListJsonSerializer.toFrame` are referenced identically across tasks. The template token names (`/*__FRAMES__*/`, `/*__LIVE__*/`) and the injected strings (`const DATA = null;`, `const LIVE = true;`/`false;`, `new EventSource("/events")`) match between the template edits (Task 3 steps) and the exporter tests (Task 3 Step 9).
