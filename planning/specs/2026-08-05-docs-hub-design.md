# Docs hub — README hub, per-structure guides, and a verified code↔event map

**Date:** 2026-08-05
**Slice:** Student on-ramp arc, PR D (final code slice; E = LICENSE + Pages flip)
**Depends on:** #45 (demo grid), #46 (Launcher + README), #47 (baked `docs/viz/` pages) — all merged
**Branch:** `feat/docs-hub`

## Why

The on-ramp arc gave the repo a front door (#46) and a no-toolchain artifact (#47), but nothing
connects them to the source a student is meant to read alongside. Two concrete gaps:

1. **The zero-toolchain tier is invisible.** #47 committed five self-contained pages to `docs/viz/`
   and deliberately did not touch `README.md` (#46 was rewriting it; both editing it would have
   conflicted). The result on merged main is that the tier decision 1 placed *first* — open a page,
   no JDK, no Maven, no server — is not mentioned in the README at all.
2. **Nothing maps code to what you see.** The repo's distinguishing claim is that events come from
   the real `put()`/`add()` path. A student reading `TeachingHashMap.put` has no index telling them
   which of the 26 event types across the four structures that method will produce.

## Scope

Ships:

| file | status |
| --- | --- |
| `README.md` | edited — add zero-toolchain section, add one guide link per structure |
| `docs/guide/{list,map,treeset,trie}.md` | new — the method→event maps |
| `src/test/java/com/gimlism/translucent/GuideEventMapTest.java` | new — pins the tables both directions |
| 11 files under `src/test/` | edited — A2, `Locale.ROOT` |

No `src/main` change. `docs/guide/` sits beside `docs/viz/`, so both are student-facing and both
publish together when the repo goes public.

Explicitly out of scope: LICENSE and the Pages toggle (PR E); Javadoc edits; any algorithm
explanation in Markdown.

## Decisions

### D1 — the zero-toolchain tier is described as local files, not a URL

The repo is private, so `docs/viz/*.html` has no browsable address. The README gives local-file
instructions ("download or clone, then open `docs/viz/map.html`"), which are accurate for every
reader today. PR E *appends* a hosted-URL line to the same section rather than rewriting it, which
keeps E the one-commit flip decision 2 requires.

Rejected: writing `gimlism.github.io` links now (they 404 for the whole private period, including
for the maintainer); and a "coming soon" note (spends README space on a status that must be cleaned
up anyway).

### D2 — the map is method → events, in emission order

One row per narrating method, listing event types in the order they fire, `?` marking conditional
ones and `×n` marking repeats. This matches how the student arrives: they are looking at `put()` and
want to know what they will see.

Rejected: event→method (inverted) — the visualiser already labels its frames, so the "where did this
come from" direction answers a question students are less likely to have; and both tables — double
the rot surface for one extra lookup direction, against decision 7's rule that Markdown carries the
least because it is the only deliverable with no verification.

### D3 — a test pins the tables in both directions

`GuideEventMapTest` parses each guide table and, for every row:

1. **Resolves the method** — scans `getMethods()` for a public method matching the documented name
   and parameter count. Not `getMethod(name, types)`: generic signatures erase to `Object` and the
   guide writes `put(key, value)`, so matching on arity keeps the doc readable without encoding
   erasure into it. An unmatched name fails the test.
2. **Runs a canonical scenario** for that structure with a recording listener attached, clearing the
   recorder around each call so events are attributed to the call that produced them.
3. **Compares sets both ways** — documented ⊇ emitted (nothing fires that the doc omits) and
   documented ⊆ emitted (no row names an event that cannot happen).

★ **The forcing property.** `documented ⊆ emitted` means the canonical scenario must exercise every
conditional the table advertises. If the trie guide says `put()` can emit `SplitEdge`, the scenario
must include a key that diverges mid-edge or the test fails. A table cannot claim an event the suite
never triggers — doc and scenario hold each other honest. This is `LauncherReadmeTest`'s
both-directions check (#46) applied to behaviour rather than names.

★ **Why runtime capture, not source parsing.** Emissions are not lexically inside the methods that
cause them: `TeachingArrayList`'s `Append` fires in the private `appendInternal`, `TeachingTreeSet`'s
`Remove` fires in the private `unlink`, `RadixTrie`'s `MergeEdge` fires in `mergeWithChild`, and
`TeachingHashMap`'s `Rotation`/`Recolor` fire from red-black **sink callbacks** (`@Override public
void rotated(...)`) that are nowhere near `put()`. Static attribution would miss all of these.
Driving real calls and capturing what arrives attributes them exactly.

**Deliberately unverified:** event *order*, and whether a `?` is truly conditional. Pinning order
would need a bespoke scenario per row and would break on harmless narration changes. Order stays
reviewed prose.

### D4 — guide files carry the map, source pointers, and entry points

Roughly 25–35 lines each: a one-line what-it-is, the method→event table, the files to read alongside
it, and how to watch this structure (its `docs/viz/` page plus its demo numbers in the README grid,
by reference rather than duplication). Everything explanatory stays in Javadoc — the guide points,
never paraphrases (decision 7).

Rejected: map-only (a student landing there has no route onward); map plus worked example trace
(unverified prose duplicating what the visualiser shows better, making exactly the ordering claims
D3 does not pin).

### D5 — A2 folds into this PR

All 11 `src/test` sites move to `Locale.ROOT`. User re-decided 2026-08-04, overriding the earlier
"separate PR" call: it is test-only convention work whose failure mode is verified unreachable, so it
does not warrant its own review cycle.

## The read-path asymmetry this exists to record

The structures disagree about whether reads narrate, and the disagreement is invisible from any one
of them:

| structure | reads |
| --- | --- |
| `TeachingArrayList.get` | silent |
| `RadixTrie.get` / `containsKey` | overridden, but silent — no `emit` on either path |
| `TeachingTreeSet.contains` | **narrates** — a `Compare` per visited node |

A student who learns "reads are silent" on the ArrayList and carries it to the TreeSet is wrong.
Recording that per structure is the guides' main content, and keeping it true as the code moves is
what D3 buys.

## README changes

Two additions; nothing rewritten.

1. **"Watch one without installing anything"**, placed above Quick start: the local-file
   instructions from D1, listing all five `docs/viz/` pages.
2. **One guide link per structure**, a single line under each existing demo table.

The 25-row demo grid, the six-modes table, and the testing paragraph stay byte-unchanged.
`LauncherReadmeTest` parses that grid in both directions, so leaving it alone keeps this PR's diff
clear of #46's guard.

## Testing

- `GuideEventMapTest` — per D3. Non-vacuity must be proven by mutation: delete a documented event
  from a table (expect red), add a phantom event to a table (expect red), rename a documented method
  (expect red). All three reverted before commit.
- Existing `LauncherReadmeTest` must stay green — it is the check that the README grid still matches
  the catalog, and this PR touches the same file.
- Full suite green (currently 549 on main).

## Risks

| risk | mitigation |
| --- | --- |
| Guide table parsing is brittle to Markdown formatting | Parser is strict and fails loudly on an unparseable row rather than skipping it — a silently skipped row is a table that stops being checked |
| Canonical scenarios drift from the documented conditionals | That *is* the test: `documented ⊆ emitted` fails when a scenario stops triggering an advertised event |
| README edits collide with `LauncherReadmeTest` | Additions only; the parsed grid is untouched, and the suite proves it |
| A2 changes test meaning | Needles contain no `i`/`I`, so the failure mode is unreachable; the change is convention alignment, and the suite proves behaviour is unchanged |
