# Teaching HashMap with Event Instrumentation — Design

**Status:** Approved 2026-07-01
**Supersedes/refines:** the prior `DESIGN.md` (Teaching HashMap Visualizer) with the
"Open Questions for the First Session" resolved.

## Goal

Build an instrumented Java `HashMap` for teaching. It mirrors the structurally
important parts of `java.util.HashMap` (buckets, separate chaining, load-factor
resize, treeify/untreeify to a red-black tree) and emits a self-contained,
immutable event on every internal state change. The event stream is the primary
deliverable; it is designed so a future visualizer, recorder, or scrubber can
consume it without querying the live map.

The visual rendering layer is explicitly a separate, later concern.

## Resolved decisions

| Question | Decision |
|---|---|
| Scope | Purpose-built teaching HashMap (per DESIGN.md); no generic reflection. |
| Build tool | Maven. |
| Java version | Java 21 LTS (records + sealed interfaces). |
| Generics | Faithful `Map<K,V>`. |
| Map interface | **Full `java.util.Map` compliance** via `AbstractMap<K,V>`. |
| Treeify structure | **Real red-black tree** (rotations, recoloring, split-on-resize, RB delete). |
| Tree event granularity | **Fine-grained** — `Rotation` and `Recolor` events surfaced. |
| Event payload | **Full immutable whole-map snapshot** per event (before+after for `Resize`). |
| Constants | **Demo-tuned and constructor-configurable**; real JDK values documented. |
| Hash function | `index = key.hashCode() & (n-1)` — **no bit-spreading** (hand-craftable collisions). |
| Group id | `com.gimlism`. |

## Tech stack & module layout

- Java 21, Maven, JUnit 5.
- Group `com.gimlism`. Packages:
  - `translucent.hashmap.core` — `TeachingHashMap`, `Node`, `TreeNode`, red-black
    tree logic.
  - `translucent.hashmap.events` — sealed `MapEvent` hierarchy, snapshot records,
    `MapEventListener`.
  - `translucent.hashmap.consumer` — `ConsoleEventLogger`, `RecordingListener`.
  - `translucent.hashmap.demo` — a `main` running a scripted operation sequence.

## Core data structure

- `TeachingHashMap<K,V> extends AbstractMap<K,V>` — full `Map` compliance.
  `AbstractMap` derives `keySet()`, `values()`, `containsValue()`, `size()`, etc.
  from `entrySet()`; the hot paths (`get`, `put`, `remove`, `containsKey`) are
  overridden for real O(1) behavior.
- `Node<K,V>[] table`. A slot is one of:
  - `null` — empty bin,
  - a `Node` — head of a separate-chaining list,
  - a `TreeNode` (`TreeNode extends Node`) — root of a red-black tree bin.

  This mirrors the JDK's mental model exactly (a bin is a chain or a tree).
- **Configurable constants** (constructor args) with teaching defaults; real JDK
  values in Javadoc for the deferred "compare to JDK" mode:

  | Constant | Teaching default | Real JDK |
  |---|---|---|
  | `initialCapacity` | 8 | 16 |
  | `loadFactor` | 0.75 | 0.75 |
  | `treeifyThreshold` | 4 | 8 |
  | `untreeifyThreshold` | 2 | 6 |
  | `minTreeifyCapacity` | 8 | 64 |

- **Hash:** `index = key.hashCode() & (n - 1)` with no bit-spreading. This lets a
  teacher hand-pick colliding keys (e.g. at capacity 8, keys hashing to 0, 8, 16
  all land in bucket 0), which makes collisions and treeify easy to demonstrate.
- Power-of-two capacity; resize doubles capacity and rehashes. Tree bins split
  on resize (a tree may split into two bins, untreeifying if a resulting bin is
  small enough).
- Full red-black tree per tree bin: insert with rotations/recoloring, delete
  driving untreeify, and split-on-resize.

## Event model

```java
sealed interface MapEvent
    permits Put, Remove, Collision, Resize, Treeify, Untreeify, Rotation, Recolor {}
```

All events are immutable `record`s. Every event carries:

- the **affected bucket index** (for highlighting), and
- a **full immutable whole-map snapshot** taken *after* the operation settles.
  `Resize` carries both a before and an after snapshot.

**Why full snapshots:** each event becomes a complete, replayable frame. A future
visualizer or scrubber needs no state reconstruction and can jump to any point.
Teaching maps are tiny, so the memory cost is irrelevant. This directly satisfies
the DESIGN.md requirement that events be self-contained for async/replay use.

