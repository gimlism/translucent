# TreeSet — Core (data structure + event vocabulary)

**Date:** 2026-07-16
**Structure:** TeachingTreeSet (the 4th teaching structure)
**Slice:** Core only — the data structure, its event vocabulary + snapshot, and
console/recording consumers. **No visualization** (ASCII or web) in this spec.
**Mirrors:** the HashMap core arc (PRs #1–#3) and the trie core (PR #10) — a
"core + console/recording" first slice, with viz as separate later arcs.

## Goal

Add a **comparison-ordered Set** backed by a red-black tree — the 4th structure on
the shared instrumentation substrate, and the strongest test yet that the
event/snapshot substrate generalizes. It is the synthesis the roadmap anticipated
("closest kin to the trie"): a **node-link binary tree** (trie-like recursive
snapshot) rebalanced by **red-black rotations/recolors** (map-like events).

Two teaching lessons distinguish it from the HashMap's treeified bins:

1. **The RB tree is the whole structure**, ordered by the element's own comparison
   — not a collision optimization inside a hash bin ordered by `hash,seq`.
2. **The comparison walk is visible.** A dedicated `Compare` narration event
   animates the search descent, including on pure reads (`contains`).

## Key decisions (locked during brainstorming, 2026-07-16)

- **Scope: core only.** ASCII viz and the 4-slice web arc are deferred to their own
  later brainstorms.
- **Shared RB core, extracted to the substrate.** A new `substrate/rbtree` package
  holds a structure-neutral red-black rebalancing kernel. The TreeSet is its first
  consumer. **The HashMap is NOT touched** in this spec; a deferred follow-up
  migrates its `TreeNode` onto the shared core (the rule-of-three confirmation).
  Extract-forward: prove the abstraction on the Set before editing stable,
  thrice-bitten merged HashMap RB code.
- **Ordering: natural + optional `Comparator`.** `TeachingTreeSet<E>` is unbounded
  (JDK-faithful); a private `compare` uses the supplied comparator if present, else
  casts to `Comparable`.
- **Interface surface: full `NavigableSet`.** Matches the map's full-`Map`-compliance
  ethos. The heaviest, most RB-orthogonal part (write-through range views) is
  sequenced LAST so it can split into a follow-up PR if the branch grows large.
- **Reads narrate.** `Compare` fires during `contains`/navigation as well as
  `add`/`remove` — a deliberate, documented divergence from the map's silent-reads
  convention. It is the whole lesson of an ordered structure.
- **Events non-generic** (`Object` element), mirroring `MapEvent`.

## Architecture

New pieces only; the generic substrate's existing packages are untouched (a new
`substrate/rbtree` package is added).

### `substrate/rbtree` — the extracted, structure-neutral RB kernel

| File | Role |
|---|---|
| `RbNode.java` | `abstract class RbNode<N extends RbNode<N>>` — the self-typed base holding `N parent, left, right; boolean red;` and link accessors. F-bounded so the shared algorithm returns/assigns the concrete node type with zero casts. |
| `RedBlackTree.java` | The neutral kernel: `insertFixup(N root, N x, sink) → N`, `deleteFromTree(N root, N z, sink) → N`, and internal `rotateLeft/Right`, `setColor`, `transplant`, `minimum`, `deleteFixup`. Touches **only** `parent/left/right/red` + the sink. |
| `RbEventSink.java` | `rotated(Direction, N pivot)`, `recolored(N node, Color old, Color new)`, plus a `NONE` no-op sink. Generic over the node type; each structure's sink extracts what it needs (the Set's element, later the map's key). |
| `Color.java`, `Direction.java` | Promoted here as structure-neutral enums (RED/BLACK, LEFT/RIGHT). |

