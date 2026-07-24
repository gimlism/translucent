# Compression-compare collapse-marker — design

**Date:** 2026-07-23
**Slice:** StandardTrie viz arc follow-up — mark, in the fat `StandardTrie` panel, the exact nodes the radix trie absorbs, so the compression lesson shows *which* nodes disappear, not just *how many*.
**Scope:** one renderer enhancement (`CompressionCompareRenderer`) + its tests. No new files, no web. Additive to `trie/compare` only.

## Motivation

PR #42 renders the fat `StandardTrie` beside the compressed `RadixTrie` with a savings banner (`standard 10 / radix 6 / saved 4`). The size difference and count tell a student that compression happened; they don't show *where*. This slice marks the fat tree's nodes that radix compression collapses, turning the abstract "4 saved" into four visibly-tagged nodes.

## What gets marked

The nodes radix absorbs are exactly the **non-root, non-key, single-child** nodes of the standard trie — a local predicate on each snapshot node, and the exact rule radix applies when it collapses a chain into one multi-character edge.

`StandardTrie` forbids dead non-key leaves (enforced by its invariants), so every non-root node is one of:
- **kept by radix:** a key node (any child count), or a branch (≥2 children) — stays an explicit radix node;
- **absorbed by radix:** non-key with exactly one child — becomes part of an edge label.

Therefore **the count of marked nodes is identically `saved()`** (standard total − radix total). The legend's number and the savings number are the same quantity computed two ways — a strong, RED-provable invariant.

For the canonical `{she, shell, shore, shy}` the four absorbed nodes are `"s"`, the `"l"` under key `"she"`, `"o"`, and `"r"` (radix edges `"sh"`, `"ll"`, `"ore"`).

## Rendering (default-on — replaces slice-1 output)

- **Only the standard panel is marked.** Radix has no absorbed nodes.
- Each absorbed node's line has its 2-character gutter swapped from `"  "` to `"· "` (U+00B7 middle dot + space). **Width is preserved** (2 chars → 2 chars), so the columns/gutter arithmetic, the stacked fallback, and the `AsciiTrieRenderer`-reuse are all unaffected.
- A third banner line is added: `  · = collapsed by radix (N nodes)`, where `N` is the marked count (== `saved()`).
- Marking is applied to the panel lines *before* layout, so it appears identically in columns and stacked modes.

```
compression compare: {she, shell, shore, shy}
  standard = 10   radix = 6   saved = 4 (40%)
  · = collapsed by radix (4 nodes)

standard (10)         radix (6)
  (root)                (root)
·   "s"                   "sh"
      "h"                   "e" ●=0
        "e" ●=0               "ll" ●=1
·         "l"               "ore" ●=2
            "l" ●=1         "y" ●=3
·       "o"
·         "r"
            "e" ●=2
        "y" ●=3
```

## Architecture

`AsciiTrieRenderer.renderTrie(snap, null)` emits one line per node in pre-order DFS (root first, then children in `children()` order), each line prefixed with the non-highlight `"  "` gutter. The marker is applied by post-processing that output — the shared renderer stays **byte-unchanged**:

- `absorbedFlags(TrieSnapshot)` — walk the snapshot in the same pre-order and produce a `List<Boolean>`, `true` for each non-root non-key single-child node. This order is identical to the renderer's line order (one flag per line), so flags zip directly onto the standard panel's tree lines.
- Over the standard panel's tree lines (all but the header), for each line whose flag is `true`, replace the leading `"  "` with `"· "`.
- The banner gains the legend line using the marked count from the same walk (so the legend and the marks can never disagree).

New imports in `CompressionCompareRenderer`: `TrieNodeSnapshot`, `TrieEdge`. `trie/core` (incl. `StandardTrie`, `RadixTrie`), `trie/events`, `substrate/**`, and `trie/viz/AsciiTrieRenderer.java` are **byte-unchanged**.

### Edge cases

- **Empty key set:** both tries are root-only; the root is never marked, so 0 marks and the legend reads `(0 nodes)` (consistent, always shown).
- **Width guard:** unchanged — marked lines have the same length as before, so `gutter + widest-right` is identical to slice 1; the stacked fallback triggers on exactly the same inputs and shows the same markers.

## Testing

Updated slice-1 tests (output changed by design):
- **Banner test** — assert the new legend line `  · = collapsed by radix (4 nodes)` is present.
- **Columns golden** (space-normalized) — regenerate the expected string with the `·` markers on the four absorbed rows and the legend line.
- **Alignment test** — expected to pass unchanged: the right (radix) panel is unmarked, and marking preserves left-panel line widths, so the gutter is identical.
- **Stacked-fallback test** — expected to pass unchanged (assertions key off headers and the lone `"r"` node, not gutters); confirm.
- **Empty-keys test** — assert `(0 nodes)` legend and that the root line is unmarked.

New tests:
- **`markedCountEqualsSaved`** — the number of `·`-marked lines equals `c.saved()` (the invariant). RED-provable: breaking the predicate breaks the equality.
- **`radixPanelIsNeverMarked`** — no `·` appears in the radix panel/right column.
- **`marksTheAbsorbedNodesNotTheKeptOnes`** — pin that the marked rows are the four single-child non-key nodes (`s`, the inner `l`, `o`, `r`) and that key/branch/root rows are unmarked (e.g. `"h"`, `"e" ●=0`, `(root)` unmarked).

## Files

- **Modified:** `trie/compare/CompressionCompareRenderer.java` (absorbed walk + gutter marking + legend line + class Javadoc note), `trie/compare/CompressionCompareRendererTest.java` (update 3 goldens, add 3 tests).
- **Unchanged (automatic):** `CompressionCompareDemo.main` output now shows markers + legend, no code change needed there.
- **Byte-unchanged / protected surface:** all of `trie/core`, `trie/events`, `substrate/**`, and `trie/viz/AsciiTrieRenderer.java`.

## Non-goals (deferred)

- Web side-by-side (its own later slice).
- Marking the radix edges that correspond to collapsed chains (the fat-side marking is the lesson).
- Colour/ANSI treatment (this renderer is plain text; the glyph carries the signal).