Event field sketch (final field lists settled during implementation/TDD):

- `Put(K key, V value, V previousValue, int bucketIndex, boolean newEntry, MapSnapshot after)`
- `Remove(K key, V removedValue, int bucketIndex, MapSnapshot after)`
- `Collision(K key, int bucketIndex, int chainLengthBefore, int chainLengthAfter, MapSnapshot after)`
- `Resize(int oldCapacity, int newCapacity, MapSnapshot before, MapSnapshot after)`
- `Treeify(int bucketIndex, MapSnapshot after)`
- `Untreeify(int bucketIndex, MapSnapshot after)`
- `Rotation(int bucketIndex, Direction direction, Object pivotKey, MapSnapshot after)`
- `Recolor(int bucketIndex, Object nodeKey, Color oldColor, Color newColor, MapSnapshot after)`

### Snapshot model

Immutable records:

- `MapSnapshot(int capacity, int size, int threshold, List<BucketSnapshot> buckets)`
- `sealed interface BucketSnapshot permits EmptyBucket, ChainSnapshot, TreeSnapshot`
  - `EmptyBucket`
  - `ChainSnapshot(List<EntrySnapshot> entries)`
  - `TreeSnapshot(TreeNodeSnapshot root)` — records node structure **with colors**
    (`TreeNodeSnapshot(Object key, Object value, Color color, TreeNodeSnapshot left,
    TreeNodeSnapshot right)`), so rotations and recolorings are visualizable.
- `EntrySnapshot(Object key, Object value, int hash)`

Snapshots copy out primitive/reference values; they hold no references back into
live nodes.

### Dispatch

```java
interface MapEventListener { void onEvent(MapEvent event); }
```

The map holds a list of listeners (`addListener` / `removeListener`). Emission is
**synchronous, after** each mutation completes, so the snapshot reflects the
settled state. A single `put` that triggers treeify may emit an ordered burst:
`Collision`… then `Treeify`, then the `Rotation`/`Recolor` events from balancing.

## Consumers

- `ConsoleEventLogger implements MapEventListener` — prints human-readable lines,
  e.g. `PUT key=64 -> bucket 0 (collision, chain len 3)`. Validates the stream
  end to end.
- `RecordingListener implements MapEventListener` — collects events into a
  `List<MapEvent>`. Backbone of sequence-based tests and a stepping-stone to the
  deferred replay feature.

## Testing strategy (TDD)

Tests are written first, per slice.

- **Correctness:** behaves as a `Map` — put/get round-trips, `remove`, resize
  preserves all entries, `entrySet()` iteration, `Iterator.remove()`, fail-fast
  `modCount` (a `ConcurrentModificationException` on structural change during
  iteration).
- **Event-sequence:** known operation scripts assert exact event sequences via
  `RecordingListener`. Examples:
  - N colliding keys (N = `treeifyThreshold`) into a table at/above
    `minTreeifyCapacity` → `Collision` events then `Treeify` + `Rotation`/`Recolor`.
  - removals dropping a tree bin below `untreeifyThreshold` → `Untreeify`.
  - inserts breaching load factor → `Resize` (before/after capacities correct).
- **Snapshot integrity:** snapshots are immutable and independent of subsequent
  mutations (mutate the map after capturing; the earlier snapshot is unchanged).

## Build sequence (vertical slices — each ends runnable)

1. **Slice 1 — Working core.** Table + separate chaining + resize + event model +
   snapshot records + `MapEventListener` dispatch + `ConsoleEventLogger` +
   `RecordingListener` + tests. A complete, demonstrable tool (no trees yet).
2. **Slice 2 — Treeify.** Chain-to-tree conversion + red-black insert with
   `Rotation`/`Recolor` events + `Treeify` event + tests.
3. **Slice 3 — Untreeify + RB delete.** Red-black deletion and untreeify (the
   hardest, most bug-prone piece — deliberately last) + tests.
4. **Slice 4 — Full iteration.** `entrySet()` iterator traversing chain *and* tree
   bins, `Iterator.remove()`, fail-fast `modCount` + tests. Completes full `Map`
   compliance.

## Out of scope (deferred, per DESIGN.md)

- Graphical rendering layer (web view or JavaFX).
- Recording, playback, and scrubbing UI (though `RecordingListener` and full
  snapshots lay the groundwork).
- TreeMap sibling visualization.
- "Compare to real JDK HashMap" mode (though configurable constants and Javadoc
  of real values lay the groundwork).
- Generic reflection-based visualization of arbitrary classes (explicitly
  rejected as pedagogically weaker).
