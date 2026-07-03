# Teaching HashMap — ASCII Visualizer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the teaching HashMap's event stream as terminal ASCII diagrams — buckets, chains, and sideways red-black trees — driven either live (a listener) or as a step-through/scrub replay of a recorded event stream.

**Architecture:** A new `viz` package built bottom-up around one pure, no-I/O renderer (`MapSnapshot`/`MapEvent` → `String`). A `Palette` handles colour (ANSI or plain). Two thin drivers wrap the renderer: `AsciiVisualizer` (a `MapEventListener`, live) and `AsciiReplayer` (steps a recorded `List<MapEvent>` forward/back, or auto-plays).

**Tech Stack:** Java 21, Maven, JUnit 5.

## Global Constraints

- Java 21 target (`maven.compiler.release=21`), Maven, built/tested on JDK 26.
- New package: `com.gimlism.translucent.hashmap.viz`. Consumes `...events.*` records and reuses `...consumer.ConsoleEventLogger.format(MapEvent)`.
- The renderer is **pure** (no I/O, no statics that touch the console); drivers own all I/O.
- Record accessors (verify against source, do not guess): `MapSnapshot(capacity, size, threshold, buckets)`; `BucketSnapshot` sealed → `EmptyBucket()`, `ChainSnapshot(entries)`, `TreeSnapshot(root)`; `EntrySnapshot(key, value, hash)`; `TreeNodeSnapshot(key, value, color, left, right)`; `Color { RED, BLACK }`; `MapEvent.after()`; event `bucketIndex()` on Put/Remove/Collision/Treeify/Untreeify/Rotation/Recolor (NOT on Resize).
- **Rendering formats (exact — pinned by golden tests):**
  - Palette node label — PLAIN: `<key>(R)` / `<key>(B)`; ANSI: RED → `[31m<key>[0m`, BLACK → `<key>` (plain).
  - Sideways tree: recurse right-subtree (above), node line, left-subtree (below). Indent = 4 spaces × depth. Connectors: root → `` (none), right child → `┌─ `, left child → `└─ `. Node line = `indent + connector + palette.node(key,color)`. Tree nodes show **key only** (values shown in chains).
  - `renderMap` — header `map: cap=<C> size=<S> threshold=<T>`, then one block per bucket index `i`:
    - prefix `> ` if `i == highlightBucket` else `  `;
    - empty: `<prefix>[<i>] ·`
    - chain: `<prefix>[<i>] <k>=<v> -> <k>=<v> …` (insertion order)
    - tree: `<prefix>[<i>] tree:` then each tree line indented by 6 spaces.
    - Blocks and lines joined by `\n`; no trailing newline.
  - `renderEvent(e)` = `ConsoleEventLogger.format(e) + "\n" + renderMap(e.after(), affectedBucket(e))`.
- Tests pin exact output with `String.join("\n", …)` (NOT text blocks).

## File structure

- Create `viz/Palette.java` — colour modes + node labelling + no-TTY factory.
- Create `viz/AsciiRenderer.java` — `renderTree`, `renderMap`, `renderEvent`, `affectedBucket`.
- Create `viz/AsciiVisualizer.java` — live `MapEventListener`.
- Create `viz/AsciiReplayer.java` — step/back/quit + auto-play over a recorded list.
- Tests under `src/test/java/com/gimlism/translucent/hashmap/viz/`.

---

### Task 1: `Palette`

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/viz/Palette.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/viz/PaletteTest.java`

**Interfaces:**
- Consumes: `com.gimlism.translucent.hashmap.events.Color`.
- Produces: `class Palette` with `enum Mode { PLAIN, ANSI }`, `Palette(Mode)`, `String node(Object key, Color color)`, `Mode mode()`, and `static Palette auto()` (ANSI iff `System.console() != null`, else PLAIN).

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/viz/PaletteTest.java`
```java
package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.events.Color;
import org.junit.jupiter.api.Test;

class PaletteTest {
    @Test
    void plainModeAnnotatesWithMarkersNoEscapes() {
        var p = new Palette(Palette.Mode.PLAIN);
        assertEquals("16(B)", p.node(16, Color.BLACK));
        assertEquals("8(R)", p.node(8, Color.RED));
        assertFalse(p.node(8, Color.RED).contains(""), "plain mode has no ANSI escapes");
    }

    @Test
    void ansiModeWrapsRedAndLeavesBlackPlain() {
        var p = new Palette(Palette.Mode.ANSI);
        assertEquals("[31m8[0m", p.node(8, Color.RED));
        assertEquals("16", p.node(16, Color.BLACK));
    }

    @Test
    void autoFactoryPicksPlainWhenNoConsole() {
        // tests run without a TTY (System.console() == null), so auto() must be PLAIN
        assertEquals(Palette.Mode.PLAIN, Palette.auto().mode());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `Palette`.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/gimlism/translucent/hashmap/viz/Palette.java`
