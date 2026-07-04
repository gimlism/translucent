# Generic Instrumentation Substrate — Extraction — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the transport shared by `TeachingHashMap` and `TeachingArrayList` — listener, recorder, replayer, live visualizer, and the one `EventRenderer<E>` seam — into a `com.gimlism.translucent.substrate` package, so a future structure inherits recording/replay/visualization for free. Pure structural extraction: **no behaviour change**, the 143 existing tests are the safety net and stay green throughout.

**Architecture:** `substrate.events` holds the marker supertypes `StructureSnapshot`/`StructureEvent`, the generic `StructureEventListener<E>`, and the generic `RecordingListener<E>`. `substrate.viz` holds the `EventRenderer<E>` seam and the generic `Visualizer<E>`/`Replayer<E>`. Each structure's `*Snapshot` implements `StructureSnapshot`, its `*Event` extends `StructureEvent` (covariant `after()`), its renderer implements `EventRenderer<E>`, and its named listener/recorder/visualizer/replayer become thin aliases/subclasses of the generic types — preserving every existing call site. Drawing is NOT unified (divergent layouts).

**Tech Stack:** Java 21, Maven, JUnit 5. No new dependencies.

## Global Constraints

- Java 21 target, Maven, JDK 26. No behaviour change; all 143 existing tests stay green after every task.
- New package `com.gimlism.translucent.substrate.{events,viz}`. `hashmap.*` and `arraylist.*` both depend on `substrate`; neither depends on the other.
- Event vocabularies and snapshot record shapes are UNCHANGED and stay concrete + sealed. Formatters stay per-structure.
- `StructureSnapshot` is a **pure marker** (no accessors) — do not add `capacity()`/`size()`.
- Named per-structure classes remain as thin aliases/subclasses so call sites compile unchanged.

## File structure

- Create `substrate/events/`: `StructureSnapshot.java`, `StructureEvent.java`, `StructureEventListener.java`, `RecordingListener.java`, `package-info.java`.
- Create `substrate/viz/`: `EventRenderer.java`, `Visualizer.java`, `Replayer.java`.
- Modify `hashmap/events/`: `MapSnapshot` (implements StructureSnapshot), `MapEvent` (extends StructureEvent), `MapEventListener` (extends StructureEventListener<MapEvent>).
- Modify `arraylist/events/`: `ListSnapshot`, `ListEvent`, `ListEventListener` likewise.
- Modify `hashmap/core/TeachingHashMap` + `arraylist/core/TeachingArrayList`: widen the listener field + add/removeListener.
- Modify `hashmap/consumer/RecordingListener` + `arraylist/consumer/ListRecordingListener`: extend the generic recorder.
- Modify `hashmap/viz/`: `AsciiRenderer` (implements EventRenderer<MapEvent>), `AsciiVisualizer` + `AsciiReplayer` (thin subclasses). Same for `arraylist/viz/`.
- Tests under `src/test/java/com/gimlism/translucent/substrate/`.

---

### Task 1: Marker supertypes `StructureSnapshot` / `StructureEvent`

**Files:**
- Create: `src/main/java/com/gimlism/translucent/substrate/events/StructureSnapshot.java`, `StructureEvent.java`, `package-info.java`
- Modify: `hashmap/events/MapSnapshot.java`, `MapEvent.java`; `arraylist/events/ListSnapshot.java`, `ListEvent.java`
- Test: `src/test/java/com/gimlism/translucent/substrate/events/StructureModelTest.java`

**Interfaces:**
- Produces: `interface StructureSnapshot {}`; `interface StructureEvent { StructureSnapshot after(); }`. `MapSnapshot`/`ListSnapshot implements StructureSnapshot`; `MapEvent`/`ListEvent extends StructureEvent` with covariant `after()`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/substrate/events/StructureModelTest.java`
```java
package com.gimlism.translucent.substrate.events;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListSnapshot;
import org.junit.jupiter.api.Test;

