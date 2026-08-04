# Color/Direction Enum Dedup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retire `hashmap.events.{Color,Direction}` (byte-identical copies of `substrate.rbtree.{Color,Direction}`) so the substrate/rbtree kernel is the single source of truth for RB colour/direction across both consumers (map + set).

**Architecture:** Pure type-identity refactor. Repoint the map's three public event types (`Rotation`, `Recolor`, `TreeNodeSnapshot`) at the substrate enums, delete the two map-local enums and the two `bridge()` ternaries in `TeachingHashMap.sinkFor` (which emit straight-through afterward), and repoint every test that names the enums. Because the enums are name-identical (`RED`/`BLACK`, `LEFT`/`RIGHT`), all serialized/rendered output is byte-identical — the suite stays green throughout. Then repurpose the #37 replay-and-compare bridge pin as a sinkFor faithfulness pin.

**Tech Stack:** Java 21, Maven, JUnit 5.

## Global Constraints

- Suite count stays **476/476** — no tests added or removed (one repurposed in Task 2).
- No behaviour/output/format change: JSON, ASCII, and SVG frames are byte-identical before and after.
- Do NOT touch `substrate/**`, `treeset/**`, viz HTML/JS, or any snapshot schema shape.
- `mvn` is the source of truth for compilation (Eclipse/LSP phantom errors are a known repo gotcha — ignore them).
- Commit messages end with the two trailer lines used across this repo (`Co-Authored-By:` and `Claude-Session:`).
- Branch is `feat/color-direction-enum-dedup` (already created, spec committed at `a2063d9`).

---

### Task 1: Dedup the enums (atomic refactor, suite stays green)

This is a single atomic change: deleting the two enum source files breaks every referrer, so all referrers must be repointed in the same commit to keep the build green. No new test is written — the existing 476-test suite is the regression oracle for a zero-behaviour-change refactor.

**Files:**
- Delete: `src/main/java/com/gimlism/translucent/hashmap/events/Color.java`
- Delete: `src/main/java/com/gimlism/translucent/hashmap/events/Direction.java`
- Modify: `src/main/java/com/gimlism/translucent/hashmap/events/Rotation.java`
- Modify: `src/main/java/com/gimlism/translucent/hashmap/events/Recolor.java`
- Modify: `src/main/java/com/gimlism/translucent/hashmap/events/TreeNodeSnapshot.java`
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`
- Modify: `src/main/java/com/gimlism/translucent/hashmap/viz/Palette.java`
- Modify (test import swap): `TreeSnapshotTest`, `TreeifyTest`, `PaletteTest`, `AsciiMapRenderTest`, `AsciiTreeRenderTest`
- Modify (test import add): `SnapshotTypesTest`, `MapEventTest`, `MapEventFormatterTest`

**Interfaces:**
- Consumes: `com.gimlism.translucent.substrate.rbtree.Color` (`{ RED, BLACK }`) and `com.gimlism.translucent.substrate.rbtree.Direction` (`{ LEFT, RIGHT }`) — already exist, unchanged.
- Produces: `hashmap.events.Rotation.direction()` now returns `substrate.rbtree.Direction`; `Recolor.oldColor()/newColor()` and `TreeNodeSnapshot.color()` now return `substrate.rbtree.Color`. Constant names identical, so all `.name()`/`toString()` output is unchanged.

- [ ] **Step 1: Repoint the three public event types**

Replace the full contents of `src/main/java/com/gimlism/translucent/hashmap/events/Rotation.java`:

```java
package com.gimlism.translucent.hashmap.events;

import com.gimlism.translucent.substrate.rbtree.Direction;

/** A single red-black tree rotation about {@code pivotKey}. */
public record Rotation(int bucketIndex, Direction direction, Object pivotKey, MapSnapshot after)
        implements MapEvent {}
```

Replace the full contents of `src/main/java/com/gimlism/translucent/hashmap/events/Recolor.java`:

```java
package com.gimlism.translucent.hashmap.events;

import com.gimlism.translucent.substrate.rbtree.Color;

