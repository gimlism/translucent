# TreeSet ASCII Visualizer — Slice 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give `TeachingTreeSet` a terminal ASCII visualizer — a pure renderer that draws its red-black tree sideways with colour and a per-node highlight — plus the standard `Replayer`/`Visualizer` subclasses and a demo, matching every sibling structure's ASCII slice.

**Architecture:** The substrate already supplies `EventRenderer<E>`, `Replayer<E>`, and `Visualizer<E>`, so the only real class is `AsciiSetRenderer`; the other two are trivial subclasses. Red/black colour reuses the map's approach, but the subtle terminal-detection half of the map's `Palette` is first promoted into a shared `substrate/viz/ColorMode` (shared PLAIN/ANSI enum) and the map retrofitted onto it. The renderer consumes shipped events/snapshots unchanged — `core`, `events`, and `substrate/rbtree` are untouched.

**Tech Stack:** Java 21 (Maven, `maven.compiler.release=21`), JUnit 5, no new dependencies.

## Global Constraints

- **Java release 21**; no new Maven dependencies.
- **JUnit 5** (`org.junit.jupiter.api`); tests are package-private classes.
- **Package layout:** new renderer/replayer/visualizer in `treeset/viz`; demo in a **new** `treeset/demo` package (core shipped none); shared detection in `substrate/viz`.
- **Do not touch** `treeset/core`, `treeset/events`, or `substrate/rbtree` — events and snapshots are consumed as they ship (keeps the snapshot-before-settled bug off-surface).
- **Colour representation:** the renderer speaks the `boolean red` from `SetNodeSnapshot`; `ColorMode` (PLAIN/ANSI) is only about the *output channel*, never a node's colour. ANSI escapes are formatted locally in each renderer (`\u001b[31m` / `\u001b[0m`).
- **Sideways layout, small teaching regime (~5–15 elements)** — chosen consciously; deep-tree layout is out of scope.
- **Every commit message ends with the two trailer lines** (`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` and the `Claude-Session:` line), per repo CLAUDE.md.
- **Branch:** `feat/treeset-ascii-viz` (already created; the spec commit is on it).
- Full-suite baseline before this work: **416 tests green**.

---

### Task 1: Promote colour-mode detection to `substrate/viz/ColorMode` and retrofit `Palette`

Extract the subtle terminal/`NO_COLOR`/JDK-22-reflection detection out of the map's `Palette` into a shared enum, retrofit `Palette` onto it, and mechanically repoint the `Palette.Mode` call sites (shared-enum decision). This lets the set renderer share the detection while keeping formatting local.

**Files:**
- Create: `src/main/java/com/gimlism/translucent/substrate/viz/ColorMode.java`
- Create: `src/test/java/com/gimlism/translucent/substrate/viz/ColorModeTest.java`
- Modify: `src/main/java/com/gimlism/translucent/hashmap/viz/Palette.java`
- Modify: `src/test/java/com/gimlism/translucent/hashmap/viz/PaletteTest.java`
- Modify (mechanical `Palette.Mode.PLAIN` → `ColorMode.PLAIN` + import): `src/test/java/com/gimlism/translucent/hashmap/viz/AsciiEventRenderTest.java`, `.../AsciiMapRenderTest.java`, `.../AsciiMapReplayerTest.java`, `.../AsciiMapVisualizerTest.java`, `.../AsciiTreeRenderTest.java`, `src/test/java/com/gimlism/translucent/substrate/SubstrateReuseTest.java`, `src/test/java/com/gimlism/translucent/substrate/viz/GenericDriversTest.java`

**Interfaces:**
- Produces: `enum com.gimlism.translucent.substrate.viz.ColorMode { PLAIN, ANSI }` with `static ColorMode detect()` and `static ColorMode decideMode(String noColorEnv, boolean consolePresent, boolean terminal)`.
- Produces: `Palette(ColorMode mode)` ctor; `ColorMode Palette.mode()`; `static Palette Palette.auto()` (unchanged signature). `Palette.node(Object, Color)` unchanged.

- [ ] **Step 1: Write the failing `ColorModeTest`**

Create `src/test/java/com/gimlism/translucent/substrate/viz/ColorModeTest.java`:

```java
package com.gimlism.translucent.substrate.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ColorModeTest {
    @Test
    void detectPicksPlainWhenNoConsole() {
        // tests run without a TTY (System.console() == null), so detect() must be PLAIN
        assertEquals(ColorMode.PLAIN, ColorMode.detect());
    }

    @Test
    void decideModeAnsiOnlyWhenConsoleIsARealTerminal() {
        assertEquals(ColorMode.ANSI, ColorMode.decideMode(null, true, true));
        // JDK 22+: System.console() is non-null even when stdout is redirected;
        // isTerminal() is false there, so we must stay PLAIN.
        assertEquals(ColorMode.PLAIN, ColorMode.decideMode(null, true, false));
        assertEquals(ColorMode.PLAIN, ColorMode.decideMode(null, false, false));
    }

    @Test
    void decideModeHonoursNoColor() {
        // https://no-color.org : any non-empty value disables colour, even on a terminal
        assertEquals(ColorMode.PLAIN, ColorMode.decideMode("1", true, true));
        // an empty value counts as "not set", so colour stays enabled on a terminal
        assertEquals(ColorMode.ANSI, ColorMode.decideMode("", true, true));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -Dtest=ColorModeTest test`
Expected: FAIL — compilation error, `ColorMode` does not exist.

- [ ] **Step 3: Create `ColorMode`**

Create `src/main/java/com/gimlism/translucent/substrate/viz/ColorMode.java`:

```java
package com.gimlism.translucent.substrate.viz;

import java.io.Console;

/**
 * Whether ASCII renderers should emit ANSI colour, decided from the runtime
 * environment. Structure-agnostic: this is only about the output channel
 * (terminal vs pipe/redirect, and the NO_COLOR convention), never about a node's
 * own colour. Promoted from the HashMap's {@code Palette} so the TreeSet renderer
 * can share the (subtle) detection logic.
 */
public enum ColorMode {
    PLAIN, ANSI;

    /** ANSI when attached to a real terminal, PLAIN otherwise (pipes, capture, tests). */
    public static ColorMode detect() {
        Console console = System.console();
        boolean terminal = console != null && isTerminal(console);
        return decideMode(System.getenv("NO_COLOR"), console != null, terminal);
    }

    /**
     * The colour decision from raw environment inputs. Package-visible for testing.
     *
     * @param noColorEnv     value of the {@code NO_COLOR} env var (null if unset)
     * @param consolePresent whether {@link System#console()} returned non-null
     * @param terminal       whether that console is a real terminal (see {@link #isTerminal})
     */
    static ColorMode decideMode(String noColorEnv, boolean consolePresent, boolean terminal) {
        if (noColorEnv != null && !noColorEnv.isEmpty()) return PLAIN; // no-color.org
        if (!consolePresent) return PLAIN;                             // pipe / redirect / no tty
        return terminal ? ANSI : PLAIN;
    }

    /**
     * Whether the console is attached to a terminal. Uses {@code Console.isTerminal()}
     * (added in JDK 22, where {@link System#console()} is non-null even under
     * redirection) reflectively, since this module compiles against release 21; on
     * JDK 21 a non-null console already implies a terminal.
     *
     * <p>A missing method means JDK 21, so we keep the pre-22 semantics (terminal).
     * Any other reflective or security failure means we cannot confirm a terminal,
     * so we fail closed to avoid leaking ANSI escapes into redirected output.
     */
    private static boolean isTerminal(Console console) {
        try {
            return (Boolean) Console.class.getMethod("isTerminal").invoke(console);
        } catch (NoSuchMethodException e) {
            return true; // JDK 21: no isTerminal(); a non-null console is a terminal
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false; // JDK 22+ reflective/security failure: fail closed (no ANSI)
        }
    }
}
```

- [ ] **Step 4: Run `ColorModeTest` to verify it passes**

Run: `mvn -q -Dtest=ColorModeTest test`
Expected: PASS (4 tests... 5 assertions across 3 methods).

- [ ] **Step 5: Retrofit `Palette` onto `ColorMode`**

Replace `src/main/java/com/gimlism/translucent/hashmap/viz/Palette.java` entirely with:

```java
package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.Color;
import com.gimlism.translucent.substrate.viz.ColorMode;

/** Renders a tree node's key with its red-black colour, in ANSI colour or plain text. */
public final class Palette {
    private static final String RED = "\u001b[31m";
    private static final String RESET = "\u001b[0m";

    private final ColorMode mode;

    public Palette(ColorMode mode) {
        this.mode = mode;
    }

    public ColorMode mode() {
        return mode;
    }

    /** A node label: {@code key(R)}/{@code key(B)} in PLAIN, or ANSI-red key in ANSI. */
    public String node(Object key, Color color) {
        if (mode == ColorMode.PLAIN) {
            return key + (color == Color.RED ? "(R)" : "(B)");
        }
        return color == Color.RED ? RED + key + RESET : String.valueOf(key);
    }

    /** ANSI when attached to a real terminal, PLAIN otherwise (pipes, capture, tests). */
    public static Palette auto() {
        return new Palette(ColorMode.detect());
    }
}
```

- [ ] **Step 6: Repoint `PaletteTest` — keep formatting tests, drop the moved detection tests**

Replace `src/test/java/com/gimlism/translucent/hashmap/viz/PaletteTest.java` entirely with (the two `decideMode*` methods now live in `ColorModeTest`; note `assertTrue` is no longer imported):

```java
package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.gimlism.translucent.hashmap.events.Color;
import com.gimlism.translucent.substrate.viz.ColorMode;
import org.junit.jupiter.api.Test;

class PaletteTest {
    @Test
    void plainModeAnnotatesWithMarkersNoEscapes() {
        var p = new Palette(ColorMode.PLAIN);
        assertEquals("16(B)", p.node(16, Color.BLACK));
        assertEquals("8(R)", p.node(8, Color.RED));
        assertFalse(p.node(8, Color.RED).contains("\u001b"), "plain mode has no ANSI escapes");
    }

    @Test
    void ansiModeWrapsRedAndLeavesBlackPlain() {
        var p = new Palette(ColorMode.ANSI);
        assertEquals("\u001b[31m8\u001b[0m", p.node(8, Color.RED));
        assertEquals("16", p.node(16, Color.BLACK));
    }

    @Test
    void autoFactoryPicksPlainWhenNoConsole() {
        // tests run without a TTY (System.console() == null), so auto() must be PLAIN
        assertEquals(ColorMode.PLAIN, Palette.auto().mode());
    }
}
```

- [ ] **Step 7: Repoint the 6 remaining call sites**

In each of these files, replace every `Palette.Mode.PLAIN` with `ColorMode.PLAIN` and add the import `import com.gimlism.translucent.substrate.viz.ColorMode;` (place it in existing import order). `GenericDriversTest` is already in package `substrate.viz`, so it needs **no** import — just the token change.

- `src/test/java/com/gimlism/translucent/hashmap/viz/AsciiEventRenderTest.java` (1 occurrence, add import)
- `src/test/java/com/gimlism/translucent/hashmap/viz/AsciiMapRenderTest.java` (2 occurrences, add import)
- `src/test/java/com/gimlism/translucent/hashmap/viz/AsciiMapReplayerTest.java` (2 occurrences, add import)
- `src/test/java/com/gimlism/translucent/hashmap/viz/AsciiMapVisualizerTest.java` (1 occurrence, add import)
- `src/test/java/com/gimlism/translucent/substrate/SubstrateReuseTest.java` (1 occurrence, add import)
- `src/test/java/com/gimlism/translucent/substrate/viz/GenericDriversTest.java` (2 occurrences, **no** import — same package)

Sanity grep after editing — this must return **nothing**:

Run: `grep -rn "Palette\.Mode" src/`
Expected: (no output)

- [ ] **Step 8: Run the full suite to verify the retrofit is behaviour-preserving**

Run: `mvn -q test`
Expected: PASS — BUILD SUCCESS. Test count = **416 + 3 new `ColorModeTest` methods − 2 moved-out `PaletteTest` methods = 417**. (The point of the full run: the shared-enum rename touched map + substrate tests; nothing may regress.)

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/gimlism/translucent/substrate/viz/ColorMode.java \
        src/test/java/com/gimlism/translucent/substrate/viz/ColorModeTest.java \
        src/main/java/com/gimlism/translucent/hashmap/viz/Palette.java \
        src/test/java/com/gimlism/translucent/hashmap/viz/
