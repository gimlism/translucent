# TreeSet ASCII Visualizer — Slice 1 (terminal renderer)

**Date:** 2026-07-16
**Structure:** TeachingTreeSet (red-black tree)
**Slice:** 1 of the TreeSet viz arc (this ASCII slice → then the 4-slice web arc: static → live SSE → REPL → controls, each its own later brainstorm)
**Mirrors:** ArrayList ASCII viz (PR #10-era trio), Trie ASCII viz (PRs #10/#15), HashMap ASCII viz

## Goal

The first non-console renderer for `TeachingTreeSet`: a pure `EventRenderer<SetEvent>`
that turns immutable set snapshots/events into a **sideways red-black tree** drawn in
ASCII, with red/black colour and a per-node highlight tracking the affected node each
frame. Wrapped in the two standard substrate subclasses (`Replayer`/`Visualizer`) plus
a demo, this gives the TreeSet the same terminal-viz trio every sibling structure has,
and is the ASCII analogue that later web slices will parallel.

The genuinely new work is rendering a **binary, colour-carrying** tree that can hold an
arbitrary number of elements. The HashMap already solved the *shape* (sideways: right
subtree above → node → left subtree below) and the *colour mechanism* (`Palette`) for its
treeified bins — but those bins are tiny (≤8 nodes) and `Palette` speaks `hashmap.events.Color`
while the set's `SetNodeSnapshot` carries a plain `boolean red`. So the renderer is a
deliberate re-derivation, not a copy, and colour becomes a small shared-substrate decision.

## Scope

**In scope:** the pure `AsciiSetRenderer` (header + sideways RB tree + colour + highlight);
the trivial `AsciiSetReplayer`/`AsciiSetVisualizer` substrate subclasses; a `TreeSetVizDemo`
scripted to force at least one rotation and one recolour (so RB rebalancing is visible) plus
a removal; promotion of the `Palette` mode-detection into a shared `substrate/viz/ColorMode`
and retrofit of the map's `Palette` onto it; tests.

**Out of scope (later slices / untouched layers):** all four web slices (static replay, live
SSE, REPL, browser controls); any change to `treeset/core`, `treeset/events`, or the
`substrate/rbtree` kernel — the events and snapshots are consumed exactly as they ship, so the
recurring **snapshot-before-settled** bug has zero surface this slice. Deep-tree layout is a
non-goal: the sideways indentation is chosen consciously for the **small teaching regime
(~5–15 elements)**, matching the map's ≤8-node bins.

## Architecture

Reuses the established ASCII-viz seam. New pieces are per-structure; the one shared-substrate
change is the colour-detection promotion.

| File | Status | Role |
|---|---|---|
| `treeset/viz/AsciiSetRenderer.java` | new | The one real class. `EventRenderer<SetEvent>`. Holds the set-specific drawing: header, sideways RB tree, colour formatting, `affectedElement` highlight. |
| `treeset/viz/AsciiSetReplayer.java` | new | `extends Replayer<SetEvent>` — trivial substrate subclass (constructor forwarding), mirrors `AsciiTrieReplayer`. |
| `treeset/viz/AsciiSetVisualizer.java` | new | `extends Visualizer<SetEvent>` — trivial substrate subclass, mirrors `AsciiTrieVisualizer` (incl. a convenience ctor that builds a default renderer). |
| `treeset/demo/TreeSetVizDemo.java` | new | First `treeset/demo/` file (core shipped none). Records a scripted insert/remove story, replays it through `AsciiSetReplayer`. |
| `substrate/viz/ColorMode.java` | new | Promoted colour-mode detection: `enum {PLAIN, ANSI}` + `detect()` (was `Palette.auto`'s core) + `decideMode(noColorEnv, consolePresent, terminal)` + `isTerminal(console)`. Structure-agnostic; the subtle NO_COLOR + JDK-22-reflection logic from PR #7 lives here once. |
| `hashmap/viz/Palette.java` | edit | Retrofit: `auto()` delegates to `ColorMode.detect()`; `decideMode`/`isTerminal`/the `Mode` enum move out to `ColorMode`. Its `node(key, Color)` formatting stays local. Behaviour unchanged. |

**Colour representation:** the renderer speaks the `boolean red` its `SetNodeSnapshot` already
carries — no new colour type is invented. `ColorMode` is only about *where output goes* (tty vs
pipe/NO_COLOR), never about a node's colour. The set's node formatting is a thin local helper:
`(element, boolean red)` → `elem(R)`/`elem(B)` in PLAIN, or ANSI-red element in ANSI.

## Data flow

```
TeachingTreeSet mutations
   → SetEvent stream (recorded by SetRecordingListener, already shipped)
   → AsciiSetReplayer / AsciiSetVisualizer
   → AsciiSetRenderer.renderEvent(e)
        = SetEventFormatter.format(e)                     [caption, already shipped]
        + renderSet(e.after(), affectedElement(e))        [the new drawing]
   → PrintStream (Visualizer) or String frames (Replayer)
```

## The renderer in detail

**`renderSet(SetSnapshot snap, Object highlight)`** — a header line `set: size=N`, then the tree
drawn sideways by an in-order-ish recursion: right subtree (depth+1, `┌─` connector) → this node
→ left subtree (depth+1, `└─` connector), reusing the map's `"    ".repeat(depth)` indentation.
An empty set (`root == null`) prints the header only. Each node line is:

```
<hi><indent><connector><label>
```

where `<hi>` is `"> "` if this node is the highlighted one else `"  "` (a uniform 2-char
left margin, like the map's bucket highlight and the trie's path highlight), and `<label>` is
the colour-formatted element. Highlight matching is by **element value** (`equals`), unambiguous
because a set holds no duplicates; a `null` or absent highlight simply matches nothing (the same
graceful degradation the trie renderer already relies on).

**`renderEvent(SetEvent e)`** = caption + newline + `renderSet(e.after(), affectedElement(e))`.

**`static Object affectedElement(SetEvent e)`** — the counterpart of the map's `affectedBucket`
and the trie's `affectedPath`:

| Event | Highlight | Live in `after()`? |
|---|---|---|
| `Compare` | the visited node's `element()` (a moving cursor over real nodes — the set's `Descend`) | yes |
| `Add` | the new `element()` | yes (linked pre-rebalance, present in snapshot) |
| `Rotation` | `pivot()` | yes |
| `Recolor` | `element()` | yes |
| `Remove` | **`null`** (highlight nothing) | n/a — removed element is gone from `after()` |

`Remove → null` is a deliberate, documented teaching simplification: the removed element no
longer exists in the settled tree and the event carries no successor identity, so rather than
enrich the event (out of scope) or diff trees, the frame shows the post-removal tree with no
marker — the caption (`remove 8`) already names what left. This is the trie-`Prune` situation
resolved toward simplicity (the trie highlights a *surviving parent*; the set has no single such
node, so it highlights nothing).

## Error handling

Pure functions over immutable, already-validated snapshots — no I/O in the renderer, nothing to
catch. `Visualizer`/`Replayer` inherit their (tested) substrate behaviour. `ColorMode.detect()`
preserves `Palette`'s existing fail-closed semantics exactly (NO_COLOR → PLAIN; no console → PLAIN;
JDK-22 reflection failure → PLAIN so no ANSI leaks into redirected output).

## Testing

- **`AsciiSetRenderTest`** — empty set (`set: size=0`, no tree body); single node; a multi-node
  tree exercising both colours and the sideways ordering (right-above/left-below); one assertion
  per `affectedElement` case, including a **moving compare cursor** across successive frames and
  **Remove highlighting nothing**; PLAIN vs ANSI label formatting.
- **`AsciiSetReplayerTest` / `AsciiSetVisualizerTest`** — mirror the trie's: step-through frame
  sequencing and live `PrintStream` output.
- **`ColorModeTest`** — ports the map's `decideMode` cases (NO_COLOR set/empty, console
  present/absent, terminal/not).
- **Regression:** the existing `Palette` tests must stay green through the retrofit (proves the
  detection promotion is behaviour-preserving).

## Deferred / follow-ups

- The TreeSet's 4-slice **web** viz arc (static replay → live SSE → REPL → browser controls),
  each a separate brainstorm→plan→PR, mirroring map/list/trie.
- Any `Remove`-event enrichment to enable a survivor highlight (would touch `core`/`events`).
- Deep-tree ASCII layout, should a demo ever need > ~15 elements.
