# Live REPL Command Layer (HashMap) — Slice 2 — Design

**Status:** Draft 2026-07-06
**Builds on:** the merged live web visualizer (PR #18, Slice 1) — `LiveServer` (substrate/viz),
`MapLiveVisualizer` (hashmap/viz), `MapWebExporter.liveHtml()`, and the live `map-viz.html`.
This slice adds an interactive **command layer** driven from a **terminal REPL**, reusing
Slice 1's SSE transport unchanged.

## Goal

Let someone drive a live `TeachingHashMap` by typing commands at a terminal — `put 3 x`,
`remove 3`, `get 3` — and watch each mutation render live in the browser, with no recompile
per change (the Slice 1 stub required editing Java and re-running). The commands are parsed and
executed by a **pure, server-agnostic interpreter** that Slice 3's browser controls will call
verbatim; the terminal REPL is just its first front-end.

## Where this sits in the sequence

Slice 1 (merged) = one-way live mirror + a `main()` TODO stub. **Slice 2 (this spec)** = a
command parser + terminal REPL, no new transport. **Slice 3 (future)** = browser-side controls
that POST a command line back to the JVM and call the *same* interpreter, adding only the
upstream `fetch()` POST.

## Why the interpreter is the architectural core

The load-bearing unit is not the REPL loop — it is a **pure interpreter**:

```java
CommandResult execute(String line, TeachingHashMap<Integer,String> map)
```

- It parses `line` and calls `map.put/remove/clear/get/containsKey/size`. **It does not know
  the server exists.** Mutations fire `MapEvent`s, which the already-attached `MapLiveVisualizer`
  broadcasts — so live rendering is a *side-effect of the listener*, fully decoupled from the
  interpreter. This keeps the interpreter trivially testable without a server or stdin.
- It is the exact entry point Slice 3's browser POST handler will call — same `execute`,
  different front-end. That is what makes Slice 3 additive rather than a rewrite.

This mirrors the repo's established "intelligence in the tested layer, thin untested shell"
pattern: `MapCommandInterpreter` holds all the logic (like `MapJsonSerializer` for the viz),
while `LiveReplDemo` is a thin I/O shell (like `map-viz.html`).

## Resolved decisions

| Question | Decision |
|---|---|
| Interpreter contract | `MapCommandInterpreter.execute(String line, TeachingHashMap<Integer,String> map)` → `CommandResult` — a record `{ String message, boolean quit }`. Pure: no I/O, no server/broadcast coupling, never throws out of `execute`. |
| Command set | **Full Map API + meta**: `put`, `remove`, `clear`, `get`, `containsKey`, `size`, `help`, `quit`. |
| Reads | **Console-only.** `get`/`containsKey`/`size` return a text result; they emit no event, so the browser is intentionally unchanged. (A transient "probe highlight" for reads was considered and deferred — it would introduce a browser frame with no backing event, breaking the "every frame = a committed event's snapshot" invariant.) |
| Key / value types | Keys `Integer`, values `String`. Matches every existing demo and keeps the bucket math (`k & (cap-1)`) legible — the whole teaching point. |
| `put` value grammar | `put <int-key> <value…>` — the value is the rest of the line after the key, trimmed (so `put 3 hello world` → value `"hello world"`). A `put` with no value is an error. |
| Command names | `put`, `remove`, `get`, `containsKey` (alias `contains`), `size`, `clear`, `help`, `quit` (alias `exit`). Case-insensitive command word; leading/trailing whitespace ignored; blank line is a no-op (empty message, not an error). |
| Error handling | Unknown command, non-integer key, or missing args → a one-line usage/error message in `CommandResult.message`, `quit=false`, map unchanged. Nothing throws out of `execute`. |
| `help` | Returns a multi-line overview: a one-line description of what the REPL does, then the grammar (one line per command with its argument shape and a short gloss). |
| REPL front-end | `LiveReplDemo.main()` — start a `LiveServer(MapWebExporter.liveHtml())`, wire `MapLiveVisualizer(server::broadcast)`, auto-open the browser, then loop reading `System.in` lines → `interpreter.execute(line, map)` → print `result.message` → until `result.quit` or EOF (Ctrl-D), then `server.stop()`. **Starts with an empty map.** |
| Transport / dependencies | **No new transport, zero new dependencies.** Reuses Slice 1's `LiveServer` verbatim; JDK-only. |

## Components

New `hashmap/repl` package; the demo joins the existing `hashmap/demo`.

| Component | Package | Responsibility | Tested |
|---|---|---|---|
| `CommandResult` | `hashmap/repl` | Immutable record `{ String message, boolean quit }` returned by `execute`. | (trivial; covered via interpreter tests) |
| `MapCommandInterpreter` | `hashmap/repl` | `execute(String line, TeachingHashMap<Integer,String> map)` → `CommandResult`. Parse the command word + args, dispatch to the map, format the result/error. **All parsing and dispatch logic lives here.** Pure — no I/O, no server reference. | ✅ unit (thorough) |
| `LiveReplDemo` | `hashmap/demo` | Thin front-end: live-server wiring + browser open + the `System.in` read loop; delegates every line to `MapCommandInterpreter.execute`. Run via `mvn exec:java -Dexec.mainClass=…demo.LiveReplDemo`. | ⛔ demo |

## Data flow

```
user types "put 24 v24"  (stdin)
  → LiveReplDemo reads the line
  → MapCommandInterpreter.execute("put 24 v24", map)
      → map.put(24, "v24")                          (a mutation)
          → TeachingHashMap emits MapEvent + snapshot
          → MapLiveVisualizer.onEvent → toFrame → server.broadcast   (Slice 1, unchanged)
          → browser renders the new frame live
      → returns CommandResult{ "put 24 = v24", quit=false }
  → LiveReplDemo prints the message

user types "get 24"
  → execute → map.get(24) → CommandResult{ "get 24 → v24", quit=false }
  → LiveReplDemo prints it; browser is unchanged (read, no event)

user types "quit"  (or Ctrl-D / EOF)
  → CommandResult{ "bye", quit=true }  (EOF: loop ends directly)
  → LiveReplDemo stops the server and exits
```

The interpreter's only contact with the live view is indirect: it mutates the map, and the
listener wired by the demo does the broadcasting. The interpreter has no server dependency.

## Result / message formats

- `put K V` → `"put K = V"` (new key: `put` returned `null`) or `"set K = V (was <old>)"`
  (existing key: `put` returned the previous value). Uses only `put`'s return value — the
  interpreter is not a listener and the browser shows the bucket visually, so the console
  message deliberately omits the bucket index (which would require the current capacity).
- `remove K` → `"removed K"` if present, `"K not found"` if absent.
- `get K` → `"get K → <value>"` if present (value shown via its `String` form), `"get K → absent"` if not.
- `containsKey K` → `"containsKey K → true|false"`.
- `size` → `"size = <n>"`.
- `clear` → `"cleared (<n> entries removed)"`.
- `help` → overview line + one line per command.
- `quit`/`exit` → `"bye"` with `quit=true`.
- Errors → e.g. `"usage: put <int-key> <value>"`, `"not an integer: 'xyz'"`,
  `"unknown command: 'foo' (type 'help')"`.

## Testing

- **`MapCommandInterpreter`** (pure — no server, no stdin): for each command, assert the
  returned `CommandResult.message` AND the resulting map state — `put` inserts (and the "set"
  vs "put → bucket" message branch), `remove` present/absent, `get` present/absent,
  `containsKey`, `size`, `clear` empties the map. Every error path (`put` missing value,
  non-integer key, unknown command, blank line) returns the right message with `quit=false` and
  the map unchanged. `quit` and `exit` set `quit=true`. `help` lists every command word.
  Command word is case-insensitive; surrounding whitespace ignored; `put`'s value keeps interior
  spaces.
- **Live integration:** one test that a `put` executed through the interpreter — with a
  `MapLiveVisualizer` attached to the map and its sink capturing frames — produces exactly one
  broadcast frame (proves the interpreter → map → listener → broadcast path holds end-to-end,
  reusing Slice 1's pieces).
- **`LiveReplDemo`:** thin I/O shell, not unit-tested; verified by running it (type commands,
  watch the browser update; `get` prints to console without changing the view; `quit` stops the
  server).

## Out of scope (future)

- **Slice 3** — browser-side controls (an input box / buttons that POST a command line to the
  JVM and call this same `MapCommandInterpreter.execute`; adds only the upstream `fetch()` POST).
- **Probe-highlight for reads** — flashing a transient highlight frame on `get`/`containsKey`.
- **List and trie** command interpreters (each parses its own key/value shapes; the terminal
  REPL loop is reusable, the per-structure interpreter is not).
- **Richer values / typed keys** (String keys, quoted values, etc.) — Integer/String is the
  teaching-legible default.
