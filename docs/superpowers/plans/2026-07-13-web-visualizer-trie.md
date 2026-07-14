# Trie Web Visualizer — Slice A (static replay) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a self-contained HTML page that replays a recorded `TrieEvent` stream as an SVG top-down node-link tree — the RadixTrie's first non-terminal visualizer.

**Architecture:** Reuses the established web-viz seam. A pure `TrieJsonSerializer` (Java owns event-shape branching) maps the event stream to JSON; a `trie-viz.html` template holds a vanilla-JS/SVG dumb renderer; `TrieWebExporter` bakes the two together via the shared `WebVizTemplate`; a `TrieWebVizDemo` writes the demo story to disk. No core/events/substrate change → the snapshot-before-settled bug has zero surface.

**Tech Stack:** Java 21 (built on JDK 26, `maven.compiler.release=21`), Maven, JUnit 5, vanilla JS/SVG.

## Global Constraints

- **Target Java 21** (`maven.compiler.release=21`); build/test on JDK 26. `mvn` is the source of truth — ignore stale Eclipse-LSP "cannot be resolved" diagnostics.
- **Do NOT modify** `trie/core/**`, `trie/events/**`, `substrate/viz/JsonWriter`, or `substrate/viz/WebVizTemplate`. Slice A adds only `trie/viz`, `trie/demo`, and one resource.
- **Minimal template scope:** `trie-viz.html` carries all three tokens (`/*__FRAMES__*/`, `/*__LIVE__*/`, `/*__CONTROLS__*/`) so `WebVizTemplate.inject` passes, but wires **only** the FRAMES + scrubber JavaScript. No SSE/EventSource, no command box — those arrive in Slices B/D.
- **Captions and highlight are reused, not reinvented:** `TrieEventFormatter.format(e)` is the one source of caption wording; `AsciiTrieRenderer.affectedPath(e)` is the one source of the highlight target (both already exist; `affectedPath` is in the same `trie/viz` package, no import needed).
- **The SVG JS is untested-by-design** — verified by the controller in a browser (Task 3 final step), never by JUnit.
- **Commit footer** on every commit:
  ```
  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm
  ```

## File Structure

| File | Task | Responsibility |
|---|---|---|
| `src/main/java/com/gimlism/translucent/trie/viz/TrieJsonSerializer.java` | 1 | `TrieEvent` stream → replay JSON (recursive nodes, per-event highlight path). |
| `src/test/java/com/gimlism/translucent/trie/viz/TrieJsonSerializerTest.java` | 1 | Frame shape, highlight (incl. Prune→parent), value/null, empty stream. |
| `src/main/resources/web/trie-viz.html` | 2 | Self-contained page: 3 tokens + scrubber + top-down SVG `renderFrame`. |
| `src/main/java/com/gimlism/translucent/trie/viz/TrieWebExporter.java` | 2 | `toHtml`/`writeHtml`, delegating token substitution to `WebVizTemplate`. |
| `src/test/java/com/gimlism/translucent/trie/viz/TrieWebExporterTest.java` | 2 | Injection: FRAMES in, flags baked `false`, no tokens survive. |
| `src/main/java/com/gimlism/translucent/trie/demo/TrieWebVizDemo.java` | 3 | Bakes the `{shore,she,shell}` insert-then-remove story to disk. |
| `src/test/java/com/gimlism/translucent/trie/demo/TrieWebVizDemoTest.java` | 3 | Demo produces a self-contained doc exercising insert + remove. |

---

## Task 1: TrieJsonSerializer

**Files:**
- Create: `src/main/java/com/gimlism/translucent/trie/viz/TrieJsonSerializer.java`
- Test: `src/test/java/com/gimlism/translucent/trie/viz/TrieJsonSerializerTest.java`

