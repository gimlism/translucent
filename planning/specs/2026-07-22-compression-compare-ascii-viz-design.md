# Compression-compare ASCII viz — design

**Date:** 2026-07-22
**Slice:** the StandardTrie viz arc, slice 1 — a static terminal view rendering the two tries side by side so #41's *measured* compression becomes *visible*.
**Scope:** one pure ASCII renderer + a `Comparison` extension + demo wiring. No web (deferred to its own later brainstorm, matching how every structure sequenced ASCII-first). No new viz trio for `StandardTrie`.

## Motivation

PR #41 landed `StandardTrie` (one node per character) beside the radix `RadixTrie` (single-child chains collapsed) and `CompressionCompareDemo`, which reports each one's node count on the same keys (`{she, shell, shore, shy}` → standard 10, radix 6, saved 4 / 40%). That payload is currently a **number**. This slice renders the two final trees next to each other with the savings banner, so a student *sees* the fat chains the radix trie collapses — the pedagogical payoff of the compression compare.

## Architecture

Both tries emit the **same** `TrieSnapshot` vocabulary, and `AsciiTrieRenderer.renderTrie(snap, highlightPath)` in `trie.viz` is already structure-agnostic — a `StandardTrie` snapshot renders through it today with zero new code. So this slice adds **one pure renderer**, not a new renderer trio, and touches no `core` / `events` / `substrate`.

### Static, not an event replay

The two tries emit **different** event streams for the same keys (StandardTrie steps per-character with a `Prune` cascade; RadixTrie steps per-suffix with `SplitEdge`/`MergeEdge`), so they cannot be animated in lockstep. The comparison view is therefore a **static render of the two final trees** plus a savings banner — not a frame-by-frame replay. (Event-driven animation, if ever wanted, is a separate concern.)

### New unit — `trie.compare.CompressionCompareRenderer` (pure, no I/O)

- `render(Comparison)` and `render(Comparison, int maxWidth)` → `String`.
- Per tree: call the existing `AsciiTrieRenderer.renderTrie(snap, null)` (null highlight ⇒ no `> ` marker, every line gets the plain `"  "` prefix), **strip its leading `"trie: size=N"` header line**, and prepend its own panel header (`standard (10)` / `radix (6)`). `AsciiTrieRenderer` stays **byte-unchanged** — the renderer is reused verbatim, its API not widened for a single caller; the header strip is local string surgery (split on the first `\n`), pinned by the golden test.
- **Columns layout (default):** interleave the two panels' lines. Gutter = widest left line + a fixed 3-space gap; pad every left line to the gutter, then append the corresponding right line. Unequal line counts are tolerated (the fat tree always has ≥ the compressed tree's lines, so the right panel simply ends early; defensively, a missing left line pads to gutter width).
- **Width guard → stacked fallback:** if `gutter + widest-right-line > maxWidth`, render **stacked** instead: banner, `standard (10):` + tree, a blank line, `radix (6):` + tree. This keeps the pretty side-by-side for normal teaching sets while a pathological deep key (e.g. `"internationalization"`, ~21 levels deep) degrades to stacked rather than wrapping past the terminal. `maxWidth` defaults to a fixed sensible width (~100); the parameterized overload makes the guard deterministically testable.
- **No collapse marker.** Marking the non-key single-child nodes the radix trie absorbs is a natural follow-up but is out of scope for slice 1; the node count and visible size difference carry the lesson.

### Banner

Two lines, mirroring `CompressionCompareDemo.main`'s current `printf`:

```
compression compare: {she, shell, shore, shy}
  standard = 10   radix = 6   saved = 4 (40%)
```

The percentage is `100 * saved / standardNodes`; `standardNodes` is always ≥ 1 (the root), so no divide-by-zero.

## Data flow — extend `Comparison` to carry the shapes

`CompressionCompareDemo.compare()` already builds both tries and reads each final snapshot internally, but its `Comparison` record returns only the two counts. **Extend `Comparison`** to also carry the two immutable `TrieSnapshot`s (`standardSnapshot`, `radixSnapshot`), so the renderer is a pure function of the comparison result rather than needing to rebuild anything.

- The existing count accessors (`standardNodes()`, `radixNodes()`, `keys()`) and `saved()` are **unchanged** — the `{she,shell,shore,shy}` canonical-count test keeps asserting through them.
- Only the record's canonical constructor gains two components; the two production call sites update: the empty-keys branch (which already builds a root-only snapshot — pass it for both) and the main `compare()` return (it already has both snapshots via `lastSnapshot`).
- `CompressionCompareDemo.main` becomes `System.out.print(new CompressionCompareRenderer().render(compare(keys)))` — the rendered banner subsumes the old `printf`.

## Testing

`CompressionCompareRendererTest` (new):

1. **Golden columns render** for `{she, shell, shore, shy}` — assert the full output string. Pins the banner (`saved = 4 (40%)`), both panel headers, the interleave, and gutter alignment in one place. RED-provable: any layout/label regression changes the string.
2. **Width guard → stacked** — render the canonical comparison with a small `maxWidth` (or a deep single key) and assert the output is stacked: the `radix` header line appears **after** every line of the standard tree (never interleaved on the same line). Discriminates the fallback branch non-vacuously.
3. **Alignment** — in columns mode, assert every right-panel line begins at the same gutter column (guards the padding computation independently of the golden string).
4. **Empty keys** — `compare(List.of())` → two root-only panels, `saved = 0`, renders without error.

Regression safety net for the reuse:

- The existing `AsciiTrieRenderer` tests already pin `renderTrie` output; since that class is byte-unchanged, they confirm the panel bodies are identical to the single-tree renderer.
- The existing `CompressionCompareDemo` count test survives the record extension (count accessors unchanged).

## Files

- **New:** `trie/compare/CompressionCompareRenderer.java`, `trie/compare/CompressionCompareRendererTest.java`
- **Modified:** `trie/compare/CompressionCompareDemo.java` (extend `Comparison` with the two snapshots; `main` renders), and its test if it constructs `Comparison` directly.
- **Byte-unchanged / protected surface:** all of `trie/core` (incl. `StandardTrie`, `RadixTrie`), `trie/events`, `substrate/**`, and `trie/viz/AsciiTrieRenderer.java`.

## Non-goals (deferred)

- Web side-by-side page (its own later brainstorm — the natural slice 2+).
- Collapse-marker highlighting of the absorbed single-child chains.
- Event-driven / animated comparison.
