# Teaching ArrayList — ASCII Visualizer — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the ArrayList's `ListEvent` stream as ASCII in the terminal — a horizontal contiguous cells row that shows appends, the amortized grow, and element shifts — via a pure renderer plus a live driver and a step-through replayer, mirroring the HashMap ASCII-visualizer slice.

**Architecture:** New `com.gimlism.translucent.arraylist.viz` package depending only on `arraylist.events`. `AsciiListRenderer` (pure `ListSnapshot`/`ListEvent` → text, no I/O, **no `Palette`** — list elements are uncoloured), `AsciiListVisualizer implements ListEventListener` (live), `AsciiListReplayer` (forward/back/quit scrubber + auto-play), `ListVizDemo`. Standalone-concrete; the duplication with `hashmap.viz` is intended fuel for the later generic-substrate extraction.

**Tech Stack:** Java 21, Maven, JUnit 5. No new dependencies.

## Global Constraints

- Java 21 target, Maven, built/tested on JDK 26 (same as the rest).
- Package `com.gimlism.translucent.arraylist.viz`; depends only on `arraylist.events`. No dependency on `hashmap.*`.
- **Layout:** horizontal row `[ a | b | c | · ]`; the affected slot wrapped `>x<`, others padded ` x `; `·` for `EmptySlot`; header `list: cap=<C> size=<S>`; capacity-0 row is `[]`.
- **No `Palette`** — the list renderer takes no colour abstraction (a revealed seam: colour is tree-specific).
- `affectedIndex`: `Append`/`Insert`/`Set`/`RemoveAt` → `index()`; `Shift` → `toIndex()` (destination); `Grow` → −1.
- Both paths (live + replay) render through the one pure `AsciiListRenderer` so output is identical for the same event.
- `exec:java` default stays `hashmap.demo.VizDemo`; `ListVizDemo` is run directly via `java -cp target/classes …`.

## File structure

- Create `arraylist/viz/AsciiListRenderer.java` (pure).
- Create `arraylist/viz/AsciiListVisualizer.java` (live `ListEventListener`).
- Create `arraylist/viz/AsciiListReplayer.java` (scrubber + auto-play).
- Create `arraylist/demo/ListVizDemo.java`.
- Tests under `src/test/java/com/gimlism/translucent/arraylist/{viz,demo}/`.

---

### Task 1: `AsciiListRenderer` — pure renderer (golden strings)

**Files:**
- Create: `src/main/java/com/gimlism/translucent/arraylist/viz/AsciiListRenderer.java`
- Test: `src/test/java/com/gimlism/translucent/arraylist/viz/AsciiListRenderTest.java`

**Interfaces:**
- Produces: `renderList(ListSnapshot, int highlightIndex)`, `renderEvent(ListEvent)`, `static int affectedIndex(ListEvent)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/arraylist/viz/AsciiListRenderTest.java`
```java
package com.gimlism.translucent.arraylist.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gimlism.translucent.arraylist.events.Append;
import com.gimlism.translucent.arraylist.events.EmptySlot;
import com.gimlism.translucent.arraylist.events.FilledSlot;
import com.gimlism.translucent.arraylist.events.Grow;
import com.gimlism.translucent.arraylist.events.ListSnapshot;
import com.gimlism.translucent.arraylist.events.RemoveAt;
import com.gimlism.translucent.arraylist.events.Set;
import com.gimlism.translucent.arraylist.events.Shift;
import com.gimlism.translucent.arraylist.events.SlotSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsciiListRenderTest {
    private final AsciiListRenderer r = new AsciiListRenderer();

    private static ListSnapshot snap(int cap, int size, Object... elems) {
        var slots = new java.util.ArrayList<SlotSnapshot>();
        for (int i = 0; i < cap; i++) slots.add(i < size ? new FilledSlot(elems[i]) : new EmptySlot());
        return new ListSnapshot(cap, size, slots);
    }

    @Test
    void rendersRowWithHighlightAndEmptyTail() {
        var s = snap(6, 5, "a", "b", "X", "c", "d");
        String expected = String.join("\n",
            "list: cap=6 size=5",
            "[ a | b |>X<| c | d | · ]");
        assertEquals(expected, r.renderList(s, 2));
    }

    @Test
    void rendersRowWithNoHighlight() {
        var s = snap(4, 2, "a", "b");
        String expected = String.join("\n",
            "list: cap=4 size=2",
            "[ a | b | · | · ]");
        assertEquals(expected, r.renderList(s, -1));
    }

    @Test
    void rendersEmptyCapacityAsBrackets() {
        String expected = String.join("\n",
            "list: cap=0 size=0",
            "[]");
        assertEquals(expected, r.renderList(new ListSnapshot(0, 0, List.of()), -1));
    }

    @Test
    void renderEventPutsLabelAboveRowAndHighlightsAffectedSlot() {
        var s = snap(6, 5, "a", "b", "c", "d", "e");
        String expected = String.join("\n",
            "APPEND e @ 4",
            "list: cap=6 size=5",
            "[ a | b | c | d |>e<| · ]");
        assertEquals(expected, r.renderEvent(new Append("e", 4, s)));
    }

    @Test
    void growHighlightsNothing() {
        var s = snap(6, 4, "a", "b", "c", "d");
        String expected = String.join("\n",
            "GROW cap 4 -> 6",
            "list: cap=6 size=4",
            "[ a | b | c | d | · | · ]");
        assertEquals(expected, r.renderEvent(new Grow(4, 6, snap(4, 4, "a", "b", "c", "d"), s)));
    }

    @Test
    void affectedIndexPerEventType() {
        var s = snap(4, 3, "a", "b", "c");
        assertEquals(2, AsciiListRenderer.affectedIndex(new Append("c", 2, s)));
        assertEquals(1, AsciiListRenderer.affectedIndex(new Set(1, "old", "b", s)));
        assertEquals(0, AsciiListRenderer.affectedIndex(new RemoveAt(0, "a", s)));
        assertEquals(3, AsciiListRenderer.affectedIndex(new Shift(2, 3, "b", s))); // destination
        assertEquals(-1, AsciiListRenderer.affectedIndex(new Grow(2, 4, s, s)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — `AsciiListRenderer` does not exist.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/gimlism/translucent/arraylist/viz/AsciiListRenderer.java`
