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
| Late-joiner / reconnect | **Snapshot-on-connect.** SSE only delivers events *after* connection, and `EventSource` auto-reconnects on drop. Because every `MapEvent` already carries a full whole-structure snapshot, and each frame embeds that snapshot, the server simply **caches the last frame it broadcast** and replays it to every new/reconnecting connection as frame 0 — so a mid-session or reconnecting browser is never blank, and multi-viewer works with no extra machinery. (Caching the last frame the server *saw* keeps `LiveServer` fully map-agnostic — it needs no callback into the structure.) A viewer who connects before the very first mutation sees a "waiting" state until one arrives. |
| Live scrub UX | **Follow-tail unless scrubbed back.** Auto-advance to the newest frame as mutations arrive, *unless* the viewer has scrubbed to an earlier frame — then hold position and show a `N new — jump to live` affordance. |
| Binding | **`127.0.0.1` by default**, bind address is a constructor arg → LAN/classroom (`0.0.0.0`) is a config flip, not a redesign. Threading is multi-viewer-ready from the start. |
| Thread model | **Virtual threads** — `server.setExecutor(Executors.newVirtualThreadPerTaskExecutor())`. A held-open SSE connection is a thread parked-and-idle almost its whole life (the canonical virtual-thread workload), so this removes the pool-cap sizing knob entirely: classroom scale stops being a decision. `newVirtualThreadPerTaskExecutor` is a Java 21 API → compiles cleanly at `release=21`. |
| Port | **Configurable; default a fixed friendly port** (predictable teaching URL). If that port is occupied, fall back to an **OS-assigned free port** and print the actual URL. (`port 0` = always ephemeral.) |
| Open browser | **Default on** via `java.awt.Desktop.browse`, with a graceful fallback to just printing the URL when headless/unavailable; suppressible via a flag. |
| Lifecycle | The student's `main()` mutations run and finish, but the **server keeps the JVM alive** so the final state stays live/scrubbable. Clean shutdown on Ctrl-C; a `stop()` for tests. This is the real behavioral change from today's mutate-and-exit demos. |
| Where intelligence lives | Unchanged from PR #17: **all layout/classification/highlight logic is pre-computed in the Java serializer**; the JS stays a dumb renderer of frames. Live mode only changes where frames *come from*, not what a frame *is*. |

## Threading (the #1 landmine) and fan-out

`HttpServer`'s default executor is **serial (single-threaded)**: a held-open SSE response on
it starves the server — the next viewer's page GET (and every future request) blocks behind
the first open stream, silent until the *second* connection. `LiveServer` therefore **must**
set an explicit executor. It uses **`Executors.newVirtualThreadPerTaskExecutor()`**: each open
SSE connection holds its own (virtual, ~KB, parked-and-idle) thread for its lifetime, so there
is no thread-pool cap to size and classroom scale is a non-issue. (Pinning — a pre-JDK-24
concern where `synchronized` pinned the carrier — is moot on the JDK 26 runtime per JEP 491;
the `release=21` target is bytecode-level only and does not affect runtime pinning behavior.)

**Fan-out must not let a slow client block the mutator.** `broadcast(frame)` runs on the
student's mutating thread; it must not write synchronously to every socket, or one stalled
browser wedges the teaching session. So each connection owns a **bounded outbound queue**:
`broadcast` *enqueues* to every connection (never blocks) and each connection's own virtual
thread drains and writes. A dead/slow client backs up only its own queue; on overflow the
queue **drops its oldest frame** to make room for the newest — the slow viewer skips
intermediate steps and converges to the latest state, never stalling the session (and, under
follow-tail, the latest is what they want anyway). A short lock around "set last frame + offer
to all" and "capture last frame + register" keeps snapshot-on-connect race-free without any
I/O inside the lock. Virtual threads make "one draining thread per connection" free, which is
why this robust design costs nothing here.

## Components

Generic transport lives in `substrate/viz` (reusable by the list/trie live front-ends later);
HashMap specifics stay in `hashmap/viz`, mirroring the existing seam.

| Component | Package | Responsibility | Tested |
|---|---|---|---|
| `LiveServer` | `substrate/viz` | Wrap `HttpServer` with a **virtual-thread executor**. Serve the page at `/`; hold SSE connections at `/events`; own the connection registry, each connection with a **bounded outbound queue** drained by its own virtual thread; `broadcast(String frameJson)` caches the frame and enqueues to all connections (non-blocking); **snapshot-on-connect** (replay the cached last frame to each new connection); `start()` / `stop()` / `port()`; port fallback to ephemeral if the requested one is taken. **Map-agnostic** (never references any structure type). | ✅ unit (JDK `HttpClient`) |
| `MapLiveVisualizer` | `hashmap/viz` | A `MapEventListener` (the live twin of `MapRecordingListener`). On each event, serialize **one** frame via `MapJsonSerializer.toFrame` and hand it to a `Consumer<String>` sink (the server's `broadcast`). | ✅ unit |
| `MapJsonSerializer` | `hashmap/viz` | **Refactor:** extract a per-event `toFrame(MapEvent)` that emits a single frame object; `toJson(list)` becomes "join many `toFrame`s into the `frames` array." Live calls `toFrame` per event; baked path unchanged in output. | ✅ unit |
| `MapWebExporter` | `hashmap/viz` | **Extend:** add `liveHtml()` (template with `frames = null`, live flag on) alongside `toHtml(json)` (baked, live flag off). Both inject the two template tokens. | ✅ unit |
| `map-viz.html` template | `src/main/resources/web/` | Add live mode behind a `/*__LIVE__*/` flag: open `EventSource('/events')`, feed frames into the existing `renderFrame()`, follow-tail + a `jump to live` button. Baked mode unchanged. | ⛔ inspection |
| `BrowserLauncher` | `hashmap/demo` | Open a URL via `java.awt.Desktop`; silently no-op on headless/unsupported (never throws). Demo glue. | ⛔ trivial |
| `LiveWebVizDemo` (stub) | `hashmap/demo` | The **student stub**: map + `MapLiveVisualizer` + `LiveServer` pre-wired, a marked `// TODO: your mutations here` block, browser auto-opens, server keeps the JVM alive until Ctrl-C. Run via `mvn exec:java -Dexec.mainClass=…demo.LiveWebVizDemo`. | ⛔ demo |

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

- **New:** a live mode that opens `new EventSource('/events')` and pushes each arriving frame
  through the *existing* `renderFrame(frame)` (via `go`/`render`) — the renderer, layout,
  highlight, and cross-fade are all reused verbatim.
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
  message; a second concurrent connection is served promptly *while the first stays open*
  (guards the serial-executor landmine); a `broadcast` reaches two concurrent connections;
  `stop()` releases the port.
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
