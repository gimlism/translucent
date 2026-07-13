# ArrayList live browser controls — Slice D (design)

**Date:** 2026-07-13
**Slice:** ArrayList Slice D (browser controls), mirroring HashMap PR #20, plus the
exporter-consolidation refactor (deferred item E) and the Issue #23 fix.
**Branch:** `feat/arraylist-live-browser-controls`

## Goal

Complete the ArrayList live-viz loop: type commands into the browser page (an input
box wired to `POST /command`) and watch each mutation render live. The list lives
server-side; the browser becomes a REPL over HTTP, running the SAME
`ListCommandInterpreter` as the terminal `ListLiveReplDemo` (Slice C). This mirrors
the HashMap's Slice 3 (PR #20).

Because adding a `CONTROLS` token to `ListWebExporter` makes it structurally
identical to `MapWebExporter`, this slice also consolidates the two exporters'
template-inject logic into a shared substrate helper (roadmap deferred item E) and,
in doing so, fixes the `MapWebExporter` token-ordering bug (Issue #23) for free.

## What is already in place (no change needed)

`LiveServer` already carries the full upstream path from HashMap Slice 3: a nullable
`Function<String,String>` command handler (4th ctor arg) and `POST /command`
(200 `text/plain`; null-handler or non-POST → 405). It stays structure-agnostic — no
map/list/trie import. `JsonWriter` is likewise untouched. So the recurring
"snapshot taken before state settled" after()-timing bug has zero new surface here.

## Components

### 1. `substrate/viz/WebVizTemplate` (new — the consolidation)

A single static `inject`:

```
String inject(String resource, String framesReplacement, String liveReplacement,
              String controlsReplacement)
```

It reads the classpath template `resource`, validates that all three shared tokens
are present, and substitutes them:

- Tokens (the shared viz-template vocabulary): `/*__FRAMES__*/`, `/*__LIVE__*/`,
  `/*__CONTROLS__*/`.
- **Ordering (load-bearing):** replace the FIXED tokens `LIVE` and `CONTROLS` first,
  inject the USER-controlled `FRAMES` last — so a frame whose data literally contains
  a fixed token cannot be rewritten by that token's replacement. This is the correct
  ordering `ListWebExporter` already used (Slice B fix 6ed1351); centralizing it here
  makes it the single source of truth.
- Missing token → `IllegalStateException("template <resource> is missing token <t>")`.
- Reads via `WebVizTemplate.class.getResourceAsStream(resource)` (absolute `/web/...`
  path; same classpath as before), throwing `IllegalStateException` if not found and
  wrapping `IOException` in `UncheckedIOException` — same behavior the exporters had.

The 3-token vocabulary is hardcoded (not a generic token→replacement map): every
structure's template uses exactly these three tokens, so a fixed signature is the
simplest thing that works (YAGNI) and states the shared contract explicitly.

### 2. `hashmap/viz/MapWebExporter` (modify — routes through the helper, fixes #23)

`toHtml`/`liveHtml`/`controlsHtml` now delegate to `WebVizTemplate.inject(
TEMPLATE_RESOURCE, …)`; the private `inject`/`requireToken`/`readTemplate` are
removed. Public surface and the three factory semantics are unchanged
(`toHtml` = frames/false/false, `liveHtml` = null/true/false, `controlsHtml` =
null/true/true; `writeHtml` still writes `toHtml`).

**Issue #23 fix:** previously `MapWebExporter.inject` replaced `FRAMES` (user data)
before `LIVE`/`CONTROLS`, so a map key/value literally containing `/*__LIVE__*/` or
`/*__CONTROLS__*/` was rewritten to `false` in the baked `toHtml` output. Routing
through `WebVizTemplate` (FRAMES last) fixes it.

### 3. `arraylist/viz/ListWebExporter` (modify — adds CONTROLS, routes through helper)

`toHtml`/`liveHtml` delegate to `WebVizTemplate.inject(TEMPLATE_RESOURCE, …)`; the
private helpers are removed. **Adds `controlsHtml()`** = `inject(TEMPLATE_RESOURCE,
"null", "true", "true")`. Since the shared helper now requires the `CONTROLS` token,
`list-viz.html` must gain that token in the same change (component 4).

### 4. `resources/web/list-viz.html` (modify — CONTROLS branch)

Mirrors `map-viz.html`, LIVE-and-CONTROLS only (no baked-controls path):

- In the bar, after the `live` button: `<input id="cmd" type="text" placeholder="add
  hello · insert 1 x · remove 1 · help" autocomplete="off" hidden>`, `<button id="run"
  hidden>Run</button>`, `<span id="cmdout"></span>`.
- `const CONTROLS = /*__CONTROLS__*/;` plus element refs `cmdInput`, `runBtn`,
  `cmdOut`.
- Inside the existing `if (LIVE) { … }` block, an `if (CONTROLS) { … }` that un-hides
  the input + button and defines `submit()`: read the line, skip if blank, clear the
  input, `fetch("/command", {method:"POST", body:line})`, put the text reply in
  `cmdOut` (and a "(command failed to reach the server)" fallback on error). Wire
  `runBtn.onclick` and the Enter key.
- The SVG redraws only via the existing SSE `onmessage` path; the POST reply is
  TEXT ONLY (do not redraw off it).

### 5. `arraylist/demo/ListLiveControlsDemo` (new)

Mirrors `hashmap/demo/LiveControlsDemo`:

- `commandHandler(TeachingArrayList<String> list, ListCommandInterpreter interpreter)`
  → `Function<String,String>` (tested seam): applies each POSTed line to `list` via
  `interpreter.execute(line, list).message()`, serialized behind a private lock. The
  lock is required because `LiveServer`'s virtual-thread executor can dispatch
  overlapping POSTs onto the non-thread-safe `TeachingArrayList`. Lock order is
  one-directional `commandLock → LiveServer.lock` (execute → mutation → event →
  `ListLiveVisualizer.onEvent` → `server.broadcast`, which takes `LiveServer.lock`;
  no reverse path). `quit`/`exit` return their message but do NOT stop the server — a
  stray POST must never kill the session.
- `main`: build the handler, `new LiveServer(ListWebExporter.controlsHtml(),
  "127.0.0.1", 7070, handler)`, `start()`, attach `ListLiveVisualizer(server::
  broadcast)`, `BrowserLauncher.open(url)`, print a hint, `DemoLifecycle.awaitShutdown(
  server)`. No stdin.

## Load-bearing detail

Read commands (`get`/`size`/`help`) fire no event → no frame → the SVG does not
change. The `POST /command` text reply is their ONLY feedback. It must not be
"simplified away."

## Testing

- **`WebVizTemplateTest`** (new): the three inject shapes produce the expected
  FRAMES/LIVE/CONTROLS substitutions; a missing token throws
  `IllegalStateException` naming the token; and the **Issue #23 regression** — a
  `framesReplacement` string literally containing `/*__LIVE__*/` (and
  `/*__CONTROLS__*/`) is injected verbatim, not corrupted, because FRAMES is
  replaced last.
- **`MapWebExporterTest`** (modify): existing assertions stay green (behavior
  unchanged); **add** an exporter-level #23 regression — `toHtml` of a frames blob
  containing `/*__LIVE__*/` keeps the token, does not rewrite it to `false`.
- **`ListWebExporterTest`** (modify): add `controlsHtml()` assertions — CONTROLS
  injected `true`, LIVE `true`, DATA `null`, output self-contained; existing Slice-A/B
  tests stay green (including the Slice-B `bakedFrameDataContainingTheLiveTokenIsNot
  Corrupted`, now satisfied via the shared helper).
- **`ListLiveControlsDemoTest`** (new): the `commandHandler` seam — a POSTed `add`
  line mutates the list and returns the message; `quit`/`exit` return their message
  while leaving the list otherwise untouched (the seam holds NO server reference, so
  `quit` is structurally unable to stop the session — the same guarantee the map's
  seam gives); a mutation through the handler broadcasts exactly one frame (attach a
  recording sink to the list).
- **`LiveServer`** POST/405 units already exist from Slice 3 — untouched.
- The CONTROLS JS (input → POST → text reply; SVG via SSE) is untested-by-design;
  the controller browser-verifies it (Chrome MCP over `http://localhost:7070`) per the
  recurring live-viz recipe: put/insert/remove redraw + reply text; a read
  (`get`/`size`) shows a reply with NO SVG change (the read-no-frame proof); zero
  console errors.

## Out of scope / deferred

- Trie's 4-slice arc (its own future work). The `WebVizTemplate` helper is now ready
  for the trie's exporter to reuse — it becomes the third consumer, retroactively
  satisfying the rule of three that motivated the extraction.
- `CommandResult` consolidation into `substrate/repl` — still deferred to the trie's
  REPL slice (unrelated to this exporter consolidation).
- MINOR carryovers from Slice C (demo cleanup window; four interpreter test-coverage
  gaps) — not this slice's surface.

## Sequencing

Built via subagent-driven-development on `feat/arraylist-live-browser-controls`, then
PR. Task shape: (1) `WebVizTemplate` + reroute both exporters + `controlsHtml()` +
`list-viz.html` CONTROLS token + all exporter/template tests (the consolidation,
Issue #23 fix, and the exporter half of the feature — one coherent, independently
testable unit); (2) `ListLiveControlsDemo` + its `commandHandler` seam test, then
controller browser-verification.
