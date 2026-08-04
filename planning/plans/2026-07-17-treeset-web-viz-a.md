# TreeSet Web Visualizer — Slice A (static replay) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give `TeachingTreeSet` a self-contained HTML page that replays a recorded `SetEvent` stream as a top-down binary red-black tree in SVG, with a scrubber — the web analogue of the ASCII renderer.

**Architecture:** Three new per-structure files on the established web-viz seam. `TreeSetJsonSerializer` (mirrors `TrieJsonSerializer`) turns events into replay JSON, resolving the per-node highlight **in Java** as an `hl` boolean. `treeset-viz.html` is a dumb SVG renderer composing the trie's page shell with the map's in-order red/black-node rendering. `TreeSetWebExporter` is the 4th `WebVizTemplate` consumer. The generic substrate is untouched.

**Tech Stack:** Java 21 (Maven, release 21), JUnit 5, vanilla JS/SVG (no dependencies).

## Global Constraints

- **Java release 21**; no new Maven dependencies. **JUnit 5**; tests package-private.
- **Package layout:** `treeset/viz/{TreeSetJsonSerializer,TreeSetWebExporter}`, `treeset/demo/TreeSetWebVizDemo`, resource `src/main/resources/web/treeset-viz.html`.
- **Do not touch** `treeset/core`, `treeset/events`, `substrate/rbtree`, `substrate/viz/JsonWriter`, `substrate/viz/WebVizTemplate` — events/snapshots consumed as shipped (keeps snapshot-before-settled off-surface).
- **Self-contained page:** `treeset-viz.html` inlines all CSS + JS; no external URLs, fonts, or scripts.
- **Highlight resolved in Java:** the serializer sets `hl:true` on the node whose `element.equals(AsciiSetRenderer.affectedElement(e))`, else `false` (Remove → none). The browser reads the boolean — it never value-matches, so no JS number-vs-string skew.
- **Slice A wires FRAMES + scrubber only.** All three tokens (`/*__FRAMES__*/`, `/*__LIVE__*/`, `/*__CONTROLS__*/`) are present (required by `WebVizTemplate.inject`) and baked to `false`, but the `if (LIVE) { EventSource… }` SSE block and the command box are **not** in this slice (they arrive in B/D).
- **Small teaching regime:** elements are drawn *inside* the node circle — short integers/strings only.
- **Every commit ends with the two trailer lines** (`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` and the `Claude-Session:` line), per CLAUDE.md.
- **Branch:** `feat/treeset-web-visualizer` (already created; the spec commit is on it).
- **mvn is source of truth**; stale Eclipse/LSP diagnostics are known false positives. Full-suite baseline before this work: **430 tests green**.

---

### Task 1: `TreeSetJsonSerializer` — events → replay JSON with the highlight resolved in Java

**Files:**
- Create: `src/main/java/com/gimlism/translucent/treeset/viz/TreeSetJsonSerializer.java`
- Test: `src/test/java/com/gimlism/translucent/treeset/viz/TreeSetJsonSerializerTest.java`

