# Color/Direction enum dedup

**Date:** 2026-07-20
**Status:** Design approved, ready for planning
**Slice type:** Refactor / consolidation (no new user-facing surface, no output change)

## Goal

Retire `hashmap/events/Color.java` and `hashmap/events/Direction.java` — which are
byte-identical copies of `substrate/rbtree/Color.java` and
`substrate/rbtree/Direction.java` — so that the `substrate/rbtree` kernel is the
single source of truth for red-black colour and rotation direction across **both**
of its consumers (the map's treeified bins and the `TreeSet`).

This is the natural follow-on to the HashMap → substrate/rbtree migration
(PR #37). That slice retired the map's duplicate *RB machinery* but deliberately
left the *enum vocabulary* duplicated, bridging substrate enums → map-local enums
at the sink boundary, because `Color`/`Direction` are woven into the map's
**public** event vocab (`Rotation`, `Recolor`, `TreeNodeSnapshot`) and deduping
them ripples into the formatter, serializer, and viz. That ripple is this slice.

Net effect: one `Color` enum and one `Direction` enum in the codebase, both under
`substrate/rbtree`, consumed directly by the map's public events and the set's.

## Background: the precedent asymmetry

The `TreeSet` was built natively on the substrate, so its public event types
already import the substrate enums directly:

```java
// treeset/events/Rotation.java
import com.gimlism.translucent.substrate.rbtree.Direction;
public record Rotation(Direction dir, Object pivot, SetSnapshot after) implements SetEvent {}
```

The set has **no** local enum copies and **no** bridge. The map, retrofitted onto
the substrate in #37, still keeps a private enum vocabulary and converts at the
boundary via two `bridge()` ternaries in `TeachingHashMap.sinkFor`:

```java
private static Direction bridge(substrate.rbtree.Direction d) {
    return d == substrate.rbtree.Direction.LEFT ? Direction.LEFT : Direction.RIGHT;
}
private static Color bridge(substrate.rbtree.Color c) {
    return c == substrate.rbtree.Color.RED ? Color.RED : Color.BLACK;
}
```

This slice makes the map match the set. It is dependency-correct in the only
possible direction: the map's public API may depend on `substrate.rbtree` types
(the set already does); the reverse — a shim making `substrate` depend on
`hashmap.events` — is forbidden (substrate must not know its consumers), and Java
has no type alias, so there is no third option. The status quo (keep the bridge)
*is* the debt being retired.

## Why this is low-risk: name-identical enums

`hashmap.events.Color` and `substrate.rbtree.Color` both declare `{ RED, BLACK }`;
the `Direction` pair both declare `{ LEFT, RIGHT }`. Every downstream consumer
serializes/renders via `.name()` / `toString()`:

- `MapEventFormatter`: `rc.oldColor() + " -> " + rc.newColor()`
- `MapJsonSerializer`: `rc.oldColor() + "→" + rc.newColor()`
- `Palette.node`: `color == Color.RED ? ... : ...`

Because the constant names are identical, **every serialized JSON string and every
rendered ASCII/SVG frame is byte-identical before and after this slice**. This is a
pure type-identity refactor with zero observable behaviour change. The suite count
stays **476/476**.

## Production changes (~7 files)

1. **Delete** `hashmap/events/Color.java` and `hashmap/events/Direction.java`.

2. **Repoint the three public event types** to import
   `substrate.rbtree.{Color,Direction}`:
   - `hashmap/events/Rotation.java` — `Direction direction`
   - `hashmap/events/Recolor.java` — `Color oldColor, Color newColor`
   - `hashmap/events/TreeNodeSnapshot.java` — `Color color`

3. **`hashmap/core/TeachingHashMap.java`:**
   - Replace the two `hashmap.events.{Color,Direction}` imports with
     `substrate.rbtree.{Color,Direction}` imports (the import clash that forced
     the FQN in `sinkFor` is now gone, so `sinkFor`'s parameter types can use the
     plain imported names).
   - `sinkFor` emits the substrate `dir` / `oldColor` / `newColor` **straight
     through**: `emit(new Rotation(i, dir, pivot.getKey(), snapshot()))` and
     `emit(new Recolor(i, node.getKey(), oldColor, newColor, snapshot()))`.
   - **Delete both `bridge()` methods.**
   - The `n.red ? Color.RED : Color.BLACK` line (TreeNodeSnapshot construction)
     now resolves `Color` to the substrate enum — text unchanged.

4. **`hashmap/viz/Palette.java`:** import `substrate.rbtree.Color` instead of
   `hashmap.events.Color`; body unchanged (`Color.RED`).

5. **`hashmap/events/MapEventFormatter.java`, `hashmap/viz/MapJsonSerializer.java`:**
   expected **no change** (they read via `.name()`/`toString()`, no enum import).
   Implementation step: `git grep` for any residual reference to the deleted
   types and fix if found.

## Test changes (~9 files)

**Mechanical enum-import repoint** (assertions unchanged — name-identical):
`hashmap/core/TreeSnapshotTest`, `hashmap/core/TreeifyTest`,
`hashmap/viz/PaletteTest`, `hashmap/viz/AsciiMapRenderTest`,
`hashmap/viz/AsciiTreeRenderTest`, `hashmap/events/SnapshotTypesTest`,
`hashmap/events/MapEventFormatterTest`, `hashmap/events/MapEventTest`.

**`hashmap/core/TreeKernelMigrationTest` — repurpose (the one substantive test
change):**

- Keep `treeNodeInheritsFromSubstrateKernel` (the structural `TreeNode is-a
  RbNode` pin) unchanged — it remains valid and valuable.
- Reframe `rotationAndRecolorSurfaceAsBridgedMapEvents` as a **faithfulness pin**
  and rename it (e.g. `mapEventStreamFaithfullyMirrorsKernelStream`). It still:
  1. drives a deterministic 8-node treeify through the **map's `put()` path** and
     captures the `Rotation`/`Recolor` stream, and
  2. independently replays the identical `(hash, seq)` node sequence directly
     through `TreeNode.build` with a raw recording `RbEventSink`, and
  3. asserts the two streams equal element-for-element (plus the existing
     raw-non-empty / has-ROT / has-COL guards so it is not `[] == []`).

  What it now proves: `sinkFor` wires `rotated() → Rotation` and
  `recolored() → Recolor` in order with the enum values passed through unmangled.
  There is no longer a `bridge()` to swap, but a *sinkFor*-level swap
  (`dir → opposite`), a swapped `oldColor`/`newColor`, or a dropped event is still
  caught by the independent `build()` oracle.

- **Rewrite the comment** to the faithfulness rationale; remove all "bridged" /
  "swapped bridge()" language (it describes machinery that no longer exists).

## Verification / non-vacuity

- `mvn clean test` → **476/476** (no tests added or removed; one repurposed).
- `git grep 'hashmap\.events\.\(Color\|Direction\)'` → empty.
- `git grep -n 'bridge(' src/main/java/.../TeachingHashMap.java` → empty.
- **RED-proof the faithfulness pin** (same discipline as #37's bridge pin):
  temporarily swap the emitted `dir` in `sinkFor` (`dir == LEFT ? RIGHT : LEFT`)
  and confirm `mapEventStreamFaithfullyMirrorsKernelStream` **fails**; revert.
  This proves the repurposed test retains teeth and did not degenerate into a
  self-comparison.

## Out of scope

- No behaviour, output, or format change (JSON, ASCII, SVG all byte-identical).
- No substrate change, no `TreeSet` change, no viz-HTML/JS change.
- Not a snapshot-schema change — `TreeNodeSnapshot`'s shape is identical; only the
  compile-time type of its `color` field moves package.

## Workflow

brainstorming (this doc) → writing-plans → subagent-driven-development
(fresh implementer + reviewer per task, ledger `.superpowers/sdd/progress.md`) →
whole-branch opus review → PR → Copilot triage →
`gh pr merge N --merge` (NOT squash, NOT `--delete-branch`) on user go-ahead.
