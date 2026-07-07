# Live Browser Controls (HashMap) — Slice 3 design

**Date:** 2026-07-08
**Structure:** HashMap (`TeachingHashMap`)
**Depends on:** Slice 1 (live web visualizer, PR #18) + Slice 2 (REPL command layer, PR #19)
**Status:** design approved; ready for implementation plan.

## Summary

Turn the live web visualizer from a **read-only mirror** into a **two-way console**. A
command input on the page POSTs a raw command line upstream; the server runs it through the
*same* `MapCommandInterpreter.execute(line, map)` that Slice 2 built for the terminal REPL. The
only genuinely new mechanism is **one upstream POST path**. Everything downstream — mutation →
`MapEvent` → `MapLiveVisualizer` → `LiveServer.broadcast` → SSE → SVG redraw — already exists and
is reused verbatim. No new transport, no new dependency.

This completes the live-viz loop sequenced across three slices: Slice 1 = one-way mirror (SSE
down), Slice 2 = command parser + terminal REPL (no new transport), **Slice 3 = browser controls
(`fetch()` POST up, reusing Slice 2's interpreter)**.

## Goal

A student running the demo can type `put 8 v8`, `remove 8`, `get 8`, `clear`, `help`, etc. into a
box **in the browser** and watch the HashMap redraw live — with no recompile and no terminal. The
browser becomes the REPL, over HTTP.

## Non-goals (YAGNI)

- No authentication or rate limiting.
- No command history / up-arrow recall / autocomplete.
- No multi-map or multi-session support.
- No list/trie browser controls yet — they will reuse this exact pattern in a later slice.
- No client-side command validation or structured form fields — the interpreter is the single
  source of truth for grammar and errors.

## Architecture — three small changes, no new transport

### A. `LiveServer` gains a generic command handler (stays structure-agnostic)

`LiveServer` today is handed `pageHtml` and a stream of opaque frame strings; it knows nothing
about maps, lists, or tries. Slice 3 keeps that property.

- Add a **4th constructor parameter**: `java.util.function.Function<String,String> commandHandler`
  (nullable). A constructor param — not a setter — avoids must-call-before-`start()` temporal
  coupling.
- Add a `POST /command` context. It reads the request body as UTF-8, calls
  `commandHandler.apply(body)`, and writes the returned string back as
  `text/plain; charset=utf-8`.
- `LiveServer` still knows nothing about maps: it holds an opaque `line → text` function exactly
  as it already holds an opaque `pageHtml` and opaque frame strings.

Existing 3-arg construction in `LiveWebVizDemo` and `LiveReplDemo` continues to work — either via
a retained 3-arg constructor that delegates with `commandHandler = null`, or by passing `null`
explicitly. (Implementation plan picks one; the retained 3-arg overload is preferred so those two
demos are untouched.)

### B. `LiveControlsDemo` (new) does the map-specific wiring

A new demo `com.gimlism.translucent.hashmap.demo.LiveControlsDemo`:

```java
var map = new TeachingHashMap<Integer, String>();
map.addListener(new MapLiveVisualizer(server::broadcast));
var interpreter = new MapCommandInterpreter();
final Object commandLock = new Object();

Function<String,String> handler = line -> {
    synchronized (commandLock) {              // serialize mutations — see "Concurrency"
        return interpreter.execute(line, map).message();
    }
};

LiveServer server = new LiveServer(MapWebExporter.liveHtml(), "127.0.0.1", PORT, handler);
server.start();
BrowserLauncher.open(url);
awaitShutdown(server);                         // park until Ctrl-C; stop() on the way out
```

All input comes from the page — no stdin loop. `LiveReplDemo` (terminal REPL) and
`LiveWebVizDemo` (student writes `put()` calls in code) are **untouched**; each demo now
demonstrates exactly one input mode.

The park-until-Ctrl-C lifecycle mirrors `LiveWebVizDemo.awaitShutdown` (shutdown hook +
`CountDownLatch`, with `stop()` in the `InterruptedException` path so an IDE stop releases the
port).

### C. The template (`map-viz.html`) gains a command input, gated on the live token

Inside the existing `if (LIVE) { ... }` block (which already opens the `EventSource`):

- Render a text input + a **Run** button in the control bar, plus a small status line for the
  response text.
- On Enter (or Run click): `fetch("/command", { method: "POST", body: line })`, then display the
  response text in the status line. Clear the input.
- **The viz is not updated from the POST response.** The redraw arrives on its own through the
  existing `/events` SSE stream (mutation → event → broadcast). The response text is separate
  feedback (see "Why the text response exists").
- In replay mode (`LIVE` is false — no server to POST to) the command input is **not rendered**.

The input-box + `fetch` JS is **untested-by-design**, like the rest of the dumb renderer —
browser-verified via the standard live-viz recipe.

## Data flow

```
browser input ──POST /command──▶ LiveServer ──handler.apply──▶ [lock] interpreter.execute(line, map)
                                                                          │
                                            map mutates ──▶ MapEvent ──▶ MapLiveVisualizer ──▶ broadcast()
   status line ◀──text resp──── LiveServer ◀──message()───────┘                                    │
        SVG redraws ◀─────────────────────── SSE /events ◀───────────────────────────────────────┘
```

Two independent channels: the POST returns **text**; the frame returns via **SSE**.

## Why the text response exists (load-bearing — do not "simplify" it away)

Read-only commands — `get`, `containsKey`, `size`, `help` — fire **no event**, so they produce
**no SSE frame**. For those, the POST response text is the student's *only* feedback. The mutating
commands (`put`, `remove`, `clear`) would redraw fine with no response body at all, because their
frame arrives via SSE regardless. **The response channel exists for the read-only commands.** A
future reader must not delete the response body as "redundant with the SSE frame" — it is not
redundant for the read-only half of the grammar.

## Concurrency (the one correctness-sensitive piece)

`LiveServer` uses a virtual-thread-per-task executor, so two overlapping `POST /command` requests
run on two threads and both would mutate a non-thread-safe `TeachingHashMap`.

- **Serialize `execute` behind a demo-side lock** (`commandLock` above) — placed in
  `LiveControlsDemo`, **not** in `LiveServer`, because a different structure may want different
  concurrency semantics and `LiveServer` must stay structure-agnostic.
- **No deadlock.** Lock order is strictly one-directional:
  `commandLock → LiveServer.lock` (execute → `map.put` → listener callback → `broadcast()` grabs
  `LiveServer.lock`). `LiveServer.handleEvents` grabs `LiveServer.lock` **alone** and never calls
  the command handler. The nesting is one-way, so there is no cycle. Invariant to preserve:
  `broadcast` must never call back into `commandLock`.
- **The hold is cheap.** `broadcast` only *enqueues* frames; the socket writes happen on each
  connection's own draining thread. Serializing `execute` therefore does **not** serialize network
  I/O — do not "optimize" the lock away thinking it blocks streaming.

## Error handling & edge cases

- `MapCommandInterpreter.execute` never throws (Slice 2 guarantee) → the handler never throws →
  `POST /command` always returns `200` + text.
- `POST /command` when `commandHandler == null` (replay-mode server, or any server constructed
  without a handler) → **405 Method Not Allowed**.
- A non-POST method on `/command` (e.g. GET) → **405 Method Not Allowed**.
- `quit` / `exit` typed in the browser → returns the `"bye"` message but **does not stop the
  server**. A stray or malicious POST must not kill the session for everyone. The server stops
  only via Ctrl-C (or IDE stop). (`CommandResult.quit()` is simply ignored on the browser path.)
- Binding stays `127.0.0.1` by default. Note for operators: flipping `bindAddr` to a LAN address
  now exposes **state mutation**, not just viewing. Acceptable for a classroom tool on a trusted
  network, but explicitly called out.
- Empty / blank POST body → `execute` treats it as a blank no-op returning `""` (existing Slice 2
  behavior); the status line shows nothing.

## Testing

- **`LiveServer` unit tests (substrate/viz):**
  - `POST /command` invokes the handler and returns its text verbatim.
  - `POST /command` on a server built with a `null` handler → `405`.
  - A non-POST method on `/command` → `405`.
  - Existing root + SSE tests remain green.
- **Headless end-to-end test (hashmap/viz):** stand up a `LiveServer` wired to a real map +
  interpreter (as `LiveControlsDemo` does), open an SSE connection, `POST /command` a `put`, and
  assert a frame carrying that mutation arrives on `/events`. Exercises the whole
  POST → execute → event → broadcast → SSE loop **without a browser**.
- **Untested-by-design:** the command-input + `fetch` JS in `map-viz.html`. Verified via the
  live-viz browser recipe (headless `BrowserLauncher`, `mvn process-classes` so the template is on
  the classpath, Chrome MCP against `http://localhost:7070`, screenshot frames as they arrive,
  console shows zero errors). This is its only verification.

## Files touched

- `src/main/java/com/gimlism/translucent/substrate/viz/LiveServer.java` — 4th ctor param +
  `POST /command` context (retain 3-arg ctor delegating with `null`).
- `src/main/java/com/gimlism/translucent/hashmap/demo/LiveControlsDemo.java` — **new**.
- `src/main/resources/web/map-viz.html` — command input + `fetch` POST inside the `LIVE` block.
- `src/test/java/com/gimlism/translucent/substrate/viz/LiveServerTest.java` — POST/405 cases.
- New or extended end-to-end test under `src/test/java/com/gimlism/translucent/hashmap/viz/`.

## Reuse note for later slices

`LiveControlsDemo`'s handler closure — `line -> interpreter.execute(line, map).message()` behind a
lock — is the entire structure-specific surface. The list and trie live front-ends reuse
`LiveServer` (now with its command handler), the `map-viz.html` template pattern, and their own
per-structure interpreter. Nothing in this slice is HashMap-only except the interpreter and map
types named in the closure.
