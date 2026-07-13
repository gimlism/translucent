# ArrayList live REPL command layer — Slice C (design)

**Date:** 2026-07-13
**Slice:** ArrayList Slice C (terminal REPL), mirroring HashMap PR #19.
**Branch:** `feat/arraylist-live-repl-command-layer`

## Goal

Add a terminal read-eval-print loop for a live `TeachingArrayList<String>`: type
commands, watch each mutation render live in the browser, no recompile per change.
This mirrors the HashMap's Slice 2 (PR #19): a pure, server-agnostic command
interpreter plus a thin demo that wires it to the already-merged Slice B live
transport (`LiveServer` + `ListLiveVisualizer` + `ListWebExporter.liveHtml()`).

The interpreter is also the shared entry point that the ArrayList browser-controls
slice (Slice D) will reuse — the only structure-specific surface a POST front-end
adds is the handler closure `line -> interp.execute(line, list).message()`.

## Why the grammar diverges from the HashMap

The HashMap REPL is put/remove/get/containsKey/size/clear. The `TeachingArrayList`
API is genuinely different — its public operations are `add(E)`, `add(int, E)`,
`set(int, E)`, `remove(int)`, `get(int)`, `size()`, with **no `clear()` and no
`contains`/`indexOf`**. So the grammar reflects the list's real operations rather
than mechanically renaming the map's. Two design decisions (both user-approved):

- **Distinct verbs, not an overloaded `add`.** The list has two insertion ops
  (append vs. positional insert). Because values are strings that may begin with an
  integer (e.g. `add 2 x`), collapsing both into one `add` word is ambiguous. So:
  `add <value>` appends, `insert <index> <value>` inserts.
- **No `clear` / no `contains`.** The structure has neither; adding a `clear`
  remove-loop would emit N events and invent API the list doesn't have. Out of scope.

## Components

### 1. `arraylist/repl/CommandResult.java`

Verbatim copy of `hashmap/repl/CommandResult` — a record `(String message, boolean
quit)` with `of(msg)` and `quitting(msg)` factories. The front-end decides how to
present the message (terminal `println` now, browser POST body in Slice D).

Dedup is **deferred**: this is the second copy; the trie's Slice C will be the third
(rule-of-three), the moment to consolidate into a `substrate/repl/CommandResult`.
Recorded alongside the deferred web-exporter-template consolidation (roadmap item E).

### 2. `arraylist/repl/ListCommandInterpreter.java`

Pure: no I/O, no reference to the server. `execute(String line,
TeachingArrayList<String> list) -> CommandResult`. Mutations it makes fire
`ListEvent`s that any attached listener (`ListLiveVisualizer`) broadcasts — live
rendering is a side-effect of the listener, not of this class. **`execute` never
throws**: every malformed input becomes a message.

Element type is `String` (rest-of-line after the index/verb, interior spaces
preserved). Indices are `int`. Messages quote values (`"…"`) so spaces and empty
strings are visible.

Grammar:

| Command | Effect | Message | Fires event? |
|---|---|---|---|
| `add <value>` | `list.add(value)` | `appended "<v>" at <i>` (i = new last index) | yes |
| `insert <index> <value>` | `list.add(index, value)` | `inserted "<v>" at <index>` | yes |
| `set <index> <value>` | old = `list.set(index, value)` | `set <index> = "<v>" (was "<old>")` | yes |
| `remove <index>` | old = `list.remove(index)` | `removed "<old>" at <index>` | yes |
| `get <index>` | `list.get(index)` | `get <index> → "<v>"` | **no** |
| `size` | `list.size()` | `size = <n>` | no |
| `help` | — | help text | no |
| `quit` (alias `exit`) | — | `bye`, quit=true | no |

Parsing: strip the line; blank or `null` -> `CommandResult.of("")` (silent no-op —
`null` guards an empty POST body in Slice D). Split off the first whitespace-delimited
token as the command (lower-cased). Then per command:

