# TreeSet Web Visualizer — Slice B (live SSE mirror)

**Date:** 2026-07-18
**Structure:** TeachingTreeSet (red-black tree)
**Slice:** B of the TreeSet's 4-slice web-viz arc (A static → **B live SSE** → C REPL → D browser controls)
**Mirrors:** Trie web viz Slice B (PR #27), HashMap live web viz (PR #18), ArrayList live web viz (PR #22)

## Goal

Turn Slice A's offline baked replay into a live connect-then-stream mirror: a running
`TeachingTreeSet` broadcasts each event as it happens, and a connected browser renders the
red-black tree changing in real time. This adds **transport only** — no new rendering. The
Slice A SVG renderer (`treeset-viz.html`) is reused untouched; Slice B re-enables the page's
dormant `LIVE` branch (Server-Sent Events) and adds a stateless broadcasting listener plus a
student-sandbox demo.

## Scope

**In scope:** a `TreeSetLiveVisualizer` (a `SetEventListener` that serializes each event to one
frame and hands it to a sink); `TreeSetWebExporter.liveHtml()`; the `LIVE` EventSource /
follow-tail / jump-to-live branch on `treeset-viz.html` (re-adding the machinery Slice A
stripped); a `TreeSetLiveWebVizDemo` student sandbox; a headless SSE end-to-end test.

**Out of scope (later slices):** terminal REPL (Slice C), browser command controls (Slice D).
The template's `CONTROLS` token stays baked `false` and its command-box branch is **not** added
this slice.

## Architecture

Reuses the established live-viz seam. The generic transport is reused verbatim; the only
per-structure additions are the listener, the exporter factory, the page's LIVE branch, and the
demo.

| File | Status | Role |
|---|---|---|
| `treeset/viz/TreeSetLiveVisualizer.java` | new | Live twin of `SetRecordingListener`: instead of buffering, `onEvent(e)` calls `sink.accept(TreeSetJsonSerializer.toFrame(e))`. Stateless (the server owns snapshot-on-connect). Byte-mirror of `TrieLiveVisualizer`. Per the `SetEventListener` contract it neither mutates nor throws — serialization is a pure read of the event's snapshot. **Deliberately no try/catch** (a catch-all would silently swallow a mid-mutation bug). |
| `treeset/viz/TreeSetWebExporter.java` | edit | Add `liveHtml()` = `WebVizTemplate.inject(TEMPLATE_RESOURCE, "null", "true", "false")` — `DATA=null`, `LIVE=true`, `CONTROLS=false`. `toHtml`/`writeHtml` unchanged; no `controlsHtml` yet (Slice D). |
| `resources/web/treeset-viz.html` | edit | Re-add the live machinery Slice A stripped: a hidden `#live` jump-to-live button in the bar; `followTail` state; `go()` sets `followTail = idx >= frames.length - 1`; `refreshMeta` computes the behind-count and toggles/labels the badge; `liveBtn.onclick` snaps to the tail; and the `if (LIVE) { const es = new EventSource("/events"); es.onmessage = … frames.push(f); followTail ? go(tail) : refreshMeta(); }` block. **No `if (CONTROLS)` command-box branch** (Slice D). The SVG renderer, layout, and CSS are untouched. |
| `treeset/demo/TreeSetLiveWebVizDemo.java` | new | Student sandbox: a `LiveServer` serving `liveHtml()`, a `TeachingTreeSet` wired to a `TreeSetLiveVisualizer(server::broadcast)`, `BrowserLauncher.open`, a scripted mutation block, and `DemoLifecycle.awaitShutdown(server)`. Byte-mirror of `TrieLiveWebVizDemo`. |

**Reused verbatim (untouched):** `substrate/viz/LiveServer` (SSE server, virtual-thread executor,
per-connection drop-oldest queues, snapshot-on-connect), `JsonWriter`, `BrowserLauncher`,
`DemoLifecycle`; and `treeset/core`, `treeset/events`, `substrate/rbtree`. `TreeSetJsonSerializer.toFrame`
already shipped (dormant) in Slice A. Because no event-emitting or snapshot code changes, the
recurring **snapshot-before-settled** bug has zero surface this slice.

## Data flow

```
TeachingTreeSet mutation (or read)
   → SetEvent
   → TreeSetLiveVisualizer.onEvent(e)  →  TreeSetJsonSerializer.toFrame(e)
   → LiveServer.broadcast(frameJson)   [caches it as the snapshot-on-connect frame]
   → SSE: data: <frame>\n\n  to every open /events connection
   → browser: frames.push(f); follow-tail → full render + cross-fade, else jump-to-live badge
```

## Reads narrate — live (zero sibling precedent)

Unlike map/list/trie, the TreeSet emits `Compare` events on **reads** (`contains`, the relative
navigators). `TreeSetLiveVisualizer` broadcasts every event, so with a browser connected a
`set.contains(25)` **animates the comparison walk live** and the resting frame becomes a
`compare …` frame. This is the correct, deliberate default: it is consistent with Slice A (which
already renders the `Compare` frames produced by add-walks) and is the reads-narrate feature
paying off. Filtering reads out of the live stream would be *inventing* behavior; we do not. The
sandbox demo showcases it (a `contains` after the inserts).

## Error handling

`TreeSetLiveVisualizer` neither catches nor throws — serialization is pure and the sink
(`LiveServer.broadcast`) does not throw (it enqueues to per-connection drop-oldest queues). The
`SetEventListener` mid-operation contract (no mutation, no throw) is honored. The live JavaScript
is untested-by-design (a dumb renderer) and controller-browser-verified.

## Testing

- **`TreeSetWebExporterLiveTest`** (or added cases to the exporter test): `liveHtml()` bakes
  `DATA=null`, `LIVE=true`, `CONTROLS=false`, substitutes all tokens, and produces a
  self-contained page with an `EventSource("/events")` branch but **no** command box.
- **`TreeSetLiveVisualizerTest`**: a real `TeachingTreeSet` + a capturing `Consumer<String>` sink;
  a mutation broadcasts exactly one frame equal to `TreeSetJsonSerializer.toFrame(event)`; a read
  (`contains`) also broadcasts (reads narrate).
- **Headless SSE end-to-end test** (the one testable transport surface): start a `LiveServer` with
  `liveHtml()`, connect an `HttpClient` to `/events`, drive a real mutation through a
  `TreeSetLiveVisualizer(server::broadcast)`, and assert the `data:` frame arrives; also assert
  snapshot-on-connect (a late connection receives the last broadcast frame). **Two traps to avoid
  (called out in the plan):**
  1. **Couple `HttpClient` close with SSE-reader close** — wrap the SSE `BufferedReader` in
     try-with-resources; closing the client while `/events` is still open *hangs the suite* (this
     project's PR #30 history). The client and reader closes are coupled.
  2. **The first frame of an insert into an empty set is `Add`, not `CreateNode`** — do **not**
     mirror the trie e2e literally (the trie reads past a leading `CreateNode`; the TreeSet has
     none — every node *is* an element, so `Add` is the node creation, a named constraint from the
     core plan). Assert on `Add`, not a frame that never arrives.
- **Controller browser verification** (not a unit test): run the live demo (or a throwaway driver)
  headless over `http://localhost:PORT` against `target/classes` after `mvn process-classes`;
  **connect during streaming** — a parked driver that sleeps between operations — and confirm the
  frame counter **climbs live on one connection with no reload** (the incremental push, the novel
  half), including a `contains` walk animating live; snapshot-on-connect on reload; scrub-back →
  "⏭ N new — jump to live" badge with the correct behind-count → click resumes follow-tail; zero
  console errors. Connecting only *after* the run would exercise snapshot-on-connect but never the
  live push — connect during.

## Deferred / follow-ups

- Slice C (terminal REPL): a `TreeSetCommandInterpreter` (pure `execute(line, set) → CommandResult`)
  + a live REPL demo; the `CommandResult` → `substrate/repl` dedup is the rule-of-three moment
  (map + list + trie already copied it; the TreeSet REPL would be the… fourth — already deduped).
- Slice D (browser controls): POST `/command` + flip the `CONTROLS` token on; reuse the interpreter.
