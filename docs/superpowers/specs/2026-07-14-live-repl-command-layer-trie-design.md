# Trie live REPL command layer — Slice C (design)

**Date:** 2026-07-14
**Slice:** Trie Slice C (terminal REPL), mirroring HashMap PR #19 and ArrayList PR #24.
**Branch:** `feat/trie-live-repl-command-layer`

## Goal

Add a terminal read-eval-print loop for a live `RadixTrie<Integer>`: type commands,
watch each mutation render live in the browser, no recompile per change. This mirrors
the HashMap's and ArrayList's Slice C: a pure, server-agnostic command interpreter
plus a thin demo that wires it to the already-merged Slice B live transport
(`LiveServer` + `TrieLiveVisualizer` + `TrieWebExporter.liveHtml()`).

The interpreter is also the shared entry point that the trie browser-controls slice
(Slice D) will reuse — the only structure-specific surface a POST front-end adds is
the handler closure `line -> interp.execute(line, trie).message()`.

## The rule-of-three moment: consolidate `CommandResult` into `substrate/repl`

This slice is the **third** consumer of the `(String message, boolean quit)` result
record. HashMap Slice C introduced it (`hashmap/repl/CommandResult`); ArrayList Slice C
copied it verbatim (`arraylist/repl/CommandResult`) and its javadoc explicitly records
"The dedup into a shared `substrate.repl` home is deferred to the trie's REPL slice
(rule of three)." That deferral comes due now.

**Step 1 of this slice is a pure refactor with no behaviour change**, done and gated
*before* any trie code exists:

1. Create `substrate/repl/CommandResult.java` — the record `(String message, boolean
   quit)` with `of(msg)` and `quitting(msg)` factories — plus `substrate/repl/
   package-info.java` (both `substrate/events` and `substrate/viz` carry one; match
   the convention).
2. Repoint the four existing consumers to import `substrate.repl.CommandResult`:
   `hashmap/repl/MapCommandInterpreter`, `hashmap/demo/LiveReplDemo`,
   `arraylist/repl/ListCommandInterpreter`, `arraylist/demo/ListLiveReplDemo`.
3. Delete `hashmap/repl/CommandResult.java` and `arraylist/repl/CommandResult.java`.
4. Fix the test blast radius **per file, not symmetrically** — verify each:
   - `MapCommandInterpreterTest` textually references `CommandResult` (same-package
     today) and will need an `import com.gimlism.translucent.substrate.repl.CommandResult`.
   - `ListCommandInterpreterTest` did *not* surface in a `CommandResult` grep — it
     likely uses `var r = interpreter.execute(...)` and needs no import change. Confirm
     by reading it rather than assuming.
5. Update the surviving javadoc: the moved `CommandResult` should no longer say
   "verbatim copy" / "dedup deferred"; state it is the shared result type used by all
   three REPLs.
6. **Gate: `mvn test` → all 333 pass, unchanged.** No new tests in this step — a pure
   move is proven by the existing suite still passing. Only after this is green does
   trie code get written.

## Why the grammar diverges: the key/value inversion

The HashMap is `Integer key → String value`; it parses the *first* token as an int and
keeps the *rest of the line* as the value. The `RadixTrie<Integer>` is the **inverse**:
`String key → Integer value`. Consequences (both user-approved):

- **The key needs no parsing.** `get` / `containsKey` / `remove` take the raw first
  token as the `String` key — there is no `parseKey` guard and no "not an integer"
  branch on the key side (strictly simpler than the map).
- **Only `put`'s value is parsed**, as a single `Integer` token. There is **no
  rest-of-line value** — `put shore 1 2` fails the int-parse and returns an error, it
  does not store `"1 2"`. Grammar limitation (stated, mirroring how the other REPLs
  document theirs): keys cannot contain spaces through this grammar. Fine — trie demo
  keys are words.
- **`keysWithPrefix <prefix>`** is a trie-specific read command with no map/list
  analogue. Read-only (no viz change, annotated like `get`). The API's
  `keysWithPrefix("")` returns *all* keys; **the REPL treats a missing prefix as `""`**
  — `keysWithPrefix` alone lists every key (user-approved: pedagogically clean, shows
  the prefix-walk degenerating to a full traversal).

## Components

### 1. `substrate/repl/CommandResult.java` (+ `package-info.java`)

See the consolidation step above. Record `(String message, boolean quit)`; factories
`of(msg)` (quit=false) and `quitting(msg)` (quit=true). No behaviour change from the
two copies it replaces.

### 2. `trie/repl/TrieCommandInterpreter.java`

Pure: no I/O, no reference to the server. `execute(String line, RadixTrie<Integer>
trie) -> CommandResult`. Mutations it makes fire `TrieEvent`s that any attached
listener (`TrieLiveVisualizer`) broadcasts — live rendering is a side-effect of the
listener, not of this class. **`execute` never throws**: every malformed input becomes
a message.

Keys are `String` (first token, no spaces). Values are `Integer`.

Grammar:

| Command | Effect | Message | Fires event? |
|---|---|---|---|
| `put <key> <int-value>` | old = `trie.put(key, value)` | `put <key> = <v>` (new) / `set <key> = <v> (was <old>)` | yes |
| `remove <key>` | old = `trie.remove(key)` | `removed <key>` / `<key> not found` | yes (on hit) |
| `get <key>` | `trie.get(key)` | `get <key> → <v>` / `get <key> → absent` | **no** |
| `containsKey <key>` (alias `contains`) | `trie.containsKey(key)` | `containsKey <key> → <bool>` | no |
| `keysWithPrefix [<prefix>]` (alias `keys`) | `trie.keysWithPrefix(prefix)` | `keysWithPrefix "<p>" → [<k1>, <k2>, …]` | **no** |
| `size` | `trie.size()` | `size = <n>` | no |
| `help` | — | help text | no |
| `quit` (alias `exit`) | — | `bye`, quit=true | no |

