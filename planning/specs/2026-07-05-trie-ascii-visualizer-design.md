# Teaching Radix Trie — ASCII Visualizer — Design

**Status:** Draft 2026-07-05
**Builds on:** the radix-trie core (PR #10) and the generic substrate (PR #9). Third
consumer of the `substrate/viz` seam, after the map and list visualizers. This is the
**acid test's visible payoff**: rendering the N-ary, `String`-labelled trie the binary
`TreeNodeSnapshot` couldn't express, and animating the **path-shaped focus** the
narrated `Descend` walk (D3) was built for.

## Goal

Render the radix trie's event stream visually in the terminal — a top-down indented
tree that shows edges splitting, branches forming, the walk descending a shared prefix,
and leaves pruning / edges merging on removal. Same three-unit shape as the map and list
viz slices; the substrate `Visualizer`/`Replayer` come for free.

## What the trie forces that its siblings didn't

- **Variable fan-out** → a **top-down indented tree** (one node per line, indented by
  depth), not the map's sideways *binary* layout (right-above / left-below) nor the
  list's horizontal cells row. Indentation expresses arbitrary child counts cleanly.
- **`String` edge labels** → each non-root line shows its incoming edge label in quotes
  (`"sh"`, `"ore"`), the compression made visible — vs the map's implicit single-char
  tree edges and the list's index cells.
- **Path-shaped focus** → the highlight target is a **path** (`String`), not an int
  index. Every `TrieEvent` carries a uniform `path` (mechanical *and* logical, since
  CO4), so the affected node is the one whose root-to-node label concatenation equals
  that path. The narrated `Descend` walk advances this path frame by frame — the whole
  point of D3.

## Resolved decisions

| Question | Decision |
|---|---|
| Layout | **Top-down indented N-ary tree.** Root line `(root)`; each child on its own line, indented 2 spaces per depth, showing its edge label in quotes. |
| Key marker | A key node shows `●=<value>` after its label (the root too, if the `""` key is present). Non-key internal nodes show only their label. |
| Highlight | The node at the event's locus is prefixed `> ` (others `  `), matching the map's 2-char highlight convention. The locus is normally the event's `path`; for a `Prune` (whose leaf is gone from `after()`) it is the surviving **parent** that lost the child — `path` minus the pruned edge label — so the deletion is still visible. A locus matching no node highlights nothing. |
| Colour / `Palette` | **None** — trie nodes are uncoloured (like the list renderer; unlike the map's red-black `Palette`). |
| Drive modes | **Both** a live `Visualizer<TrieEvent>` and a step-through `Replayer<TrieEvent>`, via thin `Ascii*` subclasses (same idiom as the map/list). |

## Architecture

New package `com.gimlism.translucent.trie.viz`, depending only on `trie.events` and
`substrate.viz`. Three units + a demo.

### 1. `AsciiTrieRenderer implements EventRenderer<TrieEvent>` — pure, no I/O

- `String renderTrie(TrieSnapshot snap, String highlightPath)`:
  - header `trie: size=<S>`;
  - then the tree, pre-order: `(root)` [`●=<v>` if the `""` key exists], then each node
    line `<hi><indent>"<label>"` [`●=<v>` if a key], children in sorted order.
  - `highlightPath` (`null` for none) prefixes the matching node's line with `> `.
- `String renderEvent(TrieEvent e)` — `TrieEventFormatter.format(e)` then
  `renderTrie(e.after(), affectedPath(e))`.
- `static String affectedPath(TrieEvent e)` — the path of the node to highlight (total: every
  variant carries a `path`). Usually the event's own `path`; for a `Prune` it is the parent's
  path (`path` minus the pruned edge label), since the leaf is gone. This is the trie's analogue
  of the map's `affectedBucket` / the list's `affectedIndex`, but a `String` locus rather than
  an `int`.

### 2. `AsciiTrieVisualizer extends Visualizer<TrieEvent>` — live driver

Thin subclass: `(PrintStream, AsciiTrieRenderer)` + a convenience `(PrintStream)`
defaulting `new AsciiTrieRenderer()`. All `onEvent` logic lives in the substrate base.

### 3. `AsciiTrieReplayer extends Replayer<TrieEvent>` — scrubber

Thin subclass: `(List<TrieEvent>, AsciiTrieRenderer)`. Forward/back/quit + auto-play and
the `── frame N/M ──` counter all inherited.

### 4. `TrieVizDemo` — the visual demo

The `TrieDemo` story (inserts with shared prefixes, then removes forcing prune/merge)
rendered as live frames via `AsciiTrieVisualizer`. Runnable directly; the `exec:java`
default stays the HashMap `VizDemo`.

## Example frame (golden-test target)

For `{"she"=1, "shore"=2}` after inserting `"shell"=3` (`SPLIT "she"@"sh"`… then the
terminal `Put`, path `"shell"`):

```
PUT "shell"=3 (new)
trie: size=3
  (root)
    "sh"
      "e"
        "ll" ●=3
      "ore" ●=2
>     ... (the node at path "shell" is highlighted)
```

(Exact indentation/markers pinned by golden-string tests during TDD.)

## Testing strategy (TDD)

- **Renderer (golden strings):** hand-built `TrieSnapshot`s exercise the root, a non-key
  branch, multiple `String`-labelled children (sorted), a key node with `●=value`, the
  `""`-key root, and the `> ` path highlight (incl. a non-matching path → no highlight).
  `affectedPath` returns each event's `path`.
- **Live visualizer:** attach to a real `RadixTrie`, run inserts + removes, assert the
  captured output contains the expected labels and node lines, and that the highlight
  tracks the narrated `Descend` walk down a shared prefix.
- **Replayer:** recorded stream + scripted stdin (`\n`, `b`, `q`) and auto-play; frame
  counter correct (inherited behaviour, but exercised for the trie event type).

## Out of scope (still deferred, per the Fable review)

- The remaining D4 items — moving `Palette`'s terminal detection to `substrate.viz`
  (only when a second *coloured* renderer needs it) and consolidating the event-grammar
  conventions into `substrate/package-info` as the "recipe" for a new vocabulary.
- The second trie implementation (standard one-char-per-edge) for a side-by-side compare.
- Web / JavaFX renderers.