/** A single red-black tree node changed colour. */
public record Recolor(int bucketIndex, Object nodeKey, Color oldColor, Color newColor, MapSnapshot after)
        implements MapEvent {}
```

Replace the full contents of `src/main/java/com/gimlism/translucent/hashmap/events/TreeNodeSnapshot.java`:

```java
package com.gimlism.translucent.hashmap.events;

import com.gimlism.translucent.substrate.rbtree.Color;

/** Immutable copy of a red-black tree node, including colour and children. */
public record TreeNodeSnapshot(
        Object key, Object value, Color color,
        TreeNodeSnapshot left, TreeNodeSnapshot right) {}
```

- [ ] **Step 2: Repoint TeachingHashMap imports**

In `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`, delete these two import lines (currently lines 6–7):

```java
import com.gimlism.translucent.hashmap.events.Color;
import com.gimlism.translucent.hashmap.events.Direction;
```

Add these two imports adjacent to the existing `import com.gimlism.translucent.substrate.rbtree.RbEventSink;` (keep the group alphabetical — `Color`, `Direction`, then `RbEventSink`):

```java
import com.gimlism.translucent.substrate.rbtree.Color;
import com.gimlism.translucent.substrate.rbtree.Direction;
```

- [ ] **Step 3: Simplify sinkFor to emit straight-through and delete the bridges**

In the same file, replace the `sinkFor` method body plus the two `bridge()` methods (currently lines 141–164) with:

```java
    /** A sink that turns tree structural changes into events for bucket {@code i}. */
    private RbEventSink<TreeNode<K, V>> sinkFor(int i) {
        return new RbEventSink<>() {
            @Override public void rotated(Direction dir, TreeNode<K, V> pivot) {
                emit(new Rotation(i, dir, pivot.getKey(), snapshot()));
            }
            @Override public void recolored(TreeNode<K, V> node, Color oldColor, Color newColor) {
                emit(new Recolor(i, node.getKey(), oldColor, newColor, snapshot()));
            }
        };
    }
```

(The `n.red ? Color.RED : Color.BLACK` line in `treeSnapshot` needs no edit — `Color` now resolves to the substrate enum via the Step 2 import.)

- [ ] **Step 4: Repoint the Palette import**

In `src/main/java/com/gimlism/translucent/hashmap/viz/Palette.java`, change line 3 from:

```java
import com.gimlism.translucent.hashmap.events.Color;
```

to:

```java
import com.gimlism.translucent.substrate.rbtree.Color;
```

(The body — `color == Color.RED ? ...` — is unchanged.)

- [ ] **Step 5: Delete the two map-local enum files**

```bash
git rm src/main/java/com/gimlism/translucent/hashmap/events/Color.java \
       src/main/java/com/gimlism/translucent/hashmap/events/Direction.java
```

- [ ] **Step 6: Confirm no production code still references the deleted types**

Run: `git grep -n 'hashmap\.events\.\(Color\|Direction\)' src/main`
Expected: no output.

Run: `git grep -n 'bridge(' src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`
Expected: no output.

- [ ] **Step 7: Swap the enum import in the five importing test files**

In each of these files, change `import com.gimlism.translucent.hashmap.events.Color;` to `import com.gimlism.translucent.substrate.rbtree.Color;` (none of them import `Direction`):

- `src/test/java/com/gimlism/translucent/hashmap/core/TreeSnapshotTest.java` (import at line 7)
- `src/test/java/com/gimlism/translucent/hashmap/core/TreeifyTest.java` (import at line 10)
- `src/test/java/com/gimlism/translucent/hashmap/viz/PaletteTest.java` (import at line 6)
- `src/test/java/com/gimlism/translucent/hashmap/viz/AsciiMapRenderTest.java` (import at line 7)
- `src/test/java/com/gimlism/translucent/hashmap/viz/AsciiTreeRenderTest.java` (import at line 5)

- [ ] **Step 8: Add the enum import to the three in-package test files**

These three live in package `com.gimlism.translucent.hashmap.events` and referenced the enums with no import (same-package). After deletion they must import the substrate enums. Add the imports (place them in the import block; keep alphabetical with existing imports):

- `src/test/java/com/gimlism/translucent/hashmap/events/SnapshotTypesTest.java` — uses `Color.BLACK` → add:
  ```java
  import com.gimlism.translucent.substrate.rbtree.Color;
  ```
- `src/test/java/com/gimlism/translucent/hashmap/events/MapEventTest.java` — uses `Direction.LEFT`, `Color.RED`, `Color.BLACK` → add:
  ```java
  import com.gimlism.translucent.substrate.rbtree.Color;
  import com.gimlism.translucent.substrate.rbtree.Direction;
  ```
- `src/test/java/com/gimlism/translucent/hashmap/events/MapEventFormatterTest.java` — uses `Direction.LEFT`, `Color.RED`, `Color.BLACK` → add:
  ```java
  import com.gimlism.translucent.substrate.rbtree.Color;
  import com.gimlism.translucent.substrate.rbtree.Direction;
  ```

- [ ] **Step 9: Run the full suite**

Run: `mvn clean test`
Expected: `BUILD SUCCESS`, `Tests run: 476, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 10: Confirm no test still references the deleted types**