**Interfaces:**
- Consumes: `JsonWriter` (`beginObject`/`endObject`/`beginArray`/`endArray`/`name(String)`/`value(String)`/`value(long)`/`value(boolean)` — `value((String) null)` emits `null`); shipped `SetSnapshot(SetNodeSnapshot root, int size)`, `SetNodeSnapshot(Object element, boolean red, SetNodeSnapshot left, SetNodeSnapshot right)`, `SetEventFormatter.format(SetEvent)`, `AsciiSetRenderer.affectedElement(SetEvent)`, events `Add`/`Compare`/`Remove`/`Rotation`/`Recolor`.
- Produces: `TreeSetJsonSerializer` with `static String toJson(List<SetEvent>)` and `static String toFrame(SetEvent)`. Frame shape: `{"event":{"type","label"},"set":{"size",root}}`; node: `{"element","red","hl","left","right"}` (children `null` when absent).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/treeset/viz/TreeSetJsonSerializerTest.java`:

```java
package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.treeset.consumer.SetRecordingListener;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.events.Add;
import com.gimlism.translucent.treeset.events.Remove;
import com.gimlism.translucent.treeset.events.SetEvent;
import com.gimlism.translucent.treeset.events.SetNodeSnapshot;
import com.gimlism.translucent.treeset.events.SetSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeSetJsonSerializerTest {

    // A deterministic hand-built tree: 20(black) { left 10(red), right 30(red) }.
    private static SetSnapshot threeNodeTree() {
        var n10 = new SetNodeSnapshot(10, true, null, null);
        var n30 = new SetNodeSnapshot(30, true, null, null);
        return new SetSnapshot(new SetNodeSnapshot(20, false, n10, n30), 3);
    }

    @Test
    void emptyStreamProducesEmptyFramesArray() {
        assertEquals("{\"frames\":[]}", TreeSetJsonSerializer.toJson(List.of()));
    }

    @Test
    void frameCarriesEventFieldsAndRecursiveSet() {
        var set = new TeachingTreeSet<Integer>();
        var rec = new SetRecordingListener();
        set.addListener(rec);
        set.add(10);

        String json = TreeSetJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"frames\":["), json);
        assertTrue(json.contains("\"type\":\"Add\""), json);
        assertTrue(json.contains("\"label\":\"add 10\""), json);
        assertTrue(json.contains("\"set\":{\"size\":1"), json);
        assertTrue(json.contains("\"element\":\"10\""), json);
        assertTrue(json.contains("\"left\":null"), json);   // a leaf
        assertTrue(json.contains("\"right\":null"), json);
    }

    @Test
    void highlightMarksTheAffectedNodeInJavaNotOthers() {
        // Add(30) -> affectedElement is 30 -> only the "30" node is hl:true.
        String frame = TreeSetJsonSerializer.toFrame(new Add(30, threeNodeTree()));
        assertTrue(frame.contains("\"element\":\"30\",\"red\":true,\"hl\":true"), frame);
        assertTrue(frame.contains("\"element\":\"20\",\"red\":false,\"hl\":false"), frame);
        assertTrue(frame.contains("\"element\":\"10\",\"red\":true,\"hl\":false"), frame);
    }

    @Test
    void removeFrameHighlightsNothing() {
        // affectedElement(Remove) is null -> no node is hl:true.
        String frame = TreeSetJsonSerializer.toFrame(new Remove(10, threeNodeTree()));
        assertFalse(frame.contains("\"hl\":true"), frame);
        assertTrue(frame.contains("\"label\":\"remove 10\""), frame);
    }

    @Test
    void emptySetSerializesNullRoot() {
        String frame = TreeSetJsonSerializer.toFrame(new Remove(10, new SetSnapshot(null, 0)));
        assertTrue(frame.contains("\"set\":{\"size\":0,\"root\":null}"), frame);
    }

    @Test
    void toFrameProducesASingleFrameWithNoFramesWrapper() {
        var set = new TeachingTreeSet<Integer>();
        var rec = new SetRecordingListener();
        set.addListener(rec);
        set.add(7);
        List<SetEvent> events = rec.events();
        String frame = TreeSetJsonSerializer.toFrame(events.get(events.size() - 1));
        assertFalse(frame.contains("\"frames\""), "single frame carries no frames wrapper");
        assertTrue(frame.startsWith("{\"event\":{"), frame);
        assertTrue(TreeSetJsonSerializer.toJson(events).contains(frame),
                "the single frame is a substring of the full frames blob");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -Dtest=TreeSetJsonSerializerTest test`
Expected: FAIL — compilation error, `TreeSetJsonSerializer` does not exist.

- [ ] **Step 3: Implement `TreeSetJsonSerializer`**

Create `src/main/java/com/gimlism/translucent/treeset/viz/TreeSetJsonSerializer.java`:

```java
package com.gimlism.translucent.treeset.viz;

import com.gimlism.translucent.substrate.viz.JsonWriter;
import com.gimlism.translucent.treeset.events.SetEvent;
import com.gimlism.translucent.treeset.events.SetEventFormatter;
import com.gimlism.translucent.treeset.events.SetNodeSnapshot;
import com.gimlism.translucent.treeset.events.SetSnapshot;
import java.util.List;

/**
 * The per-structure half of the TreeSet web-viz seam: maps a recorded {@code SetEvent} stream to the
 * JSON the browser replay consumes. The web analogue of {@link AsciiSetRenderer} — it holds the
 * recursive binary-node serialization so the front-end stays a dumb renderer. Each frame carries the
 * event's own {@code after()} snapshot; captions come from {@link SetEventFormatter}.
 *
 * <p>The per-node highlight is resolved <em>here, in Java</em>: the node whose element equals
 * {@link AsciiSetRenderer#affectedElement} is marked {@code hl:true} (Remove's null target marks
 * none). The browser reads a boolean rather than value-matching an {@code Object} across the JSON
 * boundary — so a number-vs-string mismatch can never silently drop the highlight.
 */
public final class TreeSetJsonSerializer {

    private TreeSetJsonSerializer() {}

    public static String toJson(List<SetEvent> events) {
        JsonWriter w = new JsonWriter();
        w.beginObject().name("frames").beginArray();
        for (SetEvent e : events) writeFrame(w, e);
        w.endArray().endObject();
        return w.toString();
    }

    /** One event → the JSON for a single {@code { "event":…, "set":… }} frame (no {@code frames} wrapper). */
    public static String toFrame(SetEvent e) {
        JsonWriter w = new JsonWriter();
        writeFrame(w, e);
        return w.toString();
    }

    private static void writeFrame(JsonWriter w, SetEvent e) {
        w.beginObject();
        w.name("event");
        writeEvent(w, e);
        w.name("set");
        writeSet(w, e.after(), AsciiSetRenderer.affectedElement(e));
        w.endObject();
    }

    private static void writeEvent(JsonWriter w, SetEvent e) {
        w.beginObject();
        w.name("type").value(e.getClass().getSimpleName());
        w.name("label").value(SetEventFormatter.format(e));
        w.endObject();
    }

    private static void writeSet(JsonWriter w, SetSnapshot s, Object highlight) {
        w.beginObject();
        w.name("size").value((long) s.size());
        w.name("root");
        writeNode(w, s.root(), highlight);
        w.endObject();
    }

    private static void writeNode(JsonWriter w, SetNodeSnapshot node, Object highlight) {
        if (node == null) {
            w.value((String) null);
            return;
        }
        w.beginObject();
        w.name("element").value(String.valueOf(node.element()));
        w.name("red").value(node.red());
        w.name("hl").value(highlight != null && highlight.equals(node.element()));
        w.name("left");
        writeNode(w, node.left(), highlight);
        w.name("right");
        writeNode(w, node.right(), highlight);
        w.endObject();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=TreeSetJsonSerializerTest test`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/treeset/viz/TreeSetJsonSerializer.java \
        src/test/java/com/gimlism/translucent/treeset/viz/TreeSetJsonSerializerTest.java
git commit -m "feat(treeset): TreeSetJsonSerializer — SetEvent stream to replay JSON (highlight in Java)"
```

---

### Task 2: `treeset-viz.html` + `TreeSetWebExporter`

**Files:**
- Create: `src/main/resources/web/treeset-viz.html`
- Create: `src/main/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporter.java`
- Test: `src/test/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporterTest.java`

**Interfaces:**
- Consumes: `WebVizTemplate.inject(resource, framesReplacement, liveReplacement, controlsReplacement)`.
- Produces: `TreeSetWebExporter` with `static String toHtml(String framesJson)` and `static void writeHtml(String framesJson, Path out) throws IOException`. The template exposes JS globals `const DATA = /*__FRAMES__*/;`, `const LIVE = /*__LIVE__*/;`, `const CONTROLS = /*__CONTROLS__*/;`.

- [ ] **Step 1: Create the SVG template resource**

Create `src/main/resources/web/treeset-viz.html` (self-contained; trie shell + map RB-node rendering; **no** live/controls JS this slice):

```html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>TeachingTreeSet — replay</title>
<style>
  :root { color-scheme: light dark; --bg:#f7f7f8; --fg:#1b1b1d; --muted:#777; --card:#fff;
          --edge:#8a8a8a; --cell:#eceef2; --cellb:#c8ccd4; --red:#d64545; --black:#333; --hl:#f5a623; }
  @media (prefers-color-scheme: dark) {
    :root { --bg:#16171a; --fg:#e8e8ea; --muted:#999; --card:#1f2126; --edge:#8a8a8a;
            --cell:#2a2d33; --cellb:#41454d; --red:#e05a5a; --black:#c9ccd2; --hl:#f5a623; }
  }
  * { box-sizing: border-box; }
  body { margin:0; background:var(--bg); color:var(--fg); font:15px system-ui,sans-serif; }
  #app { max-width: 960px; margin: 0 auto; padding: 18px; }
  h1 { font-size: 16px; font-weight:600; margin: 0 0 12px; }
  #bar { display:flex; align-items:center; gap:12px; flex-wrap:wrap; margin-bottom:10px; }
  button { font:inherit; padding:6px 12px; border:1px solid var(--cellb); border-radius:8px;
           background:var(--card); color:var(--fg); cursor:pointer; }
  button:disabled { opacity:.4; cursor:default; }
  #counter { color:var(--muted); font-variant-numeric: tabular-nums; }
  #caption { font-weight:600; min-height:1.4em; margin-bottom:8px; }
  #stage { background:var(--card); border:1px solid var(--cellb); border-radius:12px; padding:10px;
           transition: opacity .18s ease; overflow:auto; }
  svg text { fill: var(--fg); font: 13px system-ui, sans-serif; }
  .edge { stroke: var(--edge); stroke-width: 2; }
  .node circle { stroke: #0006; stroke-width: 1.5; }
  .node.red circle { fill: var(--red); }
  .node.black circle { fill: var(--black); }
  .node.hl circle { stroke: var(--hl); stroke-width: 3.5; }
  .node text { fill: #fff; font-variant-numeric: tabular-nums; }
</style>
</head>
<body>
<div id="app">
  <h1>TeachingTreeSet — web replay</h1>
  <div id="caption"></div>
  <div id="bar">
    <button id="prev">◀ prev</button>
    <button id="play">▶ play</button>
    <button id="next">next ▶</button>
    <span id="counter"></span>
  </div>
  <div id="stage"><svg id="svg"></svg></div>
</div>
<script>
"use strict";
const DATA = /*__FRAMES__*/;
const LIVE = /*__LIVE__*/;         // Slice A: baked false; SSE branch arrives in Slice B
const CONTROLS = /*__CONTROLS__*/; // Slice A: baked false; command box arrives in Slice D
const frames = (DATA && DATA.frames) || [];
const svg = document.getElementById("svg");
const stage = document.getElementById("stage");
const caption = document.getElementById("caption");
const counter = document.getElementById("counter");
const prevBtn = document.getElementById("prev");
const nextBtn = document.getElementById("next");
const playBtn = document.getElementById("play");

const PAD = 28, V_GAP = 72, H_GAP = 54, R = 16;
let idx = 0, timer = null;

function esc(s) { return String(s).replace(/[&<>]/g, c => ({ "&":"&amp;","<":"&lt;",">":"&gt;" }[c])); }

// In-order slot (x) + depth (y) per node: left subtree always renders left, right always right
// (BST-faithful — a single child sits on its correct side). Mirrors the map's tree-bin layout.
function buildTree(root) {
  const nodes = [], edges = [], pos = new Map();
  let slot = 0, maxDepth = 0;
  (function assign(n, depth) {
    if (!n) return;
    assign(n.left, depth + 1);
    const rec = { element: n.element, red: n.red, hl: n.hl, s: slot++, depth };
    maxDepth = Math.max(maxDepth, depth);
    nodes.push(rec); pos.set(n, rec);
    assign(n.right, depth + 1);
  })(root, 0);
  (function link(n) {
    if (!n) return;
    const p = pos.get(n);
    if (n.left)  { edges.push([p, pos.get(n.left)]);  link(n.left); }
    if (n.right) { edges.push([p, pos.get(n.right)]); link(n.right); }
  })(root);
  return { nodes, edges, slots: Math.max(1, slot), depth: maxDepth };
}

function renderFrame(f) {
  const root = f.set.root;
  if (!root) {
    svg.innerHTML = "";
    svg.setAttribute("viewBox", "0 0 320 80");
    svg.setAttribute("width", 320);
    svg.setAttribute("height", 80);
    return;
  }
  const t = buildTree(root);
  const cx = s => PAD + s * H_GAP + R;
  const cy = d => PAD + d * V_GAP + R;
  let body = "";
  t.edges.forEach(([p, c]) => {
    body += `<line class="edge" x1="${cx(p.s)}" y1="${cy(p.depth)}" x2="${cx(c.s)}" y2="${cy(c.depth)}"/>`;
  });
  t.nodes.forEach(n => {
    const cls = "node " + (n.red ? "red" : "black") + (n.hl ? " hl" : "");
    body += `<g class="${cls}"><circle cx="${cx(n.s)}" cy="${cy(n.depth)}" r="${R}"/>`
          + `<text x="${cx(n.s)}" y="${cy(n.depth) + 4}" text-anchor="middle">${esc(n.element)}</text></g>`;
  });
  const width = Math.max(320, PAD * 2 + t.slots * H_GAP);
  const height = PAD * 2 + (t.depth + 1) * V_GAP;
  svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
  svg.setAttribute("width", width);
  svg.setAttribute("height", height);
  svg.innerHTML = body;
}

function refreshMeta() {
  counter.textContent = frames.length ? `frame ${idx + 1} / ${frames.length}` : "";
  prevBtn.disabled = idx <= 0;
  nextBtn.disabled = idx >= frames.length - 1;
}

function render() {
  const f = frames[idx];
  caption.textContent = f ? f.event.label : (LIVE ? "(waiting for live events…)" : "(no events to replay)");
  if (f) renderFrame(f); else svg.innerHTML = "";
  stage.style.opacity = "0";
  requestAnimationFrame(() => requestAnimationFrame(() => { stage.style.opacity = "1"; }));
  refreshMeta();
}

function go(n) { idx = Math.max(0, Math.min(frames.length - 1, n)); render(); }
function stop() { if (timer) { clearInterval(timer); timer = null; playBtn.textContent = "▶ play"; } }
prevBtn.onclick = () => { stop(); go(idx - 1); };
nextBtn.onclick = () => { stop(); go(idx + 1); };
playBtn.onclick = () => {
  if (timer) { stop(); return; }
  if (idx >= frames.length - 1) idx = 0;
  playBtn.textContent = "⏸ pause";
  timer = setInterval(() => { if (idx >= frames.length - 1) stop(); else go(idx + 1); }, 900);
};
document.addEventListener("keydown", e => {
  if (e.key === "ArrowRight") { stop(); go(idx + 1); }
  else if (e.key === "ArrowLeft") { stop(); go(idx - 1); }
});
render();
</script>
</body>
</html>
```

- [ ] **Step 2: Write the failing exporter test**

Create `src/test/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporterTest.java`:

```java
package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TreeSetWebExporterTest {

    @Test
    void toHtmlInjectsFramesAndBakesFlagsFalse() {
        String frames = "{\"frames\":[]}";
        String html = TreeSetWebExporter.toHtml(frames);

        assertTrue(html.contains("<!doctype html>"), "expected a full document");
        assertTrue(html.contains("</html>"), "expected closing html tag");
        assertTrue(html.contains("const DATA = " + frames + ";"), html);
        assertTrue(html.contains("const LIVE = false;"), html);
        assertTrue(html.contains("const CONTROLS = false;"), html);
        assertFalse(html.contains("/*__FRAMES__*/"), "FRAMES token not substituted");
        assertFalse(html.contains("/*__LIVE__*/"), "LIVE token not substituted");
        assertFalse(html.contains("/*__CONTROLS__*/"), "CONTROLS token not substituted");
        // Slice A ships no live/controls JS branch
        assertFalse(html.contains("EventSource"), "no SSE branch in Slice A");
        assertFalse(html.contains("/command"), "no command box in Slice A");
    }

    @Test
    void writeHtmlWritesTheSameDocumentToDisk(@TempDir Path dir) throws IOException {
        String frames = "{\"frames\":[]}";
        Path out = dir.resolve("treeset.html");
        TreeSetWebExporter.writeHtml(frames, out);
        assertEquals(TreeSetWebExporter.toHtml(frames), Files.readString(out));
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `mvn -q -Dtest=TreeSetWebExporterTest test`
Expected: FAIL — compilation error, `TreeSetWebExporter` does not exist. (The template resource already exists from Step 1.)

- [ ] **Step 4: Implement `TreeSetWebExporter`**

Create `src/main/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporter.java`:

```java
package com.gimlism.translucent.treeset.viz;

import com.gimlism.translucent.substrate.viz.WebVizTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assembles the self-contained TeachingTreeSet HTML page from {@code /web/treeset-viz.html} by
 * delegating token substitution to {@link WebVizTemplate}: a baked {@code FRAMES} JSON blob (from
 * {@link TreeSetJsonSerializer}) plus the {@code LIVE} and {@code CONTROLS} mode flags — both {@code
 * false} for a static replay. The template carries the whole vanilla-JS/SVG renderer; this class only
 * chooses the three replacement values. Fourth consumer of {@link WebVizTemplate}, alongside the map,
 * list, and trie exporters. Slice A exposes only the static {@code toHtml}; {@code liveHtml}/{@code
 * controlsHtml} arrive with Slices B/D.
 */
public final class TreeSetWebExporter {

    private static final String TEMPLATE_RESOURCE = "/web/treeset-viz.html";

    private TreeSetWebExporter() {}

    /** The self-contained baked HTML with {@code framesJson} injected; live + controls OFF. */
    public static String toHtml(String framesJson) {
        return WebVizTemplate.inject(TEMPLATE_RESOURCE, framesJson, "false", "false");
    }

    /** Write {@link #toHtml(String)} to {@code out} (UTF-8). */
    public static void writeHtml(String framesJson, Path out) throws IOException {
        Files.writeString(out, toHtml(framesJson));
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q -Dtest=TreeSetWebExporterTest test`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/web/treeset-viz.html \
        src/main/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporter.java \
        src/test/java/com/gimlism/translucent/treeset/viz/TreeSetWebExporterTest.java
git commit -m "feat(treeset): treeset-viz.html SVG renderer + TreeSetWebExporter (static replay)"
```

---

### Task 3: `TreeSetWebVizDemo` — bake a scripted story

**Files:**
- Create: `src/main/java/com/gimlism/translucent/treeset/demo/TreeSetWebVizDemo.java`
- Test: `src/test/java/com/gimlism/translucent/treeset/demo/TreeSetWebVizDemoTest.java`

**Interfaces:**
- Consumes: `TeachingTreeSet`, `SetRecordingListener`, `TreeSetJsonSerializer` (Task 1), `TreeSetWebExporter` (Task 2).
- Produces: `TreeSetWebVizDemo` with `static void main(String[]) throws IOException` and a package-private `static String buildHtml()` test seam.

- [ ] **Step 1: Write the failing demo test**

Create `src/test/java/com/gimlism/translucent/treeset/demo/TreeSetWebVizDemoTest.java`:

```java
package com.gimlism.translucent.treeset.demo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TreeSetWebVizDemoTest {

    @Test
    void buildHtmlBakesTheStoryIntoASelfContainedPage() {
        String html = TreeSetWebVizDemo.buildHtml();
        assertTrue(html.contains("<!doctype html>"), "self-contained document");
        assertTrue(html.contains("</html>"), html);
        assertTrue(html.contains("const LIVE = false;"), "static replay");
        assertTrue(html.contains("\"type\":\"Add\""), "baked frames present");
        assertTrue(html.contains("\"element\":\"50\""), "the story's elements are serialized");
        assertTrue(html.contains("\"label\":\"remove 30\""), "the removal is in the story");
        assertFalse(html.contains("/*__FRAMES__*/"), "no unsubstituted token");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -Dtest=TreeSetWebVizDemoTest test`
Expected: FAIL — compilation error, `TreeSetWebVizDemo` does not exist.

- [ ] **Step 3: Implement `TreeSetWebVizDemo`**

Create `src/main/java/com/gimlism/translucent/treeset/demo/TreeSetWebVizDemo.java`:

```java
package com.gimlism.translucent.treeset.demo;

import com.gimlism.translucent.treeset.consumer.SetRecordingListener;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.viz.TreeSetJsonSerializer;
import com.gimlism.translucent.treeset.viz.TreeSetWebExporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Records a small story that exercises the tree's rebalancing — inserting ascending keys (watch
 * rotations and recolours keep the tree balanced), then removing one — and writes it out as a
 * self-contained HTML web replay. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.treeset.demo.TreeSetWebVizDemo}
 * (optionally {@code -Dexec.args="path/to/out.html"}). The whole-document build is factored into
 * {@link #buildHtml()} so it can be unit-tested without touching the filesystem.
 */
public class TreeSetWebVizDemo {

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "target/treeset-web-viz.html");
        Path parent = out.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(out, buildHtml());
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    /** The self-contained HTML replay for the standard story (package-private test seam — no filesystem). */
    static String buildHtml() {
        var set = new TeachingTreeSet<Integer>();
        var rec = new SetRecordingListener();
        set.addListener(rec);
        for (int e : new int[] {10, 20, 30, 40, 50}) {
            set.add(e);   // ascending inserts force rotations + recolours
        }
        set.remove(30);
        return TreeSetWebExporter.toHtml(TreeSetJsonSerializer.toJson(rec.events()));
    }
}
```

- [ ] **Step 4: Run the demo test to verify it passes**

Run: `mvn -q -Dtest=TreeSetWebVizDemoTest test`
Expected: PASS.

- [ ] **Step 5: Run the full suite**

Run: `mvn test`
Expected: BUILD SUCCESS. `Tests run: N` = 430 + 6 (serializer) + 2 (exporter) + 1 (demo) = **439**.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/treeset/demo/TreeSetWebVizDemo.java \
        src/test/java/com/gimlism/translucent/treeset/demo/TreeSetWebVizDemoTest.java
git commit -m "feat(treeset): TreeSetWebVizDemo — bake a scripted RB-tree story to HTML"
```

---

## After all tasks — controller browser verification (not a subagent task)

The SVG JavaScript is untested-by-design; the controller browser-verifies it (subagents cannot drive a browser). Recipe (mirrors the map/list/trie Slice-A verification):

1. `mvn -q process-classes` (copies `/web/treeset-viz.html` into `target/classes`), then run the demo to bake `target/treeset-web-viz.html`, OR serve the template + inject at runtime.
2. Serve over `http://localhost:PORT` — the Chrome extension blocks `file://` — e.g. a throwaway static server rooted where the baked HTML lives.
3. Open in Chrome (claude-in-chrome MCP) and confirm: the RB tree renders **top-down**, nodes filled **red/black** with the element centered; stepping frames shows inserts, **rotations** (subtree restructures) and **recolours** (fill flips); the **highlight ring** tracks the affected node and a **Remove** frame shows no ring; prev/play/next + arrow keys work; **zero console errors**.
4. Screenshot a couple of representative frames for the record; kill the server when done.

Then: whole-branch opus review (base `git merge-base main HEAD`) → PR → Copilot triage → `gh pr merge N --merge` (NOT squash, NOT `--delete-branch`) on user go-ahead.

## Self-review notes

- **Spec coverage:** serializer with Java-resolved highlight (Task 1); the SVG renderer composing trie-shell + map-node rendering, and the exporter with LIVE/CONTROLS baked false + no SSE/command JS (Task 2); the demo (Task 3); browser verification (controller). Every spec section maps to a task.
- **Type consistency:** frame shape `{event:{type,label}, set:{size,root}}` and node `{element,red,hl,left,right}` are identical between the serializer (Task 1) and the `treeset-viz.html` renderer (Task 2, reads `f.set.root` and `n.element/n.red/n.hl/n.left/n.right`). `toHtml`/`writeHtml` signatures match between Task 2 and Task 3's `buildHtml`.
- **No placeholders:** every code and command step is complete and literal.