**Why this boundary.** The map's RB code already isolates the two seams that make
extraction clean: ordering lives in `cmp(...)`, and events already route through a
`TreeEventSink` interface. The genuinely neutral kernel is the rebalancing math.
What stays per-structure: the **ordering** (Set = natural/`Comparator`; map =
`hash,seq`), the **search** (`find` — Set = plain BST descent emitting `Compare`;
map = dual-subtree on hash ties), and **node identity** (Set's `SetNode` holds an
element only; map's `TreeNode extends Node`, keeps values + the `next` thread).

### `treeset/core`

| File | Role |
|---|---|
| `TeachingTreeSet.java` | `extends AbstractSet<E> implements NavigableSet<E>`. Holds the root `SetNode`, `size`, an optional `Comparator`, a `modCount` for fail-fast, and the `EventDispatcher`/listener wiring. Per-structure `compare` and comparison walk. |
| `SetNode.java` | `extends RbNode<SetNode>`; adds `E element`. No value, no `next` thread. |

### `treeset/events`

| File | Role |
|---|---|
| `SetEvent.java` | `sealed interface SetEvent extends StructureEvent`, `after()` covariant to `SetSnapshot`. |
| `Compare.java` | `record Compare(Object element, Direction went, boolean found, SetSnapshot after)` — narration; snapshot unchanged, focus moves. `went` is `LEFT`/`RIGHT` for the branch taken; on the terminal comparison that lands on an equal element, `found=true` and `went` is `null`. `Direction` is reused from `substrate/rbtree` (the same enum the kernel reports rotations with). |
| `Add.java` | `record Add(Object element, SetSnapshot after)` — a new element linked at its BST slot, before fixup. |
| `Remove.java` | `record Remove(Object element, SetSnapshot after)`. |
| `Rotation.java` | `record Rotation(Direction dir, Object pivot, SetSnapshot after)` — from the shared sink. |
| `Recolor.java` | `record Recolor(Object element, Color oldColor, Color newColor, SetSnapshot after)` — from the shared sink. |
| `SetSnapshot.java` | `record SetSnapshot(SetNodeSnapshot root, int size)` — `root` nullable when empty. |
| `SetNodeSnapshot.java` | `record SetNodeSnapshot(Object element, boolean red, SetNodeSnapshot left, SetNodeSnapshot right)` — the binary analogue of `TrieNodeSnapshot`; deep-copied on emission. |
| `SetEventFormatter.java` | Human captions (mirrors `MapEventFormatter`/`TrieEventFormatter`). |
| `SetEventListener.java` | `extends StructureEventListener<SetEvent>`. |

### `treeset/consumer`

| File | Role |
|---|---|
| `ConsoleSetEventLogger.java` | Prints formatted events (mirrors `ConsoleMapEventLogger`). |
| `SetRecordingListener.java` | Buffers events for replay (mirrors `MapRecordingListener`). |

## Division of labor per operation

- **add:** the Set walks by comparison, emitting `Compare(element, LEFT/RIGHT,
  found=false, …)` at each node; on reaching a null slot it links the new red leaf,
  increments `size`, emits `Add`, then calls `RedBlackTree.insertFixup(root, node,
  sink)` which emits `Rotation`/`Recolor`. A comparison that finds an **equal**
  element emits `Compare(found=true)` and returns `false` with **no `Add`**.
- **remove:** the Set walks (emitting `Compare`) to locate the node, unlinks +
  decrements `size` via `RedBlackTree.deleteFromTree(root, node, sink)` (successor
  splice + fixup, all link-only), then emits the terminal `Remove`.
- **contains / navigation:** the walk emits `Compare` only; no structural event.
- The Set's `RbEventSink` translates the neutral `rotated`/`recolored` callbacks
  into `SetEvent`s carrying a fresh `SetSnapshot`.

## Invariant: snapshot-before-settled (named design constraint)

This bug has bitten the HashMap RB code three times: an event's snapshot captured
before the mutation committed. The extracted kernel and the Set must **mutate the
field first, then emit** — the discipline the map's `setColor` already follows:

- `Recolor`/`Rotation`: flip the colour / relink **before** the sink fires (kernel
  responsibility).
- `Add`: link the node and increment `size` **before** emitting `Add`.
- `Remove`: unlink and decrement `size` **before** the terminal `Remove`;
  intermediate rebalance events emit against the partially-rebalanced but
  link-consistent tree, exactly as the map's delete does.

The plan's tasks assert this explicitly rather than discovering it in review.

## First-event-on-empty semantics (decision + verification flag)

The "mirror-too-literally" trap bit both list (`Grow`-not-`Append`) and trie
(`CreateNode`-not-`Put`). Decided:

- `add` to an **empty** set → first event is `Add` (the black root). No `Compare`
  (nothing to compare against); no separate node-creation event — unlike the trie,
  every Set node **is** an element, so `Add` is the node creation. (The "don't
  mirror the trie too literally" checkpoint.)
- `add` of a **second** element → `Compare` (vs root) → `Add` → possibly
  `Rotation`/`Recolor`.
- `contains` on an **empty** set → **no event** (the walk never starts).

Flagged for empirical confirmation once code exists (assert the exact first-frame
type), as the other structures' first-frames were verified.

## `NavigableSet` surface, grouped by how it rides the RB tree

Task-sequenced in this order so the range views land last:

1. **Core mutators/queries:** `add`, `remove`, `contains`, `size`, `clear`,
   in-order `iterator()` (fail-fast via `modCount`, with `Iterator.remove`).
2. **Endpoints & navigation (cheap BST walks):** `first`/`last` (min/max),
   `lower`/`floor`/`ceiling`/`higher`, `pollFirst`/`pollLast`, `comparator`.
3. **Reverse:** `descendingIterator`, `descendingSet`.
4. **Range views (heavy, RB-orthogonal):** `headSet`/`tailSet`/`subSet` in both the
   `SortedSet` and inclusive-bounded `NavigableSet` forms — **write-through backing
   views** (JDK semantics) over the same tree. May split into a follow-up PR.

The RB-tree lesson is complete at step 2.

## Testing strategy (TDD, mirrors the existing core suites)

- **RB invariants** — port the `RedBlackInvariants` / `RadixTrieInvariants` pattern:
  after every mutation, assert root-is-black, no red-red, equal black-height, and
  BST ordering. Adversarial insert/delete sequences.
- **Ordering fidelity** — in-order iteration is sorted under both natural and a
  `Comparator` (e.g. reverse).
- **`NavigableSet` contract** — `floor`/`ceiling`/`lower`/`higher`/`poll*` and range
  views against a `java.util.TreeSet` oracle; range-view write-through.
- **Event/snapshot** — `Compare` fires on reads; duplicate-`add` emits no `Add`;
  snapshot-before-settled frames are true after-images; first-event-on-empty is
  `Add`.
- **Shared-core reuse** — a `substrate/rbtree` test drives the kernel directly over
  a trivial test node subclass, proving it is genuinely structure-neutral.

## Out of scope (each a clean future slice)

- **HashMap migration** onto `substrate/rbtree` (the rule-of-three follow-up).
- **All visualization** — ASCII, and the 4-slice web arc (static → live SSE → REPL
  → browser controls). This spec is core only.
- **`Comparator` in web/REPL front-ends** (a viz-slice concern).
- **Strict fidelity of *compound* range views.** `NavigableSet` compliance is full for
  the common single-level cases (a plain `subSet`/`headSet`/`tailSet` is live and
  write-through; `descendingSet()` on the base set is write-through). Three *compound*
  behaviours are deliberate teaching simplifications: a range view's `iterator()` is
  not fail-fast, `descendingSet()` of a range view (and a range view of a
  `descendingSet`) is a detached copy rather than write-through, and a range view's
  `descendingIterator().remove()` is unsupported (the ascending `iterator().remove()`
  and element `remove` are the write-through removal paths). Full write-through of
  arbitrarily-nested views is a clean future refinement, not part of the RB-tree lesson.
