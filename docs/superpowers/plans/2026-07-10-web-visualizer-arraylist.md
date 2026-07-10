# ArrayList Web Visualizer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give `TeachingArrayList` a self-contained HTML/SVG replay of a recorded `ListEvent` stream, exactly parallel to the HashMap web visualizer (PR #17).

**Architecture:** Four files, each shadowing a HashMap sibling: a `ListJsonSerializer` maps the recorded event stream to JSON frames (reusing the generic `JsonWriter` and the existing `ListEventFormatter`); a `list-viz.html` template is a dumb two-tier SVG renderer (logical list over backing array); a `ListWebExporter` injects the JSON into the template at a token; a `ListWebVizDemo` records a story and writes the file. This is Slice A of the ArrayList live-viz arc — the live/REPL/controls slices reuse this scaffolding later and are out of scope here.

**Tech Stack:** Java 21 (built on JDK 26, `maven.compiler.release=21`), Maven, JUnit 5 (`org.junit.jupiter`), vanilla JS/SVG (no front-end deps).

## Global Constraints

- Target Java 21 (`maven.compiler.release=21`); build/test on JDK 26. Source of truth for compilation is `mvn test-compile` — ignore stale Eclipse/LSP "cannot be resolved" diagnostics.
- No new dependencies. Reuse `com.gimlism.translucent.substrate.viz.JsonWriter` for all JSON syntax and `com.gimlism.translucent.arraylist.events.ListEventFormatter.format(...)` for every event caption — do not hand-roll label text.
- The template is a **dumb renderer**: all branching/serialization lives in Java; the JS only draws what the JSON says. The JS is **untested-by-design** and browser-verified (Task 4), never unit-tested.
- Each frame renders the event's own `after()` snapshot verbatim. `Shift` snapshots are deliberately mid-slide and `Grow` snapshots show the enlarged capacity before placement — the serializer must emit them as given, never "correct" them.
- Scope is Slice A (static replay) only: the template carries a single `/*__FRAMES__*/` token — **no** `LIVE`/`CONTROLS` tokens (those arrive with Slices B/D).
- Merge convention (after all tasks): PR on branch `feat/arraylist-web-visualizer`, `gh pr merge N --merge` (no squash, no `--delete-branch`).

## File Structure

| File | Status | Responsibility |
|---|---|---|
| `src/main/java/com/gimlism/translucent/arraylist/viz/ListJsonSerializer.java` | Create | `List<ListEvent>` → JSON frames; `toFrame` single-frame form for Slice B reuse |
| `src/test/java/com/gimlism/translucent/arraylist/viz/ListJsonSerializerTest.java` | Create | Per-event-type JSON shape, label reuse, slot/null/escaping encoding |
| `src/main/resources/web/list-viz.html` | Create | Two-tier SVG dumb renderer + replay scaffolding |
| `src/main/java/com/gimlism/translucent/arraylist/viz/ListWebExporter.java` | Create | Inject frames JSON into the template at `/*__FRAMES__*/` |
| `src/test/java/com/gimlism/translucent/arraylist/viz/ListWebExporterTest.java` | Create | Token replaced, self-contained output |
| `src/main/java/com/gimlism/translucent/arraylist/demo/ListWebVizDemo.java` | Create | Record the story, write `target/arraylist-web-viz.html` |
| `src/test/java/com/gimlism/translucent/arraylist/demo/ListWebVizDemoTest.java` | Create | `buildHtml()` smoke test |

---

### Task 1: ListJsonSerializer

**Files:**
- Create: `src/main/java/com/gimlism/translucent/arraylist/viz/ListJsonSerializer.java`
- Test: `src/test/java/com/gimlism/translucent/arraylist/viz/ListJsonSerializerTest.java`