- `add`: the rest is the value. Empty rest -> `usage: add <value>`.
- `insert` / `set`: split the rest into `<index>` + `<value>` (2-way split, so the
  value keeps interior spaces). Fewer than 2 tokens -> `usage: insert <index> <value>`
  / `usage: set <index> <value>`.
- `remove` / `get`: the rest is the index token.

Index/bounds handling:

- Non-integer index token -> `not an integer: '<tok>'` (empty -> the `usage:` line).
- **Out-of-range index -> catch `IndexOutOfBoundsException` from the list and return
  `index out of range: <i> (size <n>)`.** This is the list analogue of the map's
  "not found"; relying on the structure's own bounds check keeps the valid-range
  rules (0..size for `insert`, 0..size-1 for the others) in one place.

`unknown command: '<cmd>' (type 'help')` for anything else. `helpText()` is public
(Slice D reuses it), one line per command, same shape as the map's.

### 3. `arraylist/demo/ListLiveReplDemo.java`

Mirrors `hashmap/demo/LiveReplDemo`. `main`:

1. `LiveServer server = new LiveServer(ListWebExporter.liveHtml(), "127.0.0.1", 7070);`
   `server.start();`
2. `var list = new TeachingArrayList<String>();`
   `list.addListener(new ListLiveVisualizer(server::broadcast));`
3. `BrowserLauncher.open("http://localhost:" + server.port());`
4. `runRepl(stdin, System.out, list, new ListCommandInterpreter());`
5. `server.stop()` in `finally`.

`runRepl` is a package-private test seam (no server, no browser): read a line,
execute, print the message when non-empty, loop until a `quit` result or EOF
(Ctrl-D). Identical control flow to the map demo's `runRepl`.

## What is NOT touched

Core, events, and substrate are untouched — no new transport, zero new deps. The
`LiveServer` / `JsonWriter` / `ListLiveVisualizer` / `ListWebExporter` from Slice B
are reused verbatim, so the recurring after()-timing / snapshot-before-settled bug
has no new surface here. `list-viz.html` and the live JS are unchanged (no JS in this
slice); they were browser-verified in Slice B.

## Testing

- **`ListCommandInterpreterTest`** — one assertion group per command: append (index
  in message), insert (mid-list), set (returns old), remove (returns old), get, size,
  help, quit + `exit` alias. Error paths: blank line and `null` -> empty no-op;
  missing args -> `usage:`; non-integer index -> `not an integer`; out-of-range index
  on insert/set/remove/get -> `index out of range: <i> (size <n>)`; unknown command.
  Value-with-spaces preserved. **Read commands fire no event** — attach a recording
  listener and assert `get`/`size`/`help` produce zero frames while `add`/`insert`/
  `set`/`remove` each produce one.
- **`ListLiveReplDemoTest`** — the `runRepl` seam: canned `BufferedReader` input ->
  captured `PrintStream`. Assert the printed messages, that a blank-message result
  prints nothing, that `quit` ends the loop (and lines after it are not executed),
  and that EOF ends the loop.

The dumb-renderer/live JS is untested-by-design and already browser-verified in
Slice B; this slice adds no JS. Per the recurring live-viz recipe, the controller
can still smoke-test end-to-end by piping timed commands into the REPL against
`target/classes`, but that is verification, not an automated test.

## Out of scope / deferred

- `clear` / `contains` commands (structure has neither).
- `CommandResult` consolidation into `substrate/repl` — defer to the trie Slice C
  (rule-of-three).
- Browser controls (POST /command + CONTROLS token) — that is Slice D, which reuses
  this interpreter unchanged.

## Sequencing

Built via subagent-driven-development on `feat/arraylist-live-repl-command-layer`,
then PR. Mirrors PR #19's two-task shape: (1) `CommandResult` +
`ListCommandInterpreter` + its test; (2) `ListLiveReplDemo` + its `runRepl` seam test.
