# Compression-compare web side-by-side (StandardTrie viz arc, slice 2)

**Date:** 2026-07-24
**Status:** design approved, ready for implementation plan
**Depends on:** PR #41 (StandardTrie + compare), #42 (ASCII side-by-side), #43 (collapse-marker)

## Summary

A single **static**, self-contained HTML page: the browser analogue of slice-1's ASCII
`CompressionCompareRenderer`. It renders the fat `StandardTrie` (left) beside the compressed
`RadixTrie` (right) as SVG, under the savings banner, with the nodes radix absorbs drawn
**ghosted + dashed**. This makes slice-1's *measured* compression *visible in the browser* the
same way #42 made it visible in the terminal.

## Why static (no live / SSE / controls)

The compression compare is deliberately a **static render of two final trees, not an
event-replay** — the `StandardTrie` and `RadixTrie` emit *different* event streams (per-char +
Prune cascade vs per-suffix + Split/Merge) and cannot animate in lockstep. That is exactly why
slice 1 (ASCII) was static. So this slice has no natural live/repl/controls follow-ups; it is a
single page, the web twin of the ASCII renderer. There is one data blob and no frame stream.

## Scope

Additive only. New code lives in `trie/compare` (its own package's code — free to modify, unlike
the frozen shared surface), plus one small additive method on the shared `substrate/viz`
template assembler, plus one new web resource. The shared surface stays byte-unchanged:
`AsciiTrieRenderer`, `trie/core`, `trie/events`, the substrate's existing behavior, and every
sibling `-viz.html` are untouched. `WebVizTemplate.inject` is byte-unchanged; only a *new*
method is added beside it.

## Components

### 1. `TrieMetrics.isAbsorbed(TrieNodeSnapshot node, boolean root)` — shared predicate

The single definition of "radix absorbs this node":

```java
public static boolean isAbsorbed(TrieNodeSnapshot node, boolean root) {
    return !root && !node.key() && node.children().size() == 1;
}
```

`TrieMetrics` already owns `nodeCount`, so "the compare-package's node metrics" is its coherent
home. Both consumers call it:

- `CompressionCompareRenderer.collectAbsorbed` (ASCII, slice 1.5) is refactored to delegate to
  it — a pure internal refactor with **byte-identical ASCII output**, proven by the existing
  renderer goldens. `CompressionCompareRenderer` is the compare package's own code (slice 1.5
  modified it freely); it is not the frozen shared surface.
- `CompressionCompareJsonSerializer` (this slice) calls it while walking the standard tree.

Single-sourcing this is worth more than generic dedup because it is a **correctness-bearing
definition**: if ASCII and web ever disagreed on "what radix absorbs," the marked ≡ saved
invariant would silently break in one of them.

### 2. `Comparison.savedPct()` — shared percent formula

Hoist the `Math.round(100.0 * saved() / standardNodes())` formula (currently inline in
`CompressionCompareRenderer.banner`) to a method on the `Comparison` record, so the ASCII banner
and the web banner compute the percentage identically. Empty-set guard: `standardNodes()` is the
root count (≥ 1), never 0, so no divide-by-zero.

### 3. `CompressionCompareJsonSerializer` — pure `Comparison → JSON`

`public static String toJson(Comparison c)` producing:

```json
{ "keys": ["she","shell","shore","shy"],
  "standard": { "nodes": 10, "root": {node…} },
  "radix":    { "nodes": 6,  "root": {node…} },
  "saved": 4, "pct": 40 }
```

Each node: `{ "key": bool, "value": string|null, "absorbed": bool,
"children": [ { "label": string, "target": {node…} } ] }`.

Design decisions:

- **`absorbed` is computed in Java and baked per-node**; the JS reads `node.absorbed` and never
  recomputes it. This mirrors the codebase's existing decision for `highlightPath` (computed by
  `AsciiTrieRenderer.affectedPath`, baked into frame JSON — "the front-end stays a dumb
  renderer", per `TrieJsonSerializer`'s own javadoc). Baking it is what keeps the marked ≡ saved
  invariant provable by a **cheap Java test** on the serializer output rather than only in a
  browser.
- **Per-node `absorbed`, not a flat walk-order array.** Emitting a flat array correlated to a
  pre-order walk (the ASCII renderer's shape) would recreate the exact walk-order-correlation
  fragility slice 1.5 had to guard. A per-node boolean threaded through the recursive writer
  (with a `root` flag, exactly as `collectAbsorbed` does) is robust; the ~15 lines that don't
  reuse `TrieJsonSerializer.writeNode` verbatim are an acceptable, deliberate cost.
- The `absorbed` flag is only ever `true` in the **standard** tree. The radix walk still emits
  the field, always `false` (`isAbsorbed` is structurally false for every radix node: radix
  keeps root + keys + branches; every other non-root node it would keep is by definition not a
  single-child non-key node — that is what it compressed away).
- Uses the existing `substrate/viz/JsonWriter` (same as `TrieJsonSerializer`).

### 4. `WebVizTemplate.injectStatic(String resource, String data)` — substrate, additive

```java
private static final String DATA_TOKEN = "/*__DATA__*/";

public static String injectStatic(String resource, String data) {
    String template = read(resource);
    require(template, resource, DATA_TOKEN);
    return template.replace(DATA_TOKEN, data);
}
```

The existing three-token `inject` is built for frame-replay pages (FRAMES/LIVE/CONTROLS). This
static page has no frames, no live, no controls; forcing those dead tokens onto it would be
misleading against the naming discipline. `injectStatic` gives the page one honest `/*__DATA__*/`
token. Adding a method leaves the existing `inject` byte-identical, so the substrate's
shared-surface discipline holds.

### 5. `compression-compare-viz.html` — new web resource, dumb renderer

Self-contained page:

- **Banner:** `compression compare: {she, shell, shore, shy}` /
  `standard = 10   radix = 6   saved = 4 (40%)` / a legend line for the ghost marker
  (`dashed/faded = collapsed by radix (4)`). All values from the baked JSON.
- **Two panels** in a flex row (`standard (10)` left, `radix (6)` right), each a headered SVG;
  the row wraps to vertically stacked on narrow screens (the CSS analogue of the ASCII
  columns→stacked fallback).
- **Inline JS:** a `layout()` adapted from `trie-viz.html`'s two-pass tidy layout (leaf takes
  next x-slot, internal node centers over children, y = depth) and a `renderTree(root, svg)` that
  draws edges + edge labels first, then nodes. Runs **once per panel on load** — no frame index,
  no play/prev/next, no `EventSource`, no command box.