```java
package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.Color;

/** Renders a tree node's key with its red-black colour, in ANSI colour or plain text. */
public final class Palette {
    public enum Mode { PLAIN, ANSI }

    private static final String RED = "[31m";
    private static final String RESET = "[0m";

    private final Mode mode;

    public Palette(Mode mode) {
        this.mode = mode;
    }

    public Mode mode() {
        return mode;
    }

    /** A node label: {@code key(R)}/{@code key(B)} in PLAIN, or ANSI-red key in ANSI. */
    public String node(Object key, Color color) {
        if (mode == Mode.PLAIN) {
            return key + (color == Color.RED ? "(R)" : "(B)");
        }
        return color == Color.RED ? RED + key + RESET : String.valueOf(key);
    }

    /** ANSI when attached to a real terminal, PLAIN otherwise (pipes, capture, tests). */
    public static Palette auto() {
        return new Palette(System.console() != null ? Mode.ANSI : Mode.PLAIN);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `PaletteTest` green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/viz/Palette.java src/test/java/com/gimlism/translucent/hashmap/viz/PaletteTest.java
git commit -m "feat(viz): add Palette for ANSI/plain node colouring"
```

---

### Task 2: Sideways tree rendering

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/viz/AsciiRenderer.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/viz/AsciiTreeRenderTest.java`

**Interfaces:**
- Consumes: `Palette`, `TreeNodeSnapshot`, `Color`.
- Produces: `class AsciiRenderer` with `AsciiRenderer(Palette)` and `String renderTree(TreeNodeSnapshot root)` — the sideways layout, no trailing newline.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/viz/AsciiTreeRenderTest.java`
```java
package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gimlism.translucent.hashmap.events.Color;
import com.gimlism.translucent.hashmap.events.TreeNodeSnapshot;
import org.junit.jupiter.api.Test;

class AsciiTreeRenderTest {
    private static TreeNodeSnapshot leaf(Object k, Color c) {
        return new TreeNodeSnapshot(k, "v" + k, c, null, null);
    }

    @Test
    void rendersSidewaysWithConnectorsAndDepthIndent() {
        // 16(B) { left: 8(B){ left:4(R), right:12(R) }, right: 24(B) }
        TreeNodeSnapshot tree = new TreeNodeSnapshot(16, "v16", Color.BLACK,
            new TreeNodeSnapshot(8, "v8", Color.BLACK, leaf(4, Color.RED), leaf(12, Color.RED)),
            leaf(24, Color.BLACK));
        var r = new AsciiRenderer(new Palette(Palette.Mode.PLAIN));
        String expected = String.join("\n",
            "    ┌─ 24(B)",
            "16(B)",
            "        ┌─ 12(R)",
            "    └─ 8(B)",
            "        └─ 4(R)");
        assertEquals(expected, r.renderTree(tree));
    }

    @Test
    void singleNodeRendersJustTheRoot() {
        var r = new AsciiRenderer(new Palette(Palette.Mode.PLAIN));
        assertEquals("42(B)", r.renderTree(leaf(42, Color.BLACK)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `AsciiRenderer`.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/gimlism/translucent/hashmap/viz/AsciiRenderer.java`
```java
package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.TreeNodeSnapshot;

/** Pure renderer: turns immutable snapshots/events into ASCII text. No I/O. */
public final class AsciiRenderer {
    private final Palette palette;

    public AsciiRenderer(Palette palette) {
        this.palette = palette;
    }

    /** Sideways red-black tree: right-subtree above, node, left-subtree below. */
    public String renderTree(TreeNodeSnapshot root) {
        StringBuilder sb = new StringBuilder();
        appendTree(sb, root, 0, "");
        // drop the trailing newline appendTree leaves
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private void appendTree(StringBuilder sb, TreeNodeSnapshot n, int depth, String connector) {
        if (n == null) return;
        appendTree(sb, n.right(), depth + 1, "┌─ ");
        sb.append("    ".repeat(depth)).append(connector)
          .append(palette.node(n.key(), n.color())).append('\n');
        appendTree(sb, n.left(), depth + 1, "└─ ");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `AsciiTreeRenderTest` green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/viz/AsciiRenderer.java src/test/java/com/gimlism/translucent/hashmap/viz/AsciiTreeRenderTest.java
git commit -m "feat(viz): sideways ASCII red-black tree rendering"
```

