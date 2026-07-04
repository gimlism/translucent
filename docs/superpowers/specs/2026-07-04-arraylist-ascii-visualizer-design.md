# Teaching ArrayList — ASCII Visualizer — Design

**Status:** Draft 2026-07-04 (awaiting end-of-work review)
**Builds on:** the ArrayList core slice (Slice 1, `feat/arraylist-slice1`) — the
`ListEvent` stream and immutable `ListSnapshot` model are the fixed contract this
consumes. Mirrors the HashMap's ASCII-visualizer slice (merged, PR #4) in shape.

## Goal

Render the teaching ArrayList's event stream visually in the terminal, so learners
watch the array evolve — appends, the amortized **grow**, element **shifts** on
insert/remove — as ASCII diagrams. Same role as the HashMap ASCII visualizer: the
simplest pluggable renderer, establishing the list's rendering seam.

## Relationship to the HashMap visualizer (and the deferred extraction)

This slice is built **standalone-concrete** under `arraylist.viz`, sharing no types
with `hashmap.viz` — consistent with the core slice and the roadmap. It deliberately
mirrors the HashMap viz's three units (pure renderer / live driver / replayer). The
duplication between `AsciiReplayer` and `AsciiListReplayer` (and the two live
visualizers) is **intended**: it is the second concrete example that the *later*
generic-substrate extraction slice will unify. Building it reveals two real seams:

- **`Palette` is tree-specific, not a general drawing concern.** It colours
  red-black nodes; a list has no coloured elements, so the list renderer needs **no
  `Palette`**. The later "drawing primitives" extraction should treat colour as a
  per-structure decoration, not part of the shared cells/chain primitive.
- **The replay/live drivers are genuinely structure-agnostic** (frame N/M stepping,
  Enter/back/quit, auto-play) — they differ only in the event/renderer types. Prime
  candidates for a generic `StructureReplayer<E>` in the extraction slice.

## Resolved decisions

| Question | Decision |
|---|---|
| Layout | **Horizontal cells row** — a contiguous band `[ a | b | c | · ]`, not the HashMap's vertical one-slot-per-line list. Contiguity *is* the teaching point of an array; a vertical layout reads like a linked structure. |
| Slot highlight | The affected slot is wrapped `>x<` in place (self-aligning, width-independent), vs the padded ` x ` of other cells. No separate caret line to misalign. |
| Empty slots | Unused capacity renders as `·` (matches the HashMap's empty-bucket marker), so capacity-vs-size is visible at a glance. |
| Colour / `Palette` | **None.** List elements are uncoloured; the renderer takes no `Palette`. (A revealed seam — see above.) |
| Drive modes | **Both** a live `ListEventListener` and a step-through replay driver, mirroring the HashMap viz. |
| Replay control | Forward/back scrubber (each event carries a full `ListSnapshot`, so frames are independent) plus auto-play — same as `AsciiReplayer`. |

## Architecture

New package `com.gimlism.translucent.arraylist.viz`, depending only on the
`arraylist.events` package. Three units + a demo.

### 1. `AsciiListRenderer` — pure, no I/O (the reusable core)

- No constructor args (no `Palette`).
- `String renderList(ListSnapshot snap, int highlightIndex)`:
  - a header line: `list: cap=<C> size=<S>`;
  - then one row of cells: `[ <cell0> | <cell1> | … ]`, where each cell is a
    `FilledSlot`'s element (via `String.valueOf`) or `·` for an `EmptySlot`.
    The cell at `highlightIndex` (−1 for none) is wrapped `>elem<`; every other
    cell is padded ` elem `. A capacity-0 list renders the row as `[]`.
- `String renderEvent(ListEvent e)` — the event's one-line label (delegating to
  `ListEventFormatter.format(e)`, DRY) then `renderList(e.after(), affectedIndex(e))`.