class StructureModelTest {
    @Test
    void mapEventIsAStructureEventWithAStructureSnapshot() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new com.gimlism.translucent.hashmap.consumer.RecordingListener();
        map.addListener(rec);
        map.put(1, "a");
        MapEvent e = rec.events().get(0);
        StructureEvent se = assertInstanceOf(StructureEvent.class, e);
        StructureSnapshot snap = assertInstanceOf(StructureSnapshot.class, se.after());
        assertInstanceOf(MapSnapshot.class, snap);      // covariant after() still narrows
        assertSame(e.after(), se.after());
    }

    @Test
    void listEventIsAStructureEventWithAStructureSnapshot() {
        var list = new TeachingArrayList<String>(4);
        var rec = new com.gimlism.translucent.arraylist.consumer.ListRecordingListener();
        list.addListener(rec);
        list.add("a");
        ListEvent e = rec.events().get(0);
        StructureEvent se = assertInstanceOf(StructureEvent.class, e);
        assertInstanceOf(ListSnapshot.class, assertInstanceOf(StructureSnapshot.class, se.after()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — `StructureEvent`/`StructureSnapshot` do not exist (compile error).

- [ ] **Step 3: Write minimal implementation**

`substrate/events/StructureSnapshot.java`
```java
package com.gimlism.translucent.substrate.events;

/**
 * Marker supertype of every structure's immutable whole-state snapshot
 * (e.g. {@code MapSnapshot}, {@code ListSnapshot}). Intentionally a pure marker —
 * no {@code capacity()}/{@code size()} accessors — so it imposes no array-shaped
 * assumption a future structure (e.g. a Trie) could not satisfy.
 */
public interface StructureSnapshot {}
```

`substrate/events/StructureEvent.java`
```java
package com.gimlism.translucent.substrate.events;

/**
 * Marker supertype of every structure's immutable event. Each event carries a
 * whole-state {@link StructureSnapshot} taken at emission. Concrete event types
 * (the sealed {@code MapEvent}/{@code ListEvent} vocabularies) narrow {@link #after()}
 * covariantly to their own snapshot type.
 */
public interface StructureEvent {
    StructureSnapshot after();
}
```

`substrate/events/package-info.java`
```java
/**
 * The shared instrumentation substrate: structure-agnostic event model
 * ({@link com.gimlism.translucent.substrate.events.StructureEvent} /
 * {@link com.gimlism.translucent.substrate.events.StructureSnapshot}) and transport
 * ({@link com.gimlism.translucent.substrate.events.StructureEventListener},
 * {@link com.gimlism.translucent.substrate.events.RecordingListener}). Each teaching
 * structure keeps its own concrete, sealed event vocabulary and simply plugs into this.
 */
package com.gimlism.translucent.substrate.events;
```

Wire the four concrete types (add `implements`/`extends`; `after()` needs no change — the covariant return already satisfies the supertype):

`MapSnapshot` — `public record MapSnapshot(...) implements StructureSnapshot {`
`ListSnapshot` — `public record ListSnapshot(...) implements StructureSnapshot {`
`MapEvent` — `public sealed interface MapEvent extends StructureEvent permits ... {` (keep `MapSnapshot after();`)
`ListEvent` — `public sealed interface ListEvent extends StructureEvent permits ... {` (keep `ListSnapshot after();`)

(Add the `import com.gimlism.translucent.substrate.events.StructureSnapshot;` / `StructureEvent;` to each.)

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `StructureModelTest` green; all 143 existing tests still pass (additive supertypes).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/substrate/events src/main/java/com/gimlism/translucent/hashmap/events/MapSnapshot.java src/main/java/com/gimlism/translucent/hashmap/events/MapEvent.java src/main/java/com/gimlism/translucent/arraylist/events/ListSnapshot.java src/main/java/com/gimlism/translucent/arraylist/events/ListEvent.java src/test/java/com/gimlism/translucent/substrate docs/superpowers
git commit -m "refactor(substrate): add StructureEvent/StructureSnapshot marker supertypes"
```

---

### Task 2: Generic `StructureEventListener<E>` + `RecordingListener<E>`

**Files:**
- Create: `substrate/events/StructureEventListener.java`, `RecordingListener.java`
- Modify: `hashmap/events/MapEventListener.java`, `arraylist/events/ListEventListener.java` (extend the generic listener)
- Modify: `hashmap/core/TeachingHashMap.java`, `arraylist/core/TeachingArrayList.java` (widen listener field + add/removeListener + emit loop)
- Modify: `hashmap/consumer/RecordingListener.java`, `arraylist/consumer/ListRecordingListener.java` (extend generic recorder)
- Test: `src/test/java/com/gimlism/translucent/substrate/events/RecordingListenerTest.java`

**Interfaces:**
- Produces: `@FunctionalInterface StructureEventListener<E extends StructureEvent> { void onEvent(E); }`; `class RecordingListener<E extends StructureEvent> implements StructureEventListener<E>` with `events()`/`clear()`. Named listeners become sub-interfaces; named recorders become empty subclasses; the cores accept `StructureEventListener<...>`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/substrate/events/RecordingListenerTest.java`
```java
package com.gimlism.translucent.substrate.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.events.MapEvent;
import org.junit.jupiter.api.Test;

class RecordingListenerTest {
    // the SAME generic recorder type serves both structures
    @Test
    void genericRecorderRecordsMapEvents() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new RecordingListener<MapEvent>();
        map.addListener(rec);
        map.put(1, "a");
        map.put(2, "b");
        assertEquals(2, rec.events().size());
        rec.clear();
        assertEquals(0, rec.events().size());
    }

    @Test
    void genericRecorderRecordsListEvents() {
        var list = new TeachingArrayList<String>(4);
        var rec = new RecordingListener<ListEvent>();
        list.addListener(rec);
        list.add("a");
        assertEquals(1, rec.events().size());
    }

    @Test
    void eventsViewIsUnmodifiable() {
        var rec = new RecordingListener<MapEvent>();
        assertFalse(rec.events() instanceof java.util.ArrayList);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — `StructureEventListener`/`RecordingListener` in `substrate.events` do not exist; and `addListener` does not yet accept a `RecordingListener<MapEvent>`.

- [ ] **Step 3: Write minimal implementation**

`substrate/events/StructureEventListener.java`
```java
package com.gimlism.translucent.substrate.events;

/**
 * Synchronous consumer of a structure's event stream. Invoked during a mutation,
 * sometimes mid-operation. A listener may read the structure and add/remove
 * listeners, but must not structurally mutate it from within {@link #onEvent}
 * (each structure rejects re-entrant mutation with a
 * {@link java.util.ConcurrentModificationException}).
 */
@FunctionalInterface
public interface StructureEventListener<E extends StructureEvent> {
    void onEvent(E event);
}
```

`substrate/events/RecordingListener.java`
```java
package com.gimlism.translucent.substrate.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects events in emission order. Backbone of sequence-based tests and replay. */
public class RecordingListener<E extends StructureEvent> implements StructureEventListener<E> {
    private final List<E> events = new ArrayList<>();

    @Override
    public void onEvent(E event) {
        events.add(event);
    }

    /** Events in emission order (unmodifiable view). */
    public List<E> events() {
        return Collections.unmodifiableList(events);
    }

    public void clear() {
        events.clear();
    }
}
```

`hashmap/events/MapEventListener.java` — keep the Javadoc; make it a named functional alias:
```java
@FunctionalInterface
public interface MapEventListener extends StructureEventListener<MapEvent> {}
```
`arraylist/events/ListEventListener.java` likewise:
```java
@FunctionalInterface
public interface ListEventListener extends StructureEventListener<ListEvent> {}
```

`hashmap/consumer/RecordingListener.java` — reduce to an empty subclass (drop the old body/imports):
```java
package com.gimlism.translucent.hashmap.consumer;

import com.gimlism.translucent.hashmap.events.MapEvent;

/** Records {@link MapEvent}s (the map-typed {@link com.gimlism.translucent.substrate.events.RecordingListener}). */
public class RecordingListener extends com.gimlism.translucent.substrate.events.RecordingListener<MapEvent> {}
```
`arraylist/consumer/ListRecordingListener.java` likewise (`<ListEvent>`).

In `TeachingHashMap`: widen the listener plumbing (import `StructureEventListener`; drop the now-unused `MapEventListener` import):
```java
    private final List<StructureEventListener<MapEvent>> listeners = new ArrayList<>();

    public void addListener(StructureEventListener<MapEvent> listener) { listeners.add(listener); }
    public void removeListener(StructureEventListener<MapEvent> listener) { listeners.remove(listener); }

    private void emit(MapEvent event) {
        for (StructureEventListener<MapEvent> listener : List.copyOf(listeners)) listener.onEvent(event);
    }
```
`TeachingArrayList` likewise (`<ListEvent>`).

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `RecordingListenerTest` green; all existing tests pass (concrete `RecordingListener`/`ListRecordingListener` still construct and record; existing `implements MapEventListener` classes and lambdas still register through the widened `addListener`).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(substrate): generic StructureEventListener + RecordingListener"
```

---

### Task 3: Generic `EventRenderer<E>` seam + `Visualizer<E>` / `Replayer<E>`

**Files:**
- Create: `substrate/viz/EventRenderer.java`, `Visualizer.java`, `Replayer.java`
- Modify: `hashmap/viz/AsciiRenderer.java` (implements EventRenderer<MapEvent>), `AsciiVisualizer.java` + `AsciiReplayer.java` (thin subclasses)
- Modify: `arraylist/viz/AsciiListRenderer.java`, `AsciiListVisualizer.java`, `AsciiListReplayer.java` likewise
- Test: `src/test/java/com/gimlism/translucent/substrate/viz/GenericDriversTest.java`

**Interfaces:**
- Produces: `@FunctionalInterface EventRenderer<E extends StructureEvent> { String renderEvent(E); }`; `class Visualizer<E> implements StructureEventListener<E>`; `class Replayer<E>` with `run`/`autoPlay`. The four `Ascii*` drivers become thin subclasses; the two renderers implement `EventRenderer<E>`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/substrate/viz/GenericDriversTest.java`
```java
package com.gimlism.translucent.substrate.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.viz.AsciiListRenderer;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.viz.AsciiRenderer;
import com.gimlism.translucent.hashmap.viz.Palette;
import com.gimlism.translucent.substrate.events.RecordingListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GenericDriversTest {
    @Test
    void genericVisualizerRendersMapFramesLive() {
        var buffer = new ByteArrayOutputStream();
        var map = new TeachingHashMap<Integer, String>();
        map.addListener(new Visualizer<MapEvent>(
            new PrintStream(buffer, true, StandardCharsets.UTF_8),
            new AsciiRenderer(new Palette(Palette.Mode.PLAIN))));
        map.put(1, "a");
        assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("PUT 1=a"), buffer.toString());
    }

    @Test
    void genericReplayerStepsThroughListFrames() {
        var list = new TeachingArrayList<String>(4);
        var rec = new RecordingListener<ListEvent>();
        list.addListener(rec);
        list.add("a");
        list.add("b");
        var replayer = new Replayer<ListEvent>(rec.events(), new AsciiListRenderer());
        var buffer = new ByteArrayOutputStream();
        replayer.autoPlay(new PrintStream(buffer, true, StandardCharsets.UTF_8), 0);
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("── frame 1/2 ──"), out);
        assertTrue(out.contains("── frame 2/2 ──"), out);
    }

    @Test
    void genericReplayerRunHandlesQuit() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new RecordingListener<MapEvent>();
        map.addListener(rec);
        map.put(1, "a");
        var replayer = new Replayer<MapEvent>(rec.events(), new AsciiRenderer(new Palette(Palette.Mode.PLAIN)));
        var buffer = new ByteArrayOutputStream();
        replayer.run(new ByteArrayInputStream("q\n".getBytes(StandardCharsets.UTF_8)),
                     new PrintStream(buffer, true, StandardCharsets.UTF_8));
        assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("── frame 1/1 ──"), buffer.toString());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — `EventRenderer`/`Visualizer`/`Replayer` in `substrate.viz` do not exist.

- [ ] **Step 3: Write minimal implementation**

`substrate/viz/EventRenderer.java`
```java
package com.gimlism.translucent.substrate.viz;

import com.gimlism.translucent.substrate.events.StructureEvent;

/** The one seam a structure supplies to the substrate: an event → ASCII frame. */
@FunctionalInterface
public interface EventRenderer<E extends StructureEvent> {
    String renderEvent(E event);
}
```

`substrate/viz/Visualizer.java`
```java
package com.gimlism.translucent.substrate.viz;

import com.gimlism.translucent.substrate.events.StructureEvent;
import com.gimlism.translucent.substrate.events.StructureEventListener;
import java.io.PrintStream;

/** Live consumer that prints an ASCII frame to a stream on every event. */
public class Visualizer<E extends StructureEvent> implements StructureEventListener<E> {
    private final PrintStream out;
    private final EventRenderer<E> renderer;

    public Visualizer(PrintStream out, EventRenderer<E> renderer) {
        this.out = out;
        this.renderer = renderer;
    }

    @Override
    public void onEvent(E event) {
        out.println(renderer.renderEvent(event));
        out.println();
    }
}
```

`substrate/viz/Replayer.java` (logic moved verbatim from `AsciiReplayer`, typed to `E`)
```java
package com.gimlism.translucent.substrate.viz;

import com.gimlism.translucent.substrate.events.StructureEvent;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

/** Steps a recorded event stream frame-by-frame (each event's snapshot is a full frame). */
public class Replayer<E extends StructureEvent> {
    private final List<E> events;
    private final EventRenderer<E> renderer;

    public Replayer(List<E> events, EventRenderer<E> renderer) {
        this.events = events;
        this.renderer = renderer;
    }

    /** Interactive: Enter = next, {@code b} = back, {@code q} = quit; next past the end ends. */
    public void run(InputStream in, PrintStream out) {
        if (events.isEmpty()) {
            out.println("(no events to replay)");
            return;
        }
        Scanner scanner = new Scanner(in, StandardCharsets.UTF_8);
        int i = 0;
        while (true) {
            printFrame(out, i);
            out.print("[Enter=next, b=back, q=quit] ");
            out.flush();
            if (!scanner.hasNextLine()) break;
            String cmd = scanner.nextLine().trim();
            if (cmd.equals("q")) break;
            if (cmd.equals("b")) {
                i = Math.max(0, i - 1);
            } else {
                if (i == events.size() - 1) break;
                i++;
            }
        }
    }

    /** Non-interactive: render every frame in order, sleeping {@code delayMillis} between them. */
    public void autoPlay(PrintStream out, long delayMillis) {
        for (int i = 0; i < events.size(); i++) {
            printFrame(out, i);
            if (delayMillis > 0 && i < events.size() - 1) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void printFrame(PrintStream out, int i) {
        out.println("── frame " + (i + 1) + "/" + events.size() + " ──");
        out.println(renderer.renderEvent(events.get(i)));
    }
}
```

Make the renderers implement the seam:
- `AsciiRenderer` — `public final class AsciiRenderer implements EventRenderer<MapEvent> {` (add import; `renderEvent(MapEvent)` already matches).
- `AsciiListRenderer` — `public final class AsciiListRenderer implements EventRenderer<ListEvent> {`.

Replace the four drivers with thin subclasses (delete their old fields/logic):

`hashmap/viz/AsciiVisualizer.java`
```java
package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.substrate.viz.Visualizer;
import java.io.PrintStream;

/** Live ASCII visualizer for the map (a {@link Visualizer} wired with an {@link AsciiRenderer}). */
public final class AsciiVisualizer extends Visualizer<MapEvent> {
    public AsciiVisualizer(PrintStream out, AsciiRenderer renderer) { super(out, renderer); }
    public AsciiVisualizer(PrintStream out) { this(out, new AsciiRenderer(Palette.auto())); }
}
```

`hashmap/viz/AsciiReplayer.java`
```java
package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.substrate.viz.Replayer;
import java.util.List;

/** Step-through ASCII replayer for a recorded map stream (a {@link Replayer} + {@link AsciiRenderer}). */
public final class AsciiReplayer extends Replayer<MapEvent> {
    public AsciiReplayer(List<MapEvent> events, AsciiRenderer renderer) { super(events, renderer); }
}
```

`arraylist/viz/AsciiListVisualizer.java` and `AsciiListReplayer.java` — the same, with `ListEvent`, `AsciiListRenderer`, and the visualizer's default constructor `this(out, new AsciiListRenderer())`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `GenericDriversTest` green; every existing viz test (`AsciiVisualizerTest`, `AsciiReplayerTest`, `AsciiListVisualizerTest`, `AsciiListReplayerTest`, the demos) passes unchanged against the thin subclasses.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(substrate): generic EventRenderer seam, Visualizer and Replayer"
```

---

### Task 4: Capstone — one substrate, both structures

**Files:**
- Test: `src/test/java/com/gimlism/translucent/substrate/SubstrateReuseTest.java`

**Interfaces:**
- Consumes: everything above. Proves a single generic code path drives both a map and a list stream — the point of the slice.

- [ ] **Step 1: Write the failing test** (fails only if the substrate is not actually structure-agnostic)

`src/test/java/com/gimlism/translucent/substrate/SubstrateReuseTest.java`
```java
package com.gimlism.translucent.substrate;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.viz.AsciiListRenderer;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.viz.AsciiRenderer;
import com.gimlism.translucent.hashmap.viz.Palette;
import com.gimlism.translucent.substrate.events.RecordingListener;
import com.gimlism.translucent.substrate.events.StructureEvent;
import com.gimlism.translucent.substrate.viz.EventRenderer;
import com.gimlism.translucent.substrate.viz.Replayer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubstrateReuseTest {
    // ONE generic helper renders a recorded stream from ANY structure.
    private static <E extends StructureEvent> String renderAll(List<E> events, EventRenderer<E> renderer) {
        var buffer = new ByteArrayOutputStream();
        new Replayer<>(events, renderer).autoPlay(new PrintStream(buffer, true, StandardCharsets.UTF_8), 0);
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void oneGenericPathDrivesBothMapAndList() {
        var map = new TeachingHashMap<Integer, String>();
        var mapRec = new RecordingListener<MapEvent>();
        map.addListener(mapRec);
        map.put(1, "a");

        var list = new TeachingArrayList<String>(4);
        var listRec = new RecordingListener<ListEvent>();
        list.addListener(listRec);
        list.add("z");

        // same renderAll<E>, two structures
        assertTrue(renderAll(mapRec.events(), new AsciiRenderer(new Palette(Palette.Mode.PLAIN)))
            .contains("PUT 1=a"));
        assertTrue(renderAll(listRec.events(), new AsciiListRenderer())
            .contains("APPEND z @ 0"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails** — then passes once the substrate exists

Run: `mvn -q test`
Expected: with Tasks 1–3 done, this compiles and PASSES; it is the executable proof that the substrate is structure-agnostic. (Before Task 3 it would not compile.)

- [ ] **Step 3–4: Confirm green**

Run: `mvn -q test`
Expected: PASS — full suite green (143 existing + the new substrate tests).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "test(substrate): capstone proving one substrate drives map and list"
```

---

## Self-Review

**Spec coverage:**
- Transport-only extraction; drawing untouched → all tasks. ✓
- `StructureSnapshot`/`StructureEvent` markers, covariant `after()` → Task 1. ✓
- Generic `StructureEventListener<E>` + `RecordingListener<E>`; cores widened; named aliases/subclasses preserved → Task 2. ✓
- `EventRenderer<E>` seam; generic `Visualizer<E>`/`Replayer<E>`; thin `Ascii*` subclasses; renderers implement the seam → Task 3. ✓
- Vocabularies + snapshots stay concrete/sealed; formatters untouched → all. ✓
- 143 existing tests stay green each task; capstone proves reuse → Tasks 1–4. ✓

**Placeholder scan:** None — complete runnable code and real assertions.

**Type consistency:** `StructureEvent.after(): StructureSnapshot`; `MapEvent extends StructureEvent` (covariant `MapSnapshot after()`); `StructureEventListener<E extends StructureEvent>`; `RecordingListener<E extends StructureEvent>`; `EventRenderer<E extends StructureEvent>`; `Visualizer<E>`/`Replayer<E>` constructors `(PrintStream, EventRenderer<E>)` / `(List<E>, EventRenderer<E>)`. `AsciiRenderer implements EventRenderer<MapEvent>`; `AsciiListRenderer implements EventRenderer<ListEvent>`. Named subclasses keep existing constructor signatures.

## Notes for the reviewer / final review

- **Behaviour-preservation is the whole game.** The strongest evidence is that all 143 pre-existing tests compile and pass **unchanged** against the widened APIs and thin subclasses. Watch for any call site that referenced a now-removed field/method rather than the public surface.
- **Covariant `after()`** is the one non-obvious Java move: `MapEvent` re-declares `MapSnapshot after()` overriding `StructureEvent.after(): StructureSnapshot`; record accessors satisfy it. Confirm no raw-type or unchecked warnings crept in.
- **Deliberately not done:** drawing/renderer unification (divergent vertical/horizontal layouts); `StructureSnapshot` accessors (kept a pure marker to avoid array-shaped assumptions). These are documented scope, not omissions.
- The named `Ascii*`/`RecordingListener`/`ListRecordingListener` classes are retained only as thin call-site-preserving shims; a future cleanup could inline them, but keeping them keeps this slice a zero-churn, zero-behaviour-change extraction.
```