**Interfaces:**
- Consumes (all existing): `ListEvent` (sealed: `Append`/`Insert`/`Set`/`RemoveAt`/`Shift`/`Grow`), each with `after() → ListSnapshot`; `ListSnapshot(int capacity, int size, List<SlotSnapshot> slots)`; `SlotSnapshot` (sealed: `FilledSlot(Object element)` / `EmptySlot`); `ListEventFormatter.format(ListEvent) → String`; `JsonWriter` (fluent: `beginObject/endObject/beginArray/endArray/name/value(String|long|boolean)/nullValue`, `value(String)` renders `null` as JSON null and escapes `<`, U+2028, U+2029).
- Event component accessors: `Append(Object element, int index, …)`, `Insert(Object element, int index, …)`, `Set(int index, Object previousElement, Object element, …)`, `RemoveAt(int index, Object removedElement, …)`, `Shift(int fromIndex, int toIndex, Object element, …)`, `Grow(int oldCapacity, int newCapacity, …)`.
- Produces (later tasks rely on these): `ListJsonSerializer.toJson(List<ListEvent> events) → String` (a `{"frames":[…]}` document) and `ListJsonSerializer.toFrame(ListEvent e) → String` (one frame, no wrapper).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/arraylist/viz/ListJsonSerializerTest.java`:

```java
package com.gimlism.translucent.arraylist.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.consumer.ListRecordingListener;
import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import org.junit.jupiter.api.Test;

class ListJsonSerializerTest {

    private static long count(String haystack, String needle) {
        long n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) n++;
        return n;
    }

    /** Records the standard story (append past cap → grow → insert → set → remove). */
    private static ListRecordingListener story() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        for (String s : new String[]{"a", "b", "c", "d", "e"}) list.add(s); // 5th append grows 4->6
        list.add(2, "x"); // insert with a shift burst
        list.set(0, "A");
        list.remove(1);   // remove with a shift burst
        return rec;
    }

    @Test
    void frameCountEqualsEventCountAndWrapped() {
        var rec = story();
        String json = ListJsonSerializer.toJson(rec.events());
        assertEquals(rec.events().size(), count(json, "\"event\":"));
        assertTrue(json.startsWith("{\"frames\":["), json);
    }

    @Test
    void appendCarriesTypeLabelAndIndex() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add("a");
        String json = ListJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"type\":\"Append\""), json);
        assertTrue(json.contains("\"label\":\"APPEND a @ 0\""), json); // label comes from ListEventFormatter
        assertTrue(json.contains("\"index\":0"), json);
    }

    @Test
    void insertSetRemoveCarryLabelsAndIndices() {
        String json = ListJsonSerializer.toJson(story().events());
        assertTrue(json.contains("\"type\":\"Insert\""), json);
        assertTrue(json.contains("\"label\":\"INSERT x @ 2\""), json); // index embedded in the label
        assertTrue(json.contains("\"type\":\"Set\""), json);
        assertTrue(json.contains("\"label\":\"SET 0 = A (was a)\""), json);
        assertTrue(json.contains("\"type\":\"RemoveAt\""), json);
        assertTrue(json.contains("\"label\":\"REMOVE @ 1 (was b)\""), json);
    }

    @Test
    void growCarriesOldAndNewCapacity() {
        String json = ListJsonSerializer.toJson(story().events());
        assertTrue(json.contains("\"type\":\"Grow\""), json);
        assertTrue(json.contains("\"oldCapacity\":4"), json);
        assertTrue(json.contains("\"newCapacity\":6"), json);
    }

    @Test
    void shiftCarriesFromAndToIndex() {
        String json = ListJsonSerializer.toJson(story().events());
        assertTrue(json.contains("\"type\":\"Shift\""), json);
        assertTrue(json.contains("\"fromIndex\":"), json);
        assertTrue(json.contains("\"toIndex\":"), json);
    }

    @Test
    void slotsEncodeFilledAndEmpty() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add("a"); // size 1, capacity 4 -> one filled slot, three empty
        String json = ListJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"filled\":true"), json);
        assertTrue(json.contains("\"filled\":false"), json);
        assertTrue(json.contains("\"element\":\"a\""), json);
    }

    @Test
    void nullElementEncodesAsJsonNull() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add(null);
        String json = ListJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"element\":null"), json);
    }

    @Test
    void inlineScriptUnsafeCharsEscaped() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add("</script>");
        String json = ListJsonSerializer.toJson(rec.events());
        assertFalse(json.contains("</script>"), json);   // '<' escaped by JsonWriter
        assertTrue(json.contains("\\u003c"), json);
    }

    @Test
    void toFrameHasNoWrapper() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add("a");
        String frame = ListJsonSerializer.toFrame(rec.events().get(0));
        assertFalse(frame.startsWith("{\"frames\""), frame);
        assertTrue(frame.contains("\"type\":\"Append\""), frame);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ListJsonSerializerTest test`
Expected: FAIL — compilation error, `ListJsonSerializer` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/gimlism/translucent/arraylist/viz/ListJsonSerializer.java`:

