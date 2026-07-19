# TreeSet live REPL command layer — Slice C (design)

**Date:** 2026-07-19
**Slice:** TreeSet Slice C (terminal REPL), mirroring HashMap PR #19, ArrayList PR #24,
and Trie PR #28. Third slice of the TreeSet's own 4-slice web arc
(A static #33 → B live SSE #34 → **C REPL** → D browser controls).
**Branch:** `feat/treeset-live-repl-command-layer`

## Goal

Add a terminal read-eval-print loop for a live `TeachingTreeSet<Integer>`: type
commands, watch each mutation render live in the browser, no recompile per change. This
mirrors the map/list/trie Slice C: a pure, server-agnostic command interpreter plus a
thin demo that wires it to the already-merged Slice B live transport (`LiveServer` +
`TreeSetLiveVisualizer` + `TreeSetWebExporter.liveHtml()`).

The interpreter is also the shared entry point that the TreeSet browser-controls slice
(Slice D) will reuse — the only structure-specific surface a POST front-end adds is the
handler closure `line -> interp.execute(line, set).message()`.

## No rule-of-three step this time: `CommandResult` is already shared

Unlike the trie's Slice C (which had to hoist `CommandResult` into `substrate/repl` as
the third consumer), that dedup is **already done**. `substrate/repl/CommandResult` — the
record `(String message, boolean quit)` with `of(msg)` / `quitting(msg)` factories —
exists and is used by the map, list, and trie REPLs. This slice simply **reuses it**:
`TreeSetCommandInterpreter` imports `com.gimlism.translucent.substrate.repl.CommandResult`.
No refactor, no relocation, no consumer repointing.

## The distinctive divergence: a Set has no key/value, and comparison reads narrate

Two things set this REPL apart from its three predecessors.

**1. Elements, not key/value pairs.** The map parsed `<int-key> <string-value>`; the trie
inverted it to `<string-key> <int-value>`. A `TeachingTreeSet<Integer>` has *neither* — it
holds bare elements, so **every verb parses exactly one integer** (or takes no argument).
This removes a whole class of parse ambiguity: there is no rest-of-line value, no
key-vs-value question. Each element token is a single `Integer`; a non-integer or a
trailing extra token is malformed input.

**2. Comparison reads narrate live — inverting the sibling "no viz change" convention.**
In the map/list/trie REPLs, reads (`get`/`contains`) fire no event, so their help text
says "no viz change." The `TeachingTreeSet` **narrates reads**: `contains` and
`lower`/`floor`/`ceiling`/`higher` emit `Compare` events as they walk the red-black tree,
so in the live browser the comparison cursor animates down the tree (established in Slice
B). Slice C's REPL is the first place a human typing at a terminal can trigger that walk on
demand. The help text must therefore **invert** the sibling convention: it flags that these
comparison reads animate the walk live. `first`/`last`/`size` do not compare (they chase a
pointer to an extreme, or read a counter) and so do not narrate — the help distinguishes
them.

## Components

Two new production files + a package marker. **Nothing else is touched** — core, events,
substrate, `treeset/viz/*`, `treeset-viz.html`, and the Slice B transport all stay
byte-unchanged, so the recurring snapshot-before-settled bug has zero new surface.

### 1. `treeset/repl/TreeSetCommandInterpreter.java` (+ `package-info.java`)

Pure: no I/O, no reference to the server. `execute(String line, TeachingTreeSet<Integer>
set) -> CommandResult`. Mutations it makes fire `SetEvent`s that any attached listener
(`TreeSetLiveVisualizer`) broadcasts — live rendering is a side-effect of the listener, not
of this class. **`execute` never throws**: every malformed input becomes a message.

Elements are `Integer` (a single token, natural ordering). Grammar:

| Command | Effect | Message (hit / miss) | Fires event? |
|---|---|---|---|
| `add <int>` | `set.add(e)` | `added <e>` / `<e> already present` | yes (on a genuine add) |
| `remove <int>` | `set.remove(e)` | `removed <e>` / `<e> not found` | yes (on a hit) |
| `contains <int>` | `set.contains(e)` | `contains <e> → <bool>` | **yes — comparison walk narrates** |
| `first` | `set.first()` (guarded) | `first → <e>` / `first → (empty)` | no |
| `last` | `set.last()` (guarded) | `last → <e>` / `last → (empty)` | no |
| `lower <int>` | `set.lower(e)` | `lower <e> → <r>` / `lower <e> → none` | **yes — walk narrates** |
| `floor <int>` | `set.floor(e)` | `floor <e> → <r>` / `floor <e> → none` | **yes — walk narrates** |
| `ceiling <int>` | `set.ceiling(e)` | `ceiling <e> → <r>` / `ceiling <e> → none` | **yes — walk narrates** |
| `higher <int>` | `set.higher(e)` | `higher <e> → <r>` / `higher <e> → none` | **yes — walk narrates** |
| `pollFirst` | `set.pollFirst()` | `pollFirst → <e>` / `pollFirst → (empty)` | yes (on a hit) |
| `pollLast` | `set.pollLast()` | `pollLast → <e>` / `pollLast → (empty)` | yes (on a hit) |
| `size` | `set.size()` | `size = <n>` | no |
| `help` | — | help text | no |
| `quit` (alias `exit`) | — | `bye`, quit=true | no |

Parsing: strip the line; blank or `null` -> `CommandResult.of("")` (silent no-op — `null`
guards an empty POST body in Slice D). Split off the first whitespace-delimited token as the
command (lower-cased, `Locale.ROOT`). Then per command:

- **Single-int verbs** (`add`, `remove`, `contains`, `lower`, `floor`, `ceiling`,
  `higher`): the rest must be exactly one token that parses as `Integer`. Empty rest ->
  `usage: <cmd> <int>`. A non-integer -> `not an integer: '<tok>'`. A trailing extra token
  -> the same usage error (reject-extras, matching the trie's `soleKey` discipline — an
  element is one token, so a second token is malformed, not a space-bearing element).
- **No-arg verbs** (`first`, `last`, `pollFirst`, `pollLast`, `size`, `help`, `quit`): a
  trailing token -> `usage: <cmd>` (no-arg verbs reject arguments, matching the trie).
  Verb-name matching is case-insensitive, so `pollfirst` / `pollFirst` both match.

**Empty-set handling (the one guard that keeps `execute` throw-free):**
`set.first()` / `set.last()` throw `NoSuchElementException` on an empty set, so both are
guarded by an `set.isEmpty()` check up front -> `first → (empty)` / `last → (empty)`;
`set.first()` is never called on an empty set. `pollFirst()` / `pollLast()` already return
`null` on empty -> `pollFirst → (empty)`. `lower`/`floor`/`ceiling`/`higher` already return
`null` when there is no such element -> `... → none`. So no core method throws through the
interpreter.

`unknown command: '<cmd>' (type 'help')` for anything else. `helpText()` is public (Slice D
reuses it), one line per command, same shape as the map/list/trie's, with the **inverted**
viz note: the comparison reads (`contains`, `lower`/`floor`/`ceiling`/`higher`) animate the
walk live; `first`/`last`/`size` do not change the viz.

### 2. `treeset/demo/TreeSetLiveReplDemo.java`

Mirrors `trie/demo/TrieLiveReplDemo`. `main`:

1. `LiveServer server = new LiveServer(TreeSetWebExporter.liveHtml(), "127.0.0.1", 7070);`
   `server.start();`
2. `var set = new TeachingTreeSet<Integer>();`
   `set.addListener(new TreeSetLiveVisualizer(server::broadcast));`
3. `BrowserLauncher.open("http://localhost:" + server.port());`
4. `runRepl(stdin, System.out, set, new TreeSetCommandInterpreter());`
5. `server.stop()` in `finally`.