**Interfaces:**
- Consumes: `JsonWriter` (`beginObject`/`endObject`/`beginArray`/`endArray`/`name(String)`/`value(String|long|boolean)`); `TrieEvent.after() → TrieSnapshot`; `TrieSnapshot.root() → TrieNodeSnapshot`, `.size() → int`; `TrieNodeSnapshot.key() → boolean`, `.value() → Object`, `.children() → List<TrieEdge>`; `TrieEdge.label() → String`, `.target() → TrieNodeSnapshot`; `TrieEventFormatter.format(TrieEvent) → String`; `AsciiTrieRenderer.affectedPath(TrieEvent) → String` (same package).
- Produces: `TrieJsonSerializer.toJson(List<TrieEvent>) → String` — the `{ "frames": [...] }` blob consumed by Tasks 2 and 3.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/trie/viz/TrieJsonSerializerTest.java`:

```java
package com.gimlism.translucent.trie.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.events.Prune;
import com.gimlism.translucent.trie.events.TrieEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrieJsonSerializerTest {

    /** Record the demo story: 3 inserts (split/branch) then 2 removes (prune/merge). */
    private static List<TrieEvent> story() {
        var trie = new RadixTrie<Integer>();
        var rec = new TrieRecordingListener();
        trie.addListener(rec);
        int v = 1;
        for (String key : new String[] {"shore", "she", "shell"}) trie.put(key, v++);
        for (String key : new String[] {"shell", "she"}) trie.remove(key);
        return rec.events();
    }

    @Test
    void emptyStreamProducesEmptyFramesArray() {
        assertEquals("{\"frames\":[]}", TrieJsonSerializer.toJson(List.of()));
    }

    @Test
    void frameCarriesEventFieldsAndRecursiveTrie() {
        var trie = new RadixTrie<Integer>();
        var rec = new TrieRecordingListener();
        trie.addListener(rec);
        trie.put("shore", 1);

        String json = TrieJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"frames\":["), json);
        // event object: type + label + highlightPath
        assertTrue(json.contains("\"type\":\"Put\""), json);
        assertTrue(json.contains("\"highlightPath\":\"shore\""), json);
        assertTrue(json.contains("\"label\":"), json);
        // recursive trie: size, an edge labelled with the (compressed) key, a key node value
        assertTrue(json.contains("\"trie\":{\"size\":1"), json);
        assertTrue(json.contains("\"label\":\"shore\""), json);
        assertTrue(json.contains("\"key\":true"), json);
        assertTrue(json.contains("\"value\":\"1\""), json);
        // no template token could ever appear; and children arrays are present
        assertTrue(json.contains("\"children\":["), json);
    }

    @Test
    void branchAndRootNodesSerializeKeyFalseValueNull() {
        var trie = new RadixTrie<Integer>();
        var rec = new TrieRecordingListener();
        trie.addListener(rec);
        trie.put("shore", 1);
        String json = TrieJsonSerializer.toJson(rec.events());
        // the root is not a key node
        assertTrue(json.contains("\"key\":false,\"value\":null"), json);
    }

    @Test
    void nullElementValueSerializesAsJsonNull() {
        var trie = new RadixTrie<Integer>();
        var rec = new TrieRecordingListener();
        trie.addListener(rec);
        trie.put("a", null);
        String json = TrieJsonSerializer.toJson(rec.events());
        // the "a" key node ends the word but carries a null value
        assertTrue(json.contains("\"key\":true,\"value\":null"), json);
    }

    @Test
    void pruneHighlightPathIsSurvivingParentNotDeletedNode() {
        List<TrieEvent> events = story();
        Prune prune = events.stream()
                .filter(Prune.class::isInstance).map(Prune.class::cast)
                .findFirst().orElseThrow();
        String parent = AsciiTrieRenderer.affectedPath(prune);
        // the pruned node's own path is longer than the highlighted (surviving) parent
        assertTrue(prune.path().length() > parent.length(), "expected parent shorter than pruned path");
        assertTrue(prune.path().startsWith(parent), "expected parent to be a prefix of pruned path");
        // and the serialized frame for that prune highlights the parent, not the deleted node
        String json = TrieJsonSerializer.toJson(List.of(prune));
        assertTrue(json.contains("\"highlightPath\":\"" + parent + "\""), json);
        assertFalse(json.contains("\"highlightPath\":\"" + prune.path() + "\""), json);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=TrieJsonSerializerTest test`
Expected: FAIL — compilation error, `TrieJsonSerializer` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/gimlism/translucent/trie/viz/TrieJsonSerializer.java`:

```java
package com.gimlism.translucent.trie.viz;

import com.gimlism.translucent.substrate.viz.JsonWriter;
import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieEvent;
import com.gimlism.translucent.trie.events.TrieEventFormatter;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;
import java.util.List;

/**
 * The per-structure half of the RadixTrie web-viz seam: maps a recorded {@code TrieEvent} stream to
 * the JSON the browser replay consumes. The web analogue of {@link AsciiTrieRenderer} — it holds the
 * recursive node serialization so the front-end stays a dumb renderer. Each frame carries the event's
 * own {@code after()} snapshot. Captions come from {@link TrieEventFormatter} and the per-frame
 * highlight target from {@link AsciiTrieRenderer#affectedPath} (same package) — so the ASCII and web
 * renderers name and highlight the identical node for every event, including the {@code Prune} case
 * where the highlighted node is the surviving parent, not the deleted leaf.
 */
public final class TrieJsonSerializer {

    private TrieJsonSerializer() {}

    public static String toJson(List<TrieEvent> events) {
        JsonWriter w = new JsonWriter();
        w.beginObject().name("frames").beginArray();
        for (TrieEvent e : events) writeFrame(w, e);
        w.endArray().endObject();
        return w.toString();
    }

    private static void writeFrame(JsonWriter w, TrieEvent e) {
        w.beginObject();
        w.name("event");
        writeEvent(w, e);
        w.name("trie");
        writeTrie(w, e.after());
        w.endObject();
    }

    private static void writeEvent(JsonWriter w, TrieEvent e) {
        w.beginObject();
        w.name("type").value(e.getClass().getSimpleName());
        w.name("label").value(TrieEventFormatter.format(e));
        w.name("highlightPath").value(AsciiTrieRenderer.affectedPath(e));
        w.endObject();
    }

    private static void writeTrie(JsonWriter w, TrieSnapshot s) {
        w.beginObject();
        w.name("size").value((long) s.size());
        w.name("root");
        writeNode(w, s.root());
        w.endObject();
    }

    private static void writeNode(JsonWriter w, TrieNodeSnapshot node) {
        w.beginObject();
        w.name("key").value(node.key());
        w.name("value").value(node.value() == null ? null : String.valueOf(node.value()));
        w.name("children").beginArray();
        for (TrieEdge edge : node.children()) {
            w.beginObject();
            w.name("label").value(edge.label());
            w.name("target");
            writeNode(w, edge.target());
            w.endObject();
        }
        w.endArray();
        w.endObject();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=TrieJsonSerializerTest test`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/viz/TrieJsonSerializer.java \
        src/test/java/com/gimlism/translucent/trie/viz/TrieJsonSerializerTest.java
git commit -m "feat(trie-viz): TrieJsonSerializer — event stream to replay JSON

Recursive node serialization + per-frame highlightPath reusing
AsciiTrieRenderer.affectedPath (Prune highlights the surviving parent).

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm"
```

---

## Task 2: trie-viz.html template + TrieWebExporter

**Files:**
- Create: `src/main/resources/web/trie-viz.html`
- Create: `src/main/java/com/gimlism/translucent/trie/viz/TrieWebExporter.java`
- Test: `src/test/java/com/gimlism/translucent/trie/viz/TrieWebExporterTest.java`

**Interfaces:**
- Consumes: `WebVizTemplate.inject(String resource, String frames, String live, String controls) → String`; the JSON from `TrieJsonSerializer.toJson`.
- Produces: `TrieWebExporter.toHtml(String framesJson) → String`; `TrieWebExporter.writeHtml(String framesJson, Path out)` — used by Task 3.

- [ ] **Step 1: Create the template resource**

Create `src/main/resources/web/trie-viz.html` (the SVG JS is untested-by-design; it is exercised in Task 3's browser verification):

```html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>RadixTrie — replay</title>
<style>
  :root { color-scheme: light dark; --bg:#f7f7f8; --fg:#1b1b1d; --muted:#777; --card:#fff;
          --node:#eceef2; --nodeb:#c8ccd4; --key:#cfe3ff; --keyb:#5b8def; --hl:#f5a623;
          --edge:#8a8a8a; }
  @media (prefers-color-scheme: dark) {
    :root { --bg:#16171a; --fg:#e8e8ea; --muted:#999; --card:#1f2126;
            --node:#2a2d33; --nodeb:#41454d; --key:#24405f; --keyb:#5b8def; --hl:#f5a623;
            --edge:#8a8a8a; }
  }
  * { box-sizing: border-box; }
  body { margin:0; background:var(--bg); color:var(--fg); font:15px system-ui,sans-serif; }
  #app { max-width: 960px; margin: 0 auto; padding: 18px; }
  h1 { font-size: 16px; font-weight:600; margin: 0 0 12px; }
  #bar { display:flex; align-items:center; gap:12px; flex-wrap:wrap; margin-bottom:10px; }
  button { font:inherit; padding:6px 12px; border:1px solid var(--nodeb); border-radius:8px;
           background:var(--card); color:var(--fg); cursor:pointer; }
  button:disabled { opacity:.4; cursor:default; }
  #counter { color:var(--muted); font-variant-numeric: tabular-nums; }
  #caption { font-weight:600; min-height:1.4em; margin-bottom:8px; }
  #stage { background:var(--card); border:1px solid var(--nodeb); border-radius:12px; padding:10px;
           transition: opacity .18s ease; overflow:auto; }
  svg text { fill: var(--fg); font: 13px system-ui, sans-serif; }
  .edge { stroke: var(--edge); stroke-width: 1.5; }
  .elabel { fill: var(--fg); font-weight:600; }
  .root { fill: var(--muted); font-weight:600; font-size:12px; }
  .val { fill: var(--fg); font-variant-numeric: tabular-nums; }
  .node { fill: var(--node); stroke: var(--nodeb); stroke-width: 1.5; }
  .node.key { fill: var(--key); stroke: var(--keyb); }
  .node.hl { stroke: var(--hl); stroke-width: 3.5; }
</style>
</head>
<body>
<div id="app">
  <h1>RadixTrie — web replay</h1>
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

const PAD = 28, ROW_H = 80, X_GAP = 76, R = 13;
let idx = 0, timer = null;

function esc(s) { return String(s).replace(/[&<>]/g, c => ({ "&":"&amp;","<":"&lt;",">":"&gt;" }[c])); }
function txt(s) { return s === null ? "∅" : String(s); }

// Two-pass tidy layout: each leaf takes the next x-slot, an internal node centers over its children;
// y is depth. Returns node -> {x, depth, path} plus tree extent. `path` is the root-to-node label
// concatenation, matched against event.highlightPath (mirrors AsciiTrieRenderer's highlight).
function layout(root) {
  const pos = new Map();
  let leafX = 0, maxDepth = 0;
  function place(node, depth, path) {
    maxDepth = Math.max(maxDepth, depth);
    const kids = node.children || [];
    let x;
    if (kids.length === 0) {
      x = leafX++;
    } else {
      const xs = kids.map(e => place(e.target, depth + 1, path + e.label));
      x = (xs[0] + xs[xs.length - 1]) / 2;
    }
    pos.set(node, { x, depth, path });
    return x;
  }
  place(root, 0, "");
  return { pos, maxDepth, leaves: Math.max(1, leafX) };
}

function renderFrame(f) {
  const trie = f.trie, ev = f.event;
  const { pos, maxDepth, leaves } = layout(trie.root);
  const sx = x => PAD + x * X_GAP;
  const sy = d => PAD + d * ROW_H;

  let body = "";
  // edges + edge labels first, so nodes draw on top of them
  (function edges(node) {
    const p = pos.get(node);
    for (const e of (node.children || [])) {
      const c = pos.get(e.target);
      body += `<line class="edge" x1="${sx(p.x)}" y1="${sy(p.depth)}" x2="${sx(c.x)}" y2="${sy(c.depth)}"/>`;
      const mx = (sx(p.x) + sx(c.x)) / 2, my = (sy(p.depth) + sy(c.depth)) / 2;
      body += `<text class="elabel" x="${mx}" y="${my - 3}" text-anchor="middle">${esc(e.label)}</text>`;
      edges(e.target);
    }
  })(trie.root);

  // nodes
  for (const [node, p] of pos) {
    const hl = ev.highlightPath != null && p.path === ev.highlightPath;
    const cls = "node" + (node.key ? " key" : "") + (hl ? " hl" : "");
    body += `<circle class="${cls}" cx="${sx(p.x)}" cy="${sy(p.depth)}" r="${R}"/>`;
    if (p.depth === 0) {
      body += `<text class="root" x="${sx(p.x)}" y="${sy(p.depth) - R - 6}" text-anchor="middle">(root)</text>`;
    }
    if (node.key) {
      body += `<text class="val" x="${sx(p.x) + R + 5}" y="${sy(p.depth) + 4}">=${esc(txt(node.value))}</text>`;
    }
  }

  const width = Math.max(320, PAD * 2 + leaves * X_GAP);
  const height = PAD * 2 + (maxDepth + 1) * ROW_H;
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
  caption.textContent = f ? f.event.label : "(no events to replay)";
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

Create `src/test/java/com/gimlism/translucent/trie/viz/TrieWebExporterTest.java`:

```java
package com.gimlism.translucent.trie.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrieWebExporterTest {

    @Test
    void toHtmlInjectsFramesAndBakesFlagsFalse() {
        String frames = "{\"frames\":[]}";
        String html = TrieWebExporter.toHtml(frames);

        // full self-contained document
        assertTrue(html.contains("<!doctype html>"), "expected a full document");
        assertTrue(html.contains("</html>"), "expected closing html tag");
        // the frames blob is injected verbatim
        assertTrue(html.contains("const DATA = " + frames + ";"), html);
        // fixed flags baked off for a static replay
        assertTrue(html.contains("const LIVE = false;"), html);
        assertTrue(html.contains("const CONTROLS = false;"), html);
        // no template token survives the injection
        assertFalse(html.contains("/*__FRAMES__*/"), "FRAMES token not substituted");
        assertFalse(html.contains("/*__LIVE__*/"), "LIVE token not substituted");
        assertFalse(html.contains("/*__CONTROLS__*/"), "CONTROLS token not substituted");
    }

    @Test
    void writeHtmlWritesTheSameDocumentToDisk(@TempDir Path dir) throws IOException {
        String frames = "{\"frames\":[]}";
        Path out = dir.resolve("trie.html");
        TrieWebExporter.writeHtml(frames, out);
        assertEquals(TrieWebExporter.toHtml(frames), Files.readString(out));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q -Dtest=TrieWebExporterTest test`
Expected: FAIL — compilation error, `TrieWebExporter` does not exist.

- [ ] **Step 4: Write minimal implementation**

Create `src/main/java/com/gimlism/translucent/trie/viz/TrieWebExporter.java`:

```java
package com.gimlism.translucent.trie.viz;

import com.gimlism.translucent.substrate.viz.WebVizTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assembles the self-contained RadixTrie HTML page from {@code /web/trie-viz.html} by delegating
 * token substitution to {@link WebVizTemplate}: a baked {@code FRAMES} JSON blob (from
 * {@link TrieJsonSerializer}) plus the {@code LIVE} and {@code CONTROLS} mode flags — both {@code
 * false} for a static replay. The template carries the whole vanilla-JS/SVG renderer; this class
 * only chooses the three replacement values. Third consumer of {@link WebVizTemplate}, alongside
 * the map and list exporters.
 */
public final class TrieWebExporter {

    private static final String TEMPLATE_RESOURCE = "/web/trie-viz.html";

    private TrieWebExporter() {}

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

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -Dtest=TrieWebExporterTest test`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/web/trie-viz.html \
        src/main/java/com/gimlism/translucent/trie/viz/TrieWebExporter.java \
        src/test/java/com/gimlism/translucent/trie/viz/TrieWebExporterTest.java
git commit -m "feat(trie-viz): trie-viz.html top-down SVG renderer + TrieWebExporter

Self-contained page (3 tokens, FRAMES+scrubber wired; LIVE/CONTROLS
baked false for Slices B/D). TrieWebExporter is WebVizTemplate's third
consumer. SVG JS untested-by-design, browser-verified in Task 3.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm"
```

---

## Task 3: TrieWebVizDemo + browser verification

**Files:**
- Create: `src/main/java/com/gimlism/translucent/trie/demo/TrieWebVizDemo.java`
- Test: `src/test/java/com/gimlism/translucent/trie/demo/TrieWebVizDemoTest.java`

**Interfaces:**
- Consumes: `RadixTrie<Integer>` (`addListener`, `put(String,V)`, `remove(Object)`); `TrieRecordingListener` (`events() → List<TrieEvent>`); `TrieJsonSerializer.toJson`; `TrieWebExporter.toHtml`.
- Produces: `TrieWebVizDemo.buildHtml() → String` (test seam, no filesystem); `main(String[])` writes `target/trie-web-viz.html`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/trie/demo/TrieWebVizDemoTest.java`:

```java
package com.gimlism.translucent.trie.demo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrieWebVizDemoTest {

    @Test
    void buildHtmlIsSelfContainedAndExercisesInsertThenRemove() {
        String html = TrieWebVizDemo.buildHtml();

        // self-contained baked document, no unsubstituted tokens
        assertTrue(html.contains("<!doctype html>"), "expected a full document");
        assertTrue(html.contains("</html>"), "expected closing html tag");
        assertTrue(html.contains("\"frames\":["), "expected an injected frames blob");
        assertFalse(html.contains("/*__"), "no template token should survive");

        // the insert story split/branched a shared prefix — an "sh" edge and both branch labels
        assertTrue(html.contains("\"label\":\"sh\""), "expected the shared \"sh\" prefix edge");
        assertTrue(html.contains("\"label\":\"ore\""), "expected the \"ore\" branch (shore)");

        // the remove story fired the compression cleanup events
        assertTrue(html.contains("\"type\":\"Put\""), "expected inserts");
        assertTrue(html.contains("\"type\":\"Remove\""), "expected removes");
        assertTrue(html.contains("\"type\":\"Prune\""), "expected a leaf prune from the removals");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=TrieWebVizDemoTest test`
Expected: FAIL — compilation error, `TrieWebVizDemo` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/gimlism/translucent/trie/demo/TrieWebVizDemo.java`:

```java
package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.viz.TrieJsonSerializer;
import com.gimlism.translucent.trie.viz.TrieWebExporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Records a small story that exercises the trie's structural events — inserting keys with a shared
 * prefix (edges split and branch), then removing keys (leaves prune and edges merge) — and writes it
 * out as a self-contained HTML web replay. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.trie.demo.TrieWebVizDemo}
 * (optionally {@code -Dexec.args="path/to/out.html"}). The whole-document build is factored into
 * {@link #buildHtml()} so it can be unit-tested without touching the filesystem.
 */
public class TrieWebVizDemo {

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "target/trie-web-viz.html");
        Path parent = out.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(out, buildHtml());
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    /** The self-contained HTML replay for the standard story (package-private test seam — no filesystem). */
    static String buildHtml() {
        var trie = new RadixTrie<Integer>();
        var rec = new TrieRecordingListener();
        trie.addListener(rec);

        int v = 1;
        // insert keys sharing the "sh" prefix — watch edges split and branch
        for (String key : new String[] {"shore", "she", "shell"}) {
            trie.put(key, v++);
        }
        // remove keys — watch leaves prune and edges merge
        for (String key : new String[] {"shell", "she"}) {
            trie.remove(key);
        }

        return TrieWebExporter.toHtml(TrieJsonSerializer.toJson(rec.events()));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=TrieWebVizDemoTest test`
Expected: PASS (1 test).

- [ ] **Step 5: Run the full suite**

Run: `mvn -q test`
Expected: PASS — the full suite (320 baseline + 8 new = 328) green.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/demo/TrieWebVizDemo.java \
        src/test/java/com/gimlism/translucent/trie/demo/TrieWebVizDemoTest.java
git commit -m "feat(trie-demo): TrieWebVizDemo — bake the shore/she/shell story

Inserts three shared-prefix keys (split/branch) then removes two
(prune/merge), writing a self-contained target/trie-web-viz.html.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm"
```

- [ ] **Step 7: Browser verification (controller-run — the SVG JS is untested-by-design)**

This step is run by the controller, not a subagent (subagents cannot drive a browser).

1. Bake the demo page:
   ```bash
   mvn -q process-classes exec:java -Dexec.mainClass=com.gimlism.translucent.trie.demo.TrieWebVizDemo
   ```
   Confirms `target/trie-web-viz.html` is written.
2. Serve it over http (the Chrome extension blocks `file://`):
   ```bash
   (cd target && python3 -m http.server 7070) &
   ```
3. Via the claude-in-chrome MCP, open `http://localhost:7070/trie-web-viz.html` and verify:
   - **First frame** — do NOT assume it is `CreateNode`; observe the actual first caption/event and confirm the tree renders sensibly (root plus the first edge). This is the "verify, don't assume" checkpoint from the spec.
   - Stepping through: the insert story shows the `sh` edge splitting and branching into `ore` / `e`, `e` growing an `ll` child; key nodes are ringed and show `=1/=2/=3`.
   - The remove story prunes leaves and merges edges back; the highlight ring tracks the affected node (and for a `Prune`, the surviving parent).
   - Prev/next/play and the arrow keys scrub; the counter reads `frame N / M`.
   - Console shows **zero errors**.
4. Tear down: `pkill -f http.server` (port 7070) and remove any scratch files.

---

## Self-Review

**1. Spec coverage.** Every spec section maps to a task:
- Architecture table / four files → Tasks 1–3 create exactly those four files.
- Frame JSON contract (`event.type/label/highlightPath`, recursive `trie`) → Task 1 `writeEvent`/`writeTrie`/`writeNode`; asserted in `TrieJsonSerializerTest`.
- `highlightPath` = `AsciiTrieRenderer.affectedPath`, Prune→parent → Task 1 Step 3 + the `pruneHighlightPathIsSurvivingParentNotDeletedNode` test.
- SVG top-down renderer (two-pass layout, node/edge/highlight) → Task 2 `trie-viz.html`; browser-verified in Task 3 Step 7.
- Minimal template scope (3 tokens, no SSE/command) → Task 2 template omits the `if (LIVE)` branch and command box; `TrieWebExporterTest` asserts flags baked `false`.
- Error handling (all-3-tokens via WebVizTemplate) → inherited; `TrieWebExporterTest` asserts no token survives.
- Testing set (serializer / exporter / demo + browser) → Tasks 1/2/3.
- "Verify, don't assume" first event → Task 3 Step 7 bullet 1 (browser observation, not a hard-coded JUnit assertion).

**2. Placeholder scan.** No TBD/TODO; every code step shows complete code; every test step shows real assertions. The only intentionally-open item (the first event's exact type) is handled by observation in the browser step, so no JUnit assertion hard-codes an unverified value.

**3. Type consistency.** `toJson(List<TrieEvent>)`, `toHtml(String)`/`writeHtml(String,Path)`, `buildHtml()`, `affectedPath(TrieEvent)`, `format(TrieEvent)`, and `JsonWriter` overloads are used identically across tasks and match the signatures confirmed against the codebase. JSON field names (`frames/event/type/label/highlightPath/trie/size/root/key/value/children/label/target`) match between the serializer (Task 1) and the renderer's reads (Task 2).
