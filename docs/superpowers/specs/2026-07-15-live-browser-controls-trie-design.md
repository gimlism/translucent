# Trie live browser controls — Slice D (design)

**Date:** 2026-07-15
**Slice:** Trie Slice D (browser controls), the LAST of the 4-slice trie web-viz arc.
Mirrors HashMap PR #20 and ArrayList PR #25.
**Branch:** `feat/trie-live-browser-controls`

## Goal

Flip the already-baked-`false` `CONTROLS` token on in `trie-viz.html` and add a
browser command box, so a student drives a live `RadixTrie<Integer>` from the page
(no stdin) — reusing the merged `TrieCommandInterpreter` (Slice C) unchanged. The
trie lives server-side; the browser is the REPL over HTTP. Completes the trie arc
(A #26 static replay → B #27 live SSE → C #28 terminal REPL → **D browser controls**).

This is the tightest mirror in the arc: the command box's JS (`submit` → `fetch(
"/command")` → show text reply → Enter handler) is entirely grammar-agnostic, a
verbatim port from `list-viz.html`. The only structure-specific browser-controls
surface is the demo's handler closure `line -> interp.execute(line, trie).message()`.

## What already exists (do NOT rebuild)

- **`LiveServer` needs no change.** The 4-arg constructor with a nullable
  `Function<String,String>` command handler + `POST /command` (200 text/plain;
  null-handler / non-POST → 405) + coalesce-null-result → empty-200 all shipped in
  HashMap Slice 3 and are reused verbatim (`ListLiveControlsDemo` uses exactly this
  ctor). Structure-agnostic — no trie import.
- **`WebVizTemplate` consolidation is landed.** ArrayList #25 made `WebVizTemplate`
  the shared 3-token injector (fixed `LIVE`/`CONTROLS` replaced first, user `FRAMES`
  last — the load-bearing order that also fixed Issue #23); `TrieWebExporter` already
  delegates to it. Nothing to consolidate — do not reopen it.
- **`trie-viz.html` has every insertion anchor** (verified): `#bar` with the `#live`
  button (line 47), the element-ref block (lines 57–64), and the `if (LIVE){…}`
  live-setup function (line 169). `CONTROLS` is the baked token at line 55 with the
  comment "command box arrives in Slice D".

## Components

### 1. `trie/viz/TrieWebExporter.java` — add `controlsHtml()`

One method, byte-mirror of `ListWebExporter.controlsHtml()`:

```java
/** Live mode with browser command controls: {@code DATA = null}, live ON, controls ON. */
public static String controlsHtml() {
    return WebVizTemplate.inject(TEMPLATE_RESOURCE, "null", "true", "true");
}
```

`toHtml` (false/false) and `liveHtml` (null/true/false) are unchanged.

### 2. `resources/web/trie-viz.html` — add the command box (mirror list-viz.html)

Three edits, all grammar-agnostic except the placeholder:

- **HTML, inside `#bar` after the `#live` button:**
  ```html
  <input id="cmd" type="text" placeholder="put shore 1 · get shore · keysWithPrefix sh · help" autocomplete="off" hidden>
  <button id="run" hidden>Run</button>
  <span id="cmdout"></span>
  ```
  The placeholder is the ONLY trie-specific token — trie grammar examples instead of
  the list's `add hello · insert 1 x · remove 1 · help`.
- **Element refs, with the other `getElementById` calls:**
  ```js
  const cmdInput = document.getElementById("cmd");
  const runBtn = document.getElementById("run");
  const cmdOut = document.getElementById("cmdout");
  ```
- **The `if (CONTROLS){…}` block inside the `if (LIVE)` setup, after the SSE
  `onmessage` handler** — verbatim from list-viz.html:
  ```js
  if (CONTROLS) {
    cmdInput.hidden = false;
    runBtn.hidden = false;
    const submit = () => {
      const line = cmdInput.value;
      if (!line.trim()) return;
      cmdInput.value = "";
      fetch("/command", { method: "POST", body: line })
        .then(r => r.text())
        .then(msg => { cmdOut.textContent = msg; })
        .catch(() => { cmdOut.textContent = "(command failed to reach the server)"; });
    };
    runBtn.onclick = submit;
    cmdInput.addEventListener("keydown", e => { if (e.key === "Enter") submit(); });
  }
  ```
  Load-bearing: the SVG redraws ONLY via the existing SSE stream (a mutation → event →
  broadcast → `onmessage` → render); the POST reply is TEXT-ONLY. Read commands
  (`get`/`containsKey`/`keysWithPrefix`/`size`/`help`) fire no event → no frame → the
  POST text is their ONLY feedback. Do not "simplify" the reply away.

`layout`/`renderFrame` and the whole SVG renderer are UNTOUCHED — browser-verified in
Slices A/B.

### 3. `trie/demo/TrieLiveControlsDemo.java` — mirror `ListLiveControlsDemo`

