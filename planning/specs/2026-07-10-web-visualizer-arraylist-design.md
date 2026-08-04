# ArrayList web visualizer — design

**Date:** 2026-07-10
**Status:** Approved (pending user review of this spec)
**Structure:** `TeachingArrayList`

## Summary

Give `TeachingArrayList` a self-contained HTML/SVG **replay** of a recorded
`ListEvent` stream, exactly parallel to the HashMap web visualizer (PR #17).
This is the first slice of the ArrayList live-viz arc; it establishes the JSON
serializer, SVG template, and web exporter that the later three slices reuse
verbatim.

## Context

The three structures — HashMap, ArrayList, Trie — each have a core + an ASCII
visualizer on the shared substrate. Only the HashMap has gone further, up the
web/live stack (PRs #17 → #20): static web replay, then a live SSE mirror, then a
terminal REPL, then browser controls. This arc now extends that stack to the
ArrayList.

The transport substrate (`LiveServer`, `JsonWriter`) is already generic and
structure-agnostic. What each structure still needs of its own is a JSON
serializer, an SVG template (the dumb browser renderer — inherently
structure-shaped), a web exporter, and demos. This slice delivers the first of
those, in the static-replay form.

### Sequencing (the ArrayList live-viz arc)

Per the chosen decomposition, the ArrayList arc is re-sliced into the same four
stages the HashMap went through, each its own spec/plan/PR:

- **Slice A — static web visualizer (THIS SPEC).** Records a `ListEvent` stream,
  writes a self-contained HTML replay. Mirrors HashMap PR #17.
- **Slice B — live SSE mirror.** Reuses `LiveServer` + `JsonWriter`; a
  `ListLiveVisualizer` broadcasts frames; the template gains a `LIVE` mode.
  Mirrors PR #18. *(out of scope here)*
- **Slice C — terminal REPL.** A `ListCommandInterpreter` + `LiveReplDemo`
  analogue. Mirrors PR #19. *(out of scope here)*
- **Slice D — browser controls.** Command input POSTs to `/command`; template
  gains a `CONTROLS` mode. Mirrors PR #20. *(out of scope here)*

Slices B–D each get their own brainstorm/spec when reached. A layering decision
they will raise — whether `CommandResult`, `BrowserLauncher`, and `DemoLifecycle`
(currently in `hashmap/` packages, all structure-agnostic) should be promoted to
a shared location or duplicated — is **deferred to those slices**; it does not
arise in Slice A.

## Goals

- A self-contained HTML file (no external assets, no network) that replays a
  recorded `ListEvent` stream as SVG, frame per event.
- A **two-tier** rendering: a logical-list row above a backing-array row, making
  "a list is a view over an array" explicit.
- The three signature teaching moments of a growable array made visible:
  size-vs-capacity (grey capacity tail), amortized growth (`Grow` extends the
  backing array), and O(n) shifting (`Shift` bursts animate element copies).
- Reuse the shared `JsonWriter` and the existing `ListEventFormatter` labels; no
  new dependencies.

## Non-goals

- Live streaming, REPL, or browser controls (Slices B–D).
- Any change to `TeachingArrayList` core or its event vocabulary.
- A generic/cross-structure renderer. The template is ArrayList-specific by
  design, exactly as `map-viz.html` is HashMap-specific.
- Promoting shared demo/REPL helpers out of `hashmap/` (deferred to Slice B+).

## Components

Four new files, each shadowing a HashMap sibling:

| New file | Package | Mirrors | Responsibility |
|---|---|---|---|
| `ListJsonSerializer` | `arraylist/viz` | `MapJsonSerializer` | `List<ListEvent>` → JSON frames; **reuses `ListEventFormatter.format()`** for the caption |
| `list-viz.html` | `resources/web` | `map-viz.html` | dumb two-tier SVG renderer (vanilla JS/SVG) |
| `ListWebExporter` | `arraylist/viz` | `MapWebExporter` | inject the JSON into the template at a token |
| `ListWebVizDemo` | `arraylist/demo` | `WebVizDemo` | record a story, write `target/arraylist-web-viz.html` |

## Data flow

```
TeachingArrayList --emits--> ListEvent stream
   (recorded by an existing recording listener; each event carries after())
        |
        v
ListJsonSerializer.toJson(events)  ->  { "frames": [ frame, ... ] }
        |
        v
ListWebExporter.toHtml(json)  ->  list-viz.html with the JSON injected at a token
        |
        v
browser: vanilla JS renders each frame as SVG (dumb renderer)
```

Each event already carries its own `after()` snapshot (settled state, except
`Shift` which is deliberately mid-slide and `Grow` which shows the enlarged
capacity with the pending element not yet placed). The serializer emits exactly
that snapshot per frame, so the JSON shows precisely what the core emitted — the
same "each frame is the event's own after()" discipline the HashMap serializer
uses.

## JSON frame contract

One frame per event:

```json
{
  "event": { "type": "Insert", "label": "INSERT c @ 2", "index": 2 },
  "list":  {
    "capacity": 5,
    "size": 3,
    "slots": [
      { "filled": true,  "element": "a" },
      { "filled": true,  "element": "b" },
      { "filled": true,  "element": "c" },
      { "filled": false },
      { "filled": false }
    ]
  }
}
```

- `event.type` — the event's simple class name (`Append`/`Insert`/`Set`/
  `RemoveAt`/`Shift`/`Grow`).
- `event.label` — from `ListEventFormatter.format(event)` (single source of
  wording, shared with the console logger).
- `event.index` — the primary highlighted cell. Present for
  `Append`/`Insert`/`Set`/`RemoveAt` (the affected index).
- `Shift` frames additionally carry `fromIndex` and `toIndex` (source vacated,
  destination filled) so the renderer can animate the slide; `event.index` =
  `toIndex`.
- `Grow` frames carry no single index; the renderer highlights the newly-added
  tail cells `[oldCapacity, newCapacity)` (derivable from the snapshot capacity
  vs. the previous frame, or the `Grow` event's old/new capacity — the serializer
  emits `oldCapacity`/`newCapacity` for `Grow`).
- `slots` — length `capacity`; each is `{filled:true, element:"…"}` or
  `{filled:false}`. `element` is the value's string form; `null` elements encode
  as JSON `null` via `JsonWriter`.

Frames wrap in `{"frames":[…]}` for replay. A `toFrame(event)` single-frame
method (no wrapper) is included from the start so Slice B's live broadcaster can
reuse it, mirroring `MapJsonSerializer.toFrame`.

## The two-tier renderer (`list-viz.html`)

Layout, per frame:

- **Bottom row — backing array.** `capacity` cells. `[0, size)` filled (element
  text); `[size, capacity)` greyed as unused capacity. Index labels `0..cap-1`
  beneath each cell.
- **Top row — logical list.** `size` cells showing the logical sequence, each
  joined to its backing cell by a straight vertical connector (index-aligned
  1:1, since the ArrayList is contiguous from 0).
- **Header.** The event caption (`event.label`) and a `size N / capacity M`
  readout.

Highlight: the cell(s) named by `event.index` (and `fromIndex`/`toIndex` for
`Shift`, the tail region for `Grow`) are accented in both rows where applicable.

**Shift behavior (settled decision).** During a `Shift` burst the snapshot is
mid-slide. The **top logical row freezes** at the last settled logical sequence
for the whole burst; only the **bottom backing array animates** the element
copying cell-to-cell. This reads as "logically nothing has changed yet — this is
the physical O(n) cost of the operation." The renderer tracks the last settled
state (the most recent non-`Shift` frame's `slots[0, size)`) and holds the top
row to it until the next settled frame.

Interaction scaffolding (discrete frames, cross-fade between frames, a
follow-tail scrubber with a "jump to live"/end control) is carried over from
`map-viz.html` — same vanilla-JS structure, retargeted to the list DOM. The JS
is untested-by-design and browser-verified.

## Demo story (`ListWebVizDemo`)

`new TeachingArrayList<>(4)` — a small initial capacity so growth appears
quickly rather than after ten appends. Then a sequence that exercises every
event type at least once:

1. Append `a`, `b`, `c`, `d` — fills capacity 4.
2. Append `e` — triggers `Grow` (4 → 6), then `Append`. Backing array visibly
   extends; grey tail appears.
3. Insert `x` at index 2 — a `Shift` burst (high→low) then `Insert`. The classic
   "make room" animation.
4. Set index 0 to `A` — a single `Set`, no structural change.
5. Remove index 1 — a `Shift` burst (low→high) then `RemoveAt`. The "close the
   gap" animation.

Factored into a package-private `buildHtml()` seam (records the story, returns
the full HTML string) so it is unit-testable without touching the filesystem —
exactly like `WebVizDemo.buildHtml()`. `main` writes
`target/arraylist-web-viz.html` (overridable via `-Dexec.args`).

Run: `mvn exec:java -Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListWebVizDemo`.

## Testing

**Unit (JVM, no browser):**

- `ListJsonSerializer`:
  - Each event type serializes to the expected frame shape (`type`, `label`,
    `index`; `Shift` carries `fromIndex`/`toIndex`; `Grow` carries
    old/new capacity).
  - Label text comes through `ListEventFormatter` (assert on a known event's
    caption).
  - Slots encode filled vs. empty correctly; a `null` element becomes JSON
    `null`; `size`/`capacity` mirror the snapshot.
  - Inline-script safety: element strings containing `<`, and the line/para
    separators U+2028/U+2029, are escaped (the same hazard `MapJsonSerializer`
    guards, since the JSON is embedded in an inline `<script>`).
  - `toFrame` emits a single frame with no `frames` wrapper.
- `ListWebExporter`: the token is replaced by the JSON; the output contains no
  residual token and no external asset reference (self-contained).
- `ListWebVizDemo.buildHtml()`: returns non-empty HTML containing the injected
  frames array (smoke-level, like the HashMap demo test).

**Browser-verified (controller, untested-by-design JS):** run the demo, open the
HTML, confirm the two-tier layout renders, growth extends the backing row,
insert/remove shift bursts animate on the bottom row while the top row holds,
scrubbing works, and the console is clean. Uses the established live-viz
browser-verification recipe.

Target: ~15–20 new unit tests; full suite stays green.

## Risks / watch-items

- **Recurring event-frame timing bug.** The project's standing rule: any event's
  `after()` must show fully-committed state. ArrayList already encodes deliberate
  mid-operation snapshots (`Shift` mid-slide, `Grow` pre-placement) as documented
  contract, so the serializer must faithfully render those *as given* and not
  "correct" them — the renderer's freeze-top rule handles the visual, not the
  serializer.
- **Grow tail highlight source.** Highlighting `[oldCapacity, newCapacity)` needs
  the old capacity; take it from the `Grow` event's own `oldCapacity` field
  rather than diffing against the previous frame, to keep each frame
  self-contained.
- **Stale Eclipse/LSP diagnostics.** As every prior slice noted, `mvn
  test-compile` is the source of truth; ignore IDE "cannot be resolved" noise.

## Workflow

brainstorming (this spec) → writing-plans → subagent-driven-development (fresh
implementer + reviewer per task, whole-branch review) → PR
`feat/arraylist-web-visualizer`, merged with `gh pr merge N --merge`.
