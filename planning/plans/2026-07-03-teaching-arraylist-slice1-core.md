# Teaching ArrayList — Slice 1: Dynamic Array Core + Event Instrumentation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A second, standalone-concrete teaching data structure — an instrumented dynamic array mirroring the structurally important parts of `java.util.ArrayList` (capacity vs size, 1.5× amortized growth with lazy allocation, O(n) shift on insert/remove) — emitting a self-contained immutable event on every state change, consumed through a synchronous listener + console logger.

**Architecture:** New `com.gimlism.translucent.arraylist.{core,events,consumer,demo}` packages, parallel to `hashmap.*`, sharing **no types** with the map (the generic-substrate extraction is a later refactor slice, once two concrete structures reveal the seams). `TeachingArrayList<E> extends AbstractList<E> implements RandomAccess` (fail-fast iterator, `equals`/`hashCode`/`subList` derived). Sealed `ListEvent` vocabulary (`Append`, `Insert`, `Set`, `RemoveAt`, `Shift`, `Grow`), each carrying a full immutable `ListSnapshot` (`Grow` also a `before`). Growth is JDK-faithful: lazy no-arg allocation (shared empty-array sentinel), a `0 → 10` first-add jump, then `oldCap + max(minGrowth, oldCap >> 1)`.

**Tech Stack:** Java 21, Maven, JUnit 5. No new dependencies.

## Global Constraints

- Java 21 target (`maven.compiler.release=21`), Maven, built/tested on JDK 26 (same as the HashMap).
- Package root `com.gimlism.translucent.arraylist`. **No dependency on `hashmap.*`.** `core` → `events`; `consumer` → `events`; `demo` → `core`+`consumer`; `viz` deferred.
- Full `java.util.List` compliance via `AbstractList<E>` + `RandomAccess`; hot paths overridden (`get`/`set`/`size`/`add(E)`/`add(int,E)`/`remove(int)`).
- **Snapshot-per-event:** every event carries a full immutable `ListSnapshot` after the step settled; `Grow` also carries a `before`. `Shift` snapshots capture the array **mid-slide** (like a `Rotation` mid-assembly).
- **Event grammar:** `add(e)` → `Append` (`Grow → Append` on overflow; the first add on a fresh no-arg list is the `Grow(0→10) → Append` special case). `set(i,e)` → `Set`. `add(i,e)` with `i<size` → `Shift`*(high→low) `→ Insert`; with `i==size` → `Append`. `remove(i)` → `Shift`*(low→high) `→ RemoveAt`. The `Insert`/`RemoveAt`/`Append` marker is emitted **after** its `Shift`/`Grow` burst (terminal), matching the HashMap's tree-bin `Put`/`Remove` convention.
- **Growth (JDK-faithful):** no-arg constructor allocates the shared `DEFAULTCAPACITY_EMPTY_ELEMENTDATA` sentinel (capacity 0); first grow from it jumps to `max(DEFAULT_CAPACITY=10, minCapacity)`; otherwise `newCap = oldCap + max(minCapacity - oldCap, oldCap >> 1)`. Explicit-capacity constructor allocates eagerly (`initialCapacity == 0` uses the `EMPTY_ELEMENTDATA` sentinel).
- **Null elements permitted.** An `EmptySlot` (unused capacity) is distinct from a `FilledSlot(null)`.
- **Re-entrancy:** a `mutating` guard at the top of each public mutator rejects a structural mutation from within a listener with a `ConcurrentModificationException`; reads are always safe. `set` (non-structural) is guarded too (it still mutates + emits) but does **not** bump `modCount`.
- `modCount` (inherited `protected` from `AbstractList`) bumps on `add`/`add(int)`/`remove` (structural), driving the derived iterator's fail-fast.

## File structure

- Create `arraylist/package-info.java`.
- Create `arraylist/events/`: `ListEvent`, `Append`, `Insert`, `Set`, `RemoveAt`, `Shift`, `Grow`, `ListSnapshot`, `SlotSnapshot`, `FilledSlot`, `EmptySlot`, `ListEventListener`, `ListEventFormatter`.
- Create `arraylist/consumer/`: `ConsoleListEventLogger`, `ListRecordingListener`.
- Create `arraylist/core/TeachingArrayList.java`.
- Create `arraylist/demo/ListDemo.java`.
- Tests under `src/test/java/com/gimlism/translucent/arraylist/{events,core,demo}/`.

---

### Task 1: Events/snapshot scaffolding + growable core (eager constructor)

**Files:**
- Create: `src/main/java/com/gimlism/translucent/arraylist/package-info.java`
- Create: all `arraylist/events/*.java`, `arraylist/consumer/*.java`
- Create: `src/main/java/com/gimlism/translucent/arraylist/core/TeachingArrayList.java`
- Test: `src/test/java/com/gimlism/translucent/arraylist/events/ListSnapshotTest.java`
- Test: `src/test/java/com/gimlism/translucent/arraylist/core/TeachingArrayListBasicsTest.java`