```java
package com.gimlism.translucent.arraylist.viz;

import com.gimlism.translucent.arraylist.events.Append;
import com.gimlism.translucent.arraylist.events.EmptySlot;
import com.gimlism.translucent.arraylist.events.FilledSlot;
import com.gimlism.translucent.arraylist.events.Grow;
import com.gimlism.translucent.arraylist.events.Insert;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListEventFormatter;
import com.gimlism.translucent.arraylist.events.ListSnapshot;
import com.gimlism.translucent.arraylist.events.RemoveAt;
import com.gimlism.translucent.arraylist.events.Set;
import com.gimlism.translucent.arraylist.events.Shift;
import com.gimlism.translucent.arraylist.events.SlotSnapshot;
import java.util.List;
import java.util.StringJoiner;

/** Pure renderer: turns immutable list snapshots/events into ASCII text. No I/O, no colour. */
public final class AsciiListRenderer {

    /** Header line plus a horizontal cells row; {@code highlightIndex} (−1 for none) is wrapped {@code >x<}. */
    public String renderList(ListSnapshot snap, int highlightIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append("list: cap=").append(snap.capacity()).append(" size=").append(snap.size());
        StringJoiner cells = new StringJoiner("|", "[", "]");
        List<SlotSnapshot> slots = snap.slots();
        for (int i = 0; i < slots.size(); i++) {
            String content = switch (slots.get(i)) {
                case FilledSlot f -> String.valueOf(f.element());
                case EmptySlot e -> "·";
            };
            cells.add(i == highlightIndex ? ">" + content + "<" : " " + content + " ");
        }
        return sb.append('\n').append(cells).toString();
    }

    /** The event's one-line label (from {@link ListEventFormatter}) above the resulting row. */
    public String renderEvent(ListEvent e) {
        return ListEventFormatter.format(e) + "\n" + renderList(e.after(), affectedIndex(e));
    }

    /** The slot an event touched, or −1 for a whole-array {@link Grow}. */
    public static int affectedIndex(ListEvent e) {
        return switch (e) {
            case Append a -> a.index();
            case Insert in -> in.index();
            case Set s -> s.index();
            case RemoveAt r -> r.index();
            case Shift sh -> sh.toIndex();   // where the element landed
            case Grow g -> -1;
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `AsciiListRenderTest` green; all prior tests still pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/arraylist/viz/AsciiListRenderer.java src/test/java/com/gimlism/translucent/arraylist/viz/AsciiListRenderTest.java
git commit -m "feat(arraylist-viz): pure AsciiListRenderer (horizontal cells row)"
```

---

### Task 2: `AsciiListVisualizer` — live driver

**Files:**
- Create: `src/main/java/com/gimlism/translucent/arraylist/viz/AsciiListVisualizer.java`
- Test: `src/test/java/com/gimlism/translucent/arraylist/viz/AsciiListVisualizerTest.java`