git add src/test/java/com/gimlism/translucent/substrate/
git commit -m "refactor(viz): promote colour-mode detection to substrate/viz/ColorMode"
```

---

### Task 2: `AsciiSetRenderer` — the sideways RB-tree renderer

**Files:**
- Create: `src/main/java/com/gimlism/translucent/treeset/viz/AsciiSetRenderer.java`
- Test: `src/test/java/com/gimlism/translucent/treeset/viz/AsciiSetRenderTest.java`

**Interfaces:**
- Consumes: `ColorMode` (Task 1); shipped `SetSnapshot(SetNodeSnapshot root, int size)`, `SetNodeSnapshot(Object element, boolean red, SetNodeSnapshot left, SetNodeSnapshot right)`, `SetEventFormatter.format(SetEvent)`, events `Compare(Object element, Direction went, boolean found, SetSnapshot)`, `Add(Object element, SetSnapshot)`, `Rotation(Direction dir, Object pivot, SetSnapshot)`, `Recolor(Object element, Color oldColor, Color newColor, SetSnapshot)`, `Remove(Object element, SetSnapshot)`.
- Produces: `AsciiSetRenderer implements EventRenderer<SetEvent>` with `AsciiSetRenderer(ColorMode)`, `String renderSet(SetSnapshot, Object highlight)`, `String renderEvent(SetEvent)`, and `static Object affectedElement(SetEvent)`.

- [ ] **Step 1: Write the failing render test**

Create `src/test/java/com/gimlism/translucent/treeset/viz/AsciiSetRenderTest.java`:

```java
package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.substrate.rbtree.Color;
import com.gimlism.translucent.substrate.rbtree.Direction;
import com.gimlism.translucent.substrate.viz.ColorMode;
import com.gimlism.translucent.treeset.events.Add;
import com.gimlism.translucent.treeset.events.Compare;
import com.gimlism.translucent.treeset.events.Recolor;
import com.gimlism.translucent.treeset.events.Remove;
import com.gimlism.translucent.treeset.events.Rotation;
import com.gimlism.translucent.treeset.events.SetNodeSnapshot;
import com.gimlism.translucent.treeset.events.SetSnapshot;
import org.junit.jupiter.api.Test;

class AsciiSetRenderTest {
    private final AsciiSetRenderer r = new AsciiSetRenderer(ColorMode.PLAIN);

    private static SetNodeSnapshot node(Object e, boolean red, SetNodeSnapshot l, SetNodeSnapshot rt) {
        return new SetNodeSnapshot(e, red, l, rt);
    }
    private static SetNodeSnapshot leaf(Object e, boolean red) {
        return node(e, red, null, null);
    }
    // 20(B) { left: 10(R), right: 30(R) }
    private static SetSnapshot threeNodeTree() {
        return new SetSnapshot(node(20, false, leaf(10, true), leaf(30, true)), 3);
    }

    @Test
    void emptySetRendersHeaderOnly() {
        assertEquals("set: size=0", r.renderSet(new SetSnapshot(null, 0), null));
    }

    @Test
    void singleNodeRendersRootWithColourMarker() {
        assertEquals("set: size=1\n  42(B)", r.renderSet(new SetSnapshot(leaf(42, false), 1), null));
    }

    @Test
    void rendersSidewaysRightAboveNodeLeftBelow() {
        String expected = String.join("\n",
            "set: size=3",
            "      ┌─ 30(R)",
            "  20(B)",
            "      └─ 10(R)");
        assertEquals(expected, r.renderSet(threeNodeTree(), null));
    }

    @Test
    void highlightMarksTheAffectedNodeInTheLeftGutter() {
        String expected = String.join("\n",
            "set: size=3",
            "      ┌─ 30(R)",
            "  20(B)",
            ">     └─ 10(R)");   // "> " + 4-space depth indent + connector
        assertEquals(expected, r.renderSet(threeNodeTree(), 10));
    }

    @Test
    void addEventCaptionAboveTreeHighlightsTheNewElement() {
        String out = r.renderEvent(new Add(30, threeNodeTree()));
        assertTrue(out.startsWith("add 30\n"), out);
        assertTrue(out.contains(">     ┌─ 30(R)"), out); // the new node is marked
    }

    @Test
    void compareEventHighlightsTheVisitedNode() {
        String out = r.renderEvent(new Compare(20, Direction.LEFT, false, threeNodeTree()));
        assertTrue(out.startsWith("compare 20 -> go LEFT\n"), out);
        assertTrue(out.contains("> 20(B)"), out); // root is the visited/highlighted node
    }

    @Test
    void removeEventHighlightsNothing() {
        // 10 is gone from after(); nothing is marked
        SetSnapshot after = new SetSnapshot(node(20, false, null, leaf(30, true)), 2);
        String out = r.renderEvent(new Remove(10, after));
        assertTrue(out.startsWith("remove 10\n"), out);
        assertFalse(out.contains("> "), out);
    }

