# Teaching ArrayList — Slice 1: Dynamic Array Core + Event Instrumentation — Design

**Status:** Approved 2026-07-03
**Relation to existing work:** This is the **second data structure** in `translucent`,
the sibling of the Teaching HashMap (Slices 1–3, merged). It reuses the *approach* —
an immutable, self-contained event per state change, full-structure snapshots, a
synchronous listener, a console consumer — but **not (yet) any shared types**. Per
the roadmap, the generic `StructureEvent`/`StructureSnapshot` + transport/drawing
extraction is deliberately deferred to a later refactor slice, once two concrete
structures exist to reveal the real seams. This slice builds ArrayList
**standalone-concrete**, mirroring HashMap Slice 1.

## Goal

Build an instrumented Java dynamic array (`ArrayList`) for teaching. It mirrors the
structurally important parts of `java.util.ArrayList` — a backing array with a
capacity distinct from size, **1.5× amortized growth** with an array copy, and O(n)
element **shifting** on insert/remove-in-the-middle — and emits a self-contained,
immutable event on every internal state change. The event stream is the primary
deliverable; a future visualizer/recorder/scrubber consumes it without querying the
live list.

The visual rendering layer (ASCII "cells" row) and the generic-substrate extraction
are explicitly separate, later concerns.

## Resolved decisions (Slice 1)

| Question | Decision |
|---|---|
| Scope | Purpose-built teaching `ArrayList`; standalone-concrete (no shared/generic types this slice). |
| List interface | **Full `java.util.List` compliance** via `AbstractList<E>` + `RandomAccess` (derives `iterator`/`listIterator`/`subList`/`equals`/`hashCode`, with `modCount` fail-fast). Hot paths (`get`/`set`/`add`/`add(i,e)`/`remove(i)`/`size`) overridden. |
| Growth policy | **1.5× JDK-faithful:** `newCap = oldCap + max(minGrowth, oldCap >> 1)` (the JDK `newLength` floor; for single adds this is `oldCap + (oldCap >> 1)`). Not teaching-tuned doubling. |
| Initial capacity / lazy alloc | **JDK-faithful lazy:** the no-arg constructor allocates **nothing** (a shared empty-array sentinel, capacity 0); the **first add jumps straight to `DEFAULT_CAPACITY` = 10** (not via the 1.5× formula). An explicit `initialCapacity` constructor allocates eagerly. |
| Shift granularity | **Fine-grained:** one `Shift(from, to, …)` event per moved slot during insert/remove, consistent with the HashMap's per-`Rotation` granularity. |
| Event payload | **Full immutable whole-list snapshot** per event (before+after for `Grow`), mirroring the HashMap's snapshot-per-event model. |
| Null elements | **Permitted** (like `java.util.ArrayList`). An empty slot is therefore distinct from a slot holding `null` — the snapshot models this with a sealed `SlotSnapshot`. |
| Event vocabulary | Concrete + **sealed**: `Append`, `Insert`, `Set`, `RemoveAt`, `Shift`, `Grow`. |

## Tech stack & module layout

- Java 21, Maven, JUnit 5 (same build as HashMap; no new dependencies).
- Group `com.gimlism`. New packages (parallel to `translucent.hashmap.*`):
  - `translucent.arraylist.core` — `TeachingArrayList`.
  - `translucent.arraylist.events` — sealed `ListEvent` hierarchy, snapshot records,
    `ListEventListener`, `ListEventFormatter`.
  - `translucent.arraylist.consumer` — `ConsoleListEventLogger`, `ListRecordingListener`.
  - `translucent.arraylist.demo` — a `main` running a scripted operation sequence.

> **Why not reuse `hashmap.events.*` types?** The teaching value lives in the
> *concrete* vocabulary (`Shift`, `Grow` for a list vs `Collision`, `Rotation` for a
> map). A premature generic `StructureEvent` would gut that. The reusable pieces
> (a generic listener/recorder/replayer and the ASCII "cells"/"chain"/"tree" drawing
> primitives) are extracted in a **later** slice, informed by *both* concrete
> structures — see the extensibility roadmap.

## Core data structure

- `TeachingArrayList<E> extends AbstractList<E> implements RandomAccess`.
  `AbstractList` derives `iterator()`, `listIterator()`, `subList()`, `equals()`,
  `hashCode()`, `indexOf()`, etc. from `get(int)` + `size()`, with `modCount`-based
  fail-fast iteration for free. The mutators below bump `modCount` where structural.
- `Object[] elementData` backing array; `int size` (the live count) distinct from
  `elementData.length` (the capacity).