```java
package com.gimlism.translucent.arraylist.viz;

import com.gimlism.translucent.arraylist.events.Append;
import com.gimlism.translucent.arraylist.events.EmptySlot;
import com.gimlism.translucent.arraylist.events.FilledSlot;
import com.gimlism.translucent.arraylist.events.Grow;
import com.gimlism.translucent.arraylist.events.Insert;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListEventFormatter;
import com.gimlism.translucent.arraylist.events.ListSnapshot;
import com.gimlism.translucent.arraylist.events.RemoveAt;
import com.gimlism.translucent.arraylist.events.Set;
import com.gimlism.translucent.arraylist.events.Shift;
import com.gimlism.translucent.arraylist.events.SlotSnapshot;
import com.gimlism.translucent.substrate.viz.JsonWriter;
import java.util.List;

/**
 * The per-structure half of the ArrayList web-viz seam: maps a recorded {@code ListEvent} stream to
 * the JSON the browser replay consumes. The web analogue of {@code AsciiListRenderer} — it holds the
 * event-shape branching (index/from-to/capacity fields, slot classification) so the front-end stays a
 * dumb renderer. Each frame carries the event's own {@code after()} snapshot, so the JSON shows exactly
 * the state the core emitted (including the deliberate mid-slide {@code Shift} and pre-placement
 * {@code Grow} snapshots). Captions come from {@link ListEventFormatter}, the one source of wording.
 */
public final class ListJsonSerializer {

    private ListJsonSerializer() {}

    public static String toJson(List<ListEvent> events) {
        JsonWriter w = new JsonWriter();
        w.beginObject().name("frames").beginArray();
        for (ListEvent e : events) writeFrame(w, e);
        w.endArray().endObject();
        return w.toString();
    }

    /** One event → the JSON for a single {@code { "event":…, "list":… }} frame (no {@code frames} wrapper). */
    public static String toFrame(ListEvent e) {
        JsonWriter w = new JsonWriter();
        writeFrame(w, e);
        return w.toString();
    }

    private static void writeFrame(JsonWriter w, ListEvent e) {
        w.beginObject();
        w.name("event");
        writeEvent(w, e);
        w.name("list");
        writeList(w, e.after());
        w.endObject();
    }

    private static void writeEvent(JsonWriter w, ListEvent e) {
        w.beginObject();
        w.name("type").value(e.getClass().getSimpleName());
        w.name("label").value(ListEventFormatter.format(e));
        switch (e) {
            case Append a -> w.name("index").value((long) a.index());
            case Insert in -> w.name("index").value((long) in.index());
            case Set s -> w.name("index").value((long) s.index());
            case RemoveAt r -> w.name("index").value((long) r.index());
            case Shift sh -> {
                w.name("index").value((long) sh.toIndex());
                w.name("fromIndex").value((long) sh.fromIndex());
                w.name("toIndex").value((long) sh.toIndex());
            }
            case Grow g -> {
                w.name("oldCapacity").value((long) g.oldCapacity());
                w.name("newCapacity").value((long) g.newCapacity());
            }
        }
        w.endObject();
    }

    private static void writeList(JsonWriter w, ListSnapshot s) {
        w.beginObject();
        w.name("capacity").value((long) s.capacity());
        w.name("size").value((long) s.size());
        w.name("slots").beginArray();
        for (SlotSnapshot slot : s.slots()) writeSlot(w, slot);
        w.endArray();
        w.endObject();
    }

    private static void writeSlot(JsonWriter w, SlotSnapshot slot) {
        w.beginObject();
        switch (slot) {
            case FilledSlot f -> {
                w.name("filled").value(true);
                w.name("element").value(f.element() == null ? null : String.valueOf(f.element()));
            }
            case EmptySlot e -> w.name("filled").value(false);
        }
        w.endObject();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ListJsonSerializerTest test`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/arraylist/viz/ListJsonSerializer.java \
        src/test/java/com/gimlism/translucent/arraylist/viz/ListJsonSerializerTest.java
