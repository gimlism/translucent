# Trie Web Visualizer — Slice B (live SSE mirror)

**Date:** 2026-07-14
**Structure:** RadixTrie
**Slice:** B of the trie's 4-slice web-viz arc (A static → **B live SSE** → C REPL → D controls)
**Mirrors:** HashMap live viz (PR #18), ArrayList live viz (PR #22)
**Builds on:** Trie Slice A (PR #26) — the baked static replay

## Goal

Turn Slice A's offline buffer-then-bake replay into a **live connect-then-stream
mirror**: a running `RadixTrie` whose every mutation serializes to one frame and
pushes it over Server-Sent Events to the browser, which appends the frame and — by
default — follows the tail. A late-joining browser gets a snapshot-on-connect (the
server's cached last frame). This is the one-way (down) half of the live loop; the
REPL (C) and browser controls (D) add the upstream path in later slices.

The topology is already locked by the HashMap live-viz design and honored by the
ArrayList mirror: JDK `com.sun.net.httpserver.HttpServer` + SSE downstream,
virtual-thread executor with per-connection drop-oldest queues, snapshot-on-connect
cached in the server (keeps `LiveServer` structure-agnostic), and a
follow-tail-unless-scrubbed-back scrub UX with a "jump to live" button. Slice B
reuses all of that verbatim; the only new code is the trie's per-structure listener,
its `liveHtml` exporter variant, the `LIVE`-branch JS on the template, and a demo.

## Why live is simpler for the trie than for the list

The list's live path carried a cross-frame subtlety — the logical row "freezes" to
the nearest settled snapshot during a `Shift` burst (`settledLogical`). The trie has
no cross-frame state: every frame is a self-contained whole-tree snapshot, so the
existing `renderFrame` handles a live push with zero new rendering logic. Streaming
just calls the same function with a fresh snapshot.

The trie's traversal events (`Descend`, which advance the highlight without changing
structure) stream as frames that redraw with a moving highlight — so a live viewer
literally watches the walk down the shared prefix before a split/create.

## Scope

**In scope:** `TrieJsonSerializer.toFrame`; a stateless `TrieLiveVisualizer` listener;
`TrieWebExporter.liveHtml`; the `LIVE`-flag JS branch (EventSource + follow-tail +
jump-to-live badge) on `trie-viz.html`; a `TrieLiveWebVizDemo` student sandbox.

**Out of scope (later slices):** the upstream command path — terminal REPL (Slice C)
and browser controls (Slice D). The `CONTROLS` token stays baked `false`; no command
box, no `POST /command`. `LiveServer`, `JsonWriter`, `BrowserLauncher`, and
`DemoLifecycle` are reused unchanged.

## Architecture

Reuses the live-viz substrate verbatim. New/edited pieces are per-structure only.

| File | Status | Role |
|---|---|---|
| `trie/viz/TrieJsonSerializer.java` | edit | Add `toFrame(TrieEvent) → String` — one event → a single `{event,trie}` frame (no `frames` wrapper). Wraps the existing private `writeFrame` helper; Slice A deferred this per YAGNI. |
| `trie/viz/TrieLiveVisualizer.java` | new | Live twin of `TrieRecordingListener`: `implements TrieEventListener`; `onEvent(e)` calls `sink.accept(TrieJsonSerializer.toFrame(e))`. Stateless; the server owns snapshot-on-connect. Byte-mirror of `ListLiveVisualizer`. |
| `trie/viz/TrieWebExporter.java` | edit | Add `liveHtml()` → `WebVizTemplate.inject(TEMPLATE_RESOURCE, "null", "true", "false")` (DATA=null, LIVE on, CONTROLS off). |
| `resources/web/trie-viz.html` | edit | Wire the `LIVE` flag: `liveBtn` badge element, `followTail` state, `refreshMeta` behind-count, and the `if (LIVE) { EventSource … }` branch. No `CONTROLS` branch. |
| `trie/demo/TrieLiveWebVizDemo.java` | new | Student sandbox: a live `RadixTrie` on a `LiveServer`, a TODO block with the shore/she/shell insert-then-remove sample story, then `DemoLifecycle.awaitShutdown`. Byte-mirror of `ListLiveWebVizDemo`. |

**Untouched:** `trie/core/**`, `trie/events/**`, `substrate/viz/LiveServer`,
`substrate/viz/JsonWriter`, `substrate/viz/WebVizTemplate`. No event-emitting or
snapshot code changes → the snapshot-before-settled bug keeps **zero surface**.

## Data flow

```
RadixTrie mutation
  → TrieEvent
  → TrieLiveVisualizer.onEvent  →  TrieJsonSerializer.toFrame(e)   [one {event,trie} frame]
  → LiveServer.broadcast(frameJson)  →  SSE "data:" line to each open connection
  → browser: frames.push(f); followTail ? go(tail) : refreshMeta()  [renderFrame redraws SVG]
```

A browser connecting mid-run receives the server's cached last frame first
(snapshot-on-connect), then the live stream.

## Template edits — `trie-viz.html`

Slice A wired FRAMES + scrubber only. Slice B adds, mirroring list-viz.html's
Slice-B additions over its Slice A:

- A `liveBtn` element (`⏭ N new — jump to live`), hidden by default.
- `followTail` state; `go(n)` sets `followTail = idx >= frames.length - 1`.
- `refreshMeta` computes `behind = max(0, frames.length - 1 - idx)` and shows the
  badge only when `LIVE && behind > 0`.
- `if (LIVE) { const es = new EventSource("/events"); es.onmessage = ev => { parse;
  frames.push(f); if (followTail) go(tail) else refreshMeta(); } }`.
- `liveBtn.onclick` → jump to the tail and resume follow.
- The `CONTROLS` flag stays baked `false`; no command box, no `fetch`/`POST`.

The `renderFrame`/`layout` SVG logic from Slice A is unchanged.

## Error handling

- Serialization is a pure read of the event's snapshot; `toFrame` cannot mutate the
  trie and does not throw (mirrors the `TrieEventListener` contract that `onEvent`
  must not mutate). The listener adds no `try/catch` — a swallow would hide a
  mid-mutation bug (the same reasoning that kept `MapLiveVisualizer`/`ListLiveVisualizer`
  catch-free).
- `WebVizTemplate.inject` already requires all three tokens and throws on a missing
  token or absent resource; `liveHtml` inherits this. No new error paths.
- SSE reconnect duplicating a frame is a documented, benign single-page limitation
  carried over from the HashMap/ArrayList slices; not addressed here.

## Testing

Java tests mirror the ArrayList Slice B set; the live JS is untested-by-design and
controller-browser-verified.

- **`TrieJsonSerializerTest`** (edit) — add a `toFrame` case: one event → a single
  `{event,trie}` frame with the same field shape as a `toJson` frame but no `frames`
  wrapper.
- **`TrieLiveVisualizerTest`** (new) — `onEvent` forwards `TrieJsonSerializer.toFrame`
  to the sink (capture into a list and assert the frame JSON); it neither mutates the
  trie nor throws.
- **`TrieWebExporterLiveTest`** (new) — `liveHtml()` bakes `const DATA = null;`,
  `const LIVE = true;`, `const CONTROLS = false;`; no `/*__…__*/` token survives.
- **`TrieLiveVizEndToEndTest`** (new) — headless: a real `LiveServer` on
  `127.0.0.1:0`, connect an SSE reader, perform a real trie mutation, and assert a
  `data:` frame arrives and parses as a trie frame. Use try-with-resources on the
  `HttpClient`/response stream.
- **Browser verification** (controller, per the live-viz recipe): serve a headless
  demo/timed driver over `http://localhost:7070`, connect via Chrome MCP mid-stream,
  and confirm: the frame counter climbs live as keys are put; the tree redraws per
  frame (including `Descend` walk-highlight frames); snapshot-on-connect for a late
  joiner; scrub-back shows the "jump to live" badge with the correct behind-count and
  clicking it resumes follow-tail; zero console errors.

## Risk to verify, not assume

The ArrayList e2e tripped on the lazy `Grow`-before-`Append` first frame. For the
trie, Slice A already **confirmed** the first emitted event on `put` into an empty
trie is `CreateNode` (no prefix to descend, no edge to split, so the node is created
before `Put` marks the key). The e2e will therefore not assume a `Put`-first frame —
it reads until the frame it asserts on, so the leading `CreateNode` cannot break it.

## Reuse notes

- `LiveServer`, `JsonWriter`, `BrowserLauncher`, `DemoLifecycle` are reused verbatim —
  the trie adds no transport and no new substrate.
- After Slice B, the `LIVE` token on `trie-viz.html` is wired; Slice C (REPL) adds a
  `TrieCommandInterpreter` (+ the deferred `CommandResult → substrate/repl` dedup,
  the rule-of-three moment), and Slice D flips the already-present `CONTROLS` token on.
