# Generic Instrumentation Substrate — Extraction — Design

**Status:** Draft 2026-07-04
**Builds on:** the two shipped concrete structures — `TeachingHashMap` (PRs #1–#7)
and `TeachingArrayList` (PR #8). Both independently evolved a near-identical
*transport* (event listener → recorder → replayer / live visualizer). With two
concrete examples in hand, the shared seams are now real, so this slice extracts
them — the extraction the roadmap deliberately deferred until a second structure.

## Goal

Extract the **transport** shared by every instrumented teaching structure into one
reusable substrate, so a third structure (TreeSet, Trie, …) inherits recording,
replay, and live visualization for free — implementing only its own concrete event
vocabulary and a `renderEvent` function. No behaviour changes; the 143 existing
tests are the safety net and must stay green.

## Scope (resolved)

| Question | Decision |
|---|---|
| What generalizes | **Transport only:** the event listener, the recorder, the replayer, the live visualizer, and the one `EventRenderer<E>` seam they share. |
| Drawing | **Not unified.** The map renders a *vertical* indexed bucket list (sideways trees); the list a *horizontal* cells row. Same "cells" idea, genuinely different layouts — a shared drawing primitive would be an artificial merge. Each renderer stays concrete and simply *implements* `EventRenderer<E>`. |
| `StructureEvent`/`StructureSnapshot` | **Introduced** (for conceptual symmetry). Pure supertypes: `MapEvent`/`ListEvent extends StructureEvent`; `MapSnapshot`/`ListSnapshot implements StructureSnapshot`. No generic code strictly requires them today — they document the model and let the transport bound `<E extends StructureEvent>`. |
| Event vocabulary | **Stays concrete + sealed** per structure (`MapEvent` permits Put/…; `ListEvent` permits Append/…). The teaching value lives here; it is never generalized. |
| Formatters | **Stay per-structure** (`EventFormatter`, `ListEventFormatter`) — one-line labels are a concrete-vocabulary concern; the generic renderer/replayer never touch them directly. |
| Migration style | **Preserve call sites.** Named per-structure classes remain as thin aliases/subclasses of the generic types, so existing code and tests compile unchanged. |

## Architecture

New package `com.gimlism.translucent.substrate`, split into `events` (pure model +
listener/recorder) and `viz` (renderer seam + drivers). Neither `hashmap.*` nor
`arraylist.*` depends on the other; both depend on `substrate`.

### `substrate.events` — the shared model + transport

```java
/** Marker supertype of every structure's whole-state snapshot. */
public interface StructureSnapshot {}

/** Marker supertype of every structure's events; each carries a snapshot. */
public interface StructureEvent {
    StructureSnapshot after();
}

/** Synchronous consumer of a structure's event stream. */
@FunctionalInterface
public interface StructureEventListener<E extends StructureEvent> {
    void onEvent(E event);
}

/** Collects events in emission order. Backbone of sequence tests and replay. */
public class RecordingListener<E extends StructureEvent>
        implements StructureEventListener<E> {
    // add on each event; events() unmodifiable view; clear()
}
```

`StructureSnapshot` is a **pure marker** — deliberately no `capacity()`/`size()`
accessors, even though both current snapshots happen to have them, so it does not
bake array-shaped assumptions a future Trie snapshot could not satisfy.

`StructureEvent.after()` returns `StructureSnapshot`; each concrete event narrows it
covariantly (`MapEvent.after()` returns `MapSnapshot`), which Java permits.

### `substrate.viz` — the renderer seam + drivers

```java
/** The one seam a structure supplies: an event → ASCII frame function. */
@FunctionalInterface
public interface EventRenderer<E extends StructureEvent> {
    String renderEvent(E event);
}

/** Live consumer: prints a rendered frame per event. */
public class Visualizer<E extends StructureEvent> implements StructureEventListener<E> {
    Visualizer(PrintStream out, EventRenderer<E> renderer);
    // onEvent: println(renderer.renderEvent(event)); println();
}

/** Step-through scrubber over a recorded stream (each snapshot is a full frame). */
public class Replayer<E extends StructureEvent> {
    Replayer(List<E> events, EventRenderer<E> renderer);
    void run(InputStream in, PrintStream out);      // Enter=next, b=back, q=quit
    void autoPlay(PrintStream out, long delayMillis);
    // frame N/M counter, identical behaviour to today's AsciiReplayer
}
```

### Per-structure wiring (thin, call-site-preserving)

- `MapSnapshot implements StructureSnapshot`; `ListSnapshot implements StructureSnapshot`.
- `MapEvent extends StructureEvent` (covariant `MapSnapshot after()`); `ListEvent`
  likewise.
- `MapEventListener extends StructureEventListener<MapEvent>` (kept as a named
  functional alias); `ListEventListener` likewise. Existing `implements
  MapEventListener` classes and lambdas are unaffected.
- `TeachingHashMap`/`TeachingArrayList`: the internal listener list and
  `addListener`/`removeListener` widen from the named alias to
  `StructureEventListener<MapEvent>` / `<ListEvent>`, so a generic
  `RecordingListener<E>`/`Visualizer<E>` can be registered. (Both aliases are
  subtypes, so nothing existing breaks.)
- `consumer.RecordingListener extends substrate.events.RecordingListener<MapEvent>`;
  `arraylist.consumer.ListRecordingListener extends …RecordingListener<ListEvent>`
  — empty subclasses preserving `new RecordingListener()` call sites.
- `AsciiRenderer implements EventRenderer<MapEvent>`; `AsciiListRenderer implements
  EventRenderer<ListEvent>` (both already have `renderEvent`).
- `AsciiVisualizer extends Visualizer<MapEvent>` / `AsciiReplayer extends
  Replayer<MapEvent>` (and the two list counterparts) — thin subclasses holding only
  the constructors (including `AsciiVisualizer(PrintStream)` defaulting
  `new AsciiRenderer(Palette.auto())`, and the list's defaulting `new
  AsciiListRenderer()`). All run/autoPlay/onEvent logic moves into the generic base;
  the subclasses exist purely for the ergonomic default-renderer constructor and to
  keep every existing call site (`new AsciiVisualizer(out)`, `new AsciiReplayer(…)`)
  compiling unchanged.

The net code change is a **large deletion of duplicated logic** (two recorders, two
replayers, two visualizers collapse to one each) behind an unchanged public surface.

## What is explicitly NOT done

- No shared drawing primitive / renderer base (divergent layouts).
- No generalization of the sealed event vocabularies or the snapshot record shapes.
- No new capability — this is a pure structural extraction. A future structure gets
  the substrate for free, but none is added here.

## Testing strategy (TDD)

The **143 existing tests are the primary safety net** — they exercise every concrete
recorder/replayer/visualizer/listener path and must stay green throughout (each task
re-runs them). New tests prove the extraction itself:

- **Model polymorphism:** a `MapEvent` is a `StructureEvent` and its `after()` is a
  `StructureSnapshot`; likewise `ListEvent`. A `RecordingListener<MapEvent>` registers
  on a `TeachingHashMap` and records; a `RecordingListener<ListEvent>` on a
  `TeachingArrayList`.
- **One substrate, both structures (the capstone):** a single generic helper
  `<E extends StructureEvent> String replay(List<E>, EventRenderer<E>)` (or a
  `Replayer<E>` / `Visualizer<E>` used directly) drives *both* a recorded map stream
  and a recorded list stream, asserting frames render for each — proving the transport
  is structure-agnostic.
- **Generic drivers:** `Replayer<E>` stepping/back/quit + auto-play and `Visualizer<E>`
  live output, tested once at the generic level (the existing Ascii* tests continue to
  cover the concrete subclasses).

## Out of scope (future)

- A third concrete structure (TreeSet / Trie) — the first *consumer* of this
  substrate, and the real proof it paid off.
- Any drawing-layer unification, should a future structure share the map's or list's
  layout closely enough to warrant it (revisit then, with three examples).
