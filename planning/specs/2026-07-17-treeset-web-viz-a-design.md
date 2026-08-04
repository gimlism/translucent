# TreeSet Web Visualizer — Slice A (static replay)

**Date:** 2026-07-17
**Structure:** TeachingTreeSet (red-black tree)
**Slice:** A of the TreeSet's 4-slice web-viz arc (A static → B live SSE → C REPL → D browser controls)
**Mirrors:** Trie web viz Slice A (PR #26), HashMap web viz (PR #17), ArrayList web viz (PR #21)

## Goal

The first non-terminal renderer for `TeachingTreeSet`: a self-contained HTML page that
replays a recorded `SetEvent` stream as an SVG **top-down binary red-black tree**.
Replay-only, zero dependencies, discrete frames with a cross-fade. This is the web
analogue of the existing `AsciiSetRenderer` — it holds the set-specific drawing so the
front-end stays a dumb renderer.

The genuinely new file is `treeset-viz.html`, but it is a **composition of two renderers
the project already ships**, not a novel SVG: the trie's page shell (single-tree stage,
scrubber, the three mode tokens, cross-fade) and the map's red-black-node rendering
(in-order-slot layout, red/black fills, highlight ring). Everything else is a faithful
mirror of the trie's Slice A scaffolding.

## Scope

**In scope:** static baked replay of a recorded event stream; the top-down SVG renderer;
the house scrubber (prev/play/next, counter, cross-fade, keyboard nav); a demo that bakes a
scripted insert-then-remove story to `target/treeset-web-viz.html`.

**Out of scope (later slices):** live SSE (Slice B), terminal REPL (Slice C), browser
command controls (Slice D). The template carries the `FRAMES`/`LIVE`/`CONTROLS` token
literals (required by `WebVizTemplate.inject`) but Slice A wires **only** the FRAMES +
scrubber JavaScript. The SSE and command-box branches are added in the slice that
browser-verifies them — this slice does **not** copy the trie's completed post-Slice-D
`if (LIVE) { … }` block.

**Explicit scope line — small teaching regime.** Elements are drawn *inside* the node
circle, so this targets short integers / short strings only (the set is generic over `E`; a
long string will not fit a circle). Matches the ASCII slice's small-tree assumption; deep
or wide trees are a non-goal.

## Architecture

Reuses the established web-viz seam. New pieces are per-structure only; the generic
substrate is untouched.

