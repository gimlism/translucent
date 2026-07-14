# Trie Web Visualizer — Slice A (static replay)

**Date:** 2026-07-13
**Structure:** RadixTrie
**Slice:** A of the trie's 4-slice web-viz arc (A static → B live SSE → C REPL → D controls)
**Mirrors:** HashMap web viz (PR #17), ArrayList web viz (PR #21)

## Goal

The first non-terminal renderer for the `RadixTrie`: a self-contained HTML page
that replays a recorded `TrieEvent` stream as an SVG **top-down node-link tree**.
Replay-only, zero dependencies, discrete frames with a cross-fade. This is the
web analogue of the existing `AsciiTrieRenderer` — it holds the trie-specific
drawing so the front-end stays a dumb renderer.

The trie is an N-ary tree with `String`-labelled (compressed) edges and key-nodes
that carry a value — a shape neither the map's buckets/RB-trees nor the list's two
array rows can express. The SVG renderer is therefore the one genuinely new piece
of work; everything else is a faithful mirror of the ArrayList Slice A scaffolding.

## Scope

**In scope:** static baked replay of a recorded event stream; the top-down SVG
renderer; the house scrubber (prev/play/next, counter, cross-fade, keyboard nav);
a demo that bakes the `{shore, she, shell}` insert-then-remove story to
`target/trie-web-viz.html`.

**Out of scope (later slices):** live SSE (Slice B), terminal REPL (Slice C),
browser command controls (Slice D). The template carries the `LIVE`/`CONTROLS`
token literals (required by `WebVizTemplate.inject`) but Slice A wires **only**
the FRAMES + scrubber JavaScript. The SSE and command-box branches are added in
the slice that browser-verifies them.

## Architecture

Reuses the established web-viz seam. New pieces are per-structure only; the generic
substrate is untouched.

| File | Status | Role |
|---|---|---|
| `trie/viz/TrieJsonSerializer.java` | new | Per-structure half of the seam: a recorded `TrieEvent` stream → the JSON the browser replay consumes. Reuses `JsonWriter`, `TrieEventFormatter` (captions), and `AsciiTrieRenderer.affectedPath` (highlight target). |
| `resources/web/trie-viz.html` | new | Self-contained page. Carries all three tokens (`FRAMES`/`LIVE`/`CONTROLS`); Slice A wires FRAMES + scrubber only. Holds the trie `renderFrame` — the new work. |
| `trie/viz/TrieWebExporter.java` | new | `toHtml(framesJson)` / `writeHtml(...)`, delegating token substitution to `WebVizTemplate`. No `liveHtml`/`controlsHtml` yet (arrive in B/D). |
| `trie/demo/TrieWebVizDemo.java` | new | Records the demo story via `TrieRecordingListener`, serializes, writes `target/trie-web-viz.html`. Mirrors `ListWebVizDemo`. |

**Untouched:** `core/`, `events/`, `substrate/viz/JsonWriter`, `substrate/viz/WebVizTemplate`.
Because no event-emitting or snapshot code changes, the recurring
**snapshot-before-settled** bug has zero surface this slice.

## Data flow

```
RadixTrie mutations
   → TrieEvent stream (recorded by TrieRecordingListener)
   → TrieJsonSerializer.toJson(events)         [Java owns event-shape branching]
   → WebVizTemplate.inject("/web/trie-viz.html", framesJson, "false", "false")
   → baked self-contained HTML
   → browser renderFrame() draws SVG           [dumb renderer]
```

## Frame JSON contract

```
{ "frames": [
  { "event": { "type": "Descend", "label": "<caption>", "highlightPath": "she" },
    "trie":  { "size": 2, "root": <node> } }
]}

node := { "key": <bool>,
          "value": <string | null>,
          "children": [ { "label": "sh", "target": <node> }, ... ] }
```

- `event.type` = `e.getClass().getSimpleName()`.
- `event.label` = `TrieEventFormatter.format(e)` — the single source of wording,
  shown as the frame caption (mirrors the ASCII renderer's event line).
- `event.highlightPath` = `AsciiTrieRenderer.affectedPath(e)` — the root-to-node
  label concatenation of the node to highlight. For a `Prune` this is the
  **surviving parent** (the deleted leaf is gone from `after()`); `affectedPath`
  already encodes that rule, so the ASCII and web renderers highlight the identical
  node for every event.
- `trie` = the event's own `after()` snapshot, serialized recursively. Each frame
  shows exactly the state the core emitted.
- `value` via `String.valueOf`, `null` → JSON `null` (demo values are `Integer`s).

## The SVG renderer (`renderFrame` — the one piece of new logic)

Top-down tidy tree, computed in the browser from the recursive `trie` node each frame:

- **Two-pass layout.** Post-order assigns each *leaf* the next x-slot; an internal
  node's x = midpoint of its children's x-range. y = `depth · ROW_H`. Overall
  width = `leafCount · X_GAP`, height = `(maxDepth + 1) · ROW_H` — the `viewBox`
  grows with the tree (same pattern as the list renderer's capacity-driven width).
- **Nodes.** Branch node = small open circle; **key-node** (word ends here) =
  filled/ringed circle with `=value` beside it; the root is labelled `(root)`.
- **Edges.** A line parent→child with the compressed `label` as text near the edge
  midpoint.
- **Highlight.** While walking the tree the renderer accumulates each node's
  root-to-node path; the node whose path equals `event.highlightPath` gets the
  orange `--hl` ring (mirrors the ASCII `> ` prefix). Node-only — thickening the
  incoming edge is deliberately omitted to keep the renderer dumb.
- **Chrome.** Reuses the house scrubber verbatim: prev/play/next buttons, frame
  counter, cross-fade via opacity transition, left/right keyboard nav, caption from
  `event.label`. Light/dark via the shared CSS variables.

## Error handling

- `WebVizTemplate.inject` already requires all three tokens and throws
  `IllegalStateException` if any is missing or the resource is absent — the
  exporter inherits this; no new error paths.
- The renderer is defensive against an empty trie (root with no children → just the
  `(root)` node) and a no-events stream (caption `(no events to replay)`), matching
  the list/map templates.

## Testing

Java tests mirror the ArrayList Slice A set; the SVG JS is untested-by-design and
controller-browser-verified.

- **`TrieJsonSerializerTest`** — recursive node shape; `highlightPath` per event
  type including `Prune → parent`; value quoting and `null`; empty trie; a
  multi-event frame stream.
- **`TrieWebExporterTest`** — FRAMES injected, `LIVE`/`CONTROLS` baked to `false`;
  all-three-tokens-required behaviour inherited from `WebVizTemplate`.
- **`TrieWebVizDemoTest`** — demo runs headlessly, writes valid self-contained HTML
  containing the expected structure/markers.
- **Browser verification** (controller, per the live-viz recipe): `mvn process-classes`,
  serve `target/classes` over `http://localhost:7070`, load the baked page, and
  confirm the `{shore, she, shell}` insert (watch edges split and branch) and the
  `{shell, she}` remove (watch leaves prune and edges merge) render correctly, the
  highlight tracks the affected node, and the console shows zero errors.

## Risk to verify, not assume

The "don't mirror too literally" lesson (the list's e2e tripped on a lazy
`Grow`-before-`Append` first frame): **the trie's first emitted event will be
confirmed, not assumed.** For `put` into an empty trie the grammar
(`Descend* → [SplitEdge] → [CreateNode] → Put`) suggests the first frame is
`CreateNode` (no prefix to descend, no edge to split), but the implementation plan
will pin the actual first event via the recorded stream before writing any
first-frame assertion.

## Reuse notes

- `TrieWebExporter` becomes the **third** consumer of `WebVizTemplate`, retroactively
  satisfying the rule of three for the shared 3-token injector extracted during the
  ArrayList arc.
- The only structure-specific browser surface across the whole arc remains the
  future handler closure `line → interp.execute(line, trie).message()` (Slice D);
  Slice A adds none of it.