    @Test
    void ansiModeWrapsRedElementsInEscapes() {
        var ansi = new AsciiSetRenderer(ColorMode.ANSI);
        String out = ansi.renderSet(new SetSnapshot(leaf(10, true), 1), null);
        assertTrue(out.contains("\u001b[31m10\u001b[0m"), out);
    }

    @Test
    void affectedElementIsTheTouchedNodeAndNullForRemove() {
        SetSnapshot s = new SetSnapshot(leaf(1, false), 1);
        assertEquals(10, AsciiSetRenderer.affectedElement(new Add(10, s)));
        assertEquals(20, AsciiSetRenderer.affectedElement(new Compare(20, Direction.LEFT, false, s)));
        assertEquals(30, AsciiSetRenderer.affectedElement(new Rotation(Direction.LEFT, 30, s)));
        assertEquals(40, AsciiSetRenderer.affectedElement(new Recolor(40, Color.RED, Color.BLACK, s)));
        assertNull(AsciiSetRenderer.affectedElement(new Remove(50, s)));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -Dtest=AsciiSetRenderTest test`
Expected: FAIL — compilation error, `AsciiSetRenderer` does not exist.

- [ ] **Step 3: Implement `AsciiSetRenderer`**

Create `src/main/java/com/gimlism/translucent/treeset/viz/AsciiSetRenderer.java`:

```java
package com.gimlism.translucent.treeset.viz;

import com.gimlism.translucent.substrate.viz.ColorMode;
import com.gimlism.translucent.substrate.viz.EventRenderer;
import com.gimlism.translucent.treeset.events.Add;
import com.gimlism.translucent.treeset.events.Compare;
import com.gimlism.translucent.treeset.events.Recolor;
import com.gimlism.translucent.treeset.events.Remove;
import com.gimlism.translucent.treeset.events.Rotation;
import com.gimlism.translucent.treeset.events.SetEvent;
import com.gimlism.translucent.treeset.events.SetEventFormatter;
import com.gimlism.translucent.treeset.events.SetNodeSnapshot;
import com.gimlism.translucent.treeset.events.SetSnapshot;

/**
 * Pure renderer: turns immutable set snapshots/events into a sideways ASCII
 * red-black tree. No I/O. The binary, colour-carrying shape mirrors the map's
 * treeified-bin renderer applied to the whole set; a per-node highlight tracks
 * the element an event touched.
 */
public final class AsciiSetRenderer implements EventRenderer<SetEvent> {
    private static final String RED = "\u001b[31m";
    private static final String RESET = "\u001b[0m";

    private final ColorMode mode;

    public AsciiSetRenderer(ColorMode mode) {
        this.mode = mode;
    }

    /**
     * Header plus the sideways tree (right subtree above, node, left subtree below).
     * The node holding {@code highlight} (null / absent for none) is marked {@code > }.
     */
    public String renderSet(SetSnapshot snap, Object highlight) {
        StringBuilder sb = new StringBuilder("set: size=").append(snap.size());
        appendTree(sb, snap.root(), 0, "", highlight);
        return sb.toString();
    }

    private void appendTree(StringBuilder sb, SetNodeSnapshot n, int depth,
                            String connector, Object highlight) {
        if (n == null) return;
        appendTree(sb, n.right(), depth + 1, "┌─ ", highlight);
        boolean hi = highlight != null && highlight.equals(n.element());
        sb.append('\n').append(hi ? "> " : "  ")
          .append("    ".repeat(depth)).append(connector)
          .append(node(n.element(), n.red()));
        appendTree(sb, n.left(), depth + 1, "└─ ", highlight);
    }

    /** A node label: {@code elem(R)}/{@code elem(B)} in PLAIN, or ANSI-red elem in ANSI. */
    private String node(Object element, boolean red) {
        if (mode == ColorMode.PLAIN) {
            return element + (red ? "(R)" : "(B)");
        }
        return red ? RED + element + RESET : String.valueOf(element);
    }

    /** The event's one-line caption (from {@link SetEventFormatter}) above the resulting set. */
    @Override
    public String renderEvent(SetEvent e) {
        return SetEventFormatter.format(e) + "\n" + renderSet(e.after(), affectedElement(e));
    }

    /**
     * The element an event highlights, or {@code null} for none. Compare marks the
     * visited node (a moving cursor over live nodes); Add the new element; Rotation
     * the pivot; Recolor the recoloured node. Remove's element is gone from
     * {@code after()} and the event carries no successor, so a Remove frame
     * highlights nothing (the caption already names what left).
     */
    public static Object affectedElement(SetEvent e) {
        return switch (e) {
            case Compare c -> c.element();
            case Add a -> a.element();
            case Rotation ro -> ro.pivot();
            case Recolor rc -> rc.element();
            case Remove rm -> null;
        };
    }
}
```

- [ ] **Step 4: Run the render test to verify it passes**

Run: `mvn -q -Dtest=AsciiSetRenderTest test`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/treeset/viz/AsciiSetRenderer.java \
        src/test/java/com/gimlism/translucent/treeset/viz/AsciiSetRenderTest.java
git commit -m "feat(treeset): AsciiSetRenderer — sideways RB-tree ASCII renderer"
```

---

### Task 3: `AsciiSetReplayer` + `AsciiSetVisualizer`

The two trivial substrate subclasses that turn the renderer into a step-through replayer and a live console listener.

**Files:**
- Create: `src/main/java/com/gimlism/translucent/treeset/viz/AsciiSetReplayer.java`
- Create: `src/main/java/com/gimlism/translucent/treeset/viz/AsciiSetVisualizer.java`
- Test: `src/test/java/com/gimlism/translucent/treeset/viz/AsciiSetReplayerTest.java`
- Test: `src/test/java/com/gimlism/translucent/treeset/viz/AsciiSetVisualizerTest.java`

**Interfaces:**
- Consumes: `AsciiSetRenderer` (Task 2); substrate `Replayer<SetEvent>`, `Visualizer<SetEvent>`; `SetRecordingListener`, `TeachingTreeSet`.
- Produces: `AsciiSetReplayer extends Replayer<SetEvent>` (ctor `(List<SetEvent>, AsciiSetRenderer)`); `AsciiSetVisualizer extends Visualizer<SetEvent>` (ctors `(PrintStream, AsciiSetRenderer)` and `(PrintStream)`).

- [ ] **Step 1: Write the failing replayer + visualizer tests**

Create `src/test/java/com/gimlism/translucent/treeset/viz/AsciiSetReplayerTest.java`:

```java
package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.substrate.viz.ColorMode;
import com.gimlism.translucent.treeset.consumer.SetRecordingListener;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AsciiSetReplayerTest {
    private static SetRecordingListener recordThreeAdds() {
        var s = new TeachingTreeSet<Integer>();
        var rec = new SetRecordingListener();
        s.addListener(rec);
        s.add(2);
        s.add(1);
        s.add(3);
        return rec;
    }

    @Test
    void steppingForwardBackAndQuitWalksFrames() {
        var rec = recordThreeAdds();
        int n = rec.events().size();
        var replayer = new AsciiSetReplayer(rec.events(), new AsciiSetRenderer(ColorMode.PLAIN));
        var buf = new ByteArrayOutputStream();
        var in = new ByteArrayInputStream("\nb\nq\n".getBytes(StandardCharsets.UTF_8)); // next, back, quit
        replayer.run(in, new PrintStream(buf, true, StandardCharsets.UTF_8));
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("── frame 1/" + n + " ──"), out);
        assertTrue(out.contains("── frame 2/" + n + " ──"), out);
        int first = out.indexOf("frame 1/" + n);
        assertTrue(first >= 0 && out.indexOf("frame 1/" + n, first + 1) > first, "frame 1 re-shown after back");
    }

    @Test
    void autoPlayRendersEveryFrameInOrder() {
        var rec = recordThreeAdds();
        int n = rec.events().size();
        var replayer = new AsciiSetReplayer(rec.events(), new AsciiSetRenderer(ColorMode.PLAIN));
        var buf = new ByteArrayOutputStream();
        replayer.autoPlay(new PrintStream(buf, true, StandardCharsets.UTF_8), 0);
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("── frame 1/" + n + " ──"), out);
        assertTrue(out.indexOf("frame 1/" + n) < out.indexOf("frame 2/" + n), out);
        assertTrue(out.contains("set: size="), out);
    }
}
```

Create `src/test/java/com/gimlism/translucent/treeset/viz/AsciiSetVisualizerTest.java`:

```java
package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AsciiSetVisualizerTest {
    @Test
    void liveVisualizerRendersAddRebalanceAndRemoveFrames() {
        var buf = new ByteArrayOutputStream();
        var s = new TeachingTreeSet<Integer>();
        // no console under test capture -> the convenience ctor's ColorMode.detect() is PLAIN
        s.addListener(new AsciiSetVisualizer(new PrintStream(buf, true, StandardCharsets.UTF_8)));
        s.add(10);
        s.add(20);
        s.add(30); // red-red violation at 30 -> left rotation about 10 + recolour
        s.remove(30);
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("add 10"), out);
        assertTrue(out.contains("rotate "), out);   // rebalance narrated
        assertTrue(out.contains("recolor "), out);
        assertTrue(out.contains("remove 30"), out);
        assertTrue(out.contains("set: size="), out); // trees rendered
        assertTrue(out.contains("> "), out);         // highlight present on some frame
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn -q -Dtest=AsciiSetReplayerTest,AsciiSetVisualizerTest test`
Expected: FAIL — compilation error, `AsciiSetReplayer` / `AsciiSetVisualizer` do not exist.

- [ ] **Step 3: Implement both subclasses**

Create `src/main/java/com/gimlism/translucent/treeset/viz/AsciiSetReplayer.java`:

```java
package com.gimlism.translucent.treeset.viz;

import com.gimlism.translucent.substrate.viz.Replayer;
import com.gimlism.translucent.treeset.events.SetEvent;
import java.util.List;

/** Step-through ASCII replayer for a recorded set stream — a {@link Replayer} + {@link AsciiSetRenderer}. */
public final class AsciiSetReplayer extends Replayer<SetEvent> {
    public AsciiSetReplayer(List<SetEvent> events, AsciiSetRenderer renderer) {
        super(events, renderer);
    }
}
```

Create `src/main/java/com/gimlism/translucent/treeset/viz/AsciiSetVisualizer.java`:

```java
package com.gimlism.translucent.treeset.viz;

import com.gimlism.translucent.substrate.viz.ColorMode;
import com.gimlism.translucent.substrate.viz.Visualizer;
import com.gimlism.translucent.treeset.events.SetEvent;
import java.io.PrintStream;

/** Live ASCII visualizer for the set — a {@link Visualizer} wired with an {@link AsciiSetRenderer}. */
public final class AsciiSetVisualizer extends Visualizer<SetEvent> {
    public AsciiSetVisualizer(PrintStream out, AsciiSetRenderer renderer) {
        super(out, renderer);
    }

    public AsciiSetVisualizer(PrintStream out) {
        this(out, new AsciiSetRenderer(ColorMode.detect()));
    }
}
```

- [ ] **Step 4: Run to verify they pass**

Run: `mvn -q -Dtest=AsciiSetReplayerTest,AsciiSetVisualizerTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/treeset/viz/AsciiSetReplayer.java \
        src/main/java/com/gimlism/translucent/treeset/viz/AsciiSetVisualizer.java \
        src/test/java/com/gimlism/translucent/treeset/viz/AsciiSetReplayerTest.java \
        src/test/java/com/gimlism/translucent/treeset/viz/AsciiSetVisualizerTest.java
git commit -m "feat(treeset): AsciiSetReplayer + AsciiSetVisualizer substrate subclasses"
```

---

### Task 4: `TreeSetVizDemo` — a runnable scripted story

The first `treeset/demo` file: a scripted red-black-tree narrative rendered live, mirroring `TrieVizDemo`.

**Files:**
- Create: `src/main/java/com/gimlism/translucent/treeset/demo/TreeSetVizDemo.java`
- Test: `src/test/java/com/gimlism/translucent/treeset/demo/TreeSetVizDemoTest.java`

**Interfaces:**
- Consumes: `TeachingTreeSet`, `AsciiSetVisualizer` (Task 3).
- Produces: `TreeSetVizDemo` with `static void main(String[])` and a `static void run(PrintStream out)` test seam.

- [ ] **Step 1: Write the failing demo test**

Create `src/test/java/com/gimlism/translucent/treeset/demo/TreeSetVizDemoTest.java`:

```java
package com.gimlism.translucent.treeset.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TreeSetVizDemoTest {
    @Test
    void runRendersInsertsRebalancesReadsAndRemoval() {
        var buf = new ByteArrayOutputStream();
        TreeSetVizDemo.run(new PrintStream(buf, true, StandardCharsets.UTF_8));
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("add 10"), out);
        assertTrue(out.contains("rotate "), out);     // rebalancing visible
        assertTrue(out.contains("recolor "), out);
        assertTrue(out.contains("compare 40 -> found"), out);   // read narration: Compare carries the VISITED node (contains(40) hit), never the search key
        assertTrue(out.contains("remove 30"), out);
        assertTrue(out.contains("set: size="), out);   // trees rendered
        assertTrue(out.contains("> "), out);           // highlight present
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -q -Dtest=TreeSetVizDemoTest test`
Expected: FAIL — compilation error, `TreeSetVizDemo` does not exist.

- [ ] **Step 3: Implement `TreeSetVizDemo`**

Create `src/main/java/com/gimlism/translucent/treeset/demo/TreeSetVizDemo.java`:

```java
package com.gimlism.translucent.treeset.demo;

import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.viz.AsciiSetVisualizer;
import java.io.PrintStream;

/**
 * A scripted red-black-tree story rendered as live ASCII trees: inserts that force
 * rotations and recolours, a couple of reads that narrate the comparison walk
 * (a moving cursor, no structural change), and a removal (the removed element
 * simply leaves — nothing is highlighted).
 */
public class TreeSetVizDemo {
    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream out) {
        var set = new TeachingTreeSet<Integer>();
        set.addListener(new AsciiSetVisualizer(out));

        out.println("== inserting (watch the tree rotate and recolour to stay balanced) ==");
        for (int e : new int[]{10, 20, 30, 40, 50}) {
            set.add(e);
        }

        out.println("== reads narrate the comparison walk (a moving cursor, no new frame state) ==");
        set.contains(25); // a miss: walks and stops
        set.contains(40); // a hit

        out.println("== removing (the removed element simply leaves; nothing is highlighted) ==");
        set.remove(30);
    }
}
```

- [ ] **Step 4: Run the demo test to verify it passes**

Run: `mvn -q -Dtest=TreeSetVizDemoTest test`
Expected: PASS.

- [ ] **Step 5: Eyeball the real demo output (optional sanity)**

Run: `mvn -q process-classes && mvn -q exec:java -Dexec.mainClass=com.gimlism.translucent.treeset.demo.TreeSetVizDemo`
Expected: a sequence of captioned sideways trees; inserts show `rotate`/`recolor` frames; `compare 25 -> ...` frames redraw the same tree with a moving `> ` cursor; `remove 30` shows the post-removal tree with no `> `.

- [ ] **Step 6: Run the full suite**

Run: `mvn -q test`
Expected: PASS — BUILD SUCCESS, **417 + 9 (render) + 2 (replayer) + 1 (visualizer) + 1 (demo) = 430 tests** (exact totals may differ by a few if the suite counts differently; the invariant is *all green, none removed except the 2 intentionally moved to `ColorModeTest`*).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/gimlism/translucent/treeset/demo/TreeSetVizDemo.java \
        src/test/java/com/gimlism/translucent/treeset/demo/TreeSetVizDemoTest.java
git commit -m "feat(treeset): TreeSetVizDemo — scripted live ASCII RB-tree story"
```

---

## After all tasks

- Whole-branch review (opus) per the project rhythm, then PR, Copilot triage, and `gh pr merge --merge` (NOT squash, NOT `--delete-branch`) on user go-ahead.
- The 4-slice **web** viz arc (static → live SSE → REPL → controls) remains deferred to its own later brainstorms.

## Self-review notes

- **Spec coverage:** every spec section maps to a task — renderer + header + sideways tree + colour + `affectedElement`/Remove-null (Task 2); `ColorMode` promotion + `Palette` retrofit + shared-enum rename (Task 1); Replayer/Visualizer trio (Task 3); demo (Task 4); tests in every task incl. `ColorMode` regression via full-suite runs.
- **Type consistency:** `ColorMode`, `renderSet(SetSnapshot, Object)`, `affectedElement(SetEvent)`, `AsciiSetRenderer(ColorMode)`, `AsciiSetVisualizer(PrintStream)` are used identically across tasks. `Color`/`Direction` are the `substrate.rbtree` ones (as the events declare); the renderer never imports a `Color` type (it reads `boolean red`).
- **No placeholders:** every code and command step is complete and literal.
