# The StandardTrie's own visualisation arc

**Date:** 2026-08-11
**Status:** approved, ready for implementation
**Scope:** two sequenced PRs. PR 1 renames only; PR 2 makes `StandardTrie` a first-class fifth
structure — six demos, a hosted page, a guide.

## What this arc is for

`StandardTrie` has existed since PR #41 and emits a complete event stream, but a student can only
ever see its *end state* — as the left-hand panel of `compression-compare.html`, drawn beside the
radix trie with the absorbed nodes ghosted. What is missing is the motion: the one-node-per-character
chain growing under `put`, and the `Prune` cascade unwinding it under `remove`. That is the whole
argument for compression, and it is currently undrawn.

Every other structure in the repo gets six demos, a hosted replay and a guide. This closes the gap.

## The finding that sizes the work

**The visualisation stack is already implementation-agnostic.** Everything downstream of the event
stream is typed on `TrieEvent`, not on the trie that emitted it:

| Component | Signature | Radix-coupled? |
|---|---|---|
| `AsciiTrieReplayer` | `extends Replayer<TrieEvent>` | no |
| `AsciiTrieVisualizer` | `extends Visualizer<TrieEvent>` | no |
| `TrieJsonSerializer` | `toJson(List<TrieEvent>)` | no |
| `TrieWebExporter` | `toHtml(framesJson)` | no |
| `TrieLiveVisualizer` | `implements TrieEventListener` | no |
| `TrieCommandInterpreter` | `execute(String, RadixTrie<Integer>)` | **yes — the only one** |

`StandardTrie` already dispatches the shared events with a full `TrieSnapshot` on every mutation
(`StandardTrie.java:93,100,111,146,154`), using the same snapshot types as `RadixTrie`.
`TrieEventFormatter.format` and `AsciiTrieRenderer.affectedPath` are both *exhaustive* switches over
all seven event types including `Prune`, and `StandardTrie`'s vocabulary is a strict **subset** of
`RadixTrie`'s — no `SplitEdge`, no `MergeEdge`. `trie-viz.html`'s only radix-specific content is a
`<title>` and an `<h1>`: two lines out of 206.

So this arc is wiring, one decoupling, and hand-written surfaces. It is not new rendering.

★ This is the payoff from a decision made three arcs ago: the substrate was built around *the event
type*, not around the structure that emits it. Read/observe paths generalised for free. The single
exception is the one component that *mutates* rather than observes.

★ It also means the `AbstractTrie` transport extract — which #41's whole-branch reviewer ruled
**not actionable** — remains unnecessary. The two tries never needed a common supertype, because
nothing downstream ever asked for one.

## Locked decisions

1. **Full parity.** All six demo cells; `StandardTrie` becomes a first-class fifth structure. The
   grid goes 4×6 → 5×6. An incomplete row is itself a defect — the reasoning that motivated
   `TreeSetDemo` in #45.
2. **Rename the Java classes; keep the public URLs.** `Trie*Demo` → `RadixTrie*Demo`, Launcher label
   `"Trie"` → `"Trie (radix)"`. But `docs/viz/trie.html` and `docs/guide/trie.md` **keep their
   paths**, because they are live public URLs as of 2026-08-10 and GitHub Pages serving a committed
   `/docs` directory has no redirect mechanism. Accepts one asymmetry (a page named `trie.html`
   holding the radix trie) in exchange for zero broken links.
3. **A narrow capability interface** for the REPL seam, not a functional adapter and not a duplicated
   interpreter.
4. **Sixth page and fifth guide.** `docs/viz/standard-trie.html` and `docs/guide/standard-trie.md`.
5. **The demos mirror the radix scenario exactly** — same keys, same order.

## PR 1 — the rename

Rename only. No new files, no behaviour change, no new tests. It is separate because PR 2's Launcher
rows, README rows and guide all name classes PR 1 defines, and because git renders "renamed +
modified" as delete+add once content changes land alongside — a reviewer then cannot separate *did
the rename break something* from *is this new code correct*. This mirrors #45's recorded sequencing
logic ("A FIRST because B and D both hardcode class names").

### Renames — 6 main + 5 test classes

