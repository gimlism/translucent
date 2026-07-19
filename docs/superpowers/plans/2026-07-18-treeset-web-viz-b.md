# TreeSet Web Visualizer — Slice B (live SSE mirror) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Slice A's baked replay into a live connect-then-stream mirror — a running `TeachingTreeSet` broadcasts each event over Server-Sent Events and a connected browser renders the red-black tree changing in real time.

**Architecture:** Transport only; no new rendering. A stateless `TreeSetLiveVisualizer` serializes each event to one frame (`TreeSetJsonSerializer.toFrame`, already shipped) and hands it to a sink — normally `LiveServer.broadcast`. The Slice-A `treeset-viz.html` renderer is reused untouched; Slice B re-enables its dormant `LIVE` EventSource branch. `LiveServer`/`JsonWriter`/`BrowserLauncher`/`DemoLifecycle` are reused verbatim, so `core`/`events`/`substrate` stay byte-unchanged.

**Tech Stack:** Java 21 (Maven, release 21), JUnit 5, `java.net.http`, vanilla JS/SVG + SSE (no dependencies).

## Global Constraints

- **Java release 21**; no new Maven dependencies. **JUnit 5**; tests package-private.
- **New files:** `treeset/viz/TreeSetLiveVisualizer.java`, `treeset/demo/TreeSetLiveWebVizDemo.java`. **Edited:** `treeset/viz/TreeSetWebExporter.java` (add `liveHtml()`), `resources/web/treeset-viz.html` (re-add the LIVE machinery).
- **Do not touch** `treeset/core`, `treeset/events`, `substrate/rbtree`, `substrate/viz/LiveServer`, `substrate/viz/JsonWriter`, `substrate/viz/WebVizTemplate`, `substrate/viz/BrowserLauncher`, `substrate/viz/DemoLifecycle`, or `TreeSetJsonSerializer` — all consumed/reused verbatim.
- **Slice B adds the `LIVE` branch only.** The `CONTROLS` token stays baked `false`; **no** command-box (`/command`, fetch POST) JS this slice (that is Slice D).
- **Reads narrate — live, on purpose.** `TreeSetLiveVisualizer` broadcasts every event, including the `Compare` frames a `contains`/navigation read emits. A `contains` walk animating in the browser is the intended default (do not filter reads out).
- `TreeSetLiveVisualizer` **must not throw and must not catch** (a catch-all swallows a mid-mutation bug) — serialization is a pure read; byte-mirror of `TrieLiveVisualizer`.
- **SSE e2e trap 1 — reader/client close coupling:** in the end-to-end test, wrap the SSE `BufferedReader` in try-with-resources and close the `HttpClient` in a `finally`. Closing the client while `/events` is still open **hangs the suite** (this project's PR #30 history).
- **SSE e2e trap 2 — first frame is `Add`, not `CreateNode`:** the TreeSet has no `CreateNode` (every node is an element; `Add` is the creation). Do not mirror the trie e2e's read-past-`CreateNode`; assert type-agnostically on the whole-set snapshot.
- **Every commit ends with the two trailer lines** (`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` and the `Claude-Session:` line), per CLAUDE.md.
- **Branch:** `feat/treeset-live-web-visualizer` (already created; the spec commit is on it).
- **mvn is source of truth**; stale Eclipse/LSP diagnostics are known false positives. Full-suite baseline before this work: **439 tests green**.

---

### Task 1: `TreeSetLiveVisualizer` — broadcast one frame per event

**Files:**
- Create: `src/main/java/com/gimlism/translucent/treeset/viz/TreeSetLiveVisualizer.java`
- Test: `src/test/java/com/gimlism/translucent/treeset/viz/TreeSetLiveVisualizerTest.java`

**Interfaces:**
- Consumes: `SetEventListener` (functional, `onEvent(SetEvent)`); `TreeSetJsonSerializer.toFrame(SetEvent)`; `SetRecordingListener`; `TeachingTreeSet`.
- Produces: `TreeSetLiveVisualizer implements SetEventListener` with ctor `TreeSetLiveVisualizer(Consumer<String> sink)`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/treeset/viz/TreeSetLiveVisualizerTest.java`:

```java
package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.treeset.consumer.SetRecordingListener;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class TreeSetLiveVisualizerTest {

    @Test
    void emitsOneFramePerEventEqualToToFrame() {
        var frames = new ArrayList<String>();
        var rec = new SetRecordingListener();
        var set = new TeachingTreeSet<Integer>();
        set.addListener(new TreeSetLiveVisualizer(frames::add));
        set.addListener(rec);

        set.add(10);
        set.add(20);   // compare walk + add (+ any rebalance events)
        set.remove(10);

        assertTrue(frames.size() >= 3, "at least one frame per event");
        assertEquals(rec.events().size(), frames.size(), "exactly one frame per event");
        for (int i = 0; i < frames.size(); i++) {
            assertEquals(TreeSetJsonSerializer.toFrame(rec.events().get(i)), frames.get(i),
                    "frame " + i + " equals the serializer's toFrame of that event");
        }
    }

    @Test
    void readsNarrateLive_containsBroadcastsCompareFrames() {
        var set = new TeachingTreeSet<Integer>();
        set.add(10);
        set.add(20);
        set.add(30);

        var frames = new ArrayList<String>();
        set.addListener(new TreeSetLiveVisualizer(frames::add)); // attach AFTER the inserts

        set.contains(25); // a read: narrates the comparison walk, mutates nothing

        assertTrue(frames.size() >= 1, "a contains walk broadcasts Compare frames");
        assertTrue(frames.stream().allMatch(f -> f.contains("\"type\":\"Compare\"")),
                "every frame from a read is a Compare frame");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -Dtest=TreeSetLiveVisualizerTest test`
Expected: FAIL — compilation error, `TreeSetLiveVisualizer` does not exist.

- [ ] **Step 3: Implement `TreeSetLiveVisualizer`**

Create `src/main/java/com/gimlism/translucent/treeset/viz/TreeSetLiveVisualizer.java`:

```java
package com.gimlism.translucent.treeset.viz;

import com.gimlism.translucent.treeset.events.SetEvent;
import com.gimlism.translucent.treeset.events.SetEventListener;
import java.util.function.Consumer;

/**
 * Live twin of {@code SetRecordingListener}: instead of buffering the stream, it serializes each
 * event to one frame ({@link TreeSetJsonSerializer#toFrame}) and hands it to a sink — normally a
 * {@code LiveServer}'s {@code broadcast}. Stateless; the server owns snapshot-on-connect.
 *
 * <p>Per the {@link SetEventListener} contract it neither mutates the set nor throws: serialization
 * is a pure read of the event's snapshot. Because the set narrates reads, this also broadcasts the
 * {@code Compare} frames a {@code contains}/navigation walk emits — so a read animates live.
 */
public final class TreeSetLiveVisualizer implements SetEventListener {

    private final Consumer<String> sink;

    public TreeSetLiveVisualizer(Consumer<String> sink) {
        this.sink = sink;
    }

    @Override
    public void onEvent(SetEvent event) {
        sink.accept(TreeSetJsonSerializer.toFrame(event));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=TreeSetLiveVisualizerTest test`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/treeset/viz/TreeSetLiveVisualizer.java \
        src/test/java/com/gimlism/translucent/treeset/viz/TreeSetLiveVisualizerTest.java
git commit -m "feat(treeset): TreeSetLiveVisualizer — broadcast one frame per SetEvent"
```

---

### Task 2: `TreeSetWebExporter.liveHtml()` + the `LIVE` branch on `treeset-viz.html`

**Files:**
- Modify: `src/main/resources/web/treeset-viz.html`
- Modify: `src/main/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporter.java`
- Test: `src/test/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporterLiveTest.java`

**Interfaces:**
- Consumes: `WebVizTemplate.inject`.
- Produces: `TreeSetWebExporter.liveHtml()` returning the page with `DATA=null`, `LIVE=true`, `CONTROLS=false`. The page gains an `EventSource("/events")` follow-tail branch and a jump-to-live badge.

- [ ] **Step 1: Re-add the LIVE machinery to `treeset-viz.html`**

Apply these six edits to `src/main/resources/web/treeset-viz.html` (the Slice-A file). Each shows the exact existing text and its replacement. This is the trie's completed live machinery **minus** the `if (CONTROLS)` command box.

Edit 1 — add the jump-to-live button to the bar:

```
    <button id="next">next ▶</button>
    <span id="counter"></span>
  </div>
```
→
```
    <button id="next">next ▶</button>
    <span id="counter"></span>
    <button id="live" hidden></button>
  </div>
```

Edit 2 — add the `liveBtn` handle:

```
const playBtn = document.getElementById("play");
```
→
```
const playBtn = document.getElementById("play");
const liveBtn = document.getElementById("live");
```

Edit 3 — add `followTail` state:

```
const PAD = 28, V_GAP = 72, H_GAP = 54, R = 16;
let idx = 0, timer = null;
```
→
```
const PAD = 28, V_GAP = 72, H_GAP = 54, R = 16;
let idx = 0, timer = null, followTail = true;
```

Edit 4 — teach `refreshMeta` the behind-count + badge:

```
function refreshMeta() {
  counter.textContent = frames.length ? `frame ${idx + 1} / ${frames.length}` : "";
  prevBtn.disabled = idx <= 0;
  nextBtn.disabled = idx >= frames.length - 1;
}
```
→
```
function refreshMeta() {
  counter.textContent = frames.length ? `frame ${idx + 1} / ${frames.length}` : "";
  prevBtn.disabled = idx <= 0;
  nextBtn.disabled = idx >= frames.length - 1;
  const behind = Math.max(0, frames.length - 1 - idx);
  liveBtn.hidden = !(LIVE && behind > 0);
  liveBtn.textContent = `⏭ ${behind} new — jump to live`;
}
```

Edit 5 — `go()` tracks whether we are at the tail:

```
function go(n) { idx = Math.max(0, Math.min(frames.length - 1, n)); render(); }
```
→
```
function go(n) { idx = Math.max(0, Math.min(frames.length - 1, n)); followTail = idx >= frames.length - 1; render(); }
```

Edit 6 — add the jump-to-live click handler and the `LIVE` EventSource branch (replace the tail of the script):

```
document.addEventListener("keydown", e => {
  if (e.key === "ArrowRight") { stop(); go(idx + 1); }
  else if (e.key === "ArrowLeft") { stop(); go(idx - 1); }
});
render();
```
→
```
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

Do **not** add any `if (CONTROLS)` block, `#cmd`/`#run`/`#cmdout` elements, `/command`, or `fetch(` — those are Slice D.

- [ ] **Step 2: Write the failing exporter live test**

Create `src/test/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporterLiveTest.java`:

```java
package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TreeSetWebExporterLiveTest {

    @Test
    void liveHtmlHasNoBakedFramesAndOpensAnEventSource() {
        String html = TreeSetWebExporter.liveHtml();
        assertTrue(html.toLowerCase().contains("<!doctype html"), "full document");
        assertFalse(html.contains("/*__FRAMES__*/"), "frames token replaced");
        assertFalse(html.contains("/*__LIVE__*/"), "live token replaced");
        assertTrue(html.contains("const DATA = null;"), "no baked frames in live mode");
        assertTrue(html.contains("const LIVE = true;"), "live mode on");
        assertTrue(html.contains("const CONTROLS = false;"), "controls stay off in slice B");
        assertTrue(html.contains("new EventSource(\"/events\")"), "opens the SSE stream");
        assertFalse(html.contains("/command"), "no command box in slice B");
    }

    @Test
    void bakedHtmlStillInjectsFramesAndTurnsLiveOff() {
        String html = TreeSetWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.contains("const DATA = {\"frames\":[]};"), "baked frames injected");
        assertTrue(html.contains("const LIVE = false;"), "live mode off for the baked file");
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `mvn -q -Dtest=TreeSetWebExporterLiveTest test`
Expected: FAIL — compilation error, `TreeSetWebExporter.liveHtml()` does not exist.

- [ ] **Step 4: Add `liveHtml()` to `TreeSetWebExporter`**

In `src/main/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporter.java`, add this method after `toHtml` (before `writeHtml`):

```java
    /** Live mode (SSE), no browser controls: {@code DATA = null}, live ON, controls OFF. */
    public static String liveHtml() {
        return WebVizTemplate.inject(TEMPLATE_RESOURCE, "null", "true", "false");
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q -Dtest=TreeSetWebExporterLiveTest test`
Expected: PASS (2 tests). (`liveHtmlHasNoBakedFramesAndOpensAnEventSource` passes only because Step 1 added the `EventSource("/events")` branch to the page — it exercises both the exporter and the template edit.)

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/web/treeset-viz.html \
        src/main/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporter.java \
        src/test/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporterLiveTest.java
git commit -m "feat(treeset): live SSE branch on treeset-viz.html + TreeSetWebExporter.liveHtml()"
```

---

### Task 3: `TreeSetLiveWebVizDemo` + headless SSE end-to-end test

**Files:**
- Create: `src/main/java/com/gimlism/translucent/treeset/demo/TreeSetLiveWebVizDemo.java`
- Test: `src/test/java/com/gimlism/translucent/treeset/viz/TreeSetLiveVizEndToEndTest.java`

**Interfaces:**
- Consumes: `LiveServer`, `BrowserLauncher`, `DemoLifecycle`, `TreeSetWebExporter.liveHtml()` (Task 2), `TreeSetLiveVisualizer` (Task 1), `TeachingTreeSet`.
- Produces: `TreeSetLiveWebVizDemo` (student sandbox `main`, untested-by-design — it parks). The e2e test is Task 3's gate.

- [ ] **Step 1: Write the failing end-to-end test**

Create `src/test/java/com/gimlism/translucent/treeset/viz/TreeSetLiveVizEndToEndTest.java`:

```java
package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
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

/** Proves the full slice: a TeachingTreeSet mutation surfaces as a live SSE frame over HTTP. */
class TreeSetLiveVizEndToEndTest {

    @Test
    void aMutationSurfacesAsALiveFrameOverHttp() throws IOException {
        LiveServer server = new LiveServer(TreeSetWebExporter.liveHtml(), "127.0.0.1", 0);
        server.start();
        HttpClient client = HttpClient.newHttpClient();
        try {
            var set = new TeachingTreeSet<Integer>();
            set.addListener(new TreeSetLiveVisualizer(server::broadcast));
            set.add(7); // the last event of this add becomes the cached snapshot-on-connect frame

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                InputStream bodyStream = client.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/events")).build(),
                        BodyHandlers.ofInputStream()).body();
                // try-with-resources on the reader: closing the client (below) while /events is still
                // open would hang the suite (PR #30). Reader and client closes are coupled.
                try (var r = new BufferedReader(new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String frame = line.substring("data: ".length());
                            // Type-agnostic: add(7)'s last event may be Add or the root-blacken Recolor;
                            // both carry the whole-set snapshot with element 7. The TreeSet has no
                            // CreateNode (Add is the creation), so do NOT assert one.
                            assertTrue(frame.contains("\"set\":{"), "frame carries the whole-set snapshot");
                            assertTrue(frame.contains("\"element\":\"7\""), "frame carries the added element");
                            return;
                        }
                    }
                    throw new IOException("no data line received");
                }
            });
        } finally {
            client.close();
            server.stop();
        }
    }
}
```

- [ ] **Step 2: Run it — it should PASS on the shipped Task 1-2 code**

Run: `mvn -q -Dtest=TreeSetLiveVizEndToEndTest test`
Expected: PASS. Unlike a red-first unit test, this is an **integration proof**: it wires together only code that already shipped in Tasks 1-2 (`TreeSetLiveVisualizer` + `TreeSetWebExporter.liveHtml()` + the reused `LiveServer`), so it is green immediately. It is the transport gate for the demo (whose `main` parks and is untested-by-design). If it instead **hangs**, you hit trap 1 — confirm the SSE `BufferedReader` is in try-with-resources and `client.close()` is in the `finally`. If it fails an assertion, re-read trap 2 (assert the whole-set snapshot, not a `CreateNode`/specific type).

- [ ] **Step 3: Implement `TreeSetLiveWebVizDemo`**

Create `src/main/java/com/gimlism/translucent/treeset/demo/TreeSetLiveWebVizDemo.java`:

```java
package com.gimlism.translucent.treeset.demo;

import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.viz.TreeSetLiveVisualizer;
import com.gimlism.translucent.treeset.viz.TreeSetWebExporter;
import java.io.IOException;

/**
 * The student sandbox: a running {@link TeachingTreeSet} whose every event renders live in the
 * browser. Write your own add/remove/contains calls in the marked block, then run:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.treeset.demo.TreeSetLiveWebVizDemo}
 * and watch the red-black tree rotate and recolour as your code runs — and, because the set narrates
 * reads, watch a {@code contains} walk animate the comparison cursor live. The server keeps running
 * after your code finishes so the final state stays live and scrubbable — stop it with Ctrl-C.
 */
public class TreeSetLiveWebVizDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        LiveServer server = new LiveServer(TreeSetWebExporter.liveHtml(), "127.0.0.1", DEFAULT_PORT);
        server.start();
        String url = "http://localhost:" + server.port();

        var set = new TeachingTreeSet<Integer>();
        set.addListener(new TreeSetLiveVisualizer(server::broadcast));

        BrowserLauncher.open(url);
        System.out.println("Serving live at " + url + " — Ctrl-C to stop.");

        // ---------------------------------------------------------------
        // TODO: your mutations here — each renders live in the browser.
        for (int e : new int[] {10, 20, 30, 40, 50}) { // watch rotations + recolours
            set.add(e);
        }
        set.contains(25); // a read: the comparison walk animates live (reads narrate)
        set.remove(30);
        // ---------------------------------------------------------------

        DemoLifecycle.awaitShutdown(server);
    }
}
```

- [ ] **Step 4: Run the e2e test to verify it passes**

Run: `mvn -q -Dtest=TreeSetLiveVizEndToEndTest test`
Expected: PASS — a real mutation surfaced as an SSE `data:` frame; no hang (reader closed before the client).

- [ ] **Step 5: Run the full suite**

Run: `mvn test`
Expected: BUILD SUCCESS. `Tests run: N` = 439 + 2 (live visualizer) + 2 (exporter live) + 1 (e2e) = **444**.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/treeset/demo/TreeSetLiveWebVizDemo.java \
        src/test/java/com/gimlism/translucent/treeset/viz/TreeSetLiveVizEndToEndTest.java
git commit -m "feat(treeset): TreeSetLiveWebVizDemo + headless SSE end-to-end test"
```

---

## After all tasks — controller browser verification (not a subagent task)

The live JS (EventSource, follow-tail, jump-to-live) is untested-by-design; the controller browser-verifies it. **Connect DURING streaming**, not just after — otherwise only snapshot-on-connect is exercised and the novel incremental push is never seen. Recipe:

1. `mvn -q process-classes` (copies `/web/treeset-viz.html` into `target/classes`).
2. Run a throwaway driver (or `TreeSetLiveWebVizDemo` with `-Djava.awt.headless=true` so `BrowserLauncher` no-ops) that starts a `LiveServer` on a fixed port and streams mutations on a timer — e.g. add 10/20/30/40/50 with a sleep between each, then a `contains(25)`, then `remove(30)`, then a trailing `sleep` so the process stays parked.
3. Connect the claude-in-chrome MCP to `http://localhost:PORT` **while the driver is mid-stream**.
4. Confirm: the frame counter **climbs live on one connection with no reload** (the incremental push); rotations/recolours render as they arrive; the **`contains(25)` walk animates the comparison cursor live** (the reads-narrate showcase, no sibling has this); scrub back → "⏭ N new — jump to live" badge with the correct behind-count → click resumes follow-tail; reload → snapshot-on-connect shows the latest frame; **zero console errors**.
5. Screenshot a representative live frame; `pkill -f TreeSetLiveWebVizDemo` (or the driver) and stop the server when done.

Then: whole-branch opus review (base `git merge-base main HEAD`) → PR → Copilot triage → `gh pr merge N --merge` (NOT squash, NOT `--delete-branch`) on user go-ahead.

## Self-review notes

- **Spec coverage:** the stateless broadcasting listener + reads-narrate-live (Task 1); `liveHtml()` + the LIVE EventSource/follow-tail/jump-to-live branch, CONTROLS staying off (Task 2); the student sandbox demo + the headless SSE e2e with both documented traps (Task 3); browser verification connecting during streaming (controller). Every spec section maps to a task.
- **Type consistency:** `TreeSetLiveVisualizer(Consumer<String>)`, `TreeSetWebExporter.liveHtml()`, `server::broadcast`, `TreeSetJsonSerializer.toFrame` are used identically across tasks. The e2e asserts the frame shape (`"set":{`, `"element":"7"`) the serializer emits (Slice A).
- **No placeholders:** every code, command, and HTML-edit step is complete and literal.