**Interfaces:**
- Produces: sealed `ListEvent` (six records) each with `ListSnapshot after()`; `ListSnapshot(int capacity, int size, List<SlotSnapshot> slots)` (invariant `slots.size()==capacity`, `0<=size<=capacity`); `sealed SlotSnapshot permits FilledSlot, EmptySlot`; `ListEventListener` (functional); `ListEventFormatter.format(ListEvent)`; `ConsoleListEventLogger`, `ListRecordingListener`.
- Produces: `TeachingArrayList<E> extends AbstractList<E> implements RandomAccess` with `TeachingArrayList(int initialCapacity)`, `get`/`set`/`size`/`add(E)` (append + 1.5× growth), `addListener`/`removeListener`, package-private `snapshot()`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/gimlism/translucent/arraylist/events/ListSnapshotTest.java`
```java
package com.gimlism.translucent.arraylist.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ListSnapshotTest {
    @Test
    void slotsSizeMustEqualCapacity() {
        assertThrows(IllegalArgumentException.class,
            () -> new ListSnapshot(3, 1, List.of(new FilledSlot("a"))));
    }

    @Test
    void sizeMustBeWithinCapacity() {
        assertThrows(IllegalArgumentException.class,
            () -> new ListSnapshot(1, 2, List.of(new FilledSlot("a"))));
    }

    @Test
    void emptySlotIsDistinctFromFilledNull() {
        SlotSnapshot empty = new EmptySlot();
        SlotSnapshot filledNull = new FilledSlot(null);
        assertNotEquals(empty, filledNull);
        assertEquals(new FilledSlot(null), filledNull);
    }

    @Test
    void snapshotIsImmutableCopyOfSlots() {
        var slots = new java.util.ArrayList<SlotSnapshot>();
        slots.add(new FilledSlot("a"));
        slots.add(new EmptySlot());
        var snap = new ListSnapshot(2, 1, slots);
        slots.set(0, new FilledSlot("mutated"));           // mutate the source list
        assertEquals(new FilledSlot("a"), snap.slots().get(0)); // snapshot unaffected
        assertThrows(UnsupportedOperationException.class,
            () -> snap.slots().add(new EmptySlot()));      // and unmodifiable
    }
}
```

`src/test/java/com/gimlism/translucent/arraylist/core/TeachingArrayListBasicsTest.java`
```java
package com.gimlism.translucent.arraylist.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.consumer.ListRecordingListener;
import com.gimlism.translucent.arraylist.events.Append;
import com.gimlism.translucent.arraylist.events.EmptySlot;
import com.gimlism.translucent.arraylist.events.FilledSlot;
import com.gimlism.translucent.arraylist.events.Grow;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListSnapshot;
import com.gimlism.translucent.arraylist.events.Set;
import java.util.ConcurrentModificationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeachingArrayListBasicsTest {
    @Test
    void addGetSetSizeRoundTrip() {
        var list = new TeachingArrayList<String>(4);
        assertTrue(list.add("a"));
        assertTrue(list.add("b"));
        assertEquals(2, list.size());
        assertEquals("a", list.get(0));
        assertEquals("b", list.set(1, "B"));
        assertEquals("B", list.get(1));
        assertNull(new TeachingArrayList<String>(4).stream().findFirst().orElse(null));
    }

    @Test
    void getOutOfRangeThrows() {
        var list = new TeachingArrayList<String>(4);
        list.add("a");
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> list.set(1, "x"));
    }

    @Test
    void nullElementsAreStorable() {
        var list = new TeachingArrayList<String>(4);
        list.add(null);
        assertEquals(1, list.size());
        assertNull(list.get(0));
        // snapshot models it as a FilledSlot(null), not an EmptySlot
        assertEquals(new FilledSlot(null), list.snapshot().slots().get(0));
        assertInstanceOf(EmptySlot.class, list.snapshot().slots().get(1));
    }

    @Test
    void appendEmitsAppendEventWithSettledSnapshot() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add("a");
        assertEquals(1, rec.events().size());
        Append a = assertInstanceOf(Append.class, rec.events().get(0));
        assertEquals("a", a.element());
        assertEquals(0, a.index());
        assertEquals(1, a.after().size());
        assertEquals(4, a.after().capacity());
    }

    @Test
    void setEmitsSetEventAndDoesNotBumpModCount() {
        var list = new TeachingArrayList<String>(4);
        list.add("a");
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.set(0, "A");
        Set s = assertInstanceOf(Set.class, rec.events().get(0));
        assertEquals(0, s.index());
        assertEquals("a", s.previousElement());
        assertEquals("A", s.element());
        // set is non-structural: iterating and then set() must not fail-fast
        var it = list.iterator();
        list.set(0, "A2");
        assertEquals("A2", it.next());
    }

    @Test
    void growthFollowsOneAndAHalfFromEagerCapacity() {
        var list = new TeachingArrayList<Integer>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        for (int i = 0; i < 10; i++) list.add(i);   // caps 4 -> 6 -> 9 -> 13
        List<Integer> grownCaps = rec.events().stream()
            .filter(e -> e instanceof Grow).map(e -> ((Grow) e).newCapacity()).toList();
        assertEquals(List.of(6, 9, 13), grownCaps);
        // Grow leads its Append and carries the pre-append size in `after`
        int firstGrow = indexOfFirst(rec.events(), Grow.class);
        Grow g = (Grow) rec.events().get(firstGrow);
        assertEquals(4, g.oldCapacity());
        assertEquals(6, g.newCapacity());
        assertEquals(4, g.after().size());          // element not placed yet
        assertInstanceOf(Append.class, rec.events().get(firstGrow + 1));
        assertEquals(6, ((Append) rec.events().get(firstGrow + 1)).after().capacity());
        // all elements preserved in order
        for (int i = 0; i < 10; i++) assertEquals(i, list.get(i));
    }

    @Test
    void snapshotIsIndependentOfLaterMutation() {
        var list = new TeachingArrayList<String>(4);
        list.add("a");
        ListSnapshot snap = list.snapshot();
        list.add("b");
        list.set(0, "Z");
        assertEquals(1, snap.size());
        assertEquals(new FilledSlot("a"), snap.slots().get(0));
    }

    @Test
    void reentrantMutationFromListenerIsRejected() {
        var list = new TeachingArrayList<String>(4);
        list.addListener(e -> list.add("reentrant")); // structural mutation during dispatch
        assertThrows(ConcurrentModificationException.class, () -> list.add("a"));
    }

    private static int indexOfFirst(List<ListEvent> events, Class<?> type) {
        for (int i = 0; i < events.size(); i++) if (type.isInstance(events.get(i))) return i;
        return -1;
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test`
Expected: FAIL — the `arraylist` packages do not exist yet (compile errors).

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/gimlism/translucent/arraylist/package-info.java`
```java
/**
 * Teaching ArrayList with event instrumentation.
 *
 * <p>An instrumented dynamic array for teaching: it mirrors the structurally
 * important parts of {@code java.util.ArrayList} (capacity vs size, 1.5× amortized
 * growth with lazy allocation, O(n) shift on insert/remove) and emits an immutable
 * event on every internal state change. Sibling of the teaching HashMap; shares no
 * types with it (the generic substrate is extracted in a later refactor slice).
 */
package com.gimlism.translucent.arraylist;
```

`arraylist/events/SlotSnapshot.java`, `FilledSlot.java`, `EmptySlot.java`
```java
package com.gimlism.translucent.arraylist.events;

/** One backing-array slot at snapshot time: filled with an element, or unused capacity. */
public sealed interface SlotSnapshot permits FilledSlot, EmptySlot {}
```
```java
package com.gimlism.translucent.arraylist.events;

/** A slot holding a live element (which may itself be {@code null}). */
public record FilledSlot(Object element) implements SlotSnapshot {}
```
```java
package com.gimlism.translucent.arraylist.events;

/** An unused capacity slot — distinct from a {@link FilledSlot} holding {@code null}. */
public record EmptySlot() implements SlotSnapshot {}
```

`arraylist/events/ListSnapshot.java`
```java
package com.gimlism.translucent.arraylist.events;

import java.util.List;

/** Immutable whole-list state at a point in time. */
public record ListSnapshot(int capacity, int size, List<SlotSnapshot> slots) {
    public ListSnapshot {
        slots = List.copyOf(slots);
        if (slots.size() != capacity) {
            throw new IllegalArgumentException(
                "slots.size() (" + slots.size() + ") must equal capacity (" + capacity + ")");
        }
        if (size < 0 || size > capacity) {
            throw new IllegalArgumentException(
                "size (" + size + ") must be in [0, capacity (" + capacity + ")]");
        }
    }
}
```

`arraylist/events/ListEvent.java`
```java
package com.gimlism.translucent.arraylist.events;

/**
 * An immutable, self-contained record of a single list state change.
 *
 * <p><b>Event grammar per operation.</b> A single call can emit several events; the
 * logical marker ({@link Append}/{@link Insert}/{@link RemoveAt}) is the <em>terminal</em>
 * event, trailing its mechanical burst:
 * <ul>
 *   <li><b>append:</b> [{@code Grow →}] {@code Append}.</li>
 *   <li><b>set:</b> {@code Set}.</li>
 *   <li><b>insert (i &lt; size):</b> [{@code Grow →}] {@code Shift}* (high→low) {@code → Insert}.</li>
 *   <li><b>insert (i == size):</b> same as append.</li>
 *   <li><b>remove:</b> {@code Shift}* (low→high) {@code → RemoveAt}.</li>
 * </ul>
 */
public sealed interface ListEvent
        permits Append, Insert, Set, RemoveAt, Shift, Grow {
    /**
     * Whole-list snapshot at the moment this event was emitted. For most events this
     * is the settled state after the step; a {@link Shift} captures the array
     * <em>mid-slide</em> and a {@link Grow}'s {@code after} shows the new capacity
     * with the pending element not yet placed.
     */
    ListSnapshot after();
}
```

`arraylist/events/Append.java` / `Insert.java` / `Set.java` / `RemoveAt.java` / `Shift.java` / `Grow.java`
```java
package com.gimlism.translucent.arraylist.events;
/** An element was appended at {@code index} (== the prior size). */
public record Append(Object element, int index, ListSnapshot after) implements ListEvent {}
```
```java
package com.gimlism.translucent.arraylist.events;
/** An element was inserted at {@code index}; the terminal event of an insert. */
public record Insert(Object element, int index, ListSnapshot after) implements ListEvent {}
```
```java
package com.gimlism.translucent.arraylist.events;
/** The element at {@code index} was replaced in place (non-structural). */
public record Set(int index, Object previousElement, Object element, ListSnapshot after) implements ListEvent {}
```
```java
package com.gimlism.translucent.arraylist.events;
/** The element at {@code index} was removed; the terminal event of a remove. */
public record RemoveAt(int index, Object removedElement, ListSnapshot after) implements ListEvent {}
```
```java
package com.gimlism.translucent.arraylist.events;
/** One element slid one slot (right on insert, left on remove); snapshot is mid-slide. */
public record Shift(int fromIndex, int toIndex, Object element, ListSnapshot after) implements ListEvent {}
```
```java
package com.gimlism.translucent.arraylist.events;
/** The backing array grew (or was first allocated); {@code before}/{@code after} bracket the copy. */
public record Grow(int oldCapacity, int newCapacity, ListSnapshot before, ListSnapshot after) implements ListEvent {}
```

`arraylist/events/ListEventListener.java`
```java
package com.gimlism.translucent.arraylist.events;

/**
 * Consumer of the list's event stream. Invoked synchronously during a mutation,
 * sometimes mid-operation (e.g. between the {@link Shift}s of an insert). A listener
 * may freely read the list and add/remove listeners, but must not structurally mutate
 * it from within {@link #onEvent} (rejected with a
 * {@link java.util.ConcurrentModificationException}).
 */
@FunctionalInterface
public interface ListEventListener {
    void onEvent(ListEvent event);
}
```

`arraylist/events/ListEventFormatter.java`
```java
package com.gimlism.translucent.arraylist.events;

/** Turns a {@link ListEvent} into its canonical one-line human-readable label. */
public final class ListEventFormatter {
    private ListEventFormatter() {}

    public static String format(ListEvent event) {
        return switch (event) {
            case Append a -> "APPEND " + a.element() + " @ " + a.index();
            case Insert in -> "INSERT " + in.element() + " @ " + in.index();
            case Set s -> "SET " + s.index() + " = " + s.element() + " (was " + s.previousElement() + ")";
            case RemoveAt r -> "REMOVE @ " + r.index() + " (was " + r.removedElement() + ")";
            case Shift sh -> "SHIFT " + sh.fromIndex() + " -> " + sh.toIndex() + " (" + sh.element() + ")";
            case Grow g -> "GROW cap " + g.oldCapacity() + " -> " + g.newCapacity();
        };
    }
}
```

`arraylist/consumer/ConsoleListEventLogger.java`
```java
package com.gimlism.translucent.arraylist.consumer;

import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListEventFormatter;
import com.gimlism.translucent.arraylist.events.ListEventListener;
import java.io.PrintStream;

/** Prints a human-readable line per event. Validates the stream end to end. */
public class ConsoleListEventLogger implements ListEventListener {
    private final PrintStream out;

    public ConsoleListEventLogger() { this(System.out); }
    public ConsoleListEventLogger(PrintStream out) { this.out = out; }

    @Override
    public void onEvent(ListEvent event) {
        out.println(ListEventFormatter.format(event));
    }
}
```

`arraylist/consumer/ListRecordingListener.java`
```java
package com.gimlism.translucent.arraylist.consumer;

import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects events in order. Backbone of sequence-based tests and future replay. */
public class ListRecordingListener implements ListEventListener {
    private final List<ListEvent> events = new ArrayList<>();

    @Override
    public void onEvent(ListEvent event) { events.add(event); }

    /** Events in emission order (unmodifiable view). */
    public List<ListEvent> events() { return Collections.unmodifiableList(events); }

    public void clear() { events.clear(); }
}
```

`arraylist/core/TeachingArrayList.java` (Task 1 version — eager constructor + growth; insert/remove arrive in Task 3)
```java
package com.gimlism.translucent.arraylist.core;

import com.gimlism.translucent.arraylist.events.Append;
import com.gimlism.translucent.arraylist.events.EmptySlot;
import com.gimlism.translucent.arraylist.events.FilledSlot;
import com.gimlism.translucent.arraylist.events.Grow;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListEventListener;
import com.gimlism.translucent.arraylist.events.ListSnapshot;
import com.gimlism.translucent.arraylist.events.Set;
import com.gimlism.translucent.arraylist.events.SlotSnapshot;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * A teaching ArrayList: a backing array with a capacity distinct from size, 1.5×
 * amortized growth, and O(n) shift on insert/remove — every mutation observable
 * through an immutable event stream. Sibling of {@code TeachingHashMap}; shares no
 * types with it.
 */
public class TeachingArrayList<E> extends AbstractList<E> implements RandomAccess {

    /** JDK-faithful default capacity the first add jumps to from the lazy sentinel. */
    static final int DEFAULT_CAPACITY = 10;

    Object[] elementData;
    private int size;

    private final List<ListEventListener> listeners = new ArrayList<>();
    private boolean mutating;

    public TeachingArrayList(int initialCapacity) {
        if (initialCapacity < 0) throw new IllegalArgumentException("initialCapacity < 0");
        this.elementData = new Object[initialCapacity];
    }

    @Override public int size() { return size; }

    @SuppressWarnings("unchecked")
    private E elementAt(int i) { return (E) elementData[i]; }

    @Override
    public E get(int index) {
        Objects.checkIndex(index, size);
        return elementAt(index);
    }

    @Override
    public E set(int index, E element) {
        Objects.checkIndex(index, size);
        beginMutation();
        try {
            E old = elementAt(index);
            elementData[index] = element;          // non-structural: no modCount bump
            emit(new Set(index, old, element, snapshot()));
            return old;
        } finally {
            mutating = false;
        }
    }

    @Override
    public boolean add(E element) {
        beginMutation();
        try {
            appendInternal(element);
            return true;
        } finally {
            mutating = false;
        }
    }

    /** Append at the tail (caller holds the mutation guard). May grow first. */
    private void appendInternal(E element) {
        ensureCapacity(size + 1);                  // may emit Grow (size still old)
        int index = size;
        elementData[index] = element;
        size++;
        modCount++;
        emit(new Append(element, index, snapshot()));
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity - elementData.length > 0) grow(minCapacity);
    }

    /** Grow the backing array to hold at least {@code minCapacity}, emitting {@link Grow}. */
    private void grow(int minCapacity) {
        ListSnapshot before = snapshot();
        int oldCapacity = elementData.length;
        int newCapacity = oldCapacity + Math.max(minCapacity - oldCapacity, oldCapacity >> 1);
        elementData = Arrays.copyOf(elementData, newCapacity);
        emit(new Grow(oldCapacity, newCapacity, before, snapshot()));
    }

    // --- event dispatch ---------------------------------------------------------

    public void addListener(ListEventListener listener) { listeners.add(listener); }
    public void removeListener(ListEventListener listener) { listeners.remove(listener); }

    private void emit(ListEvent event) {
        // Copy so a listener may add/remove listeners during dispatch.
        for (ListEventListener listener : List.copyOf(listeners)) listener.onEvent(event);
    }

    /** Marks the start of a mutation, rejecting a re-entrant one from a listener. */
    private void beginMutation() {
        if (mutating) {
            throw new ConcurrentModificationException(
                "list mutated from within an event listener; listeners may read the list "
                + "but must not add/set/remove during event dispatch");
        }
        mutating = true;
    }

    /** Immutable whole-list snapshot: filled slots [0,size), empty slots [size,capacity). */
    ListSnapshot snapshot() {
        List<SlotSnapshot> slots = new ArrayList<>(elementData.length);
        for (int i = 0; i < elementData.length; i++) {
            slots.add(i < size ? new FilledSlot(elementData[i]) : new EmptySlot());
        }
        return new ListSnapshot(elementData.length, size, slots);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test`
Expected: PASS — `ListSnapshotTest` and `TeachingArrayListBasicsTest` green; all existing HashMap tests still pass (new packages are additive).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/arraylist src/test/java/com/gimlism/translucent/arraylist
git commit -m "feat(arraylist): dynamic-array core with Append/Set/Grow event stream"
```

---

### Task 2: Lazy allocation (no-arg constructor + 0→10 first-add jump)

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/arraylist/core/TeachingArrayList.java`
- Test: `src/test/java/com/gimlism/translucent/arraylist/core/LazyAllocationTest.java`

**Interfaces:**
- Consumes: `grow`, `snapshot`, `Grow`, `Append` (Task 1).
- Produces: a no-arg `TeachingArrayList()` allocating the shared `DEFAULTCAPACITY_EMPTY_ELEMENTDATA` sentinel (capacity 0); `grow` special-cases the sentinel to jump to `max(DEFAULT_CAPACITY, minCapacity)`; the explicit constructor uses `EMPTY_ELEMENTDATA` for `initialCapacity == 0`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/arraylist/core/LazyAllocationTest.java`
```java
package com.gimlism.translucent.arraylist.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.consumer.ListRecordingListener;
import com.gimlism.translucent.arraylist.events.Append;
import com.gimlism.translucent.arraylist.events.Grow;
import org.junit.jupiter.api.Test;

class LazyAllocationTest {
    @Test
    void freshNoArgListAllocatesNothing() {
        var list = new TeachingArrayList<String>();
        assertEquals(0, list.size());
        assertEquals(0, list.snapshot().capacity());          // nothing allocated
        assertTrue(list.snapshot().slots().isEmpty());
    }

    @Test
    void firstAddJumpsStraightToTen() {
        var list = new TeachingArrayList<String>();
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add("a");
        Grow g = assertInstanceOf(Grow.class, rec.events().get(0));
        assertEquals(0, g.oldCapacity());
        assertEquals(10, g.newCapacity());
        assertEquals(0, g.before().capacity());
        assertEquals(0, g.before().size());
        assertEquals(10, g.after().capacity());
        assertEquals(0, g.after().size());                    // element not placed yet
        Append a = assertInstanceOf(Append.class, rec.events().get(1));
        assertEquals(10, a.after().capacity());
        assertEquals(1, a.after().size());
    }

    @Test
    void secondGrowFollowsOneAndAHalfFromTen() {
        var list = new TeachingArrayList<Integer>();
        var rec = new ListRecordingListener();
        list.addListener(rec);
        for (int i = 0; i < 11; i++) list.add(i);             // caps 0->10, then 10->15 (size 11 <= 15)
        var caps = rec.events().stream()
            .filter(e -> e instanceof Grow).map(e -> ((Grow) e).newCapacity()).toList();
        assertEquals(java.util.List.of(10, 15), caps);
        for (int i = 0; i < 11; i++) assertEquals(i, list.get(i));
    }

    @Test
    void explicitZeroCapacityGrowsByFormulaNotToTen() {
        var list = new TeachingArrayList<Integer>(0);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add(1); // explicit-zero path: 0 -> 1 (not the default-sentinel jump to 10)
        Grow g = assertInstanceOf(Grow.class, rec.events().get(0));
        assertEquals(0, g.oldCapacity());
        assertEquals(1, g.newCapacity());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — no no-arg constructor (`freshNoArgListAllocatesNothing` won't compile), and once added, the first grow would go `0 → max(1,...)` not `0 → 10`.

- [ ] **Step 3: Write minimal implementation**

In `TeachingArrayList`, add the shared sentinels and the no-arg constructor, and special-case `grow`:
```java
    /** Shared empty array for no-arg (default-capacity) lists — first add jumps to DEFAULT_CAPACITY. */
    private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};
    /** Shared empty array for explicit zero-capacity lists — grows by the 1.5× formula. */
    private static final Object[] EMPTY_ELEMENTDATA = {};

    /** Lazy: allocates nothing until the first add, which jumps to {@link #DEFAULT_CAPACITY}. */
    public TeachingArrayList() {
        this.elementData = DEFAULTCAPACITY_EMPTY_ELEMENTDATA;
    }
```
Change the explicit constructor's zero case:
```java
    public TeachingArrayList(int initialCapacity) {
        if (initialCapacity < 0) throw new IllegalArgumentException("initialCapacity < 0");
        this.elementData = (initialCapacity == 0) ? EMPTY_ELEMENTDATA : new Object[initialCapacity];
    }
```
Special-case `grow` for the default sentinel (jump to 10) — replace the `newCapacity` computation:
```java
    private void grow(int minCapacity) {
        ListSnapshot before = snapshot();
        int oldCapacity = elementData.length;
        int newCapacity;
        if (elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
            newCapacity = Math.max(DEFAULT_CAPACITY, minCapacity);   // 0 -> 10 jump (fresh allocation)
            elementData = new Object[newCapacity];
        } else {
            newCapacity = oldCapacity + Math.max(minCapacity - oldCapacity, oldCapacity >> 1);
            elementData = Arrays.copyOf(elementData, newCapacity);
        }
        emit(new Grow(oldCapacity, newCapacity, before, snapshot()));
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `LazyAllocationTest` green; `TeachingArrayListBasicsTest` (eager constructor) still passes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/arraylist/core/TeachingArrayList.java src/test/java/com/gimlism/translucent/arraylist/core/LazyAllocationTest.java
git commit -m "feat(arraylist): JDK-faithful lazy allocation with 0->10 first-add jump"
```

---

### Task 3: Insert / remove-in-the-middle with fine-grained Shift + fail-fast

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/arraylist/core/TeachingArrayList.java`
- Test: `src/test/java/com/gimlism/translucent/arraylist/core/InsertRemoveShiftTest.java`

**Interfaces:**
- Consumes: `appendInternal`, `ensureCapacity`, `snapshot`, `beginMutation`, `Shift`, `Insert`, `RemoveAt` (+ Task 1/2).
- Produces: overridden `add(int index, E)` (append when `index==size`, else shift-right burst + `Insert`) and `remove(int index)` (shift-left burst + `RemoveAt`); both `modCount`-bumping (fail-fast iteration). Event order matches the grammar exactly.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/arraylist/core/InsertRemoveShiftTest.java`
```java
package com.gimlism.translucent.arraylist.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gimlism.translucent.arraylist.consumer.ListRecordingListener;
import com.gimlism.translucent.arraylist.events.Grow;
import com.gimlism.translucent.arraylist.events.Insert;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.RemoveAt;
import com.gimlism.translucent.arraylist.events.Shift;
import java.util.ConcurrentModificationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class InsertRemoveShiftTest {
    private static TeachingArrayList<String> of(String... xs) {
        var list = new TeachingArrayList<String>(xs.length == 0 ? 4 : xs.length);
        for (String x : xs) list.add(x);
        return list;
    }

    private static String tag(ListEvent e) {
        return switch (e) {
            case Shift s -> "SHIFT " + s.fromIndex() + "->" + s.toIndex() + "(" + s.element() + ")";
            case Insert in -> "INSERT@" + in.index();
            case RemoveAt r -> "REMOVE@" + r.index();
            default -> e.getClass().getSimpleName();
        };
    }

    @Test
    void insertInMiddleShiftsHighToLowThenInserts() {
        var list = new TeachingArrayList<String>(6);
        for (String x : new String[]{"a", "b", "c", "d"}) list.add(x);
        var rec = new ListRecordingListener();
        list.addListener(rec);

        list.add(2, "X"); // [a,b,c,d] -> [a,b,X,c,d]

        assertEquals(List.of("SHIFT 3->4(d)", "SHIFT 2->3(c)", "INSERT@2"),
            rec.events().stream().map(InsertRemoveShiftTest::tag).toList());
        assertEquals(List.of("a", "b", "X", "c", "d"), list);
        // the first Shift snapshot is mid-slide: capacity 6, size 5, slot 4 already holds d
        Shift first = (Shift) rec.events().get(0);
        assertEquals(5, first.after().size());
    }

    @Test
    void insertAtEndIsAnAppendNotAShift() {
        var list = of("a", "b");
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add(2, "c"); // index == size -> append semantics
        assertEquals(List.of("Append"),
            rec.events().stream().map(e -> e.getClass().getSimpleName()).toList());
        assertEquals(List.of("a", "b", "c"), list);
    }

    @Test
    void insertTriggeringGrowEmitsGrowThenShiftsThenInsert() {
        var list = of("a", "b", "c", "d"); // capacity 4, full
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add(1, "X"); // must grow 4->6 first, then shift [1,4) right
        var kinds = rec.events().stream().map(e -> e.getClass().getSimpleName()).toList();
        assertEquals("Grow", kinds.get(0));
        assertInstanceOf(Grow.class, rec.events().get(0));
        assertEquals("Insert", kinds.get(kinds.size() - 1));
        assertEquals(List.of("a", "X", "b", "c", "d"), list);
    }

    @Test
    void removeInMiddleShiftsLowToHighThenRemoves() {
        var list = of("a", "b", "c", "d");
        var rec = new ListRecordingListener();
        list.addListener(rec);

        assertEquals("b", list.remove(1)); // [a,b,c,d] -> [a,c,d]

        assertEquals(List.of("SHIFT 2->1(c)", "SHIFT 3->2(d)", "REMOVE@1"),
            rec.events().stream().map(InsertRemoveShiftTest::tag).toList());
        assertEquals(List.of("a", "c", "d"), list);
        // terminal RemoveAt snapshot: size 3, tail slot cleared to EmptySlot
        RemoveAt r = (RemoveAt) rec.events().get(2);
        assertEquals(3, r.after().size());
    }

    @Test
    void removeLastEmitsOnlyRemoveAt() {
        var list = of("a", "b", "c");
        var rec = new ListRecordingListener();
        list.addListener(rec);
        assertEquals("c", list.remove(2));
        assertEquals(List.of("REMOVE@2"),
            rec.events().stream().map(InsertRemoveShiftTest::tag).toList());
        assertEquals(List.of("a", "b"), list);
    }

    @Test
    void structuralChangeDuringIterationFailsFast() {
        var list = of("a", "b", "c");
        var it = list.iterator();
        it.next();
        list.remove(0);                       // structural: modCount bumped
        assertThrows(ConcurrentModificationException.class, it::next);
    }

    @Test
    void iteratorRemoveDeletesThroughRemoveInt() {
        var list = of("a", "b", "c");
        var it = list.iterator();
        it.next();
        it.remove();                          // AbstractList.Itr.remove -> remove(0)
        assertEquals(List.of("b", "c"), list);
    }

    @Test
    void insertRangeCheck() {
        var list = of("a");
        assertThrows(IndexOutOfBoundsException.class, () -> list.add(2, "x"));
        assertThrows(IndexOutOfBoundsException.class, () -> list.add(-1, "x"));
        assertThrows(IndexOutOfBoundsException.class, () -> list.remove(1));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — `add(int,E)`/`remove(int)` inherit `AbstractList`'s `UnsupportedOperationException` (and `Shift`/`Insert`/`RemoveAt` are never emitted).

- [ ] **Step 3: Write minimal implementation**

Add to `TeachingArrayList` (imports: `Insert`, `RemoveAt`, `Shift`). Insert opens one logical slot at the tail (bump `size` first) so each `Shift` snapshot shows the moved element in its new slot:
```java
    @Override
    public void add(int index, E element) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        beginMutation();
        try {
            if (index == size) {
                appendInternal(element);                 // no shift -> Append
            } else {
                insertInternal(index, element);          // Shift* -> Insert
            }
        } finally {
            mutating = false;
        }
    }

    /** Insert at index &lt; size: grow if needed, slide [index,size) right one slot, place. */
    private void insertInternal(int index, E element) {
        ensureCapacity(size + 1);                        // may emit Grow (size still old)
        size++;                                          // open one slot at the tail
        modCount++;
        for (int i = size - 2; i >= index; i--) {        // high -> low, avoid overwrite
            elementData[i + 1] = elementData[i];
            emit(new Shift(i, i + 1, elementData[i + 1], snapshot()));
        }
        elementData[index] = element;
        emit(new Insert(element, index, snapshot()));
    }

    @Override
    public E remove(int index) {
        Objects.checkIndex(index, size);
        beginMutation();
        try {
            E old = elementAt(index);
            modCount++;
            for (int i = index + 1; i < size; i++) {     // low -> high, slide survivors left
                elementData[i - 1] = elementData[i];
                emit(new Shift(i, i - 1, elementData[i - 1], snapshot()));
            }
            elementData[--size] = null;                  // drop size, clear the vacated tail
            emit(new RemoveAt(index, old, snapshot()));
            return old;
        } finally {
            mutating = false;
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `InsertRemoveShiftTest` green (exact Shift/Insert/RemoveAt order, mid-slide snapshots, grow-before-shift, fail-fast, iterator remove); all prior tests still pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/arraylist/core/TeachingArrayList.java src/test/java/com/gimlism/translucent/arraylist/core/InsertRemoveShiftTest.java
git commit -m "feat(arraylist): insert/remove with fine-grained Shift events and fail-fast"
```

---

### Task 4: Demo (scripted event stream)

**Files:**
- Create: `src/main/java/com/gimlism/translucent/arraylist/demo/ListDemo.java`
- Test: `src/test/java/com/gimlism/translucent/arraylist/demo/ListDemoTest.java`

**Interfaces:**
- Consumes: `TeachingArrayList`, `ConsoleListEventLogger`.
- Produces: `ListDemo.run(PrintStream)` driving a scripted sequence — appends past the lazy jump and a grow, an insert, and a remove — so the output contains `GROW`, `APPEND`, `INSERT`, `SHIFT`, and `REMOVE` lines. `main` calls `run(System.out)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/arraylist/demo/ListDemoTest.java`
```java
package com.gimlism.translucent.arraylist.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ListDemoTest {
    @Test
    void runShowsGrowAppendInsertShiftAndRemove() {
        var buffer = new ByteArrayOutputStream();
        ListDemo.run(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("GROW"), "expected grow, got:\n" + out);
        assertTrue(out.contains("APPEND"), "expected append, got:\n" + out);
        assertTrue(out.contains("INSERT"), "expected insert, got:\n" + out);
        assertTrue(out.contains("SHIFT"), "expected shift, got:\n" + out);
        assertTrue(out.contains("REMOVE"), "expected remove, got:\n" + out);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — `ListDemo` does not exist.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/gimlism/translucent/arraylist/demo/ListDemo.java`
```java
package com.gimlism.translucent.arraylist.demo;

import com.gimlism.translucent.arraylist.consumer.ConsoleListEventLogger;
import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import java.io.PrintStream;

/** Scripted demonstration of the teaching ArrayList event stream. */
public class ListDemo {
    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream out) {
        // Small eager capacity so an ordinary 1.5× grow fires quickly.
        var list = new TeachingArrayList<String>(4);
        list.addListener(new ConsoleListEventLogger(out));

        out.println("== appending past the initial capacity to force a grow ==");
        for (String s : new String[]{"a", "b", "c", "d", "e"}) { // 5th append: grow 4 -> 6
            list.add(s);
        }

        out.println("== inserting in the middle (shifts the tail right) ==");
        list.add(1, "X"); // [a,b,c,d,e] -> [a,X,b,c,d,e]

        out.println("== removing from the middle (shifts survivors left) ==");
        list.remove(2);   // remove "b"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `ListDemoTest` green (output shows GROW, APPEND, INSERT, SHIFT, REMOVE). Optionally run `mvn -q exec:java -Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListDemo` to eyeball the stream (wire an exec profile only if the HashMap demo has one worth mirroring — otherwise defer).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/arraylist/demo/ListDemo.java src/test/java/com/gimlism/translucent/arraylist/demo/ListDemoTest.java
git commit -m "feat(arraylist): scripted demo of the list event stream"
```

---

## Self-Review

**Spec coverage:**
- Standalone-concrete `arraylist.*` packages, no `hashmap.*` dependency → Tasks 1–4. ✓
- `AbstractList` + `RandomAccess`, full-List compliance, hot paths overridden → Tasks 1, 3. ✓
- Sealed `ListEvent` (`Append`/`Insert`/`Set`/`RemoveAt`/`Shift`/`Grow`), snapshot-per-event, `Grow` before/after → Tasks 1–3. ✓
- `ListSnapshot` capacity==slots + size-range invariant; sealed `SlotSnapshot` distinguishing `EmptySlot` from `FilledSlot(null)` → Task 1. ✓
- 1.5× growth (`oldCap + max(minGrowth, oldCap>>1)`) → Task 1; JDK-faithful lazy allocation + `0→10` jump + explicit-zero path → Task 2. ✓
- Fine-grained `Shift` (high→low insert, low→high remove), mid-slide snapshots, terminal `Insert`/`RemoveAt`, grow-before-shift → Task 3. ✓
- Re-entrancy guard (incl. non-structural `set`), `modCount` fail-fast, iterator remove → Tasks 1, 3. ✓
- `ConsoleListEventLogger` + `ListEventFormatter` + `ListRecordingListener` + demo → Tasks 1, 4. ✓

**Placeholder scan:** None — every step has complete, runnable code and real assertions.

**Type consistency:** `ListSnapshot(int,int,List<SlotSnapshot>)`; events `Append(Object,int,ListSnapshot)`, `Insert(Object,int,ListSnapshot)`, `Set(int,Object,Object,ListSnapshot)`, `RemoveAt(int,Object,ListSnapshot)`, `Shift(int,int,Object,ListSnapshot)`, `Grow(int,int,ListSnapshot,ListSnapshot)`; core methods `snapshot()` (package-private), `appendInternal`, `insertInternal`, `ensureCapacity`, `grow`, `beginMutation`, `emit`. Names consistent across tasks. Test hooks: package-private `snapshot()` (core tests), public `events()` on `ListRecordingListener`.

## Notes for the reviewer / final review

- **Mid-slide snapshot semantics** are the subtle part: insert bumps `size` *before* the shift loop (so each `Shift`'s snapshot shows the moved element in its new slot, with a transient duplicate at the source — the honest element-wise shift); remove keeps `size` until *after* the loop then clears the tail. If the final review prefers hiding the transient duplicate, that's a snapshot-timing change confined to `insertInternal`/`remove`.
- **`add(int size, e)` emits `Append`, not `Insert`** (append semantics for the boundary index). Deliberate; the grammar's `i==size` row documents it.
- **Deferred (next slices), by design:** the generic `StructureEvent`/`StructureSnapshot` extraction + shared transport/drawing primitives (now informed by two concrete structures); the ASCII "cells"-row viz + replayer for lists; bulk mutators (`addAll`/`removeIf`/…); the `Grow` special-case for the explicit-zero sentinel is covered but `MAX_ARRAY_SIZE`/huge-overflow handling is out of scope.
- Recommend the strong model for **Task 3** (the shift/snapshot-timing crux) and its review.
```
