# Compression-compare Web Side-by-Side Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a single static, self-contained HTML page that renders the fat `StandardTrie` beside the compressed `RadixTrie` as SVG, under the savings banner, with radix-absorbed nodes drawn ghosted + dashed — the browser analogue of slice-1's ASCII `CompressionCompareRenderer`.

**Architecture:** A pure `Comparison → JSON` serializer bakes both trees plus a per-node `absorbed` flag (computed by a new shared `TrieMetrics.isAbsorbed` predicate) into one data blob; a new additive `WebVizTemplate.injectStatic` substitutes that blob into a new `compression-compare-viz.html` whose inline JS is a dumb two-panel SVG renderer; a thin exporter wires them together. The one place existing code changes is `CompressionCompareRenderer`, which is refactored to delegate to the shared predicate and a new `Comparison.savedPct()` — kept byte-identical by its existing goldens.

**Tech Stack:** Java 21, Maven, JUnit 5. Vanilla-JS/SVG front-end (no framework). Existing `substrate/viz` `JsonWriter` + `WebVizTemplate`.

## Global Constraints

- Java 21, Maven. Run the full suite with `mvn -q test`.
- **Additive-only outside `trie/compare`.** These stay byte-unchanged: `AsciiTrieRenderer`, `trie/core`, `trie/events`, every sibling `-viz.html`, and the existing `WebVizTemplate.inject` (only a *new* method is added beside it).
- `JsonWriter.value` has overloads for `String` (nullable), `long`, `boolean` only — **cast every `int` count to `(long)`**.
- `TrieNodeSnapshot(boolean key, Object value, List<TrieEdge> children)`; `TrieEdge(String label, TrieNodeSnapshot target)`.
- Canonical key set `{she, shell, shore, shy}` → standard 10 / radix 6 / saved 4 (40%), 4 absorbed nodes.
- Reuse the sibling palette CSS vars (`--node`, `--nodeb`, `--key`, `--keyb`, `--edge`, `--muted`) and their `prefers-color-scheme: dark` overrides — verbatim from `trie-viz.html`.

---

### Task 1: Shared `isAbsorbed` predicate + `savedPct`, and refactor the ASCII renderer onto them

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/trie/compare/TrieMetrics.java`
- Modify: `src/main/java/com/gimlism/translucent/trie/compare/CompressionCompareDemo.java` (the `Comparison` record)
- Modify: `src/main/java/com/gimlism/translucent/trie/compare/CompressionCompareRenderer.java:42-49` (banner) and `:74-77` (collectAbsorbed)
- Test: `src/test/java/com/gimlism/translucent/trie/compare/TrieMetricsTest.java` (extend)
- Test (regression, unchanged): `src/test/java/com/gimlism/translucent/trie/compare/CompressionCompareRendererTest.java`

**Interfaces:**
- Produces: `TrieMetrics.isAbsorbed(TrieNodeSnapshot node, boolean root) -> boolean` — the single definition of "radix absorbs this node."
- Produces: `CompressionCompareDemo.Comparison.savedPct() -> long` — rounded percent of standard nodes saved.
- Consumes: existing `Comparison.saved()`, `Comparison.standardNodes()`.

- [ ] **Step 1: Write the failing test** — add to `TrieMetricsTest.java`:

```java
    @Test
    void isAbsorbedOnlyForNonRootNonKeySingleChild() {
        var leaf = new TrieNodeSnapshot(false, null, List.of());
        var singleChild = new TrieNodeSnapshot(false, null, List.of(new TrieEdge("a", leaf)));
        var twoChild = new TrieNodeSnapshot(false, null,
            List.of(new TrieEdge("a", leaf), new TrieEdge("b", leaf)));
        var keySingleChild = new TrieNodeSnapshot(true, 1, List.of(new TrieEdge("a", leaf)));

        assertFalse(TrieMetrics.isAbsorbed(singleChild, true), "root is never absorbed");
        assertTrue(TrieMetrics.isAbsorbed(singleChild, false), "non-root single-child non-key is absorbed");
        assertFalse(TrieMetrics.isAbsorbed(twoChild, false), "a 2-child branch is kept");
        assertFalse(TrieMetrics.isAbsorbed(keySingleChild, false), "a key node is kept");
        assertFalse(TrieMetrics.isAbsorbed(leaf, false), "a leaf (no children) is kept");
    }

    @Test
    void savedPctRoundsShareOfStandardNodes() {
        assertEquals(40L, CompressionCompareDemo.compare(List.of("she","shell","shore","shy")).savedPct());
        assertEquals(0L, CompressionCompareDemo.compare(List.of()).savedPct());
    }