---

### Task 3: `renderMap` (header + buckets + highlight)

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/viz/AsciiRenderer.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/viz/AsciiMapRenderTest.java`

**Interfaces:**
- Consumes: `MapSnapshot`, `BucketSnapshot`, `EmptyBucket`, `ChainSnapshot`, `TreeSnapshot`, `EntrySnapshot`.
- Produces: `String renderMap(MapSnapshot snap, int highlightBucket)` — header + one block per bucket; empty/chain/tree; `> ` marks `highlightBucket` (−1 = none); no trailing newline.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/viz/AsciiMapRenderTest.java`
```java
package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gimlism.translucent.hashmap.events.BucketSnapshot;
import com.gimlism.translucent.hashmap.events.ChainSnapshot;
import com.gimlism.translucent.hashmap.events.Color;
import com.gimlism.translucent.hashmap.events.EmptyBucket;
import com.gimlism.translucent.hashmap.events.EntrySnapshot;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import com.gimlism.translucent.hashmap.events.TreeNodeSnapshot;
import com.gimlism.translucent.hashmap.events.TreeSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsciiMapRenderTest {
    private final AsciiRenderer r = new AsciiRenderer(new Palette(Palette.Mode.PLAIN));

    @Test
    void rendersHeaderEmptyAndChainWithHighlight() {
        List<BucketSnapshot> buckets = List.of(
            new ChainSnapshot(List.of(new EntrySnapshot(0, "zero", 0), new EntrySnapshot(8, "eight", 8))),
            new EmptyBucket(),
            new ChainSnapshot(List.of(new EntrySnapshot(2, "two", 2))));
        var snap = new MapSnapshot(3, 3, 2, buckets);
        String expected = String.join("\n",
            "map: cap=3 size=3 threshold=2",
            "  [0] 0=zero -> 8=eight",
            "  [1] ·",
            "> [2] 2=two");
        assertEquals(expected, r.renderMap(snap, 2));
    }

    @Test
    void rendersTreeBucketIndentedUnderHeader() {
        TreeNodeSnapshot root = new TreeNodeSnapshot(16, "v16", Color.BLACK,
            new TreeNodeSnapshot(8, "v8", Color.RED, null, null),
            new TreeNodeSnapshot(24, "v24", Color.RED, null, null));
        var snap = new MapSnapshot(1, 3, 0, List.of(new TreeSnapshot(root)));
        String expected = String.join("\n",
            "map: cap=1 size=3 threshold=0",
            "> [0] tree:",
            "          ┌─ 24(R)",
            "      16(B)",
            "          └─ 8(R)");
        assertEquals(expected, r.renderMap(snap, 0));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `renderMap`.

- [ ] **Step 3: Write minimal implementation**

Add imports to `AsciiRenderer`:
```java
import com.gimlism.translucent.hashmap.events.BucketSnapshot;
import com.gimlism.translucent.hashmap.events.ChainSnapshot;
import com.gimlism.translucent.hashmap.events.EmptyBucket;
import com.gimlism.translucent.hashmap.events.EntrySnapshot;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import com.gimlism.translucent.hashmap.events.TreeSnapshot;
import java.util.List;
import java.util.StringJoiner;
```

Add methods to `AsciiRenderer`:
```java
    /** Whole-map diagram; {@code highlightBucket} (−1 for none) is marked with {@code >}. */
    public String renderMap(MapSnapshot snap, int highlightBucket) {
        StringBuilder sb = new StringBuilder();
        sb.append("map: cap=").append(snap.capacity())
          .append(" size=").append(snap.size())
          .append(" threshold=").append(snap.threshold());
        List<BucketSnapshot> buckets = snap.buckets();
        for (int i = 0; i < buckets.size(); i++) {
            sb.append('\n').append(i == highlightBucket ? "> " : "  ")
              .append('[').append(i).append("] ").append(renderBucket(buckets.get(i)));
        }
        return sb.toString();
    }

    private String renderBucket(BucketSnapshot bucket) {
        return switch (bucket) {
            case EmptyBucket e -> "·";
            case ChainSnapshot c -> {
                StringJoiner sj = new StringJoiner(" -> ");
                for (EntrySnapshot en : c.entries()) sj.add(en.key() + "=" + en.value());
                yield sj.toString();
            }
            case TreeSnapshot t -> {
                StringBuilder sb = new StringBuilder("tree:");
                for (String line : renderTree(t.root()).split("\n", -1)) {
                    sb.append("\n      ").append(line);
                }
                yield sb.toString();
            }
        };
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `AsciiMapRenderTest` green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/viz/AsciiRenderer.java src/test/java/com/gimlism/translucent/hashmap/viz/AsciiMapRenderTest.java
git commit -m "feat(viz): render whole-map ASCII with bucket highlight"
```

---

### Task 4: `renderEvent` + `affectedBucket`

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/viz/AsciiRenderer.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/viz/AsciiEventRenderTest.java`

**Interfaces:**
- Consumes: `MapEvent` and its subtypes, `consumer.ConsoleEventLogger.format`.
- Produces: `String renderEvent(MapEvent e)` = label line + `renderMap(e.after(), affectedBucket(e))`; `static int affectedBucket(MapEvent e)` returns the event's `bucketIndex()` or −1 for `Resize`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/viz/AsciiEventRenderTest.java`
```java
package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.events.ChainSnapshot;
import com.gimlism.translucent.hashmap.events.EmptyBucket;
import com.gimlism.translucent.hashmap.events.EntrySnapshot;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import com.gimlism.translucent.hashmap.events.Put;
import com.gimlism.translucent.hashmap.events.Resize;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsciiEventRenderTest {
    private final AsciiRenderer r = new AsciiRenderer(new Palette(Palette.Mode.PLAIN));

    @Test
    void renderEventPutsLabelAboveMapAndHighlightsBucket() {
        var snap = new MapSnapshot(2, 1, 1,
            List.of(new EmptyBucket(),
                    new ChainSnapshot(List.of(new EntrySnapshot(1, "one", 1)))));
        var put = new Put(1, "one", null, 1, true, snap);
        String out = r.renderEvent(put);
        String expected = String.join("\n",
            "PUT 1=one -> bucket 1 (new)",  // ConsoleEventLogger.format(put)
            "map: cap=2 size=1 threshold=1",
            "  [0] ·",
            "> [1] 1=one");                 // bucket 1 highlighted
        assertEquals(expected, out);
    }

    @Test
    void affectedBucketIsMinusOneForResize() {
        var snap = new MapSnapshot(0, 0, 0, List.of());
        assertEquals(-1, AsciiRenderer.affectedBucket(new Resize(8, 16, snap, snap)));
    }

    @Test
    void affectedBucketReadsBucketIndexForBucketEvents() {
        var snap = new MapSnapshot(0, 0, 0, List.of());
        assertEquals(3, AsciiRenderer.affectedBucket(new Put("k", "v", null, 3, true, snap)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `renderEvent`/`affectedBucket`.

- [ ] **Step 3: Write minimal implementation**

Add imports to `AsciiRenderer`:
```java
import com.gimlism.translucent.hashmap.consumer.ConsoleEventLogger;
import com.gimlism.translucent.hashmap.events.Collision;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.Put;
import com.gimlism.translucent.hashmap.events.Recolor;
import com.gimlism.translucent.hashmap.events.Remove;
import com.gimlism.translucent.hashmap.events.Resize;
import com.gimlism.translucent.hashmap.events.Rotation;
import com.gimlism.translucent.hashmap.events.Treeify;
import com.gimlism.translucent.hashmap.events.Untreeify;
```

Add methods to `AsciiRenderer`:
```java
    /** The event's one-line label (from ConsoleEventLogger) above the resulting map. */
    public String renderEvent(MapEvent e) {
        return ConsoleEventLogger.format(e) + "\n" + renderMap(e.after(), affectedBucket(e));
    }

    /** The bucket an event touched, or −1 for a whole-table Resize. */
    public static int affectedBucket(MapEvent e) {
        return switch (e) {
            case Put p -> p.bucketIndex();
            case Remove r -> r.bucketIndex();
            case Collision c -> c.bucketIndex();
            case Treeify t -> t.bucketIndex();
            case Untreeify u -> u.bucketIndex();
            case Rotation ro -> ro.bucketIndex();
            case Recolor rc -> rc.bucketIndex();
            case Resize rs -> -1;
        };
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `AsciiEventRenderTest` green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/viz/AsciiRenderer.java src/test/java/com/gimlism/translucent/hashmap/viz/AsciiEventRenderTest.java
git commit -m "feat(viz): render event label + highlighted map frame"
```

---

### Task 5: `AsciiVisualizer` (live listener)

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/viz/AsciiVisualizer.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/viz/AsciiVisualizerTest.java`

**Interfaces:**
- Consumes: `MapEventListener`, `AsciiRenderer`, `Palette`, `TeachingHashMap`.
- Produces: `class AsciiVisualizer implements MapEventListener` with `AsciiVisualizer(PrintStream out, AsciiRenderer renderer)` and `AsciiVisualizer(PrintStream out)` (defaults to `Palette.auto()`); `onEvent` prints `renderer.renderEvent(e)` followed by a blank line.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/viz/AsciiVisualizerTest.java`
```java
package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AsciiVisualizerTest {
    @Test
    void livePrintsAFramePerMutation() {
        var buffer = new ByteArrayOutputStream();
        var out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        var map = new TeachingHashMap<Integer, String>();
        map.addListener(new AsciiVisualizer(out, new AsciiRenderer(new Palette(Palette.Mode.PLAIN))));

        map.put(0, "zero");
        map.put(8, "eight"); // collision in bucket 0

        String printed = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("PUT 0=zero -> bucket 0 (new)"), printed);
        assertTrue(printed.contains("map: cap=8"), printed);
        // the collision frame shows both entries chained in bucket 0
        assertTrue(printed.contains("0=zero -> 8=eight"), printed);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `AsciiVisualizer`.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/gimlism/translucent/hashmap/viz/AsciiVisualizer.java`
```java
package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.MapEventListener;
import java.io.PrintStream;

/** A live consumer that prints an ASCII frame to a stream on every map event. */
public final class AsciiVisualizer implements MapEventListener {
    private final PrintStream out;
    private final AsciiRenderer renderer;

    public AsciiVisualizer(PrintStream out, AsciiRenderer renderer) {
        this.out = out;
        this.renderer = renderer;
    }

    public AsciiVisualizer(PrintStream out) {
        this(out, new AsciiRenderer(Palette.auto()));
    }

    @Override
    public void onEvent(MapEvent event) {
        out.println(renderer.renderEvent(event));
        out.println();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `AsciiVisualizerTest` green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/viz/AsciiVisualizer.java src/test/java/com/gimlism/translucent/hashmap/viz/AsciiVisualizerTest.java
git commit -m "feat(viz): live AsciiVisualizer MapEventListener"
```

---

### Task 6: `AsciiReplayer` (step/back/quit + auto-play)

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/viz/AsciiReplayer.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/viz/AsciiReplayerTest.java`

**Interfaces:**
- Consumes: `AsciiRenderer`, `MapEvent`, `RecordingListener` (as the event source in tests).
- Produces: `class AsciiReplayer` with `AsciiReplayer(List<MapEvent> events, AsciiRenderer renderer)`, `void run(InputStream in, PrintStream out)` (interactive: Enter=next, `b`=back, `q`=quit; Enter past the last frame ends), and `void autoPlay(PrintStream out, long delayMillis)` (renders every frame in order; `delayMillis <= 0` = no sleep). Each frame prints a `── frame N/M ──` header then `renderer.renderEvent`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/viz/AsciiReplayerTest.java`
```java
package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.RecordingListener;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AsciiReplayerTest {
    private static RecordingListener recordThreePuts() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new RecordingListener();
        map.addListener(rec);
        map.put(1, "a");
        map.put(2, "b");
        map.put(3, "c");
        return rec;
    }

    @Test
    void steppingForwardBackAndQuitWalksFrames() {
        var rec = recordThreePuts(); // 3 events
        var replayer = new AsciiReplayer(rec.events(), new AsciiRenderer(new Palette(Palette.Mode.PLAIN)));
        var buffer = new ByteArrayOutputStream();
        // next (0->1), back (1->0), quit
        var in = new ByteArrayInputStream("\nb\nq\n".getBytes(StandardCharsets.UTF_8));
        replayer.run(in, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("── frame 1/3 ──"), out);
        assertTrue(out.contains("── frame 2/3 ──"), out);
        // after back we are on frame 1 again -> "frame 1/3" appears at least twice
        int first = out.indexOf("frame 1/3");
        assertTrue(first >= 0 && out.indexOf("frame 1/3", first + 1) > first, "frame 1 shown again after back");
    }

    @Test
    void autoPlayRendersEveryFrameInOrder() {
        var rec = recordThreePuts();
        var replayer = new AsciiReplayer(rec.events(), new AsciiRenderer(new Palette(Palette.Mode.PLAIN)));
        var buffer = new ByteArrayOutputStream();
        replayer.autoPlay(new PrintStream(buffer, true, StandardCharsets.UTF_8), 0);
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("── frame 1/3 ──"), out);
        assertTrue(out.contains("── frame 2/3 ──"), out);
        assertTrue(out.contains("── frame 3/3 ──"), out);
        // frames are in order
        assertTrue(out.indexOf("frame 1/3") < out.indexOf("frame 2/3"), out);
        assertTrue(out.indexOf("frame 2/3") < out.indexOf("frame 3/3"), out);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `AsciiReplayer`.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/gimlism/translucent/hashmap/viz/AsciiReplayer.java`
```java
package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.MapEvent;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

/** Steps a recorded event stream frame-by-frame (each event's snapshot is a full frame). */
public final class AsciiReplayer {
    private final List<MapEvent> events;
    private final AsciiRenderer renderer;

    public AsciiReplayer(List<MapEvent> events, AsciiRenderer renderer) {
        this.events = events;
        this.renderer = renderer;
    }

    /** Interactive: Enter = next, {@code b} = back, {@code q} = quit; next past the end ends. */
    public void run(InputStream in, PrintStream out) {
        if (events.isEmpty()) {
            out.println("(no events to replay)");
            return;
        }
        Scanner scanner = new Scanner(in);
        int i = 0;
        while (true) {
            printFrame(out, i);
            out.print("[Enter=next, b=back, q=quit] ");
            if (!scanner.hasNextLine()) break;
            String cmd = scanner.nextLine().trim();
            if (cmd.equals("q")) break;
            if (cmd.equals("b")) {
                i = Math.max(0, i - 1);
            } else {
                if (i == events.size() - 1) break; // next past the last frame ends
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
Expected: PASS — `AsciiReplayerTest` green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/viz/AsciiReplayer.java src/test/java/com/gimlism/translucent/hashmap/viz/AsciiReplayerTest.java
git commit -m "feat(viz): AsciiReplayer step-through and auto-play"
```

---

## Self-Review

**Spec coverage:**
- Pure renderer core (no I/O) → `AsciiRenderer` (Tasks 2–4). ✓
- Palette: ANSI + plain fallback + no-TTY auto → Task 1. ✓
- Sideways tree (right-above/left-below, depth indent, `┌─`/`└─`) → Task 2. ✓
- `renderMap` header + empty/chain/tree + highlight → Task 3. ✓
- `renderEvent` (label via `ConsoleEventLogger.format` + highlight; `Resize` → −1) → Task 4. ✓
- Live `AsciiVisualizer` (`MapEventListener`) → Task 5. ✓
- `AsciiReplayer` forward/back/quit scrubber + auto-play, injectable streams → Task 6. ✓
- Golden-string tests for renderer; live + replay behavioral tests → all tasks. ✓
- No premature cross-visualizer abstraction (web/JavaFX deferred) → nothing built beyond ASCII. ✓

**Placeholder scan:** None — every step has complete code and exact golden strings.

**Type consistency:** `AsciiRenderer(Palette)` → `renderTree`/`renderMap(snap,int)`/`renderEvent`/`static affectedBucket`; `Palette(Mode)`/`node(Object,Color)`/`mode()`/`auto()`; `AsciiVisualizer(PrintStream,AsciiRenderer)`; `AsciiReplayer(List<MapEvent>,AsciiRenderer)`/`run(InputStream,PrintStream)`/`autoPlay(PrintStream,long)`. Record accessors used (`capacity/size/threshold/buckets`, `entries`, `key/value/hash`, `key/value/color/left/right`, `after`, `bucketIndex`) match the events package. Reuses public `ConsoleEventLogger.format(MapEvent)`.

## Notes for the reviewer

- The exact ASCII spacing (4 spaces/depth, 6-space tree indent under a bucket, `> `/`  ` prefixes) is a deliberate choice pinned by golden strings; if a reviewer prefers different spacing, it's a cosmetic change localized to `AsciiRenderer` + the golden expectations.
- `renderTree` tree nodes show the key only (values appear in chain rendering) — matches the approved design preview.
- Next visualizer slices (web JSON export + HTML, JavaFX) consume the same event/snapshot contract and are out of scope here.