Run: `git grep -n 'hashmap\.events\.\(Color\|Direction\)' src/test`
Expected: no output.

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "refactor(hashmap): dedup Color/Direction onto substrate/rbtree

Retire hashmap.events.{Color,Direction} (byte-identical copies of the
substrate enums) and the two bridge() ternaries in sinkFor; the map's
public events (Rotation/Recolor/TreeNodeSnapshot) now import the
substrate enums directly, mirroring the TreeSet. Name-identical enums =>
byte-identical output; suite 476/476 unchanged.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm"
```

---

### Task 2: Repurpose + RED-proof the sinkFor faithfulness pin

The #37 replay-and-compare test existed to guard the now-deleted `bridge()` ternaries against a `LEFT↔RIGHT`/`RED↔BLACK` swap. With the bridge gone, its purpose shifts: it now proves the map's `put()`-driven event stream faithfully mirrors the kernel's raw `TreeNode.build` stream (no dropped, reordered, or enum-mangled `Rotation`/`Recolor` between the sink and the listener). A `sinkFor`-level swap (`dir → opposite`), a swapped `oldColor`/`newColor`, or a dropped event is still caught by the independent `build()` oracle. This task renames it, rewrites the stale "bridged" comment, and RED-proves it is not vacuous.

**Files:**
- Modify: `src/test/java/com/gimlism/translucent/hashmap/core/TreeKernelMigrationTest.java`

**Interfaces:**
- Consumes: `TeachingHashMap.sinkFor` behaviour from Task 1 (straight-through emit), `TreeNode.build`, `RbEventSink`.
- Produces: renamed test `mapEventStreamFaithfullyMirrorsKernelStream`.

- [ ] **Step 1: Rename the test method and rewrite its comment**

In `TreeKernelMigrationTest.java`, rename the method `rotationAndRecolorSurfaceAsBridgedMapEvents` to `mapEventStreamFaithfullyMirrorsKernelStream`. Leave the structural pin `treeNodeInheritsFromSubstrateKernel` and the entire body of the renamed test (the map `put()` capture, the independent raw `TreeNode.build` replay, the `raw.isEmpty()` / has-ROT / has-COL guards, and the `assertEquals(raw, bridged)` line) unchanged in mechanics. Replace the multi-line comment block above the method (currently the "Bridge pin (replay-and-compare)…" paragraph, and any inline "bridged"/"swapped bridge()" wording) with:

```java
    // Faithfulness pin (replay-and-compare): a single deterministic treeify build is
    // driven TWICE over the identical (hash, seq) node sequence -- once through the map's
    // put() path, whose sinkFor turns each kernel callback into a Rotation/Recolor map
    // event, and once directly through the raw substrate kernel (TreeNode.build with a
    // recording RbEventSink<TreeNode<...>> capturing the kernel's own enums).
    //
    // Both drive the same red-black insertion (ordered purely by (hash, seq), see
    // TreeNode.cmp), so the two callback streams MUST line up element-for-element.
    // Comparing by enum .name() means a sinkFor that swaps a direction, swaps
    // oldColor/newColor, or drops an event flips or shortens the map stream but not the
    // raw one, breaking the match. There is no longer any bridge() to swap -- the map
    // emits the substrate enums straight through -- so this pins that sinkFor's wiring
    // stays faithful. A same-cardinality check like "direction == LEFT || direction ==
    // RIGHT" is vacuously true for a 2-value enum and can't catch that -- do not reduce
    // this back to that shape.