| File | Status | Role |
|---|---|---|
| `treeset/viz/TreeSetJsonSerializer.java` | new | Per-structure half of the seam: a recorded `SetEvent` stream → the JSON the browser replay consumes. Reuses `JsonWriter`, `SetEventFormatter` (captions), and `AsciiSetRenderer.affectedElement` (highlight target). `toJson(List)` + `toFrame(SetEvent)` (the latter dormant until Slice B's live path). Direct analog of `TrieJsonSerializer`. |
| `resources/web/treeset-viz.html` | new | Self-contained page. Carries all three tokens (`FRAMES`/`LIVE`/`CONTROLS`); Slice A wires FRAMES + scrubber only. Holds the set `renderFrame` — the trie's shell composed with the map's RB-node rendering. |
| `treeset/viz/TreeSetWebExporter.java` | new | `toHtml(framesJson)` / `writeHtml(...)`, delegating token substitution to `WebVizTemplate` (4th consumer). No `liveHtml`/`controlsHtml` yet (arrive in B/D). |
| `treeset/demo/TreeSetWebVizDemo.java` | new | Records the demo story via `SetRecordingListener`, serializes, writes `target/treeset-web-viz.html`. Mirrors `TrieWebVizDemo`. |

**Untouched:** `treeset/core/`, `treeset/events/`, `substrate/rbtree/`, `substrate/viz/JsonWriter`,
`substrate/viz/WebVizTemplate`. Because no event-emitting or snapshot code changes, the
recurring **snapshot-before-settled** bug has zero surface this slice.

## Data flow

```
TeachingTreeSet mutations
   → SetEvent stream (recorded by SetRecordingListener, already shipped)
   → TreeSetJsonSerializer.toJson(events)        [Java owns event-shape + highlight branching]
   → { "frames": [ { "event": {type,label}, "set": {size, root} }, … ] }
   → TreeSetWebExporter.toHtml(framesJson)        [WebVizTemplate injects; LIVE/CONTROLS = false]
   → self-contained treeset-web-viz.html
   → browser: dumb SVG renderer replays frames with a scrubber
```

## The SVG renderer (`treeset-viz.html`)

**Node model per frame:** a recursive binary node `{ element, red, hl, left, right }` (left/right
null when absent). The whole-set frame is `{ size, root }`.

**Layout (from the map's RB-bin renderer):** in-order traversal assigns each node an x-slot
(increment on visit), depth assigns y. This is BST-faithful — left subtree always renders left,
right always right, and a single child sits on its correct side (unlike a center-over-children
tidy layout). Edges are parent→child straight lines drawn before nodes; **no edge labels** (a
set has none).

**Node styling (from the map):** `--red` / `--black` CSS vars (light + dark), circle fill by
`node.red`, white centered element text, a `--hl` highlight ring on `node.hl`. Reuse the map's
exact palette values for cross-structure consistency; copy the ~6 CSS lines into this
self-contained page (each `*-viz.html` is standalone by design — no external CSS).

**Shell (from the trie):** caption line, prev/play/next + counter scrubber, cross-fade on frame
change, arrow-key nav, the `LIVE`/`CONTROLS` tokens present but their JS branches omitted this
slice.

## Highlight — marked in Java, not matched in JS

The affected node per event comes from `AsciiSetRenderer.affectedElement(e)` — the **same** node
the ASCII renderer highlights (Compare→visited, Add→new, Rotation→pivot, Recolor→recoloured,
Remove→none). Because set elements are `Object`, matching by value **in JS** is a silent-mismatch
trap: if `element` serializes as JSON number `10` and the highlight target as string `"10"`, then
`10 === "10"` is false and the ring never renders — invisible to any non-browser test.

So the serializer resolves the highlight **in Java**: during the recursive node walk it sets
`hl: true` on the node whose `element.equals(affectedElement(e))`, `false` otherwise (Remove's
null target marks nothing). The browser just reads the boolean. This moves the equality into Java
where it is unit-testable and eliminates the JS type-coercion trap — a deliberate, stronger
divergence from the trie's `highlightPath` string-match (both trie labels are strings, so the trie
never hits this).

## Error handling

`TreeSetJsonSerializer` is pure over immutable, already-validated snapshots — no I/O. The
`TreeSetWebExporter` delegates token substitution to `WebVizTemplate`, which throws
`IllegalStateException` if a token is missing (a template-authoring error caught by the exporter
test). The SVG JavaScript is untested-by-design (a dumb renderer) and browser-verified.

## Testing

- **`TreeSetJsonSerializerTest`** — the JSON shape: a `frames` array; each frame's `event`
  (`type`, `label` from `SetEventFormatter`) and recursive `set`/node object; empty-set root
  `null`; **the highlight is pinned in Java** — for an event whose affected node is live (e.g. an
  `Add`), exactly that node's serialized object has `hl:true` and the others `hl:false`; a `Remove`
  frame marks no node. `element` and the `hl` decision use the *same* stringified value, so no
  number-vs-string skew.
- **`TreeSetWebExporterTest`** — `toHtml` injects the frames JSON, bakes `LIVE`/`CONTROLS` to
  `false`, and produces a self-contained page (no external URLs); a missing template token throws.
- **`TreeSetWebVizDemoTest`** — running the demo seam writes a non-empty `target/treeset-web-viz.html`
  containing the baked frames.
- **Browser verification (controller, not a unit test):** serve `target/classes` over
  `http://localhost:PORT` (the extension blocks `file://`) after `mvn process-classes`, open
  `treeset-viz.html` with a baked demo, and confirm: the RB tree renders top-down with red/black
  nodes; rotations/recolours restructure/recolour across frames; the highlight ring tracks the
  affected node (and a `Remove` frame shows none); scrubber + keyboard nav work; zero console
  errors. The SVG JS is untested-by-design; this is its only verification.

## Deferred / follow-ups

- Slice B (live SSE mirror): `TreeSetLiveVisualizer` + the `LIVE` branch on `treeset-viz.html` +
  `TreeSetWebExporter.liveHtml()` + a live demo; reuse `LiveServer`/`JsonWriter` verbatim.
- Slices C (REPL) and D (browser controls).
- The `toFrame(SetEvent)` method ships this slice but is exercised only when Slice B's live path
  broadcasts single frames.