```

Ensure these imports exist in `TrieMetricsTest.java` (add any missing):
```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import java.util.List;
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=TrieMetricsTest`
Expected: FAIL — `cannot find symbol: method isAbsorbed` / `method savedPct`.

- [ ] **Step 3: Add `isAbsorbed` to `TrieMetrics.java`** (after the existing `nodeCount`/`count` members, keeping the `import`s — `TrieNodeSnapshot` is already imported):

```java
    /**
     * Whether the radix trie absorbs this node: a non-root, non-key node with exactly one child
     * (part of a single-child chain radix compresses into an edge label). The single shared
     * definition used by both {@code CompressionCompareRenderer} (ASCII) and
     * {@code CompressionCompareJsonSerializer} (web) so they can never disagree on what is marked.
     */
    public static boolean isAbsorbed(TrieNodeSnapshot node, boolean root) {
        return !root && !node.key() && node.children().size() == 1;
    }
```

- [ ] **Step 4: Add `savedPct()` to the `Comparison` record** in `CompressionCompareDemo.java` (beside `saved()`):

```java
        /** Rounded percentage of standard nodes the radix compression saves. */
        public long savedPct() {
            return Math.round(100.0 * saved() / standardNodes);
        }
```

- [ ] **Step 5: Refactor `CompressionCompareRenderer` onto both** (behavior-preserving). Replace the body of `collectAbsorbed` (`:74-77`) so the predicate is delegated:

```java
    private void collectAbsorbed(TrieNodeSnapshot node, boolean root, List<Boolean> flags) {
        flags.add(TrieMetrics.isAbsorbed(node, root));
        for (TrieEdge e : node.children()) collectAbsorbed(e.target(), false, flags);
    }
```

And replace `banner` (`:42-49`) so the percent comes from `savedPct()` (drop the local `pct` var):

```java
    private String banner(CompressionCompareDemo.Comparison c, int absorbedCount) {
        return "compression compare: {" + String.join(", ", c.keys()) + "}\n"
            + "  standard = " + c.standardNodes()
            + "   radix = " + c.radixNodes()
            + "   saved = " + c.saved() + " (" + c.savedPct() + "%)\n"
            + "  · = collapsed by radix (" + absorbedCount + " nodes)";
    }