```java
public static void main(String[] args) throws IOException {
    var trie = new RadixTrie<Integer>();
    Function<String, String> handler = commandHandler(trie, new TrieCommandInterpreter());

    LiveServer server = new LiveServer(TrieWebExporter.controlsHtml(), "127.0.0.1", 7070, handler);
    server.start();
    trie.addListener(new TrieLiveVisualizer(server::broadcast));

    String url = "http://localhost:" + server.port();
    BrowserLauncher.open(url);
    System.out.println("Live controls — serving at " + url + " — type commands in the browser; Ctrl-C to stop.");

    DemoLifecycle.awaitShutdown(server);
}
```

`commandHandler(RadixTrie<Integer>, TrieCommandInterpreter)` is a tested seam:
serializes `execute` behind a private `Object` lock because `LiveServer`'s
virtual-thread executor can dispatch overlapping POSTs onto the non-thread-safe
`RadixTrie`. `quit`/`exit` return their message but do NOT stop the server (a stray
POST must not kill the session; the seam holds no server reference). Lock order is
one-directional (commandLock → LiveServer's internal lock) so no deadlock.

Note the constructor-ordering detail from the list demo: create the server (which
takes `controlsHtml()`) and `start()` it, then `addListener` — matches the mirror.

## What is NOT touched

`LiveServer` / `JsonWriter` / `WebVizTemplate` / `BrowserLauncher` / `DemoLifecycle` /
`TrieLiveVisualizer` / `TrieJsonSerializer` / trie `core` + `events` — all reused
verbatim. `trie-viz.html`'s `layout`/`renderFrame`/SSE branch is unchanged; only the
command box is added. So the recurring snapshot-before-settled bug has ZERO new
surface, and `toHtml`/`liveHtml` behaviour is unchanged (only a new `controlsHtml`).

## Testing

- **`TrieWebExporterControlsTest`** (mirror `ListWebExporterControlsTest`) —
  `controlsHtml()` injects `CONTROLS = true`, `LIVE = true`, `DATA = null`, and the
  rendered page references `/command` (the box is present). Assert `toHtml`/`liveHtml`
  still bake `CONTROLS = false` (the box stays absent in the two non-controls modes).
- **`TrieLiveControlsEndToEndTest`** (mirror `ListLiveControlsEndToEndTest`) — headless:
  a real `LiveServer` on port 0 with the `commandHandler` seam, an SSE reader connected
  FIRST, then a `POST /command` with `put cat 1`; assert the POST reply text
  (`put cat = 1`) AND that a frame streams over SSE carrying the mutation.
  **e2e frame-sequence gotcha (the "don't mirror too literally" trap):** because the
  reader connects BEFORE the POST, it reads frames INCREMENTALLY — unlike Slice B's
  e2e where the put preceded connect and snapshot-on-connect served the settled last
  frame. A first `put` into an empty trie emits **CreateNode THEN Put** (the trie's
  Grow-then-Append analog). So the test must assert on the settled `Put` frame (read
  past the leading `CreateNode`) or on node-presence that holds in both frames — NOT
  "the first streamed frame is Put." Before writing the assertion, POST one command
  against the running demo/headless server and observe what actually streams first.
- **commandHandler seam unit test** — applies a line to a trie via the interpreter and
  returns the message; a `quit` line returns "bye" but the server (not held by the
  seam) is unaffected; overlapping calls are serialized (smoke: two sequential calls
  mutate consistently).
- **CONTROLS JS is untested-by-design** — the controller browser-verifies via Chrome
  MCP (headless demo over `http://localhost:7070` against `target/classes` after
  `mvn process-classes`; the extension blocks `file://`). Verify: box renders; a `put`
  redraws the SVG live + shows the reply; a `get` shows the reply with NO SVG change
  and the frame counter FROZEN (read-no-frame proof); snapshot-on-connect survives a
  reload; zero console errors.

## Out of scope / deferred

- No new grammar — `TrieCommandInterpreter` is reused UNCHANGED.
- No change to the static/live replay modes or the SVG renderer.
- Deferred minors still open across the arc (fix on a future test-touch, not here):
  HttpClient-unclosed in the trie + list e2e files; 2 tautological Slice-A
  `TrieJsonSerializerTest` Prune-test assertions.

## Sequencing

Built via subagent-driven-development on `feat/trie-live-browser-controls`, then PR.
Two tasks (mirrors #25's shape):

1. **`TrieWebExporter.controlsHtml()` + `trie-viz.html` command box + `TrieWebExporterControlsTest`** —
   the exporter/template surface, gated on the controls test + the existing suite green.
2. **`TrieLiveControlsDemo` + `commandHandler` seam + `TrieLiveControlsEndToEndTest`** —
   the demo wiring and headless e2e (with the frame-sequence gotcha resolved by
   observation), then controller browser-verification.
