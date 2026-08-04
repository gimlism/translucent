# Teaching HashMap — ASCII Visualizer — Design

**Status:** Approved 2026-07-03
**Builds on:** the completed data structure (Slices 1–3, merged) — the event
stream (`MapEvent`) and immutable `MapSnapshot` model are the fixed contract this
consumes. See the Slice 1–3 design specs.

## Goal

Render the teaching HashMap's event stream visually in the terminal, so learners
watch the structure evolve — collisions, treeify, rotations, resize, deletion,
untreeify — as ASCII diagrams. This is the first of several planned **pluggable
visualizers** (web and JavaFX to follow); it is the simplest and establishes the
reusable rendering seam.

## Pluggability principle

Every visualizer is a consumer of the existing, stable contract: the
`MapEvent`/`MapSnapshot` model, delivered via `MapEventListener`
(`ConsoleEventLogger` and `RecordingListener` are already such consumers). We do
NOT introduce a speculative cross-visualizer "Visualizer" super-interface for the
not-yet-built web/JavaFX renderers (YAGNI) — the shared contract already exists.
What later visualizers reuse is the layout thinking and the snapshot model, not a
premature abstraction. The one seam we establish now is a **pure renderer** (a
`MapSnapshot → text` function with no I/O), kept separate from the code that
drives it, so it is testable and so the "what to draw" logic never tangles with
"when to draw."

## Resolved decisions

| Question | Decision |
|---|---|
| Drive modes | **Both** a live `MapEventListener` and a step-through replay driver. |
| Colour | **ANSI with a plain-text fallback** (`(R)`/`(B)` markers); auto-detect no-TTY → plain, explicit override available. |
| Tree layout | **Sideways** (rotated): right-subtree above, node, left-subtree below; indent = depth; `┌─`/`└─` connectors. |
| Replay control | **Forward/back scrubber** (every event carries a full snapshot, so frames are independent) plus auto-play. |

## Architecture

New package `com.gimlism.translucent.hashmap.viz`. Depends on the `events`
package (snapshot + event records) and reuses `consumer.ConsoleEventLogger`'s
one-line event formatter. Three units:

### 1. `AsciiRenderer` — pure, no I/O (the reusable core)

- Constructed with a `Palette` (colour mode).
- `String renderMap(MapSnapshot snap, int highlightBucket)` — renders the whole
  map: a header line (e.g. `cap=16 size=7 threshold=12`), then each bucket by
  index, rendered as:
  - **empty:** `[i] ·` (or similar empty marker);
  - **chain:** `[i] k=v -> k=v -> …` (insertion order);
  - **tree:** `[i] (tree)` followed by the sideways ASCII tree.
  `highlightBucket` (−1 for none) marks the changed bucket so the eye lands on
  it.
- `String renderEvent(MapEvent e)` — the event's one-line label (delegating to
  `ConsoleEventLogger.format(e)`, DRY) followed by `renderMap(e.after(),
  affectedBucket(e))`, where `affectedBucket` returns the event's `bucketIndex`
  (Put/Remove/Collision/Treeify/Untreeify/Rotation/Recolor) or −1 for `Resize`.
- **Sideways tree rendering:** recursively render `node.right` (depth+1), then the
  node line, then `node.left` (depth+1). Connectors: `┌─ ` for a right child,
  `└─ ` for a left child, and a plain prefix for the root. Each node line shows
  the key with its colour via the `Palette`. Exact indentation and spacing are
  pinned by golden-string tests.

### 2. `Palette` — colour abstraction

- Two modes: `PLAIN` (annotate nodes as `key(R)` / `key(B)` — no escapes,
  test-friendly, pipe-safe) and `ANSI` (wrap RED keys in the ANSI red escape,
  leave BLACK in the terminal default).
- A factory chooses PLAIN when the output is not a TTY (`System.console() ==
  null`) unless a mode is explicitly forced. Keeps captured/piped output and
  tests colour-agnostic by default.

### 3. `AsciiVisualizer implements MapEventListener` — live driver

- Constructed with a `PrintStream` and an `AsciiRenderer`.
- `onEvent(MapEvent e)` prints `renderer.renderEvent(e)` to the stream.
- Attached via `map.addListener(new AsciiVisualizer(out, renderer))` — the
  "perform an operation, watch the map redraw" mode.

### 4. `AsciiReplayer` — step-through / scrubber driver

- Constructed with a recorded `List<MapEvent>` (typically from a
  `RecordingListener`), an `AsciiRenderer`, an input source (`Readable`/`Scanner`
  over an `InputStream`) and a `PrintStream` — both injectable for testing.
- Frames are independent (each event's `after()` snapshot is a full frame), so it
  is a real scrubber:
  - **step forward** (Enter) — advance to the next frame;
  - **back** (`b`) — re-render the previous frame;
  - **quit** (`q`);
  - **auto-play** — a non-interactive mode that renders all frames with an
    optional delay between them.
- Displays a `frame N/M` counter and the current event's `renderEvent` output.

## Data flow

- **Live:** `map.addListener(asciiVisualizer)` → each mutation emits an event →
  `AsciiVisualizer.onEvent` → `AsciiRenderer.renderEvent` → `PrintStream`.
- **Replay:** attach a `RecordingListener`, run a scripted sequence, then hand its
  `events()` to an `AsciiReplayer` → the user (or the auto-play loop) steps
  through frames, each rendered by the same `AsciiRenderer`.

Both paths funnel through the one pure `AsciiRenderer`, so live and replay output
are identical for the same event.

## Testing strategy (TDD)

- **Renderer (golden strings):** hand-built `MapSnapshot`s exercise each bucket
  kind — empty, a chain, and a sideways red-black tree — asserting the exact ASCII
  in `PLAIN` mode. A separate test asserts `ANSI` mode wraps RED keys in the
  escape and leaves BLACK unwrapped. A `highlightBucket` test asserts the marker
  lands on the right bucket.
- **`renderEvent`:** asserts the one-line label (from `ConsoleEventLogger.format`)
  precedes the map diagram, and that `affectedBucket` highlights correctly per
  event type (incl. `Resize` → no single highlight).
- **Live visualizer:** attach to a real `TeachingHashMap`, run a scripted
  sequence (collision → treeify → resize → remove → untreeify), assert the
  captured output contains the expected frames/labels.
- **Replayer:** feed a recorded event list plus scripted stdin (`\n`, `b`, `q`),
  assert forward stepping, back-stepping re-renders the prior frame, quit exits,
  and the `frame N/M` counter is correct; a separate test covers auto-play
  rendering all frames non-interactively.
- **Palette:** `PLAIN` produces no escape sequences; `ANSI` wraps as expected; the
  no-TTY factory defaults to `PLAIN`.

## Out of scope (future visualizer slices)

- **Web visualizer:** a Java→JSON exporter for the event stream (the contract
  spike already proved the model serializes cleanly) plus a self-contained
  HTML/SVG page that replays and animates it.
- **JavaFX visualizer:** an in-process desktop app rendering events live to a
  scene graph.
- Smooth tween animation (node movement on resize, morphing rotations) — the
  ASCII version is frame-by-frame by nature; motion is a web/JavaFX concern.

All three future directions consume the same `MapEvent`/`MapSnapshot` contract
this slice renders.