| From | To |
|---|---|
| `trie/demo/TrieDemo` | `RadixTrieDemo` |
| `trie/demo/TrieVizDemo` | `RadixTrieVizDemo` |
| `trie/demo/TrieWebVizDemo` | `RadixTrieWebVizDemo` |
| `trie/demo/TrieLiveWebVizDemo` | `RadixTrieLiveWebVizDemo` |
| `trie/demo/TrieLiveReplDemo` | `RadixTrieLiveReplDemo` |
| `trie/demo/TrieLiveControlsDemo` | `RadixTrieLiveControlsDemo` |
| `trie/demo/TrieDemoTest` | `RadixTrieDemoTest` |
| `trie/demo/TrieVizDemoTest` | `RadixTrieVizDemoTest` |
| `trie/demo/TrieWebVizDemoTest` | `RadixTrieWebVizDemoTest` |
| `trie/demo/TrieLiveReplDemoTest` | `RadixTrieLiveReplDemoTest` |
| `trie/demo/TrieLiveControlsEndToEndTest` | `RadixTrieLiveControlsEndToEndTest` |

**Explicitly NOT renamed:** everything in `trie/viz/`, `trie/events/`, `trie/repl/`, `trie/compare/`
and their tests (`TrieJsonSerializerTest`, `TrieWebExporterTest`, `TrieEventFormatterTest`, …). Those
components are implementation-agnostic and PR 2 makes them serve *both* tries, so `Trie*` is the
correct name for them. Renaming them would be actively wrong.

Use `git mv` so `git log --follow` survives.

### Non-class edits

- `Launcher` — the `TRIE` package constant, six FQCNs, label `"Trie"` → `"Trie (radix)"`.
- `RegenerateDocs` — the `TrieWebVizDemo::buildHtml` reference.
- `README.md` — line 5 (prose), line 64 (the quick-start example names `TrieVizDemo`), line 106
  (section heading), lines 110–115 (the six table rows).

### The substring hazard, restated

#45's trap does **not** apply unchanged and should not be copied across. There, the names nested
(`Demo` ⊂ `VizDemo` ⊂ `WebVizDemo` ⊂ `LiveWebVizDemo`), so longest-first still corrupted. These six
do not nest — no `Trie*Demo` name contains another. Two different hazards replace it:

1. `TrieDemo\b` will **not** match inside `TrieDemoTest` — the `\b` fails against the following `T`.
   So the test classes need their own explicit renames; they do not fall out of the same pass.
2. Once PR 2 introduces `StandardTrieDemo`, a later naive re-run of a `TrieDemo` pass would corrupt
   it into `StandardRadixTrieDemo`.

Negative lookbehind `(?<!\w)TrieDemo\b` handles (2) and makes the pass idempotent — the same fix as
#45, earning its place for a different reason.

Use `find … -exec` rather than an unquoted `$files` variable: zsh does not word-split, so a
`perl -pi $files` pass silently treats the whole list as one filename (recorded gotcha).

### Verification