```

(If the local variable is named `bridged`, you may rename it to `mapStream` for clarity, or leave it — mechanics are unchanged either way. If renamed, update its two uses.)

- [ ] **Step 2: Run the renamed test to confirm it passes**

Run: `mvn test -Dtest=TreeKernelMigrationTest`
Expected: `BUILD SUCCESS`, both tests pass.

- [ ] **Step 3: RED-proof non-vacuity (temporary swap, must fail)**

Temporarily edit `TeachingHashMap.sinkFor`'s `rotated` to emit the opposite direction:

```java
            @Override public void rotated(Direction dir, TreeNode<K, V> pivot) {
                emit(new Rotation(i, dir == Direction.LEFT ? Direction.RIGHT : Direction.LEFT,
                        pivot.getKey(), snapshot()));
            }
```

Run: `mvn test -Dtest=TreeKernelMigrationTest`
Expected: `mapEventStreamFaithfullyMirrorsKernelStream` **FAILS** on the `assertEquals(raw, ...)` line (a ROT direction differs between the map and raw streams).

This proves the repurposed test retains teeth and did not degenerate into a self-comparison.

- [ ] **Step 4: Revert the RED-proof edit**

Run: `git checkout src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`

Then confirm the swap is gone:
Run: `git grep -n 'Direction.RIGHT : Direction.LEFT' src/main`
Expected: no output.

- [ ] **Step 5: Run the full suite**

Run: `mvn clean test`
Expected: `BUILD SUCCESS`, `Tests run: 476, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/com/gimlism/translucent/hashmap/core/TreeKernelMigrationTest.java
git commit -m "test(hashmap): repurpose bridge pin as sinkFor faithfulness pin

The #37 replay-and-compare test guarded the now-deleted bridge()
ternaries. Reframe it: it now pins that the map's put()-driven event
stream faithfully mirrors the kernel's raw TreeNode.build stream (no
dropped/reordered/enum-mangled Rotation/Recolor). Rename + rewrite the
stale 'bridged' comment. RED-proven non-vacuous (a sinkFor direction
swap fails it).

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm"
```

---

## Self-Review

**Spec coverage:**
- Delete `hashmap/events/{Color,Direction}.java` → Task 1 Step 5. ✓
- Repoint 3 public event types → Task 1 Step 1. ✓
- TeachingHashMap: imports + straight-through sinkFor + delete bridge → Task 1 Steps 2–3. ✓
- `n.red ? Color.RED : Color.BLACK` resolves to substrate → covered by Step 2 import, noted in Step 3. ✓
- Palette import → Task 1 Step 4. ✓
- Formatter/serializer expected no change; grep confirms → Task 1 Steps 6 & 10. ✓
- 9 test files repointed → Task 1 Steps 7–8 (8 files: 5 swap + 3 add) plus TreeKernelMigrationTest in Task 2 (the 9th, repurposed not merely repointed). ✓
- Verification 476/476 + grep-empty + bridge-empty → Task 1 Steps 6, 9, 10. ✓
- RED-proof the faithfulness pin → Task 2 Step 3. ✓
- Repurpose migration test (rename + comment) → Task 2 Step 1. ✓

**Placeholder scan:** No TBD/TODO; every code step shows full code; every command shows expected output. ✓

**Type consistency:** `substrate.rbtree.Color {RED,BLACK}` and `substrate.rbtree.Direction {LEFT,RIGHT}` used consistently; `mapEventStreamFaithfullyMirrorsKernelStream` is the single test name across Task 2. ✓
