# TreeSet browser controls — Slice D (design)

**Date:** 2026-07-19
**Slice:** TreeSet Slice D (browser controls), mirroring HashMap PR #20, ArrayList PR #25,
and Trie PR #29. **Final** slice of the TreeSet's own 4-slice web arc
(A static #33 → B live SSE #34 → C REPL #35 → **D browser controls**).
**Branch:** `feat/treeset-live-browser-controls`

## Goal

Turn the browser page itself into the REPL: a command box on `treeset-viz.html` that
`POST`s each typed line to the running server, which applies it to a live
`TeachingTreeSet<Integer>` and streams the resulting mutation back over the existing SSE
transport. No stdin, no recompile per change. This mirrors the map/list/trie Slice D and
completes the TreeSet's web arc, bringing it to full parity with the three sibling
structures.

The command box has been dormant since Slice A: `treeset-viz.html` already carries the
`CONTROLS` token (baked `false`). This slice flips it on and wires the box.

## The point of this slice: browser-typed reads narrate live

Map/list/trie Slice D each prove that a *mutation* typed into the browser surfaces as a
live frame. A byte-mirror gives TreeSet that same guarantee. But TreeSet's distinctive
claim — established in Slice B, with **zero sibling precedent** — is that a **comparison
read** animates: `contains`/`floor`/`lower`/`ceiling`/`higher` emit `Compare` events, so
the comparison cursor walks down the red-black tree in the browser.

Slice B proved that from a *demo script*. Slice C proved it from a *terminal REPL*. Slice
D is the **first time a human types a comparison read into the browser itself and watches
it walk** — and nothing currently pins that path. So this slice's end-to-end test does
something no sibling's does: it POSTs a comparison read and asserts a `Compare` frame
surfaces on the live stream. That is the "one novel behaviour, pinned non-vacuously"
discipline carried forward from Slice C.

## Almost nothing new; the interpreter is reused byte-unchanged

The whole slice rides three slices of prior discipline:

- **`TreeSetCommandInterpreter`** (Slice C) is pure and server-agnostic —
  `execute(line, set) -> CommandResult`, never throws. Reused **unchanged**. The only
  structure-specific surface a POST front-end adds is the handler closure
  `line -> interp.execute(line, set).message()`.
- **`LiveServer`** already accepts a `Function<String,String>` command handler and serves
  `POST /command` (+ a 405 for other methods) — merged since the HashMap arc, reused verbatim.
- **`WebVizTemplate`** / **`TreeSetLiveVisualizer`** / **`TreeSetJsonSerializer`** — all
  reused verbatim.

Because core, events, substrate, the serializer, and the live visualizer are all
**byte-unchanged**, the recurring **snapshot-before-settled** bug has **zero surface** this
slice — the whole-branch review will confirm the diff is exactly the files below.

## Components

Two new/edited production files + one HTML template edit + tests.

### 1. `treeset/viz/TreeSetWebExporter.java` — add `controlsHtml()`

One new method, byte-identical in shape to `TrieWebExporter.controlsHtml()`:

```java
/** Live mode with browser command controls: DATA = null, live ON, controls ON. */
public static String controlsHtml() {
    return WebVizTemplate.inject(TEMPLATE_RESOURCE, "null", "true", "true");
}
```

`toHtml` (baked, `false`/`false`) and `liveHtml` (`null`/`true`/`false`) are unchanged.

### 2. `src/main/resources/web/treeset-viz.html` — flip on the command box

Four surgical edits, mirroring what trie Slice D added to `trie-viz.html` (the `CONTROLS`
token already exists at line 52; the box, CSS, refs, and JS do not):

1. **CSS** — `#cmd` (the input) and `#cmdout` (the reply span) rules, character-identical to
   the trie's.
2. **`#bar` markup** — `<input id="cmd" … hidden>` + `<button id="run" hidden>Run</button>`
   + `<span id="cmdout"></span>`. The one structure-specific value is the input
   **placeholder**: `add 30 · contains 30 · floor 25 · pollFirst · help` — it leads with a
   mutation, then **two narrating reads** (`contains` + a navigation query) to advertise the
   feature no sibling has, then a poll mutation.
3. **Element refs** — `cmdInput` / `runBtn` / `cmdOut`.
4. **`if (CONTROLS) { … }` block** — placed inside the existing LIVE branch (the box only
   makes sense when connected to a live server). Unhides the input + Run button; on Enter or
   Run-click, `fetch("/command", {method:"POST", body:line})`, show the text reply in
   `#cmdout`. Byte-identical to the trie's block. The SVG redraws **only** via the SSE
   stream — the POST reply is text-only.

### 3. `treeset/demo/TreeSetLiveControlsDemo.java`

Byte-mirror of `trie/demo/TrieLiveControlsDemo`:

```java
var set = new TeachingTreeSet<Integer>();
Function<String,String> handler = commandHandler(set, new TreeSetCommandInterpreter());
LiveServer server = new LiveServer(TreeSetWebExporter.controlsHtml(), "127.0.0.1", 7070, handler);
server.start();
set.addListener(new TreeSetLiveVisualizer(server::broadcast));
BrowserLauncher.open("http://localhost:" + server.port());
DemoLifecycle.awaitShutdown(server);
```

`commandHandler(TeachingTreeSet<Integer> set, TreeSetCommandInterpreter interp)` is a
package-private, tested seam returning a `Function<String,String>`. It serializes each call
behind a **private lock** — `LiveServer`'s virtual-thread executor can dispatch overlapping
POSTs onto a non-thread-safe `TeachingTreeSet`. `quit`/`exit` return their message but do
**not** stop the server: the seam holds no server reference, so a stray POST cannot kill the
session.

## What is NOT touched

`TreeSetCommandInterpreter`, `LiveServer`, `JsonWriter`, `WebVizTemplate`,
`TreeSetLiveVisualizer`, `TreeSetJsonSerializer`, `BrowserLauncher`, `DemoLifecycle`, and
all of core/events/substrate. The static/live rendering JS on `treeset-viz.html` (layout +
`renderFrame` + the SSE branch) is unchanged — only the dormant `CONTROLS`-gated box is
added. Diff = exactly the ~3 files above (+ tests).

## Testing

### `treeset/demo/TreeSetLiveControlsEndToEndTest` — two tests

1. **Parity (a mutation surfaces live).** `commandHandler(set, interp)` behind a real
   `LiveServer(controlsHtml(), … , handler)` on port 0. Open `/events`, wait until the
   server registers the connection, then `POST /command` with `add 30`. Assert the response
   body is `added 30`, and read the SSE stream until a frame with **`"type":"Add"`** appears.
   **Trap (confirmed against `TreeSetJsonSerializer` before writing the scan):** TreeSet has
   **no `CreateNode`** — an add into an empty set emits `Add` directly (add *is* creation),
   unlike the trie whose e2e reads *past* a leading `CreateNode`. So the scan waits for
   `Add`, not `Put`, and there is no leading frame to skip.

2. **The novel behaviour, pinned non-vacuously (a browser-typed read narrates).** POST a
   couple of `add`s to build a small tree, then `POST /command` with `contains 30` (or
   `floor 25`). Assert a frame with **`"type":"Compare"`** surfaces on the live stream. This
   is the browser-typed reads-narrate path that no sibling proves — the discriminating
   assertion of the whole slice. (A companion check that `quit`/`exit` return `bye` without
   stopping the server or mutating the set mirrors the sibling `quit` test.)

### `treeset/viz/TreeSetWebExporterControlsTest`

Mirrors `TrieWebExporterControlsTest`: `controlsHtml()` emits `CONTROLS = true` + `LIVE =
true` + `DATA = null`; `liveHtml()` leaves `CONTROLS = false`; `toHtml(...)` leaves both
false; and the template carries the box (`id="cmd"` + `/command`) in every mode (the box
markup is static; `CONTROLS` only unhides it) so this guards that the Slice D template edit
actually landed.

> **Note — a stale Slice-B assertion may need removing.** If `TreeSetWebExporterLiveTest`
> asserts the baked page does *not* contain `/command` (the trie's Slice-B test did), that
> assertion becomes false once the box's `POST /command` is in the template unconditionally.
> The plan checks `TreeSetWebExporterLiveTest` and, if such an assertion exists, removes it
> (the `CONTROLS = false` assertion still guards that the box stays dormant) — mirroring the
> trie precedent. Confirmed at implementation time, not assumed.

### Browser-verify (Slice-D convention — this slice adds live JS)

The `CONTROLS` JS is untested-by-design (it is browser JS). Per the recurring live-viz
recipe, the controller browser-verifies via Chrome MCP over `http://localhost:7070` after
`mvn process-classes` (the extension blocks `file://`), driving the **real** box
(`cmd.value` + `run.click`). The verify must **type a comparison read** (`floor 25` /
`contains 30`), not just a mutation — so it exercises the distinctive render, the comparison
cursor animating down the RB tree, closing the gap Slice B's script-driven verify left open.
Checklist: CONTROLS on (box unhidden); `add` renders live (counter climbs, tree grows with
rotations/recolours); a typed **comparison read** animates the walk **and** shows its text
reply with the tree otherwise settled; reload → snapshot-on-connect; zero console errors.

## Out of scope / deferred

- **View-returning verbs** (`subSet`/`headSet`/`tailSet`/`descendingSet`/`iterator`) — out
  of scope since Slice C; the interpreter doesn't expose them and the single-tree renderer
  can't show a sub-view.
- **The interpreter itself** — reused byte-unchanged; no grammar changes this slice.
- **`treeset-viz.html` renderer/layout/SSE JS** — unchanged; browser-verified in Slice B.

## Sequencing

Built via subagent-driven-development on `feat/treeset-live-browser-controls`. Two tasks:

1. **`TreeSetWebExporter.controlsHtml()` + `treeset-viz.html` box + `TreeSetWebExporterControlsTest`**
   (+ remove any stale Slice-B `/command`-absence assertion). The exporter/template surface.
2. **`TreeSetLiveControlsDemo` + `commandHandler` seam + `TreeSetLiveControlsEndToEndTest`**
   (both the parity `Add`-frame test and the novel `Compare`-frame test).

Then: browser-verify → whole-branch opus review → PR → Copilot triage → `gh pr merge N
--merge` (NOT squash, NOT `--delete-branch`; can't self-approve as gimlism) on user
go-ahead. When Slice D merges, the TreeSet's 4-slice web arc is **complete** — full parity
with map/list/trie (static replay + live SSE + terminal REPL + browser controls, all on the
shared substrate).