- **Lazy allocation (JDK-faithful).** The no-arg constructor allocates **nothing** —
  `elementData` starts as a shared `DEFAULTCAPACITY_EMPTY_ELEMENTDATA` sentinel array of
  length 0 (identity-compared, exactly like `java.util.ArrayList`, so a brand-new list
  snapshots as `capacity 0`). This deferral — and the shared-sentinel trick that
  implements it — is itself a teaching point.
  - `DEFAULT_CAPACITY = 10` (the JDK value). The **first add** on the default-sentinel
    list grows straight to `max(DEFAULT_CAPACITY, minCapacity)` — a `0 → 10` jump, **not**
    the 1.5× formula — emitting `Grow(0, 10, …) → Append`.
  - An explicit `initialCapacity` constructor allocates `new Object[initialCapacity]`
    **eagerly** (so a demo may pass a small capacity — e.g. 4 — to make ordinary 1.5×
    grows fire early). `initialCapacity == 0` uses an `EMPTY_ELEMENTDATA` sentinel that
    grows by the formula (`0 → 1 → 2 → 3 …`), matching the JDK's explicit-zero path.
- **Growth:** when an add would exceed capacity, grow (from the sentinel: jump to
  `max(DEFAULT_CAPACITY, minCapacity)`; otherwise `oldCap + max(minCapacity - oldCap,
  oldCap >> 1)` — the JDK `newLength` floor), `Arrays.copyOf` the elements, emit
  `Grow(oldCapacity, newCapacity, before, after)`.
- **Overloaded operations** (each a distinct teaching shape):
  - `add(E)` — **append** at `size` (no shift; may grow first).
  - `add(int, E)` — **insert**, shifting `[i, size)` one slot right (may grow first).
  - `set(int, E)` — in-place replace (no structural change, no `modCount` bump).
  - `remove(int)` — **remove-at**, shifting `(i, size)` one slot left, clearing the
    vacated tail slot.

## Event model

```java
sealed interface ListEvent
    permits Append, Insert, Set, RemoveAt, Shift, Grow {
    ListSnapshot after();
}
```

All events are immutable `record`s carrying a **full immutable whole-list snapshot**
taken at the moment of emission (`Grow` also carries a `before`). Field sketch (final
lists settle during TDD):

- `Append(Object element, int index, ListSnapshot after)` — `index == old size`.
- `Insert(Object element, int index, ListSnapshot after)` — terminal marker of an
  insert, emitted **after** its `Shift` burst; `after` shows the element in place.
- `Set(int index, Object previousElement, Object element, ListSnapshot after)`.
- `RemoveAt(int index, Object removedElement, ListSnapshot after)` — terminal marker
  of a remove, emitted **after** its `Shift` burst.
- `Shift(int fromIndex, int toIndex, Object element, ListSnapshot after)` — one
  element moved one slot; `after` captures the array **mid-slide** (analogous to a
  `Rotation` capturing the tree mid-assembly).
- `Grow(int oldCapacity, int newCapacity, ListSnapshot before, ListSnapshot after)` —
  capacity growth (the amortization teaching moment). Usually a 1.5× `Arrays.copyOf`;
  the **first** grow of a lazily-allocated default list is the special `0 → 10` jump
  (a fresh allocation, no elements to copy). `after` reflects the new capacity with the
  element **not yet** placed (the trailing `Append`/`Insert` places it).

### Event grammar per operation

| Operation | Emitted events (in order) |
|---|---|
| `add(e)` — no grow | `Append` |
| `add(e)` — triggers grow | `Grow → Append` (the first add on a fresh no-arg list is the `Grow(0→10) → Append` special case) |
| `set(i, e)` | `Set` |
| `add(i, e)` — no grow | `Shift`* (high→low: `size-1→size`, … `i→i+1`) `→ Insert` |
| `add(i, e)` — triggers grow | `Grow →` `Shift`* `→ Insert` |
| `remove(i)` | `Shift`* (low→high: `i+1→i`, … `size-1→size-2`) `→ RemoveAt` |

The logical marker (`Insert`/`RemoveAt`) is **terminal**, trailing its mechanical
`Shift` burst — the same "the settled event follows its assembly steps" convention the
HashMap uses for tree-bin `Put`/`Remove`. `Append`/`Set`/`Grow` have no burst.

### Snapshot model

Immutable records mirroring the HashMap's snapshot style:

- `ListSnapshot(int capacity, int size, List<SlotSnapshot> slots)` — `slots.size()`
  **must equal** `capacity` (validated in the compact constructor, like `MapSnapshot`).
  Slots `[0, size)` are `FilledSlot`; `[size, capacity)` are `EmptySlot`.
- `sealed interface SlotSnapshot permits FilledSlot, EmptySlot`
  - `FilledSlot(Object element)` — holds the element (which may itself be `null`).
  - `EmptySlot` — an unused capacity slot (distinct from a filled-with-`null` slot).