- `static int affectedIndex(ListEvent e)`:
  - `Append`/`Insert`/`Set`/`RemoveAt` → their `index()`;
  - `Shift` → `toIndex()` (where the element **landed** — so a shift burst walks the
    highlight across the row frame by frame);
  - `Grow` → −1 (whole-array change; no single slot).

### 2. `AsciiListVisualizer implements ListEventListener` — live driver

- Constructed with a `PrintStream` + `AsciiListRenderer` (plus a convenience
  constructor defaulting the renderer).
- `onEvent(ListEvent e)` prints `renderer.renderEvent(e)` then a blank line.
- Attached via `list.addListener(new AsciiListVisualizer(out))` — the "perform an
  operation, watch the array redraw" mode.

### 3. `AsciiListReplayer` — step-through / scrubber driver

- Constructed with a recorded `List<ListEvent>` (typically from a
  `ListRecordingListener`) and an `AsciiListRenderer`; `run(InputStream, PrintStream)`
  and `autoPlay(PrintStream, long delayMillis)` take their I/O injected for testing.
- Frames are independent (each event's `after()` is a full frame), so it is a real
  scrubber: **step forward** (Enter), **back** (`b`), **quit** (`q`), and a
  non-interactive **auto-play**. Displays a `── frame N/M ──` counter above each
  event's `renderEvent` output. Behaviourally identical to `AsciiReplayer`.

### 4. `ListVizDemo` — the visual demo

Same scripted story as `ListDemo` (appends → grow → insert → remove) but rendered
as live ASCII frames via `AsciiListVisualizer`. Runnable directly
(`java -cp target/classes com.gimlism.translucent.arraylist.demo.ListVizDemo`); the
`exec:java` default stays the HashMap `VizDemo` (unchanged).

## Data flow

- **Live:** `list.addListener(asciiListVisualizer)` → each mutation emits a
  `ListEvent` → `AsciiListVisualizer.onEvent` → `AsciiListRenderer.renderEvent` →
  `PrintStream`.
- **Replay:** attach a `ListRecordingListener`, run a scripted sequence, hand its
  `events()` to an `AsciiListReplayer` → step through frames, each rendered by the
  same `AsciiListRenderer`.

Both paths funnel through the one pure `AsciiListRenderer`, so live and replay output
are identical for the same event.

## Example frames (PLAIN, golden-test targets)

```
APPEND e @ 4
list: cap=6 size=5
[ a | b | c | d |>e<| · ]
```
```
SHIFT 3 -> 4 (d)
list: cap=6 size=6
[ a | b | c | c |>d<| e ]     (mid-slide: d copied to slot 4, source not yet overwritten)
```
```
GROW cap 4 -> 6
list: cap=6 size=4
[ a | b | c | d | · | · ]     (no slot highlighted; the whole array was copied)
```

## Testing strategy (TDD)

- **Renderer (golden strings):** hand-built `ListSnapshot`s exercise a filled+empty
  row, the `>x<` highlight, the no-highlight (−1) case, and the capacity-0 `[]` row,
  asserting exact ASCII. `renderEvent` asserts the one-line label precedes the row
  and that `affectedIndex` highlights correctly per event type (incl. `Grow` → none,
  `Shift` → destination).
- **Live visualizer:** attach to a real `TeachingArrayList`, run a scripted sequence
  (append → grow → insert → remove), assert the captured output contains the expected
  labels and row fragments.
- **Replayer:** feed a recorded event list plus scripted stdin (`\n`, `b`, `q`),
  assert forward stepping, back re-renders the prior frame, quit exits, and the
  `frame N/M` counter is correct; a separate test covers auto-play.

## Out of scope (future slices)

- The generic-substrate extraction (a shared `StructureReplayer`/drawing primitives
  unifying the two concrete visualizers) — now well-informed by two concrete
  renderers/replayers, but still deferred to its own slice.
- Web / JavaFX renderers, tween animation — same framing as the HashMap viz spec.