1. Suite stays at **exactly 572/572**. Any change in count means something was added or lost.
2. All six `DocsPagesGoldenTest` cases (five pages plus `index.html`) stay green **without
   regeneration** — class names do not appear in recorded output bytes (established empirically in
   #45). A golden that moves proves the rename reached further than intended.
3. `LauncherCatalogTest` (`Class.forName` per FQCN) and `LauncherReadmeTest` (README ↔ CATALOG, both
   directions) cover the Launcher and README edits: a missed rename fails on class load, a missed
   README row fails on the reverse check.
4. `mvn clean` after the branch switch — the recorded stale-bytecode variant. The JDT/Eclipse
   language server throws phantom project-wide errors after bulk renames; **mvn is the source of
   truth.**
5. Post-merge Pages check, even though PR 1 touches no file under `docs/` — #51 confirmed Pages
   rebuilds on every push to `main` regardless.

## PR 2 — the structure

### `PrefixMap<V>` — `trie/core/PrefixMap.java`

```java
public interface PrefixMap<V> extends Map<String, V> {
    List<String> keysWithPrefix(String prefix);
}
```

`RadixTrie` and `StandardTrie` each gain `implements PrefixMap<V>` — one line each, no bodies move.
Both already declare `public List<String> keysWithPrefix(String)` with identical signatures
(`RadixTrie.java:269`, `StandardTrie.java:217`), so the interface is satisfied by existing code. The
interpreter's other five operations (`put`, `remove`, `get`, `containsKey`, `size`) are `Map` methods
both tries inherit from `AbstractMap<String,V>`.

**The Javadoc must state what this is not.** It names a capability the REPL requires; it does not
extract shared implementation. #41's reviewer ruled the ~70% plumbing parallel to be justified
duplication for a teaching library — each structure must read standalone, and node types differ
fundamentally — and that ruling stands: both tries keep every line they have. Without this note in
the source, a future reader or Copilot pass sees an interface over two tries and reasonably concludes
the ruled-out extract was started and abandoned.

Note this is the repo's first *structure-capability* interface; every existing interface
(`TrieEventListener`, `StructureEventListener`, `EventRenderer`, …) is an observer interface, and all
four command interpreters take a concrete structure type. The deviation is justified by tries being
the only structure with two implementations.

### `TrieCommandInterpreter` reseat

`execute(String, RadixTrie<Integer>)` → `execute(String, PrefixMap<Integer>)`. Every call site in the
switch resolves unchanged.

The help text's first line is hardcoded `"RadixTrie live REPL — …"`. `helpText()` has **no external
callers** — it is reached only via `case "help"`, which already holds the trie. So it becomes
`helpText(PrefixMap<?> trie)`, deriving the name from `trie.getClass().getSimpleName()`.

That self-heals: PR 1 renames the demo classes, and if `RadixTrie` itself is ever renamed the help
text follows. The alternative — a hand-written name per demo — is a fourth copy of a string that can
drift from the class it describes, the exact failure mode this repo keeps closing. Accepted
trade-off: it deviates from the three sibling interpreters' no-arg `helpText()`.

### The six `StandardTrie*Demo` classes

Structural mirrors of their `RadixTrie*` counterparts, with `new StandardTrie<Integer>()` in place of
`new RadixTrie<Integer>()`, in package `trie/demo/`.

**Scenario — identical to the radix demos:** `put shore, she, shell` then `remove shell, she`. The
two replay pages then differ in exactly one variable — the structure — so every difference a student
sees between `trie.html` and `standard-trie.html` is caused by compression and nothing else.

Traced against `StandardTrie`, that scenario exercises its entire event vocabulary:

| Step | Emits |
|---|---|
| `put("shore")` | `CreateNode` ×5 (s·h·o·r·e), `Put` |
| `put("she")` | `Descend` ×2, `CreateNode` (e), `Put` |
| `put("shell")` | `Descend` ×3, `CreateNode` ×2 (l·l), `Put` |
| `remove("shell")` | `Descend` ×5, `Remove`, `Prune` ×2 |
| `remove("she")` | `Descend` ×3, `Remove`, `Prune` ×1 |

All five of its types — `Descend`, `CreateNode`, `Put`, `Remove`, `Prune` — with no `SplitEdge` or
`MergeEdge`, which is exactly the contrast. This satisfies the guide's forcing property without
inventing a scenario.

**Prose is rewritten, not copied.** The radix demos narrate *"watch edges split and branch"* and
*"leaves prune and single-child edges merge"* — both actively false for a standard trie, which never
splits or merges. Replacements narrate the one-node-per-character chain growing and the prune cascade
unwinding it.

`DEFAULT_PORT` stays **7070**, matching all twelve existing live demos — a student learns one port.

⚠️ **Known limitation, to be stated in the demos' Javadoc:** the live demos ignore `args` entirely
(`TrieLiveWebVizDemo.java:22-23` — `main` reads `DEFAULT_PORT` directly and never parses a port), so
the radix and standard live demos **cannot run concurrently**; the second fails to bind 7070. The two
*replay* pages can of course be opened side by side, since they are static files. Making the port an
argument is a reasonable follow-up across all fourteen live demos, but it is a separate, uniform
change and does not belong in this arc.

### The template token

`trie-viz.html` carries the structure name twice — `<title>RadixTrie — replay</title>` and
`<h1>RadixTrie — web replay</h1>`. One `/*__STRUCTURE__*/` token covers both, since `String.replace`
substitutes every occurrence.

New `WebVizTemplate.injectNamed(resource, frames, live, controls, structure)` — deliberately a
distinct name, not a five-arg overload of `inject`, because two same-typed positional overloads
differing only in arity is a call-site trap. It substitutes **STRUCTURE, LIVE, CONTROLS, then FRAMES
last**, preserving the ordering invariant. `TrieWebExporter`'s three entry points (`toHtml`,
`liveHtml`, `controlsHtml`) gain a structure parameter; **no default is provided**, because a shared
exporter silently defaulting to one implementation is the ambiguity this arc removes.