- Snapshots copy out values; they hold no reference back into `elementData`.

### Dispatch

```java
interface ListEventListener { void onEvent(ListEvent event); }
```

The list holds a listener list (`addListener`/`removeListener`); emission is
**synchronous**, sometimes **mid-operation** (each `Shift` fires during the slide).
As in the HashMap, a re-entrant structural mutation from within a listener is rejected
with a `ConcurrentModificationException` (a `mutating` guard set at the top of each
structural mutator); reads are always safe.

## Consumers

- `ConsoleListEventLogger implements ListEventListener` — prints human-readable lines
  (e.g. `SHIFT 3 -> 4 (c)`, `GROW cap 4 -> 6`), via `ListEventFormatter`. The `events`
  package owns `ListEventFormatter` so every presentation layer shares one formatting
  of the vocabulary (mirrors `EventFormatter`).
- `ListRecordingListener implements ListEventListener` — collects events into a
  `List<ListEvent>`; backbone of the sequence-based tests and the deferred replay.

## Testing strategy (TDD)

Tests written first, per task.

- **Correctness (behaves as a `List`):** `add`/`get`/`set` round-trips; `add(i,e)` and
  `remove(i)` produce the exact expected sequence; `size()` vs capacity; growth
  preserves all elements in order; `AbstractList` iteration and `ListIterator`;
  fail-fast `modCount` (a `ConcurrentModificationException` on structural change during
  iteration); `null` elements storable and retrievable.
- **Lazy allocation:** a no-arg list has `capacity 0` and `size 0` and its snapshot has
  an empty `slots` list until the first add; the first add emits `Grow(0 → 10) → Append`;
  an explicit-capacity list allocates eagerly (no `Grow` until it fills).
- **Growth arithmetic:** after the `0 → 10` jump, capacities follow `10 → 15 → 22 → 33 …`
  (or from a configured small initial capacity, `4 → 6 → 9 …`); `Grow.before`/`after`
  capacities correct; `size` unchanged across a grow.
- **Event-sequence (via `ListRecordingListener`):** each row of the grammar table above
  asserted exactly — e.g. `add(2, x)` into `[a,b,c,d]` emits
  `Shift(3→4) → Shift(2→3) → Insert(index=2)`; `remove(1)` emits
  `Shift(2→1) → Shift(3→2) → RemoveAt(index=1)`; an append that overflows emits
  `Grow → Append`.
- **Snapshot integrity:** snapshots are immutable and independent of later mutation
  (capture, mutate the list, assert the earlier snapshot unchanged); `EmptySlot` vs a
  `FilledSlot(null)` are distinguished; `slots.size() == capacity` invariant enforced.
- **Re-entrancy:** a listener that mutates the list during dispatch triggers a
  `ConcurrentModificationException`; a listener that only reads is fine.

## Build sequence (this slice → runnable end state)

1. Backing array (eager, explicit-capacity constructor) + `size`/capacity +
   `add`(append)/`get`/`set`/`size` **including 1.5× growth on overflow** +
   `Append`/`Set`/`Grow` events + snapshot records + `ListEventListener` dispatch +
   `ConsoleListEventLogger` + `ListRecordingListener` + tests. (A fully working growable
   dynamic array via the explicit-capacity constructor.)
2. Lazy allocation: the shared empty-array sentinel + no-arg constructor (capacity 0)
   layered on top, and the special `0 → 10` first-add jump + tests.
3. Insert/remove-in-the-middle: `Shift` burst + `Insert`/`RemoveAt` terminal markers +
   `modCount`/fail-fast + tests.
4. Demo `main`: a scripted sequence (appends → grow → insert → remove) driving the
   console logger, with an output-assertion test (mirrors `DemoTest`).

(The exact task decomposition lands in the implementation plan.)

## Out of scope (deferred)

- The generic `StructureEvent`/`StructureSnapshot` interfaces and the extraction of a
  shared transport (listener/recorder/replayer) and drawing primitives — a later
  refactor slice, once this second concrete structure has revealed the seams.
- The ASCII "cells"-row visualizer + replayer for the list (a later slice, reusing the
  HashMap's `AsciiRenderer` cell primitive once it is generalized).
- "Compare to real `java.util.ArrayList`" mode. Lazy allocation *is* modeled this slice;
  the remaining documented divergence is minor (e.g. no `MAX_ARRAY_SIZE`/overflow-huge
  handling, no `trimToSize`/`ensureCapacity` public API).
- `addAll`/`removeAll`/`removeIf`/`retainAll` bulk mutators and their event batching
  (the single-element mutators establish the vocabulary first).
- Concurrent/thread-safety concerns beyond the single-thread fail-fast guard.