`runRepl(BufferedReader, PrintStream, TeachingTreeSet<Integer>, TreeSetCommandInterpreter)`
is a package-private test seam (no server, no browser): read a line, execute, print the
message when non-empty, loop until a `quit` result or EOF (Ctrl-D). Identical control flow
to the trie demo's `runRepl`.

## What is NOT touched

Core, events, and the Slice B transport are untouched — no new transport, zero new deps.
`LiveServer` / `JsonWriter` / `BrowserLauncher` / `DemoLifecycle` / `TreeSetLiveVisualizer` /
`TreeSetWebExporter` / `WebVizTemplate` are reused verbatim, so the recurring
snapshot-before-settled bug has no new surface. `treeset-viz.html` and its live JS are
unchanged (no JS in this slice); they were browser-verified in Slice B. `CommandResult` is
imported, not modified.

## Testing

- **`TreeSetCommandInterpreterTest`** — one assertion group per command: add (new ->
  `added`, duplicate -> `already present`), remove (hit -> `removed`, miss -> `not found`),
  contains (true / false), first/last (populated -> element, **empty -> `(empty)`**),
  lower/floor/ceiling/higher (a match -> the neighbour; no such element -> `none`;
  floor/ceiling on an exact member return that member), pollFirst/pollLast (populated ->
  element and the set shrinks; **empty -> `(empty)`**), size, help, quit (+ `exit` alias).
  Error paths: blank line and `null` -> empty no-op; missing arg on a single-int verb ->
  `usage:`; non-integer -> `not an integer`; a trailing extra token on both a single-int
  verb and a no-arg verb -> `usage:`; unknown command.
  **The reads-narrate divergence is pinned directly:** attach a recording listener
  (`SetRecordingListener`) and assert that `contains` and `floor` each broadcast at least
  one `Compare` frame, while `size` / `help` / `first` broadcast none. This is the slice's
  one genuinely novel behaviour and the sibling REPLs have no analogue for it — assert the
  structure's *actual* event output rather than mirroring the map's silent-read assumption.
- **`TreeSetLiveReplDemoTest`** — the `runRepl` seam: canned `BufferedReader` input ->
  captured `PrintStream`. Assert the printed messages, that a blank-message result prints
  nothing, that `quit` ends the loop (lines after it are not executed), and that EOF ends
  the loop.

The dumb-renderer / live JS is untested-by-design and already browser-verified in Slice B;
this slice adds no JS. Per the recurring live-viz recipe the controller can still smoke-test
end-to-end by piping timed commands into the REPL against `target/classes`, but that is
verification, not an automated test.

## Out of scope / deferred

- **View-returning verbs** (`subSet` / `headSet` / `tailSet` / `descendingSet` /
  `iterator`) — these return a collection or a live sub-view, not a single narratable
  answer, and rendering a sub-view in the single-tree browser renderer is a viz question,
  not a REPL one. Out of scope for Slice C (user-approved: the "full navigation showcase"
  scope is the scalar navigation surface, not the compound views).
- `clear` — the set exposes `clear()`, but a REPL `clear` adds no pedagogy the per-element
  `remove` doesn't, and the siblings omit it. Out of scope.
- Browser controls (POST /command + CONTROLS token) — that is Slice D, which reuses this
  interpreter unchanged (the only new surface being the handler closure).

## Sequencing

Built via subagent-driven-development on `feat/treeset-live-repl-command-layer`, then PR.
Two tasks (no `CommandResult` consolidation step — it is already shared):

1. **`TreeSetCommandInterpreter` + `package-info` + `TreeSetCommandInterpreterTest`** — the
   pure command layer on the shared `CommandResult`, including the empty-set guards and the
   reads-narrate assertions.
2. **`TreeSetLiveReplDemo` + `TreeSetLiveReplDemoTest`** — the demo wiring and its `runRepl`
   seam test.

Then: whole-branch opus review -> PR -> Copilot triage -> `gh pr merge N --merge` (NOT
squash, NOT --delete-branch) on user go-ahead. After Slice C merges, Slice D (browser
controls) completes the TreeSet's 4-slice arc and brings it to full parity with map/list/trie.