Call sites to update: three radix demos, three new standard demos, and the existing test sites in
`TrieWebExporterTest`, `TrieWebExporterControlsTest`, `TrieWebExporterLiveTest`,
`TrieLiveVizEndToEndTest` and `RadixTrieLiveControlsEndToEndTest`. `CompressionCompareWebExporter` is
untouched — it uses `injectStatic` and has its own template.

⚠️ **Do not** call the existing `inject(...)` and then `.replace(STRUCTURE_TOKEN, name)` on the
result. That substitutes into a string already containing user frame data, so a trie key or value
serialising to that token literal would be rewritten. `WebVizTemplate`'s own Javadoc records this as
"the fix for the earlier map-side ordering bug": fixed flags first, user data last, order centralised
so no per-structure exporter can get it wrong. The shortcut reintroduces a bug the class exists to
prevent — which is itself the argument for the substitution living inside `WebVizTemplate`, where the
invariant is visible, rather than in `TrieWebExporter`, where it is not.

**The free proof:** filling the new token with the identical string `"RadixTrie"` must leave
`docs/viz/trie.html` byte-for-byte unchanged, which `DocsPagesGoldenTest` already asserts against the
committed public file. A green golden is therefore positive evidence the extraction preserved
behaviour, not merely an absence of complaints.

### Copy-paste guard

Six near-identical classes is this PR's highest-risk shape. A cheap assertion catches the whole class
of error: `standard-trie.html` contains `"StandardTrie"` and **not** `"RadixTrie"`, and the converse
for `trie.html`.

The goldens alone cannot catch this: regenerate with a wrong title and the committed bytes agree with
the generated bytes, green.

### The page

`RegenerateDocs.vizPages()` gains one entry, placed **before** the radix `trie.html` entry so the
site's card order matches the menu order:

```java
new Page("viz/standard-trie.html", "Standard trie",
         "One node per character — the chain a radix trie compresses away.",
         "guide/standard-trie.md", StandardTrieWebVizDemo::buildHtml)
```

