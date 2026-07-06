# Live Web Visualizer — HashMap — Design

**Status:** Draft 2026-07-06
**Builds on:** the merged HashMap web replay (PR #17) — its `MapEvent`/`MapSnapshot`
contract, `MapJsonSerializer`, the `map-viz.html` dumb renderer, and `MapWebExporter`.
This slice turns that **offline, buffer-then-bake** pipeline into a **live, connect-then-stream**
one, without discarding the offline path.

## Goal

Let a running JVM push each `TeachingHashMap` mutation to a browser **as it happens**, so the
SVG renderer updates live instead of replaying a pre-recorded file. The first concrete payoff
is a **student stub app**: a runnable class where the map and a live visualizer are already
wired up, with a `// TODO: your mutations here` block — the student writes ordinary Java,
runs it, and watches their own `put`/`remove` render live in the browser.

## The full loop, sequenced (only Slice 1 is in scope here)

The user's end goal is the full loop: teacher-driven view, student-written code, and
browser-side controls. These decompose into three slices, each independently shippable and
each reusing the last. **This spec covers Slice 1 only**; slices 2–3 are recorded so the
Slice-1 transport choices read as deliberate groundwork, not accident.

| Slice | Delivers | New transport | Builds on |
|---|---|---|---|
| **1 — Live mirror (this spec)** | JVM streams events → browser renders live; **plain `main()` TODO stub** demo | SSE **downstream** only | PR #17 renderer + pipeline |
| **2 — Command layer + REPL** | A `parse("put 3 x") → apply` command core, driven from a **terminal REPL**; renders live via Slice 1's SSE | none (terminal in, SSE out) | Slice 1 |
| **3 — Browser controls** | Buttons/keys in the browser POST commands back to the JVM | `fetch()` POST **upstream** | Slice 2's parser + Slice 1's SSE |

Key realization behind the ordering: teacher-driven and student-written code are the *same*
foundation — a `main()` that mutates the map with a live listener attached — so **Slice 1
already delivers the sandbox scenario**. The REPL (Slice 2) needs no new transport (terminal
in, existing SSE out); browser controls (Slice 3) reuse Slice 2's parser and only add the
upstream POST. The command parser is shared by the REPL and the browser, which is why they are
adjacent slices.

## Why the offline seam can't just be reused

PR #17's pipeline is one-shot: `MapRecordingListener` buffers the **entire** event stream,
`MapJsonSerializer.toJson(list)` bakes it into one JSON blob, `MapWebExporter` injects it into
`map-viz.html`, and the browser opens a finished file. The load-bearing assumption is that
*the stream is complete before the browser ever sees it*. Live breaks exactly that: frames
arrive one at a time, after the browser has connected, for an open-ended session. So we add a
**streaming** path alongside the baked one — same renderer, different frame source.

## Resolved decisions

| Question | Decision |
|---|---|
| Transport | **JDK built-in `com.sun.net.httpserver.HttpServer`** (module `jdk.httpserver`, classpath-accessible in a plain Maven build — no external dependency, no `--add-modules`). |
| Downstream (JVM → browser) | **Server-Sent Events** (`text/event-stream`): the server writes one `data: {frame-json}\n\n` per mutation and flushes. Native `EventSource` in the browser; auto-reconnects for free. |
| Upstream (browser → JVM) | **Deferred to Slice 3** — plain `fetch()` POST to the same server. Chosen now so the server design doesn't need reworking later. |
| Why not WebSocket | The JDK ships a WebSocket *client* but **no server**, so WS would cost a third-party dependency and buy nothing SSE-down + POST-up doesn't. Rejected on the zero-dep ethos. |
| Zero new dependencies | Preserved. Server, SSE framing, and JSON are all hand-rolled / JDK-only, matching the rest of the project. |
| One template or two | **One template, two data sources.** `map-viz.html` gains a live mode: if served with a live flag it opens `new EventSource('/events')` and feeds each frame into the *same* `applyFrame()`; otherwise it reads the baked `/*__FRAMES__*/` array as today. The offline exporter is untouched. |
| Late-joiner / reconnect | **Snapshot-on-connect.** SSE only delivers events *after* connection, and `EventSource` auto-reconnects on drop. Because every `MapEvent` already carries a full whole-structure snapshot, the server sends the **current snapshot as frame 0** to every new/reconnecting connection — so a mid-session or reconnecting browser is never blank, and multi-viewer works with no extra machinery. |
| Live scrub UX | **Follow-tail unless scrubbed back.** Auto-advance to the newest frame as mutations arrive, *unless* the viewer has scrubbed to an earlier frame — then hold position and show a `N new — jump to live` affordance. |
| Binding | **`127.0.0.1` by default**, bind address is a constructor arg → LAN/classroom (`0.0.0.0`) is a config flip, not a redesign. Threading is multi-viewer-ready from the start. |
| Port | **Configurable; default a fixed friendly port** (predictable teaching URL). If that port is occupied, fall back to an **OS-assigned free port** and print the actual URL. (`port 0` = always ephemeral.) |
| Open browser | **Default on** via `java.awt.Desktop.browse`, with a graceful fallback to just printing the URL when headless/unavailable; suppressible via a flag. |
| Lifecycle | The student's `main()` mutations run and finish, but the **server keeps the JVM alive** so the final state stays live/scrubbable. Clean shutdown on Ctrl-C; a `stop()` for tests. This is the real behavioral change from today's mutate-and-exit demos. |
| Where intelligence lives | Unchanged from PR #17: **all layout/classification/highlight logic is pre-computed in the Java serializer**; the JS stays a dumb renderer of frames. Live mode only changes where frames *come from*, not what a frame *is*. |

## Threading (the #1 landmine)

`HttpServer`'s default executor is **serial (single-threaded)**. A held-open SSE response on
it starves the server — the next viewer's page GET (and every future request) blocks behind
the first open stream. It is silent until the *second* connection. Therefore `LiveServer`
**must** call `server.setExecutor(pool)` with a bounded thread pool; **each open SSE connection
holds one thread** for its lifetime. This is designed in from the start, not retrofitted.

## Components

Generic transport lives in `substrate/viz` (reusable by the list/trie live front-ends later);
HashMap specifics stay in `hashmap/viz`, mirroring the existing seam.

| Component | Package | Responsibility | Tested |
|---|---|---|---|
| `LiveServer` | `substrate/viz` | Wrap `HttpServer`. Serve the page at `/`; hold SSE connections at `/events`; own the thread pool + connection registry; `broadcast(String frameJson)` to all connections; **snapshot-on-connect** (send a supplied frame 0 to each new connection); `start(bindAddr, port)` / `stop()`; keep the JVM alive. **Map-agnostic.** | ✅ unit (JDK `HttpClient`) |
| `MapLiveVisualizer` | `hashmap/viz` | A `MapEventListener` (the live twin of `MapRecordingListener`). On each event, serialize **one** frame and `server.broadcast(...)`. Also supplies the current snapshot frame for snapshot-on-connect. | ✅ unit |
| `MapJsonSerializer` | `hashmap/viz` | **Refactor:** extract a per-event `toFrame(MapEvent)` that emits a single frame object; `toJson(list)` becomes "join many `toFrame`s into the `frames` array." Live calls `toFrame` per event; baked path unchanged in output. | ✅ unit |
| `map-viz.html` template | `src/main/resources/web/` | Add live mode: open `EventSource('/events')`, feed frames into the existing `applyFrame()`, follow-tail + `jump to live`. Baked mode unchanged. | ⛔ inspection |
| `LiveWebVizDemo` (stub) | `hashmap/demo` | The **student stub**: map + `MapLiveVisualizer` + `LiveServer` pre-wired, a marked `// TODO: your mutations here` block, browser auto-opens. Run via `mvn exec:java -Dexec.mainClass=…demo.LiveWebVizDemo`. | ⛔ demo |

## Data flow (Slice 1)

```
student's main():  map.put(3, "x")
  → TeachingHashMap emits MapEvent + full MapSnapshot (state already settled)
  → MapLiveVisualizer.onEvent(e)  → MapJsonSerializer.toFrame(e)   (one frame)
  → LiveServer.broadcast(json)    → every open SSE connection
  → browser EventSource.onmessage → applyFrame(json) → SVG updates (follow-tail)

new browser connects mid-session:
  → LiveServer sends the latest snapshot as frame 0 → viewer sees current state immediately
```

## Front-end (delta over PR #17)

- **New:** a live mode that opens `new EventSource('/events')` and calls the *existing*
  `applyFrame(frame)` on each message — the renderer, layout, highlight, and cross-fade are all
  reused verbatim.
- **New:** follow-tail logic — append arriving frames to the timeline; auto-advance to the
  newest **unless** the viewer has scrubbed back, in which case hold and show
  `N new — jump to live`.
- **Unchanged:** SVG layout, colour tween, the scrubber controls, the caption/highlight model.
  A "frame" is byte-identical to the baked path's frame; only its arrival differs.

## Event-frame check (recurring bug pattern)

The live path serializes **the event's own already-settled snapshot** at emit time — the same
discipline the offline serializer relies on. The one live-specific hazard: snapshot-on-connect
must send the map's **current** committed snapshot (not a stale cached one), so `LiveServer`
must ask `MapLiveVisualizer` for the latest frame at connect time, after the most recent event
has fully committed.

## Testing

- **`LiveServer`** (zero-dep, JDK `java.net.http.HttpClient` against a real server on an
  ephemeral port): the page serves at `/`; a GET to `/events` receives the injected
  snapshot-on-connect frame first; a `broadcast(...)` after connect is received as a `data:`
  message; a second concurrent connection is served promptly (guards the serial-executor
  landmine); `stop()` releases the port.
- **`MapLiveVisualizer`:** a `put` produces one broadcast whose frame equals
  `MapJsonSerializer.toFrame(thatEvent)`; the connect-frame reflects the current map state.
- **`MapJsonSerializer.toFrame`:** one event → one well-formed frame object; `toJson(list)`
  still equals the concatenation of `toFrame`s in the `frames` wrapper (baked output unchanged).
- **Front-end:** not unit-tested — a dumb renderer of pre-computed frames, per PR #17.
  The live-mode JS (EventSource wiring + follow-tail) is the only new untested surface;
  browser-verified against `LiveWebVizDemo`.

## Out of scope (future)

- **Slice 2** — the command parser + terminal REPL.
- **Slice 3** — browser-side controls (upstream POST).
- **List and trie** live front-ends (reuse `LiveServer` + the template; add a per-structure
  live visualizer each).
- **Authentication / real access control** for LAN mode — Slice 1 binds localhost; a shared
  classroom deployment would need at least a token, deferred with the `0.0.0.0` binding.
- **JavaFX** desktop renderer (separate subsystem).