git commit -m "feat(viz): ListJsonSerializer — ArrayList event stream to JSON frames"
```

---

### Task 2: list-viz.html template (two-tier renderer)

**Files:**
- Create: `src/main/resources/web/list-viz.html`

**Interfaces:**
- Consumes: the JSON frame contract produced by Task 1 — `{ "event": { "type", "label", "index"?, "fromIndex"?, "toIndex"?, "oldCapacity"?, "newCapacity"? }, "list": { "capacity", "size", "slots": [ {"filled":true,"element":"…"} | {"filled":false} ] } }`, wrapped `{"frames":[…]}`.
- Produces: a self-contained page with exactly one `/*__FRAMES__*/` token (replaced by Task 3). No automated unit test — untested-by-design; a smoke gate confirms the token, and Task 4 browser-verifies the rendering.

- [ ] **Step 1: Create the template file**

Create `src/main/resources/web/list-viz.html`:

```html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>TeachingArrayList — replay</title>
<style>
  :root { color-scheme: light dark; --bg:#f7f7f8; --fg:#1b1b1d; --muted:#777; --card:#fff;
          --cell:#eceef2; --cellb:#c8ccd4; --empty:#f2f3f5; --emptyb:#d9dce2; --hl:#f5a623;
          --conn:#8a8a8a; }
  @media (prefers-color-scheme: dark) {
    :root { --bg:#16171a; --fg:#e8e8ea; --muted:#999; --card:#1f2126;
            --cell:#2a2d33; --cellb:#41454d; --empty:#202226; --emptyb:#31343a; --hl:#f5a623;
            --conn:#8a8a8a; }
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
  .tier { fill: var(--muted); font-weight:600; font-size:12px; }
  .idx { fill: var(--muted); font-weight:600; }
  .idx.hl { fill: var(--hl); font-weight:700; }
  .cell rect { fill: var(--cell); stroke: var(--cellb); }
  .cell.empty rect { fill: var(--empty); stroke: var(--emptyb); stroke-dasharray:4 3; }
  .cell.hl rect { stroke: var(--hl); stroke-width: 3; }
  .cell text { fill: var(--fg); }
  .conn { stroke: var(--conn); stroke-width: 1.5; }
</style>
</head>
<body>
<div id="app">
  <h1>TeachingArrayList — web replay</h1>
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
const frames = (DATA && DATA.frames) || [];
const svg = document.getElementById("svg");
const stage = document.getElementById("stage");
const caption = document.getElementById("caption");
const counter = document.getElementById("counter");
const prevBtn = document.getElementById("prev");
const nextBtn = document.getElementById("next");
const playBtn = document.getElementById("play");

const PAD = 16, CELL_W = 60, CELL_H = 34, CELL_GAP = 4, CONNECTOR_H = 40, LABEL_H = 22;
let idx = 0, timer = null;

function esc(s) { return String(s).replace(/[&<>]/g, c => ({ "&":"&amp;","<":"&lt;",">":"&gt;" }[c])); }
function txt(s) { return s === null ? "∅" : String(s); }
const colX = i => PAD + i * (CELL_W + CELL_GAP);

// Elements of the nearest prior non-Shift frame. The logical row freezes to this during a
// Shift burst, so the top tier holds still while the backing array copies element-by-element.
function settledLogical(i) {
  for (let j = i; j >= 0; j--) {
    if (frames[j].event.type !== "Shift") {
      const L = frames[j].list;
      return L.slots.slice(0, L.size).map(s => s.filled ? s.element : null);
    }
  }
  const L = frames[i].list;
  return L.slots.slice(0, L.size).map(s => s.filled ? s.element : null);
}

function cell(x, y, text, filled, hl) {
  const cls = "cell" + (filled ? "" : " empty") + (hl ? " hl" : "");
  const label = filled
    ? `<text x="${x + CELL_W / 2}" y="${y + CELL_H / 2 + 4}" text-anchor="middle">${esc(txt(text))}</text>`
    : "";
  return `<g class="${cls}"><rect x="${x}" y="${y}" width="${CELL_W}" height="${CELL_H}" rx="6"/>${label}</g>`;
}

function renderFrame(f) {
  const L = f.list, ev = f.event;
  const cap = L.capacity, size = L.size, slots = L.slots;
  const isShift = ev.type === "Shift";
  const topLogical = isShift
    ? settledLogical(idx)
    : slots.slice(0, size).map(s => s.filled ? s.element : null);

  const topY = PAD + 14, bottomY = topY + CELL_H + CONNECTOR_H;

  const hlBottom = new Set();
  if (isShift) {
    if (ev.fromIndex != null) hlBottom.add(ev.fromIndex);
    if (ev.toIndex != null) hlBottom.add(ev.toIndex);
  } else if (ev.type === "Grow") {
    for (let i = ev.oldCapacity; i < ev.newCapacity; i++) hlBottom.add(i);
  } else if (ev.index != null) {
    hlBottom.add(ev.index);
  }

  let body = "";
  body += `<text class="tier" x="${PAD}" y="${topY - 6}">logical list · size ${size}</text>`;
  body += `<text class="tier" x="${PAD}" y="${bottomY - 6}">backing array · capacity ${cap}</text>`;

  // top logical tier
  topLogical.forEach((el, j) => {
    const hl = !isShift && ev.index === j;
    body += cell(colX(j), topY, el, true, hl);
  });

  // connectors — only on settled frames; during a shift the two tiers decouple
  if (!isShift) {
    topLogical.forEach((_, j) => {
      const x = colX(j) + CELL_W / 2;
      body += `<line class="conn" x1="${x}" y1="${topY + CELL_H}" x2="${x}" y2="${bottomY}"/>`;
    });
  }

  // bottom backing tier + index labels
  for (let i = 0; i < cap; i++) {
    const s = slots[i];
    body += cell(colX(i), bottomY, s.filled ? s.element : null, s.filled, hlBottom.has(i));
    body += `<text class="idx${hlBottom.has(i) ? " hl" : ""}" x="${colX(i) + CELL_W / 2}" `
          + `y="${bottomY + CELL_H + 16}" text-anchor="middle">${i}</text>`;
  }

  const width = Math.max(320, PAD * 2 + cap * (CELL_W + CELL_GAP) - CELL_GAP);
  const height = bottomY + CELL_H + LABEL_H + PAD;
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

- [ ] **Step 2: Smoke-gate the resource (token present, copies to classpath)**

Run: `mvn -q process-classes && grep -c '/\*__FRAMES__\*/' target/classes/web/list-viz.html`
Expected: prints `1` (the resource copied to the classpath and carries exactly the frames token; no live/controls tokens).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/web/list-viz.html
git commit -m "feat(viz): list-viz.html — two-tier ArrayList SVG replay template"
```

---

### Task 3: ListWebExporter

**Files:**
- Create: `src/main/java/com/gimlism/translucent/arraylist/viz/ListWebExporter.java`
- Test: `src/test/java/com/gimlism/translucent/arraylist/viz/ListWebExporterTest.java`

**Interfaces:**
- Consumes: `/web/list-viz.html` on the classpath (Task 2) containing `/*__FRAMES__*/`; a frames-JSON string (from Task 1, though the exporter takes any string).
- Produces (later tasks rely on these): `ListWebExporter.toHtml(String framesJson) → String` (self-contained HTML with the token replaced) and `ListWebExporter.writeHtml(String framesJson, Path out)`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/arraylist/viz/ListWebExporterTest.java`:

```java
package com.gimlism.translucent.arraylist.viz;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ListWebExporterTest {

    @Test
    void injectsFramesAndRemovesToken() {
        String marker = "{\"frames\":[{\"probe\":42}]}";
        String html = ListWebExporter.toHtml(marker);
        assertTrue(html.contains(marker), "injected JSON should appear verbatim");
        assertFalse(html.contains("/*__FRAMES__*/"), "token should be gone after injection");
    }

    @Test
    void outputIsSelfContainedHtml() {
        String html = ListWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.startsWith("<!doctype html>"), html.substring(0, Math.min(40, html.length())));
        assertTrue(html.contains("<svg"), "carries the inline SVG stage");
        assertFalse(html.contains("http://"), "no external asset references");
        assertFalse(html.contains("https://"), "no external asset references");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ListWebExporterTest test`
Expected: FAIL — compilation error, `ListWebExporter` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/gimlism/translucent/arraylist/viz/ListWebExporter.java`:

```java
package com.gimlism.translucent.arraylist.viz;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assembles the self-contained HTML replay by injecting a serialized {@code frames} JSON blob (from
 * {@link ListJsonSerializer}) into the {@code /web/list-viz.html} template at its
 * {@code /*__FRAMES__*}{@code /} token. The template carries the whole vanilla-JS/SVG renderer; this
 * class only substitutes the data, so the output is one shareable file with no external assets. Slice A
 * has a single token; the live/controls tokens arrive with later slices.
 */
public final class ListWebExporter {

    private static final String TEMPLATE_RESOURCE = "/web/list-viz.html";
    private static final String FRAMES_TOKEN = "/*__FRAMES__*/";

    private ListWebExporter() {}

    /** The self-contained baked HTML with {@code framesJson} injected. */
    public static String toHtml(String framesJson) {
        String template = readTemplate();
        requireToken(template, FRAMES_TOKEN);
        return template.replace(FRAMES_TOKEN, framesJson);
    }

    /** Write {@link #toHtml(String)} to {@code out} (UTF-8). */
    public static void writeHtml(String framesJson, Path out) throws IOException {
        Files.writeString(out, toHtml(framesJson));
    }

    private static void requireToken(String template, String token) {
        if (!template.contains(token)) {
            throw new IllegalStateException("template " + TEMPLATE_RESOURCE + " is missing token " + token);
        }
    }

    private static String readTemplate() {
        try (InputStream in = ListWebExporter.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("template resource not found on classpath: " + TEMPLATE_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ListWebExporterTest test`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/arraylist/viz/ListWebExporter.java \
        src/test/java/com/gimlism/translucent/arraylist/viz/ListWebExporterTest.java
git commit -m "feat(viz): ListWebExporter — inject frames JSON into list-viz.html"
```

---

### Task 4: ListWebVizDemo + browser verification

**Files:**
- Create: `src/main/java/com/gimlism/translucent/arraylist/demo/ListWebVizDemo.java`
- Test: `src/test/java/com/gimlism/translucent/arraylist/demo/ListWebVizDemoTest.java`

**Interfaces:**
- Consumes: `TeachingArrayList<E>(int initialCapacity)` with `add(E)`, `add(int,E)`, `set(int,E)`, `remove(int)`; `ListRecordingListener` (`.events() → List<ListEvent>`, inherited from `RecordingListener<ListEvent>`); `ListJsonSerializer.toJson`; `ListWebExporter.toHtml`.
- Produces: `ListWebVizDemo.buildHtml() → String` (package-private test seam) and a `main` that writes `target/arraylist-web-viz.html`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/arraylist/demo/ListWebVizDemoTest.java`:

```java
package com.gimlism.translucent.arraylist.demo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ListWebVizDemoTest {

    @Test
    void buildHtmlEmbedsTheWholeStory() {
        String html = ListWebVizDemo.buildHtml();
        assertTrue(html.contains("<!doctype html>"), "self-contained page");
        assertTrue(html.contains("\"frames\":["), "frames injected");
        assertFalse(html.contains("/*__FRAMES__*/"), "token replaced");
        // the story exercises every event type at least once
        assertTrue(html.contains("\"type\":\"Grow\""), html);
        assertTrue(html.contains("\"type\":\"Shift\""), html);
        assertTrue(html.contains("\"type\":\"Insert\""), html);
        assertTrue(html.contains("\"type\":\"Set\""), html);
        assertTrue(html.contains("\"type\":\"RemoveAt\""), html);
        assertTrue(html.contains("\"element\":\"x\""), "inserted element present");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ListWebVizDemoTest test`
Expected: FAIL — compilation error, `ListWebVizDemo` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/gimlism/translucent/arraylist/demo/ListWebVizDemo.java`:

```java
package com.gimlism.translucent.arraylist.demo;

import com.gimlism.translucent.arraylist.consumer.ListRecordingListener;
import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.viz.ListJsonSerializer;
import com.gimlism.translucent.arraylist.viz.ListWebExporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Records a small story that exercises every {@code ListEvent} — append past the initial capacity
 * (forcing a grow), an insert-in-the-middle (a shift burst), a set, and a remove (another shift) —
 * and writes it out as a self-contained HTML web replay. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListWebVizDemo}
 * (optionally {@code -Dexec.args="path/to/out.html"}). The whole-document build is factored into
 * {@link #buildHtml()} so it can be unit-tested without touching the filesystem.
 */
public class ListWebVizDemo {

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "target/arraylist-web-viz.html");
        Path parent = out.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(out, buildHtml());
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    /** The self-contained HTML replay for the standard story (package-private test seam — no filesystem). */
    static String buildHtml() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);

        // fill the initial capacity, then one more append to force a grow (4 -> 6)
        for (String s : new String[]{"a", "b", "c", "d", "e"}) {
            list.add(s);
        }
        list.add(2, "x"); // insert in the middle: shift the tail right, then place
        list.set(0, "A");  // in-place set, no structural change
        list.remove(1);    // remove in the middle: shift survivors left

        return ListWebExporter.toHtml(ListJsonSerializer.toJson(rec.events()));
    }
}
```

- [ ] **Step 4: Run the whole suite to verify it passes**

Run: `mvn -q test`
Expected: PASS — all prior tests plus `ListWebVizDemoTest`; full suite green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/arraylist/demo/ListWebVizDemo.java \
        src/test/java/com/gimlism/translucent/arraylist/demo/ListWebVizDemoTest.java
git commit -m "feat(demo): ListWebVizDemo — self-contained ArrayList web replay"
```

- [ ] **Step 6: Browser-verify the untested renderer (controller task)**

The JS renderer is untested-by-design; verify it in a real browser using the project's live-viz verification recipe:

```bash
mvn -q exec:java -Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListWebVizDemo
# writes target/arraylist-web-viz.html
```

Open `target/arraylist-web-viz.html` (a `file://` URL is fine — it is self-contained) and confirm:
- Two tiers render: a "logical list" row above a "backing array" row, joined by vertical connectors, with index labels beneath the backing row and a grey dashed tail for unused capacity.
- Stepping through frames (next/prev, arrow keys, play): the `Grow` frame extends the backing array (cap 4 → 6, two new grey cells highlighted); `Insert`/`RemoveAt` shift bursts animate on the **bottom** row while the **top** logical row holds still and the connectors drop away during the burst; `Set` recolors one cell in place.
- The frame counter tracks position; cross-fade is smooth; the browser console shows **zero errors**.

Kill the process if it lingers (`pkill -f ListWebVizDemo`) and remove any scratch files. Record the verification result in the task ledger.

---

## Self-Review

**Spec coverage:**
- Two-tier renderer (logical over backing, grey capacity tail, connectors) → Task 2. ✓
- JSON frame contract (type/label/index/from-to/old-new-capacity; slots filled/empty; null→JSON null) → Task 1 + tests. ✓
- Label reuse via `ListEventFormatter` → Task 1 (`writeEvent`), asserted in `appendCarriesTypeLabelAndIndex`. ✓
- Inline-script escaping (`<`, U+2028/U+2029) → handled by `JsonWriter`, asserted in `inlineScriptUnsafeCharsEscaped`. ✓
- `toFrame` single-frame form for Slice B reuse → Task 1, asserted in `toFrameHasNoWrapper`. ✓
- Freeze-top-animate-bottom during Shift → Task 2 (`settledLogical` + connector suppression). ✓ (browser-verified, Step 6.)
- Demo starts at capacity 4, exercises every event type, testable `buildHtml()` seam → Task 4 + test. ✓
- Grow highlight uses the event's own `oldCapacity`/`newCapacity` (self-contained frame) → Task 1 emits them; Task 2 reads them. ✓
- Scope limited to the single `FRAMES` token (no LIVE/CONTROLS) → Task 2 template + Task 3 exporter + smoke gate. ✓

**Placeholder scan:** none — every step carries complete code and an exact command with expected output.

**Type consistency:** `ListJsonSerializer.toJson/toFrame`, `ListWebExporter.toHtml/writeHtml`, `ListWebVizDemo.buildHtml`, and `ListRecordingListener.events()` are referenced identically across tasks. Event-component accessors (`index()`, `fromIndex()`/`toIndex()`, `oldCapacity()`/`newCapacity()`, `element()`) match the record definitions. JSON field names (`type`/`label`/`index`/`fromIndex`/`toIndex`/`oldCapacity`/`newCapacity`/`capacity`/`size`/`slots`/`filled`/`element`) are identical in the serializer (writer) and template (reader).