The blurb is one sentence in the house style of the existing five (compare *"A radix trie whose edges
split and merge as keys arrive and leave."*).

`SiteIndex` builds the sixth card from it automatically — the generated-not-hand-written rule from
#49 doing its job. `buildHtml()` must be `public` on `StandardTrieWebVizDemo`, matching the widening
#47 applied to the other four WebVizDemos.

**The existing radix entry's display title changes too:** `Page` title `"Trie"` → `"Radix trie"`, and
the matching README hosted-links row label with it. That is display text on the index card, not a
path — decision 2 disambiguates everywhere *except* URLs, and leaving one card labelled plain "Trie"
beside a "Standard trie" card would reintroduce the exact ambiguity this arc removes. `docs/index.html`
regenerates accordingly.

### The guide

`docs/guide/standard-trie.md`, following the four existing guides: a method → events table with the
contrast explicit. `put` grows one node per character (`CreateNode` ×n) where the radix trie splits an
edge; `remove` unwinds via a `Prune` cascade where the radix trie merges. No `SplitEdge`/`MergeEdge`
row exists, and that absence *is* the lesson.

⚠️ The table must not overclaim. `remove("shell")` prunes twice and `remove("she")` prunes once, and
#48's structural lesson is that the suite compares event **names** only — order and conditional
markers are pure prose, checked by review, not by tests. Every conditional marker gets verified
row-by-row against source before merge.

### Launcher ordering

The menu is explicitly ordered by teaching difficulty. A standard trie is the naive form and the
radix trie is the optimisation of it, so the coherent sequence is naive → optimised → measured:

```
19–24  Trie (standard)      <- NEW
25–30  Trie (radix)         <- was 19–24
31     Trie (compression)   <- was 25
```

The README's trie rows renumber with it. Covered by `LauncherReadmeTest` (both directions) and
`LauncherMenuTest` (whole rendered rows — the original `contains("4")` version passed for a menu
missing rows). Menu numbers are display only; no URL depends on them.

### Which guards move, and which do not

| Guard | Self-heals? | Action |
|---|---|---|
| `DocsPagesGoldenTest` | yes — parameterised over `pages()` | regenerate; goes 6→7 cases by itself |
| `SiteIndexTest` (both directions) | yes — reads disk + index | none |
| `ReadmeSiteLinksTest` count | yes — `vizPages().size() + 1` (`:90`) | none |
| `ReadmeSiteLinksTest` URL presence | **no** | add the hosted URL to README |
| `GuideEventMapTest.cases()` (`:172`) | **no** — hand-written registry | add a fifth `GuideCase` + scenario |
| `LauncherCatalogTest` / `LauncherReadmeTest` | **no** — CATALOG and README are longhand | add six rows to each |
| README prose line 5 | **no** | "Four data structures … radix **Trie**" → five, two tries |

The two hand-written registries are where a defect would land, and both fail loudly: `cases()` is
pinned against the guide directory in both directions by #51/#52, so a fifth guide file with no case
fails naming the file.

★ The general rule this arc keeps meeting: **generated surfaces self-heal, hand-written ones point at
nothing.** `Page.path()` self-heals because `main()` writes wherever the record points. `Page.guide()`
does not. Only the hand-written side can go stale.

## Verification

1. `mvn clean test` — `clean` is mandatory on branch switches (recorded stale-bytecode variant).
2. Run `RegenerateDocs`; confirm `docs/viz/trie.html` and the other four are **byte-unchanged**, and
   only `standard-trie.html` and `index.html` are new or modified.
3. Determinism: two consecutive generator runs byte-identical (#47's method).
4. Mutation-prove the new guards. At minimum: delete the fifth `GuideCase` → red naming the file;
   typo the hosted URL → red in both directions at once.
5. Browser render check on the live page after merge. Chrome MCP works over `https://` (it only ever
   refused `file://`). ⚠️ An in-page `fetch()` of the cross-origin guide links returns "Failed to
   fetch" — that is CORS, not a broken link, and is a recorded measurement artifact not to re-raise.
6. Post-merge Pages check on **both** PRs:
   `gh api repos/gimlism/translucent/pages/builds/latest --jq '{status,commit,error:.error.message}'`
   then confirm all pages 200 and SHA-256 byte-identical to the committed files.

## What could go wrong

- **Rename overreach** (PR 1) — caught by the 572/572 count and by goldens that must not move.
- **Copy-paste across six mirrored demos** — caught by the title assertion plus review of the
  rewritten prose.
- **Ordering regression in `WebVizTemplate`** — avoided structurally by putting the substitution
  inside the class that owns the invariant.
- **A guide table naming events the scenario never emits** — caught by the forcing property
  (`documented ⊆ emitted`), which fired three times for real in #48. If it fires, change the
  scenario; do not trim the table.

## Sequencing for implementation planning

PR 1 and PR 2 get **separate implementation plans**. PR 1 is small, mechanical and fully verified by
existing guards; PR 2 is a normal multi-task branch of the shape #48 and #49 used (spec → plan →
subagent-driven development with a ledger). Plan and ship PR 1 first — PR 2's Launcher rows, README
rows and guide all name classes PR 1 defines.

## Out of scope

The parked items remain parked: JavaFX in-process renderer, compare-to-JDK mode, collapse-MORPH
animation. The `AbstractTrie` transport extract stays not-actionable — and this arc's central finding
is that it is also unnecessary.