**Interfaces:**
- Consumes: `AsciiListRenderer`, `ListEventListener`, `TeachingArrayList`.
- Produces: `AsciiListVisualizer implements ListEventListener` printing `renderEvent` + blank line per event; a convenience constructor defaulting the renderer.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/arraylist/viz/AsciiListVisualizerTest.java`
```java
package com.gimlism.translucent.arraylist.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AsciiListVisualizerTest {
    @Test
    void liveVisualizerRendersAppendGrowInsertRemoveFrames() {
        var buffer = new ByteArrayOutputStream();
        var list = new TeachingArrayList<String>(4);
        list.addListener(new AsciiListVisualizer(new PrintStream(buffer, true, StandardCharsets.UTF_8)));

        for (String s : new String[]{"a", "b", "c", "d", "e"}) list.add(s); // grow 4 -> 6
        list.add(1, "X");   // shift right + insert
        list.remove(2);     // shift left + remove

        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("GROW cap 4 -> 6"), out);
        assertTrue(out.contains("APPEND e @ 4"), out);
        assertTrue(out.contains("INSERT X @ 1"), out);
        assertTrue(out.contains("SHIFT"), out);
        assertTrue(out.contains("REMOVE @ 2"), out);
        // a rendered row appears (highlight marker present)
        assertTrue(out.contains(">X<"), out);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — `AsciiListVisualizer` does not exist.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/gimlism/translucent/arraylist/viz/AsciiListVisualizer.java`
```java
package com.gimlism.translucent.arraylist.viz;

import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListEventListener;
import java.io.PrintStream;

/** A live consumer that prints an ASCII frame to a stream on every list event. */
public final class AsciiListVisualizer implements ListEventListener {
    private final PrintStream out;
    private final AsciiListRenderer renderer;

    public AsciiListVisualizer(PrintStream out, AsciiListRenderer renderer) {
        this.out = out;
        this.renderer = renderer;
    }

    public AsciiListVisualizer(PrintStream out) {
        this(out, new AsciiListRenderer());
    }

    @Override
    public void onEvent(ListEvent event) {
        out.println(renderer.renderEvent(event));
        out.println();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `AsciiListVisualizerTest` green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/arraylist/viz/AsciiListVisualizer.java src/test/java/com/gimlism/translucent/arraylist/viz/AsciiListVisualizerTest.java
git commit -m "feat(arraylist-viz): live AsciiListVisualizer ListEventListener"
```

---

### Task 3: `AsciiListReplayer` — step-through / scrubber

**Files:**
- Create: `src/main/java/com/gimlism/translucent/arraylist/viz/AsciiListReplayer.java`
- Test: `src/test/java/com/gimlism/translucent/arraylist/viz/AsciiListReplayerTest.java`

**Interfaces:**
- Consumes: `ListEvent`, `AsciiListRenderer`, `ListRecordingListener`.
- Produces: `AsciiListReplayer(List<ListEvent>, AsciiListRenderer)` with `run(InputStream, PrintStream)` (Enter=next, `b`=back, `q`=quit) and `autoPlay(PrintStream, long)`; a `── frame N/M ──` counter per frame. Behaviourally identical to `AsciiReplayer`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/arraylist/viz/AsciiListReplayerTest.java`
```java
package com.gimlism.translucent.arraylist.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.consumer.ListRecordingListener;
import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AsciiListReplayerTest {
    private static ListRecordingListener recordThreeAppends() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add("a");
        list.add("b");
        list.add("c");
        return rec;
    }

    @Test
    void steppingForwardBackAndQuitWalksFrames() {
        var rec = recordThreeAppends(); // 3 events
        var replayer = new AsciiListReplayer(rec.events(), new AsciiListRenderer());
        var buffer = new ByteArrayOutputStream();
        var in = new ByteArrayInputStream("\nb\nq\n".getBytes(StandardCharsets.UTF_8)); // next, back, quit
        replayer.run(in, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("── frame 1/3 ──"), out);
        assertTrue(out.contains("── frame 2/3 ──"), out);
        int first = out.indexOf("frame 1/3");
        assertTrue(first >= 0 && out.indexOf("frame 1/3", first + 1) > first, "frame 1 shown again after back");
    }

    @Test
    void autoPlayRendersEveryFrameInOrder() {
        var rec = recordThreeAppends();
        var replayer = new AsciiListReplayer(rec.events(), new AsciiListRenderer());
        var buffer = new ByteArrayOutputStream();
        replayer.autoPlay(new PrintStream(buffer, true, StandardCharsets.UTF_8), 0);
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("── frame 1/3 ──"), out);
        assertTrue(out.contains("── frame 3/3 ──"), out);
        assertTrue(out.indexOf("frame 1/3") < out.indexOf("frame 2/3"), out);
        assertTrue(out.indexOf("frame 2/3") < out.indexOf("frame 3/3"), out);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — `AsciiListReplayer` does not exist.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/gimlism/translucent/arraylist/viz/AsciiListReplayer.java` (mirrors `hashmap.viz.AsciiReplayer`)
```java
package com.gimlism.translucent.arraylist.viz;

import com.gimlism.translucent.arraylist.events.ListEvent;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

/** Steps a recorded list-event stream frame-by-frame (each event's snapshot is a full frame). */
public final class AsciiListReplayer {
    private final List<ListEvent> events;
    private final AsciiListRenderer renderer;

    public AsciiListReplayer(List<ListEvent> events, AsciiListRenderer renderer) {
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

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `AsciiListReplayerTest` green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/arraylist/viz/AsciiListReplayer.java src/test/java/com/gimlism/translucent/arraylist/viz/AsciiListReplayerTest.java
git commit -m "feat(arraylist-viz): AsciiListReplayer step-through and auto-play"
```

---

### Task 4: `ListVizDemo` — the visual demo

**Files:**
- Create: `src/main/java/com/gimlism/translucent/arraylist/demo/ListVizDemo.java`
- Test: `src/test/java/com/gimlism/translucent/arraylist/demo/ListVizDemoTest.java`

**Interfaces:**
- Consumes: `TeachingArrayList`, `AsciiListVisualizer`.
- Produces: `ListVizDemo.run(PrintStream)` — the `ListDemo` story rendered as live frames; `main` calls `run(System.out)`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/arraylist/demo/ListVizDemoTest.java`
```java
package com.gimlism.translucent.arraylist.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ListVizDemoTest {
    @Test
    void runRendersLabelledFramesWithGrowShiftAndHighlight() {
        var buffer = new ByteArrayOutputStream();
        ListVizDemo.run(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("GROW cap 4 -> 6"), out);
        assertTrue(out.contains("SHIFT"), out);
        assertTrue(out.contains("INSERT X @ 1"), out);
        assertTrue(out.contains("REMOVE @ 2"), out);
        assertTrue(out.contains(">X<"), out); // the inserted element highlighted in its row
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — `ListVizDemo` does not exist.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/gimlism/translucent/arraylist/demo/ListVizDemo.java`
```java
package com.gimlism.translucent.arraylist.demo;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.viz.AsciiListVisualizer;
import java.io.PrintStream;

/**
 * Same scripted story as {@link ListDemo}, rendered as live ASCII frames instead of a text log:
 * every mutation prints the contiguous cells row with the affected slot highlighted.
 */
public class ListVizDemo {
    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream out) {
        var list = new TeachingArrayList<String>(4);
        list.addListener(new AsciiListVisualizer(out));

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
Expected: PASS — `ListVizDemoTest` green. Optionally eyeball: `java -cp target/classes com.gimlism.translucent.arraylist.demo.ListVizDemo`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/arraylist/demo/ListVizDemo.java src/test/java/com/gimlism/translucent/arraylist/demo/ListVizDemoTest.java
git commit -m "feat(arraylist-viz): live ASCII demo of the list event stream"
```

---

## Self-Review

**Spec coverage:**
- Horizontal cells row, `>x<` highlight, `·` empty, `[]` empty-capacity, no `Palette` → Task 1. ✓
- `affectedIndex` (Shift→destination, Grow→−1) → Task 1. ✓
- Live `ListEventListener` driver → Task 2; forward/back/quit scrubber + auto-play → Task 3. ✓
- Both drivers render through the one pure `AsciiListRenderer` → Tasks 2/3. ✓
- Demo mirroring the `ListDemo` story → Task 4. ✓

**Placeholder scan:** None — every step has complete, runnable code and real assertions.

**Type consistency:** `AsciiListRenderer.renderList(ListSnapshot,int)` / `renderEvent(ListEvent)` / `static affectedIndex(ListEvent)`; `AsciiListVisualizer(PrintStream[,AsciiListRenderer])`; `AsciiListReplayer(List<ListEvent>,AsciiListRenderer)` with `run(InputStream,PrintStream)`/`autoPlay(PrintStream,long)`. Golden strings use exact `ListEventFormatter` labels from the core slice.

## Notes for the reviewer / final review

- **Layout is the decision to eyeball:** horizontal contiguous row vs the HashMap's vertical bucket list. Chosen for array contiguity; if the final review prefers vertical, it's a contained rewrite of `renderList` + its golden tests only.
- **Intended duplication:** `AsciiListReplayer` ≈ `AsciiReplayer`, and the two live visualizers are near-identical. This is the second concrete example that the later generic-substrate extraction slice unifies (a `StructureReplayer<E>` + structure-agnostic drivers). Flagged, not accidental.
- Mid-slide rows (a `Shift`'s snapshot shows the transient duplicate) render honestly; the highlight tracks the destination slot so the eye follows the element across the burst.
```
