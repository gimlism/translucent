# ArrayList live web visualizer — design (Slice B)

**Date:** 2026-07-10
**Status:** Approved (pending user review of this spec)
**Structure:** `TeachingArrayList`

## Summary

Turn Slice A's baked HTML replay into a live **connect-then-stream** mirror:
every `TeachingArrayList` mutation is serialized to one JSON frame and pushed to
every connected browser over Server-Sent Events, redrawing the existing two-tier
SVG. Exactly parallel to HashMap **PR #18**. This is Slice B of the ArrayList
live-viz arc; it reuses the shared transport (`LiveServer`) and Slice A's
serializer/exporter/template unchanged except for a live-mode branch.

## Context

Slice A (merged, PR #21) gave `TeachingArrayList` a static web visualizer: a
recorded `ListEvent` stream baked into a self-contained HTML replay. The shared
substrate already carries a generic live transport, `substrate/viz/LiveServer`
(one HTML page + SSE frame stream, structure-agnostic), built for the HashMap's
live slice. This slice wires the ArrayList into that transport, mirroring the
HashMap's PR #18 exactly.

The mutation → serialize → broadcast → SSE → redraw loop is **reused verbatim**
from the substrate and Slice A. The renderer is unchanged. What is new is a live
listener, a live-mode branch in the template/exporter, and a student-sandbox
demo — plus a one-time promotion of two structure-agnostic demo helpers into the
substrate so the ArrayList demo can reach them.

### Sequencing (the ArrayList live-viz arc)

- **Slice A — static web visualizer: MERGED (PR #21).**
- **Slice B — live SSE mirror (THIS SPEC).** Mirrors HashMap PR #18.
- **Slice C — terminal REPL.** Mirrors PR #19. *(out of scope here)*
- **Slice D — browser controls.** Mirrors PR #20. *(out of scope here)*

## Goals

- A live one-way mirror: mutations stream down over SSE and redraw the two-tier
  SVG as they happen; a late joiner sees the latest state (snapshot-on-connect,
  already handled by `LiveServer`).
- Reuse the generic `LiveServer` + `JsonWriter` and Slice A's `ListJsonSerializer`
  / `ListWebExporter` / `list-viz.html` with the smallest possible additions.
- A student-sandbox demo: a running `TeachingArrayList` whose every mutation
  renders live, the server staying up (scrubbable) until Ctrl-C.
- Promote the two structure-agnostic demo helpers (`BrowserLauncher`,
  `DemoLifecycle`) into the substrate so both the HashMap and ArrayList demos
  share one copy (rule of three: HashMap now, ArrayList here, Trie later).

## Non-goals

- Browser controls / upstream commands (Slice D) — LIVE token only, **no**
  CONTROLS token or `/command` use.
- Any change to `TeachingArrayList` core, its event vocabulary, or `LiveServer`.
- Extracting a shared web-template helper out of `MapWebExporter` /
  `ListWebExporter` (deferred-minor E from Slice A). The exporters' token sets are
  still diverging (Map: FRAMES/LIVE/CONTROLS; List: FRAMES/LIVE until Slice D);
  revisit at the trie slice. `ListWebExporter` grows its own `liveHtml()` +
  `/*__LIVE__*/` token, mirroring how `MapWebExporter` grew in PR #18.
- Any new dependency.

## Components

**New:**

| File | Package | Mirrors | Responsibility |
|---|---|---|---|
| `ListLiveVisualizer` | `arraylist/viz` | `MapLiveVisualizer` | `ListEventListener` that serializes each event to one frame and pushes it to a `Consumer<String>` sink |
| `ListLiveWebVizDemo` | `arraylist/demo` | `LiveWebVizDemo` | student sandbox: `LiveServer(liveHtml())` + `ListLiveVisualizer` + browser open + park until Ctrl-C |

**Edited:**

| File | Change |
|---|---|
| `resources/web/list-viz.html` | add `/*__LIVE__*/` token + the LIVE/EventSource branch (jump-to-live button, behind-count) — port from `map-viz.html` |
| `arraylist/viz/ListWebExporter` | add `liveHtml()` + `/*__LIVE__*/` token; `toHtml`/`liveHtml` share a 2-arg `inject(frames, live)` |

**Promoted (moved, made public):**

| File | From → To | Referencing demos to update |
|---|---|---|
| `BrowserLauncher` | `hashmap/demo` → `substrate/viz` | `LiveWebVizDemo`, `LiveControlsDemo`, `LiveReplDemo` (3) |
| `DemoLifecycle` | `hashmap/demo` → `substrate/viz` | `LiveWebVizDemo`, `LiveControlsDemo` (2) |

Home is `substrate/viz` (not a new package): `DemoLifecycle.awaitShutdown` is
already coupled to `substrate.viz.LiveServer`, and `BrowserLauncher` opens the
viz URL. Both become `public` with `public static` methods; behavior byte-for-byte
unchanged.

## Data flow (live)

```
TeachingArrayList --emits ListEvent--> ListLiveVisualizer.onEvent(e)
    e -> ListJsonSerializer.toFrame(e)  (one frame, no {"frames":[…]} wrapper)
    -> sink.accept(frameJson)           (sink = LiveServer::broadcast)
       LiveServer caches it (snapshot-on-connect) + enqueues to every SSE conn
          -> browser EventSource("/events").onmessage
             -> frames.push(frame); followTail ? go(last) : refreshMeta()
             -> renderFrame(frame)       (the unchanged Slice A two-tier renderer)
```

`ListLiveVisualizer` is the live twin of `ListRecordingListener`: instead of
buffering, it serializes and forwards per event. Stateless; the server owns
snapshot-on-connect. Per the `ListEventListener` / `StructureEventListener`
contract it neither mutates the list nor throws — serialization is a pure read of
the event's snapshot. (It forwards whatever `toFrame` produces, including the
deliberate mid-slide `Shift` and pre-placement `Grow` frames — same as the baked
path.)

## `ListLiveVisualizer`

Exact analogue of `MapLiveVisualizer`:

```java
public final class ListLiveVisualizer implements ListEventListener {
    private final Consumer<String> sink;
    public ListLiveVisualizer(Consumer<String> sink) { this.sink = sink; }
    @Override public void onEvent(ListEvent event) {
        sink.accept(ListJsonSerializer.toFrame(event));
    }
}
```

`ListEventListener extends StructureEventListener<ListEvent>`, so
`list.addListener(new ListLiveVisualizer(server::broadcast))` type-checks exactly
as the HashMap does.

## Template LIVE branch (`list-viz.html`)

Port the LIVE machinery from `map-viz.html`, adapted to the list DOM (no CONTROLS):

- `const LIVE = /*__LIVE__*/;` alongside the existing `const DATA = /*__FRAMES__*/;`.
- A hidden `#live` "⏭ N new — jump to live" button; `refreshMeta` computes the
  behind-count and shows the button only when `LIVE && behind > 0`.
- On connect: `const es = new EventSource("/events"); es.onmessage = ev => { …
  frames.push(f); followTail ? go(frames.length-1) : refreshMeta(); }`.
- `caption` shows "(waiting for live events…)" when `LIVE` and no frames yet.
- **Add** follow-tail tracking (Slice A's static `go()` has none): a
  `let followTail = true` flag, set `followTail = idx >= frames.length - 1` inside
  `go()`, driving the EventSource handler (auto-advance vs badge-only) and the
  jump-to-live button — ported from `map-viz.html`. The existing frame renderer
  and the fix-A label (`size ${topLogical.length}`) are unchanged from Slice A.
- **No** command input, Run button, `#cmdout`, or `fetch("/command")` — those are
  Slice D. The single new token is `/*__LIVE__*/`.

The LIVE branch is dumb-renderer JS: untested-by-design, browser-verified.

## `ListWebExporter` changes

Grow it to two modes, mirroring `MapWebExporter`'s PR-#18 shape (but two tokens,
not three):

```java
private static final String FRAMES_TOKEN = "/*__FRAMES__*/";
private static final String LIVE_TOKEN   = "/*__LIVE__*/";

public static String toHtml(String framesJson) { return inject(framesJson, "false"); }
public static String liveHtml()                 { return inject("null", "true"); }

private static String inject(String framesReplacement, String liveReplacement) {
    String t = readTemplate();
    requireToken(t, FRAMES_TOKEN);
    requireToken(t, LIVE_TOKEN);
    return t.replace(FRAMES_TOKEN, framesReplacement).replace(LIVE_TOKEN, liveReplacement);
}
```

`writeHtml(String, Path)` is unchanged (delegates to `toHtml`).

## `ListLiveWebVizDemo`

Exact analogue of `LiveWebVizDemo` (the student sandbox):

```java
LiveServer server = new LiveServer(ListWebExporter.liveHtml(), "127.0.0.1", 7070);
server.start();
String url = "http://localhost:" + server.port();
var list = new TeachingArrayList<String>(4);
list.addListener(new ListLiveVisualizer(server::broadcast));
BrowserLauncher.open(url);                        // now substrate.viz.BrowserLauncher
System.out.println("Serving live at " + url + " — Ctrl-C to stop.");
// --- student mutation block (edit me) ---
for (String s : new String[]{"a","b","c","d","e"}) list.add(s);   // grows 4 -> 6
list.add(2, "x");
list.remove(1);
// ---
DemoLifecycle.awaitShutdown(server);              // now substrate.viz.DemoLifecycle
```

Default port 7070 with `LiveServer`'s ephemeral fallback; binds 127.0.0.1.

Run: `mvn exec:java -Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListLiveWebVizDemo`.

## Testing

**Unit (JVM, no browser):**

- `ListLiveVisualizer`: a capturing `Consumer<String>` sink; drive a small story;
  assert it receives exactly one frame per event, each a single unwrapped frame
  (no `{"frames":[…]}` wrapper) whose content matches `ListJsonSerializer.toFrame`
  for that event (e.g. contains the expected `"type"`/`"label"`). Real behavior,
  no mocks.
- `ListWebExporter.liveHtml()`: `DATA` replaced with `null`, `/*__LIVE__*/` → `true`,
  no residual tokens, self-contained (no external URLs). The existing `toHtml`
  test still passes (now also replaces `/*__LIVE__*/` → `false`).

**Headless SSE end-to-end (no browser), mirroring the HashMap live slice:**

- Start a `LiveServer(ListWebExporter.liveHtml(), "127.0.0.1", 0)` (ephemeral
  port), attach a `ListLiveVisualizer(server::broadcast)` to a
  `TeachingArrayList`, open an SSE client (JDK `HttpClient`) to `/events`, wait
  until `openConnections() >= 1`, then mutate the list and assert a `data:` frame
  carrying the expected event arrives. Close the stream and stop the server (learn
  from the Slice-1 note: close the SSE `HttpClient`/stream in a
  try-with-resources).

**Promotion regression:** moving `BrowserLauncher`/`DemoLifecycle` to
`substrate/viz` must leave the 3 HashMap demos compiling and behaviorally
unchanged; the full suite stays green (no HashMap test asserts these helpers'
package, but the move must not break compilation or existing demo tests).

**Browser-verified (controller, untested-by-design LIVE JS):** run
`ListLiveWebVizDemo` with `-Djava.awt.headless=true` (so `BrowserLauncher` no-ops
against `target/classes` after `mvn -q process-classes`), stream mutations on a
timer / from a parked driver, connect via the Chrome MCP to
`http://localhost:7070`, and confirm: frames arrive live and the counter climbs;
two-tier render + Grow/Shift/Insert/Set/Remove correct; follow-tail and
"jump to live" work; snapshot-on-connect shows the latest frame on a late
connect; zero console errors. Kill the process and remove scratch when done.

Target: ~10–14 new tests; full suite stays green.

## Risks / watch-items

- **Recurring event-frame timing bug.** This slice adds no core/event-emission
  change; the live listener forwards whatever `toFrame` is handed (the same
  snapshots the baked path renders). Zero new surface — confirm at the
  whole-branch review, per the standing rule.
- **Promotion blast radius.** `BrowserLauncher` (3 demos) and `DemoLifecycle`
  (2 demos) change import lines only; the classes move verbatim and gain
  `public`. No logic change. Verify all HashMap demos still compile and the full
  suite is green after the move.
- **Live JS untested-by-design.** As every live slice: the EventSource branch is
  browser-verified by the controller, not unit-tested. `mvn` is the source of
  truth; ignore stale Eclipse/LSP diagnostics.
- **`toHtml` still works after adding the LIVE token.** The Slice-A baked path
  (`ListWebVizDemo`, its test) must keep working — `toHtml` now injects
  `LIVE=false`; confirm the static replay is unaffected.

## Workflow

brainstorming (this spec) → writing-plans → subagent-driven-development (fresh
implementer + reviewer per task, whole-branch review) → PR
`feat/arraylist-live-web-visualizer`, merged with `gh pr merge N --merge`.