```

- [ ] **Step 6: Run the new tests and the ASCII regression goldens**

Run: `mvn -q test -Dtest=TrieMetricsTest,CompressionCompareRendererTest,CompressionCompareDemoTest`
Expected: PASS. The unchanged `CompressionCompareRendererTest` goldens (`bannerLeadsWithKeysCountsAndSavings`, `columnsInterleaveBothTreesWithSavings`, `markedCountEqualsSaved`, …) prove the ASCII output is byte-identical after the refactor.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/compare/TrieMetrics.java \
        src/main/java/com/gimlism/translucent/trie/compare/CompressionCompareDemo.java \
        src/main/java/com/gimlism/translucent/trie/compare/CompressionCompareRenderer.java \
        src/test/java/com/gimlism/translucent/trie/compare/TrieMetricsTest.java
git commit -m "refactor(compare): hoist isAbsorbed + savedPct as shared single sources

TrieMetrics.isAbsorbed(node, root) and Comparison.savedPct() become the one
definition of what radix absorbs and the savings percent; the ASCII renderer
delegates to both (goldens prove byte-identical output). Prepares the web
serializer to bake the same absorbed flag per node.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: `CompressionCompareJsonSerializer` — pure `Comparison → JSON`

**Files:**
- Create: `src/main/java/com/gimlism/translucent/trie/compare/CompressionCompareJsonSerializer.java`
- Test: `src/test/java/com/gimlism/translucent/trie/compare/CompressionCompareJsonSerializerTest.java`

**Interfaces:**
- Consumes: `TrieMetrics.isAbsorbed`, `Comparison.savedPct()` (Task 1); `substrate/viz/JsonWriter`.
- Produces: `CompressionCompareJsonSerializer.toJson(CompressionCompareDemo.Comparison c) -> String` emitting `{ keys, standard:{nodes,root}, radix:{nodes,root}, saved, pct }` where each node is `{ key, value, absorbed, children:[{label,target}] }`.

- [ ] **Step 1: Write the failing test** — `CompressionCompareJsonSerializerTest.java`:

```java
package com.gimlism.translucent.trie.compare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CompressionCompareJsonSerializerTest {
    private static final List<String> CANON = List.of("she", "shell", "shore", "shy");

    private static long count(String s, String literal) {
        return Pattern.compile(Pattern.quote(literal)).matcher(s).results().count();
    }

    /** Split the blob at the radix tree so absorbed flags can be attributed to a specific panel. */
    private static String standardHalf(String json) { return json.substring(0, json.indexOf("\"radix\"")); }
    private static String radixHalf(String json) { return json.substring(json.indexOf("\"radix\"")); }

    @Test
    void emitsKeysCountsAndSavings() {
        String json = CompressionCompareJsonSerializer.toJson(CompressionCompareDemo.compare(CANON));
        assertTrue(json.contains("\"keys\":[\"she\",\"shell\",\"shore\",\"shy\"]"), json);
        assertTrue(json.contains("\"nodes\":10"), json);
        assertTrue(json.contains("\"nodes\":6"), json);
        assertTrue(json.contains("\"saved\":4"), json);
        assertTrue(json.contains("\"pct\":40"), json);
    }

    @Test
    void absorbedTrueCountEqualsSavedAndOnlyInStandardPanel() {
        var c = CompressionCompareDemo.compare(CANON);
        String json = CompressionCompareJsonSerializer.toJson(c);
        assertEquals(c.saved(), count(standardHalf(json), "\"absorbed\":true"),
            "standard panel must mark exactly saved() nodes:\n" + json);
        assertEquals(0, count(radixHalf(json), "\"absorbed\":true"),
            "radix panel must never be marked:\n" + json);
    }

    @Test
    void markedEqualsSavedForASecondShape() {
        // "abc" -> standard root,a,b,c (4) vs radix root,"abc" (2); "a","b" are absorbed -> 2 == saved 2.
        var c = CompressionCompareDemo.compare(List.of("abc"));
        String json = CompressionCompareJsonSerializer.toJson(c);
        assertEquals(2, c.saved(), "sanity: {abc} saves 2");
        assertEquals(c.saved(), count(standardHalf(json), "\"absorbed\":true"), json);
    }

    @Test
    void emptyKeySetIsTwoRootOnlyTreesWithNoMarks() {
        String json = CompressionCompareJsonSerializer.toJson(CompressionCompareDemo.compare(List.of()));
        assertTrue(json.contains("\"keys\":[]"), json);
        assertTrue(json.contains("\"saved\":0"), json);
        assertTrue(json.contains("\"pct\":0"), json);
        assertEquals(0, count(json, "\"absorbed\":true"), json);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=CompressionCompareJsonSerializerTest`
Expected: FAIL — `cannot find symbol: CompressionCompareJsonSerializer`.

- [ ] **Step 3: Write the serializer** — `CompressionCompareJsonSerializer.java`:

```java
package com.gimlism.translucent.trie.compare;

import com.gimlism.translucent.substrate.viz.JsonWriter;
import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;

/**
 * Pure {@code Comparison -> JSON} for the static web side-by-side page: both trees plus the savings
 * metrics, each node carrying a baked {@code absorbed} flag (from {@link TrieMetrics#isAbsorbed}) so
 * the browser stays a dumb renderer — the same "bake the decision in Java" choice
 * {@code TrieJsonSerializer} makes for {@code highlightPath}. {@code absorbed} is only ever true in
 * the standard tree; the radix trie keeps no single-child non-key node.
 */
public final class CompressionCompareJsonSerializer {

    private CompressionCompareJsonSerializer() {}

    public static String toJson(CompressionCompareDemo.Comparison c) {
        JsonWriter w = new JsonWriter();
        w.beginObject();
        w.name("keys").beginArray();
        for (String k : c.keys()) w.value(k);
        w.endArray();
        w.name("standard");
        writeTree(w, c.standardNodes(), c.standardSnapshot());
        w.name("radix");
        writeTree(w, c.radixNodes(), c.radixSnapshot());
        w.name("saved").value((long) c.saved());
        w.name("pct").value(c.savedPct());
        w.endObject();
        return w.toString();
    }

    private static void writeTree(JsonWriter w, int nodes, TrieSnapshot snap) {
        w.beginObject();
        w.name("nodes").value((long) nodes);
        w.name("root");
        writeNode(w, snap.root(), true);
        w.endObject();
    }

    private static void writeNode(JsonWriter w, TrieNodeSnapshot node, boolean root) {
        w.beginObject();
        w.name("key").value(node.key());
        w.name("value").value(node.value() == null ? null : String.valueOf(node.value()));
        w.name("absorbed").value(TrieMetrics.isAbsorbed(node, root));
        w.name("children").beginArray();
        for (TrieEdge edge : node.children()) {
            w.beginObject();
            w.name("label").value(edge.label());
            w.name("target");
            writeNode(w, edge.target(), false);
            w.endObject();
        }
        w.endArray();
        w.endObject();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=CompressionCompareJsonSerializerTest`
Expected: PASS (all four methods).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/compare/CompressionCompareJsonSerializer.java \
        src/test/java/com/gimlism/translucent/trie/compare/CompressionCompareJsonSerializerTest.java
git commit -m "feat(compare): serialize the compression comparison to web JSON

Pure Comparison -> JSON with both trees, savings metrics, and a per-node
absorbed flag baked from TrieMetrics.isAbsorbed. Marked count == saved() and
the radix panel is never marked, pinned at the JSON level.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: `WebVizTemplate.injectStatic` — one-token static-page injector

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/substrate/viz/WebVizTemplate.java`
- Test: `src/test/java/com/gimlism/translucent/substrate/viz/WebVizTemplateTest.java` (extend)
- Create: `src/test/resources/web/webviztemplate-static.html`

**Interfaces:**
- Produces: `WebVizTemplate.injectStatic(String resource, String data) -> String` — reads `resource`, requires the single `/*__DATA__*/` token, substitutes `data`. Throws `IllegalStateException` naming the token if absent.
- Consumes: existing private `read` / `require` helpers.

- [ ] **Step 1: Create the test resource** `src/test/resources/web/webviztemplate-static.html` (exact single line, no trailing newline needed):

```
DATA=/*__DATA__*/
```

- [ ] **Step 2: Write the failing test** — add to `WebVizTemplateTest.java`:

```java
    @Test
    void injectStaticSubstitutesTheDataToken() {
        String out = WebVizTemplate.injectStatic("/web/webviztemplate-static.html", "{\"x\":1}");
        assertTrue(out.contains("DATA={\"x\":1}"), out);
        assertFalse(out.contains("/*__"), "no token left behind");
    }

    @Test
    void injectStaticMissingDataTokenThrowsNamingIt() {
        // webviztemplate-missing.html has no /*__DATA__*/ token.
        var e = assertThrows(IllegalStateException.class,
                () -> WebVizTemplate.injectStatic("/web/webviztemplate-missing.html", "x"));
        assertTrue(e.getMessage().contains("/*__DATA__*/"), e.getMessage());
    }
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q test -Dtest=WebVizTemplateTest`
Expected: FAIL — `cannot find symbol: method injectStatic`.

- [ ] **Step 4: Add the method and token to `WebVizTemplate.java`** — add the constant beside the other token constants:

```java
    private static final String DATA_TOKEN = "/*__DATA__*/";
```

and add the method beside `inject`:

```java
    /**
     * Read {@code resource} and substitute the single {@code DATA} token with {@code data}, for a
     * static page that carries one baked data blob and no frame-replay flags. The three-token
     * {@link #inject} stays the right tool for live (FRAMES/LIVE/CONTROLS) pages.
     */
    public static String injectStatic(String resource, String data) {
        String template = read(resource);
        require(template, resource, DATA_TOKEN);
        return template.replace(DATA_TOKEN, data);
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q test -Dtest=WebVizTemplateTest`
Expected: PASS (existing four methods + the two new ones).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/substrate/viz/WebVizTemplate.java \
        src/test/java/com/gimlism/translucent/substrate/viz/WebVizTemplateTest.java \
        src/test/resources/web/webviztemplate-static.html
git commit -m "feat(substrate): add WebVizTemplate.injectStatic for one-token static pages

Additive single-DATA-token injector for static pages that carry no frame-replay
flags. Existing three-token inject is byte-unchanged.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: `compression-compare-viz.html` page + `CompressionCompareWebExporter`

**Files:**
- Create: `src/main/resources/web/compression-compare-viz.html`
- Create: `src/main/java/com/gimlism/translucent/trie/compare/CompressionCompareWebExporter.java`
- Test: `src/test/java/com/gimlism/translucent/trie/compare/CompressionCompareWebExporterTest.java`

**Interfaces:**
- Consumes: `WebVizTemplate.injectStatic` (Task 3); `CompressionCompareJsonSerializer.toJson` (Task 2); `CompressionCompareDemo.compare`.
- Produces: `CompressionCompareWebExporter.toHtml(String dataJson) -> String`; `writeHtml(Comparison, Path)`; `main(String[])`.

- [ ] **Step 1: Write the failing test** — `CompressionCompareWebExporterTest.java`:

```java
package com.gimlism.translucent.trie.compare;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CompressionCompareWebExporterTest {
    private static String html() {
        return CompressionCompareWebExporter.toHtml(
            CompressionCompareJsonSerializer.toJson(
                CompressionCompareDemo.compare(List.of("she", "shell", "shore", "shy"))));
    }

    @Test
    void embedsTheComparisonData() {
        String out = html();
        assertTrue(out.contains("\"keys\":[\"she\",\"shell\",\"shore\",\"shy\"]"), out);
        assertTrue(out.contains("\"saved\":4"), out);
    }

    @Test
    void isSelfContainedStaticPageNoLiveOrControls() {
        String out = html();
        assertFalse(out.contains("EventSource"), "static page must not open an SSE stream");
        assertFalse(out.contains("/command"), "static page must not POST commands");
        assertFalse(out.contains("/*__"), "no template token left behind");
    }

    @Test
    void carriesTheGhostMarkerStyleAndLegend() {
        String out = html();
        assertTrue(out.contains(".node.ghost"), "ghost marker CSS must be present");
        assertTrue(out.contains("collapsed by radix"), "legend text must be present");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=CompressionCompareWebExporterTest`
Expected: FAIL — `cannot find symbol: CompressionCompareWebExporter`.

- [ ] **Step 3: Create the page** `src/main/resources/web/compression-compare-viz.html` (complete file — the palette block is copied verbatim from `trie-viz.html`; `.node.ghost` is the new marker):

```html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>compression compare — StandardTrie vs RadixTrie</title>
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
  #app { max-width: 1100px; margin: 0 auto; padding: 18px; }
  h1 { font-size: 16px; font-weight:600; margin: 0 0 10px; }
  #banner { margin-bottom: 14px; }
  #banner .metrics { font-variant-numeric: tabular-nums; margin-top: 2px; }
  #legend { color: var(--muted); margin-top: 2px; }
  #panels { display:flex; gap:24px; flex-wrap:wrap; align-items:flex-start; }
  .panel { flex: 1 1 360px; min-width: 0; }
  .panel h2 { font-size: 14px; font-weight:600; margin: 0 0 6px; }
  .stage { background:var(--card); border:1px solid var(--nodeb); border-radius:12px; padding:10px;
           overflow:auto; }
  svg text { fill: var(--fg); font: 13px system-ui, sans-serif; }
  .edge { stroke: var(--edge); stroke-width: 1.5; }
  .elabel { fill: var(--fg); font-weight:600; }
  .root { fill: var(--muted); font-weight:600; font-size:12px; }
  .val { fill: var(--fg); font-variant-numeric: tabular-nums; }
  .node { fill: var(--node); stroke: var(--nodeb); stroke-width: 1.5; }
  .node.key { fill: var(--key); stroke: var(--keyb); }
  .node.ghost { opacity: .4; stroke-dasharray: 4 3; }
</style>
</head>
<body>
<div id="app">
  <h1>compression compare — StandardTrie vs RadixTrie</h1>
  <div id="banner"></div>
  <div id="panels">
    <div class="panel"><h2 id="hstd"></h2><div class="stage"><svg id="svgStd"></svg></div></div>
    <div class="panel"><h2 id="hrdx"></h2><div class="stage"><svg id="svgRdx"></svg></div></div>
  </div>
</div>
<script>
"use strict";
const DATA = /*__DATA__*/;
const PAD = 28, ROW_H = 80, X_GAP = 76, R = 13;

function esc(s) { return String(s).replace(/[&<>]/g, c => ({ "&":"&amp;","<":"&lt;",">":"&gt;" }[c])); }
function txt(s) { return s === null ? "∅" : String(s); }

// Two-pass tidy layout (adapted from trie-viz.html): each leaf takes the next x-slot, an internal
// node centers over its children, y is depth. Static — no highlight/frame concept.
function layout(root) {
  const pos = new Map();
  let leafX = 0, maxDepth = 0;
  (function place(node, depth) {
    maxDepth = Math.max(maxDepth, depth);
    const kids = node.children || [];
    let x;
    if (kids.length === 0) { x = leafX++; }
    else { const xs = kids.map(e => place(e.target, depth + 1)); x = (xs[0] + xs[xs.length - 1]) / 2; }
    pos.set(node, { x, depth });
    return x;
  })(root, 0);
  return { pos, maxDepth, leaves: Math.max(1, leafX) };
}

function renderTree(root, svgEl) {
  const { pos, maxDepth, leaves } = layout(root);
  const sx = x => PAD + x * X_GAP, sy = d => PAD + d * ROW_H;
  let body = "";
  (function edges(node) {
    const p = pos.get(node);
    for (const e of (node.children || [])) {
      const c = pos.get(e.target);
      body += `<line class="edge" x1="${sx(p.x)}" y1="${sy(p.depth)}" x2="${sx(c.x)}" y2="${sy(c.depth)}"/>`;
      const mx = (sx(p.x) + sx(c.x)) / 2, my = (sy(p.depth) + sy(c.depth)) / 2;
      body += `<text class="elabel" x="${mx}" y="${my - 3}" text-anchor="middle">${esc(e.label)}</text>`;
      edges(e.target);
    }
  })(root);
  for (const [node, p] of pos) {
    const cls = "node" + (node.key ? " key" : "") + (node.absorbed ? " ghost" : "");
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
  svgEl.setAttribute("viewBox", `0 0 ${width} ${height}`);
  svgEl.setAttribute("width", width);
  svgEl.setAttribute("height", height);
  svgEl.innerHTML = body;
}

function render() {
  if (!DATA) return;
  document.getElementById("banner").innerHTML =
    `<div>compression compare: {${esc(DATA.keys.join(", "))}}</div>`
    + `<div class="metrics">standard = ${DATA.standard.nodes}`
    + `&nbsp;&nbsp;&nbsp;radix = ${DATA.radix.nodes}`
    + `&nbsp;&nbsp;&nbsp;saved = ${DATA.saved} (${DATA.pct}%)</div>`
    + `<div id="legend">dashed / faded = collapsed by radix (${DATA.saved} nodes)</div>`;
  document.getElementById("hstd").textContent = `standard (${DATA.standard.nodes})`;
  document.getElementById("hrdx").textContent = `radix (${DATA.radix.nodes})`;
  renderTree(DATA.standard.root, document.getElementById("svgStd"));
  renderTree(DATA.radix.root, document.getElementById("svgRdx"));
}
render();
</script>
</body>
</html>
```

- [ ] **Step 4: Create the exporter** `CompressionCompareWebExporter.java`:

```java
package com.gimlism.translucent.trie.compare;

import com.gimlism.translucent.substrate.viz.WebVizTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Assembles the self-contained compression-compare HTML page from
 * {@code /web/compression-compare-viz.html} by injecting one baked {@code DATA} blob (from
 * {@link CompressionCompareJsonSerializer}) via {@link WebVizTemplate#injectStatic}. Static: no live
 * SSE, no controls — the {@code StandardTrie} and {@code RadixTrie} emit different event streams and
 * cannot animate in lockstep, so the page renders the two final trees once.
 */
public final class CompressionCompareWebExporter {

    private static final String TEMPLATE_RESOURCE = "/web/compression-compare-viz.html";

    private CompressionCompareWebExporter() {}

    /** The self-contained HTML with {@code dataJson} injected. */
    public static String toHtml(String dataJson) {
        return WebVizTemplate.injectStatic(TEMPLATE_RESOURCE, dataJson);
    }

    /** Write {@link #toHtml(String)} for {@code comparison} to {@code out} (UTF-8). */
    public static void writeHtml(CompressionCompareDemo.Comparison comparison, Path out) throws IOException {
        Files.writeString(out, toHtml(CompressionCompareJsonSerializer.toJson(comparison)));
    }

    /**
     * Write the canonical {@code {she, shell, shore, shy}} page to {@code args[0]} (default
     * {@code target/compression-compare.html}) for manual browser inspection.
     */
    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "target/compression-compare.html");
        writeHtml(CompressionCompareDemo.compare(List.of("she", "shell", "shore", "shy")), out);
        System.out.println("wrote " + out.toAbsolutePath());
    }
}
```

- [ ] **Step 5: Run the exporter test**

Run: `mvn -q test -Dtest=CompressionCompareWebExporterTest`
Expected: PASS (all three methods).

- [ ] **Step 6: Run the full suite**

Run: `mvn -q test`
Expected: PASS — every prior test plus the new ones (green build; count grows by the tasks' new tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/web/compression-compare-viz.html \
        src/main/java/com/gimlism/translucent/trie/compare/CompressionCompareWebExporter.java \
        src/test/java/com/gimlism/translucent/trie/compare/CompressionCompareWebExporterTest.java
git commit -m "feat(compare): web side-by-side compression-compare page + exporter

Static self-contained HTML rendering the fat StandardTrie beside the compressed
RadixTrie as SVG, absorbed nodes drawn ghosted + dashed, under the savings
banner. Dumb two-panel renderer reads the baked DATA blob; exporter injects it
via WebVizTemplate.injectStatic.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Browser end-to-end verification (controller-driven, not a JUnit test)

**Files:** none (verification only). This mirrors every prior web slice's browser-verify pass.

- [ ] **Step 1: Generate the page**

Run: `mvn -q compile exec:java -Dexec.mainClass=com.gimlism.translucent.trie.compare.CompressionCompareWebExporter -Dexec.args="target/compression-compare.html"`
Expected: `wrote …/target/compression-compare.html`.

- [ ] **Step 2: Open it in Chrome** (Claude-in-Chrome: load core browser tools, create a tab, navigate to `file://…/target/compression-compare.html`).

- [ ] **Step 3: Verify the checklist** (screenshot + `read_console_messages`):
  - Both trees render as SVG side by side (standard left, radix right).
  - Exactly **4** nodes in the *standard* panel are dashed + faded (the `"s"`, inner `"l"`, `"o"`, `"r"` chain nodes); the *radix* panel has **zero** dashed nodes.
  - Banner reads `compression compare: {she, shell, shore, shy}` / `standard = 10   radix = 6   saved = 4 (40%)`; legend reads `dashed / faded = collapsed by radix (4 nodes)`.
  - Panel headers read `standard (10)` and `radix (6)`.
  - **Zero** console errors.

- [ ] **Step 4: Verify dark mode** — re-check readability with the browser emulating `prefers-color-scheme: dark` (the ghost dashed/faded style must still be legible against `--card`).

- [ ] **Step 5: Record the result** in the SDD progress ledger (`.superpowers/sdd/progress.md`) — no commit needed (verification produces no source change; `target/` is build output).

---

## Self-Review

**Spec coverage:**
- `TrieMetrics.isAbsorbed` shared predicate + ASCII renderer delegation → Task 1. ✓
- `Comparison.savedPct()` → Task 1. ✓
- `CompressionCompareJsonSerializer` (baked per-node `absorbed`, keys/counts/saved/pct) → Task 2. ✓
- marked ≡ saved invariant + radix-never-marked + 2nd data point + empty set → Task 2. ✓
- `WebVizTemplate.injectStatic` (+ missing-token throw) → Task 3. ✓
- `compression-compare-viz.html` dumb two-panel renderer, `.ghost` from `absorbed`, palette reuse, responsive wrap → Task 4. ✓
- `CompressionCompareWebExporter` (`toHtml`/`writeHtml`/`main`) → Task 4. ✓
- Self-contained/static exporter asserts (no EventSource/command/token) → Task 4. ✓
- Browser e2e (4 ghost nodes, radix clean, banner, both themes, no console errors) → Task 5. ✓

**Placeholder scan:** none — every code/test step carries complete content.

**Type consistency:** `isAbsorbed(TrieNodeSnapshot, boolean) -> boolean`, `savedPct() -> long`, `toJson(Comparison) -> String`, `injectStatic(String, String) -> String`, `toHtml(String) -> String` are used identically wherever referenced. All `int` counts cast to `(long)` for `JsonWriter.value`. JSON field names (`keys/standard/radix/nodes/root/key/value/absorbed/children/label/target/saved/pct`) match between serializer (Task 2) and the page's JS reads (Task 4).