- **Ghost marker:** nodes with `absorbed === true` get a `.ghost` class: dashed stroke +
  ~40% opacity. Reuse the sibling palette vars (`--node`, `--nodeb`, `--key`, `--keyb`, `--edge`)
  and their `prefers-color-scheme: dark` overrides so the ghost reads in **both** themes.
- `const DATA = /*__DATA__*/;` — the single injected token.

Per-page inline JS matches the established pattern (each `-viz.html` carries its own inline
renderer; there is no shared JS asset). The layout algorithm is copied and adapted, not reused
verbatim, because this page draws two static trees rather than stepping a frame stream.

### 6. `CompressionCompareWebExporter` — `JSON → HTML`

- `public static String toHtml(String json)` → `WebVizTemplate.injectStatic(RESOURCE, json)`.
- `public static void writeHtml(String json, Path out)`.
- `public static void main(String[] args)` — writes the canonical `{she,shell,shore,shy}` page
  to a file (mirrors `TrieWebExporter`), so the slice is browser-verifiable like every prior web
  slice.

## Data flow

```
CompressionCompareDemo.compare(keys)  ->  Comparison(standardNodes, radixNodes, keys,
                                                     standardSnapshot, radixSnapshot)
      |                                                        |
      | .saved(), .savedPct()                                  | snapshots
      v                                                        v
CompressionCompareJsonSerializer.toJson(Comparison)  --(TrieMetrics.isAbsorbed per node)-->  JSON
      |
      v
CompressionCompareWebExporter.toHtml(json)  --(WebVizTemplate.injectStatic)-->  self-contained HTML
      |
      v
compression-compare-viz.html  ->  layout() + renderTree() x2  ->  two SVGs, absorbed nodes .ghost
```

## Testing (verification split cleanly: Java asserts the data, e2e asserts the CSS)

**Java — asserts the data:**

- `CompressionCompareJsonSerializerTest`
  - JSON shape + canonical counts: `standard.nodes == 10`, `radix.nodes == 6`, `saved == 4`,
    `pct == 40` for `{she, shell, shore, shy}`; tree structure sane (root present, a key node
    carries its value).
  - ★ **marked ≡ saved invariant**: the count of `absorbed:true` nodes in the *standard* tree
    equals `Comparison.saved()`; the *radix* tree has **zero** absorbed nodes. Non-vacuity: a
    second data point (`{abc}` → `saved 0`, zero absorbed) **and** a predicate-flip proof
    (flipping `isAbsorbed` to mark kept nodes makes the count assertion fail).
  - Empty key set → two root-only trees, `saved 0`, zero absorbed, `pct 0`.
- `TrieMetricsTest` (extend): `isAbsorbed` truth table — root→false, key→false, multi-child→false,
  non-root single-child non-key→true.
- `WebVizTemplateTest` (extend): `injectStatic` substitutes `/*__DATA__*/`; throws
  `IllegalStateException` on a template missing the token.
- `CompressionCompareRendererTest` (existing goldens): unchanged and still green after the
  `collectAbsorbed`/`banner` refactor — proves the ASCII output is byte-identical.
- `CompressionCompareWebExporterTest`
  - `toHtml` embeds the JSON (page contains the keys and the metric values).
  - Self-contained + static: page contains **no** `EventSource`, no `/command`, and no leftover
    `/*__DATA__*/`, `/*__FRAMES__*/`, `/*__LIVE__*/`, `/*__CONTROLS__*/` tokens.
  - Page carries the `.ghost` CSS class and the legend text.

**Browser e2e — asserts the CSS actually applies** (controller-driven Chrome verify, like every
prior web slice; not a JUnit test):

- Both trees render as SVG.
- Exactly the 4 absorbed nodes in the *standard* panel are dashed + faded; the *radix* panel has
  **zero** dashed nodes.
- Banner reads `standard = 10   radix = 6   saved = 4 (40%)`; legend present.
- Readable in **both** light and dark color schemes.
- Zero console errors.

## Non-goals / deferred

- No live SSE, REPL, or browser controls (structurally N/A for a two-stream static compare).
- No morph/animation of standard collapsing into radix (a compelling teaching device, but its
  own future slice).
- No `AbstractTrie` extraction, JavaFX, or compare-to-JDK (unrelated roadmap candidates).

## Risks

- **ASCII byte-identity after the `collectAbsorbed`/`savedPct` refactor** — mitigated by the
  existing `CompressionCompareRendererTest` goldens (must stay green, unchanged).
- **marked ≡ saved could pass vacuously** — mitigated by the predicate-flip proof + a second
  data point, per slice 1.5's own non-vacuity discipline.
- **Ghost style unreadable in one theme** — mitigated by reusing the sibling palette vars and
  verifying both schemes in the e2e pass.
```
