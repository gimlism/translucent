# Web Visualizer — HashMap Replay — Design

**Status:** Draft 2026-07-06
**Builds on:** the complete HashMap (PRs #1–3), its `MapEvent`/`MapSnapshot` contract, and
the `MapRecordingListener`. This is the **first web renderer** and the first non-terminal
visualizer — a new medium alongside the ASCII track, reusing the same recorded event stream.

## Goal

Render a recorded `TeachingHashMap` event stream as an **interactive replay in a browser**:
a self-contained HTML file that steps frame-by-frame through the collide → treeify → resize
→ untreeify story as SVG — buckets, chains, and red-black tree bins with node colour. The
same three-unit rhythm as the ASCII slices (a generic substrate primitive, a per-structure
mapping, driver/demo), in a new output medium.

## Scope

- **HashMap only.** The list and trie web front-ends are deferred to later slices that reuse
  this pipeline. JavaFX is a separate, deferred subsystem — out of scope entirely.
- **Replay only.** We already record the stream (`MapRecordingListener` → `List<MapEvent>`);
  a live browser view would need a server/websocket. Out of scope.
- **Zero new dependencies.** The project hand-rolls everything (RB trees, etc.); adding a
  JSON library (Jackson/Gson) or a JS build toolchain would be tonally wrong.

## Why the web track needs a new seam

The existing viz seam — `EventRenderer<E>`: `event → String`, driven in-process to a
`PrintStream` by `Visualizer`/`Replayer` — is *in-process, synchronous, text*. A browser is
out-of-process, so the web renderer cannot be "another `EventRenderer`." It needs a parallel
seam: **snapshot/event → JSON data**, inlined into a static page whose JavaScript draws it.

This mirrors the ASCII split exactly — a generic substrate primitive plus a per-structure
mapping — because `StructureSnapshot` is a **pure marker with no accessors**: nothing generic
can introspect a `MapSnapshot` without reflection (against the ethos). So the snapshot→JSON
*mapping* is per-structure (the web analogue of the per-structure `AsciiMapRenderer`), while
only the JSON *syntax* is shared.

## Resolved decisions

| Question | Decision |
|---|---|
| Live vs replay | **Replay only** — consume the recorded `List<MapEvent>`, no server. |
| Distribution | **A single self-contained `.html`** — JSON inlined into a template; opens by double-click, shareable as one file. No CDN, no external assets, no build step. |
| JSON library | **Hand-rolled `JsonWriter`** in `substrate/viz` — escaping + object/array/number/null syntax only. |
| Generic vs per-structure | `JsonWriter` is generic (syntax, reused later). The snapshot/event→JSON **mapping** is per-structure (`MapJsonSerializer`). **No** speculative substrate "JsonSerializer" interface (honors the existing "no speculative cross-viz interface" decision). |
| Front-end tech | **Vanilla JS + inline SVG**, no framework, no CDN. |
| Where the intelligence lives | **All of it in the Java serializer** (recursive tree walk, bucket classification, highlight selection). The JS is a **dumb renderer** of pre-computed frames — this is what earns a JS front-end its place in a JUnit-only project and justifies leaving it untested. |
| Transition fidelity | **Discrete frames + a light cross-fade / colour tween.** One SVG per event/snapshot (the ASCII frame model, in a browser). Positions snap — **no** positional interpolation between snapshots. |
| Scrubber model | **Mirror the existing `Replayer`:** step forward / back / play-pause, with a `frame N / M` counter and the event caption. |

## Components

| Component | Package | Responsibility | Tested |
|---|---|---|---|
| `JsonWriter` | `substrate/viz` | Escape strings; write objects, arrays, numbers, booleans, null. Structure-agnostic syntax. | ✅ unit |
| `MapJsonSerializer` | `hashmap/viz` | `List<MapEvent>` → a JSON `frames` array; each frame = an **event descriptor** + a whole-map **snapshot** (recursive for tree bins). Uses `JsonWriter`. | ✅ unit |
| `map-viz.html` template | `src/main/resources/web/` | Vanilla JS + inline SVG renderer + scrubber, with a single `/*__FRAMES__*/` injection token. | ⛔ inspection |
| `MapWebExporter` | `hashmap/viz` | Read the template resource, inject the serialized JSON at the token, write a self-contained `.html` to a given path/stream. | ✅ unit (injection) |
| `WebVizDemo` | `hashmap/demo` | Record the collide → treeify → resize → untreeify story and export the `.html`. Run via `mvn exec:java -Dexec.mainClass=…demo.WebVizDemo`. | ⛔ demo |

## The JSON frame model

Each recorded event becomes one frame: the whole-map snapshot to draw, plus a descriptor of
what happened (for the caption and highlight).

```json
{
  "frames": [
    {
      "event": { "type": "Put", "label": "put 25 → bucket 1", "bucket": 1, "highlightKey": "25" },
      "map": {
        "capacity": 8, "size": 5, "threshold": 6,
        "buckets": [
          { "kind": "empty" },
          { "kind": "chain", "entries": [ { "key": "1", "value": "a", "hash": 1 } ] },
          { "kind": "tree",
            "root": { "key": "17", "value": "q", "color": "BLACK",
                      "left":  { "key": "9",  "value": "i", "color": "RED", "left": null, "right": null },
                      "right": { "key": "25", "value": "y", "color": "RED", "left": null, "right": null } } }
        ]
      }
    }
  ]
}
```

- **`event.type`** is the `MapEvent` subtype name (`Put`, `Remove`, `Collision`, `Resize`,
  `Treeify`, `Untreeify`, `Rotation`, `Recolor`). **`event.label`** is a short human caption.
  **`event.bucket`** / **`event.highlightKey`** drive the SVG highlight (absent → no highlight;
  e.g. a `Resize` highlights nothing specific).
- **`map`** serializes `MapSnapshot`: `capacity`/`size`/`threshold` and `buckets`, where each
  `BucketSnapshot` is `empty`, `chain` (ordered `entries` of `{key,value,hash}`), or `tree`
  (a recursive `root` of `{key,value,color,left,right}`, `null` for absent children).
- Keys/values are arbitrary objects → serialized via their `String` form (the teaching viz
  shows their `toString`); a `null` key or value → JSON `null`. `hash` is the raw int.

## Data flow

1. Build a `TeachingHashMap`; attach a `MapRecordingListener`.
2. `WebVizDemo` runs the scripted story (collide → treeify → resize → untreeify).
3. Take `List<MapEvent>` from the recorder.
4. `MapJsonSerializer.toJson(events)` → JSON string (via `JsonWriter`).
5. `MapWebExporter.export(json, out)` reads `map-viz.html`, replaces `/*__FRAMES__*/`, writes
   the self-contained `.html`.
6. Open the file → the scrubber steps frames as discrete SVG with a light cross-fade.

## Front-end (the dumb renderer)

- **Layout:** buckets as a labelled vertical column; a `chain` bucket as a row of linked cells
  (`key=value`); a `tree` bucket as a recursively laid-out binary tree with red/black node
  fill and parent→child edges. Purely a function of the frame's `map` JSON.
- **Highlight:** the node/bucket named by `event.bucket`/`event.highlightKey` gets a ring;
  the caption shows `event.label`. No highlight when the descriptor omits them.
- **Controls:** step forward / back / play-pause; `frame N / M`; the caption. Transition is a
  short cross-fade + colour tween between frames — positions snap.
- Self-contained: inline `<style>`, `<script>`, and SVG; no network requests.

## Event-frame check (recurring bug pattern)

The serializer reads each event's **already-settled** `after()` snapshot — it does not recompute
state — so it inherits the frame correctness the core already guarantees. The one thing to get
right: a frame's `map` must come from **that event's** `after()` (not the map's live state at
serialization time, which is the end state), so serialization must walk each event's own snapshot.

## Testing

- **`JsonWriter`:** escaping (`"`, `\`, control chars, newline, tab); nested objects/arrays;
  `null`; numbers and booleans; empty object/array.
- **`MapJsonSerializer`:** a treeified bin serializes to a nested `root` with `left`/`right`
  and `color` (`RED`/`BLACK`); a `chain` frame lists entries in insertion order with `hash`;
  a `Resize` frame's `map.capacity` is the new capacity; a `null` key and a `null` value each
  serialize to JSON `null`; the frame count equals the event count; `event.type` matches the
  `MapEvent` subtype. (Parse-free: assert on substrings / a tiny inline JSON reader in the test.)
- **`MapWebExporter`:** the output contains the injected JSON, contains no leftover
  `/*__FRAMES__*/` token, and is a complete HTML document (`<!doctype html>` … `</html>`).
- **Front-end:** not unit-tested — justified because it is a dumb renderer of pre-computed
  frames; correctness lives in the tested Java layer. Verified by inspection and by opening the
  `WebVizDemo` output.

## Out of scope (future)

- The **list and trie** web front-ends (reuse `JsonWriter` + the template scaffolding, add a
  per-structure serializer and SVG layout each).
- **Live** (server-driven) browser view.
- **JavaFX** desktop renderer (separate subsystem).
- **Positional animation** (interpolating node motion across snapshots) — deliberately declined
  in favor of discrete frames.