Parsing: strip the line; blank or `null` -> `CommandResult.of("")` (silent no-op —
`null` guards an empty POST body in Slice D). Split off the first whitespace-delimited
token as the command (lower-cased). Then per command:

- `put`: split the rest into `<key>` + `<value-token>` (2-way split). Fewer than 2
  tokens -> `usage: put <key> <int-value>`. Parse the second token as `Integer`; on
  failure -> `not an integer: '<tok>'` (this is `put`'s only parse branch, and it is on
  the *value*, mirroring where the map guards its *key*). `put shore 1 2` → the value
  token is `1 2` → parse fails → `not an integer: '1 2'`.
- `remove` / `get` / `containsKey`: the rest is the key token. Empty rest ->
  `usage: <cmd> <key>`.
- `keysWithPrefix`: the rest is the prefix; **empty rest is allowed** and passed as
  `""` (lists all keys). Never a usage error.
- `size` / `help` / `quit` take no argument.

`unknown command: '<cmd>' (type 'help')` for anything else. `helpText()` is public
(Slice D reuses it), one line per command, same shape as the map's and list's, with
the trie-specific note that `get` / `containsKey` / `keysWithPrefix` / `size` don't
change the viz.

### 3. `trie/demo/TrieLiveReplDemo.java`

Mirrors `arraylist/demo/ListLiveReplDemo`. `main`:

1. `LiveServer server = new LiveServer(TrieWebExporter.liveHtml(), "127.0.0.1", 7070);`
   `server.start();`
2. `var trie = new RadixTrie<Integer>();`
   `trie.addListener(new TrieLiveVisualizer(server::broadcast));`
3. `BrowserLauncher.open("http://localhost:" + server.port());`
4. `runRepl(stdin, System.out, trie, new TrieCommandInterpreter());`
5. `server.stop()` in `finally`.

`runRepl(BufferedReader, PrintStream, RadixTrie<Integer>, TrieCommandInterpreter)` is a
package-private test seam (no server, no browser): read a line, execute, print the
message when non-empty, loop until a `quit` result or EOF (Ctrl-D). Identical control
flow to the list demo's `runRepl`.

## What is NOT touched

Core, events, and the Slice B transport are untouched — no new transport, zero new
deps. `LiveServer` / `JsonWriter` / `TrieLiveVisualizer` / `TrieWebExporter` /
`WebVizTemplate` from Slice B are reused verbatim, so the recurring after()-timing /
snapshot-before-settled bug has no new surface here. `trie-viz.html` and the live JS
are unchanged (no JS in this slice); they were browser-verified in Slice B. The
`CommandResult` move is a pure relocation — the record's shape and behaviour are
identical.

## Testing

- **`TrieCommandInterpreterTest`** — one assertion group per command: put (new →
  `put`, overwrite → `set … (was …)`), remove (hit → `removed`, miss → `not found`),
  get (present / absent), containsKey (+ `contains` alias), keysWithPrefix (a real
  prefix → matching keys sorted; **empty prefix → all keys**; a no-match prefix →
  `[]`), size, help, quit (+ `exit` alias). Error paths: blank line and `null` -> empty
  no-op; missing args -> `usage:`; non-integer value on `put` -> `not an integer`
  (including the `put shore 1 2` multi-token case); unknown command. **Read commands
  fire no event** — attach a recording listener (`TrieRecordingListener`) and assert
  `get` / `containsKey` / `keysWithPrefix` / `size` / `help` produce zero frames while
  `put` / `remove` (on a hit) each produce ≥1. Confirm the structure's *actual*
  behaviour rather than mirroring the map's assumptions (e.g. the first `put` into an
  empty trie emits a `CreateNode`, verified in Slice A).
- **`TrieLiveReplDemoTest`** — the `runRepl` seam: canned `BufferedReader` input ->
  captured `PrintStream`. Assert the printed messages, that a blank-message result
  prints nothing, that `quit` ends the loop (lines after it are not executed), and that
  EOF ends the loop.
- **Regression gate for Step 1**: the existing 333 tests pass unchanged after the
  `CommandResult` move, before any trie test is added.

The dumb-renderer / live JS is untested-by-design and already browser-verified in
Slice B; this slice adds no JS. Per the recurring live-viz recipe, the controller can
still smoke-test end-to-end by piping timed commands into the REPL against
`target/classes`, but that is verification, not an automated test.

## Out of scope / deferred

- `clear` command — the trie exposes no `clear()`; a remove-loop would emit N events
  and invent API the trie doesn't have. Out of scope (matches the list's reasoning).
- Browser controls (POST /command + CONTROLS token) — that is Slice D, which reuses
  this interpreter unchanged.
- The web-exporter-template consolidation (roadmap item E) remains deferred; this slice
  touches only `CommandResult`, not the exporters.

## Sequencing

Built via subagent-driven-development on `feat/trie-live-repl-command-layer`, then PR.
Three tasks:

1. **`CommandResult` consolidation** — create `substrate/repl/CommandResult` +
   `package-info`, repoint the four consumers, delete the two copies, fix the test
   imports, update javadoc. Gate: 333 tests green, unchanged.
2. **`TrieCommandInterpreter` + `TrieCommandInterpreterTest`** — the pure command layer
   on the now-shared `CommandResult`.
3. **`TrieLiveReplDemo` + `TrieLiveReplDemoTest`** — the demo wiring and its `runRepl`
   seam test.
