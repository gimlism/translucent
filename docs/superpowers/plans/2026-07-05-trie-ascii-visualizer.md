# Teaching Radix Trie — ASCII Visualizer — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the radix trie's `TrieEvent` stream as ASCII — a top-down indented N-ary tree with `String`-labelled edges and a path-shaped highlight that tracks the narrated walk — via a pure renderer plus thin live/replay drivers over the substrate. Third consumer of `substrate/viz`; the acid test's visible payoff.

**Architecture:** New `com.gimlism.translucent.trie.viz` depending only on `trie.events` + `substrate.viz`. `AsciiTrieRenderer implements EventRenderer<TrieEvent>` (pure; `renderTrie(snap, highlightPath)` + `renderEvent` + static `affectedPath`), `AsciiTrieVisualizer extends Visualizer<TrieEvent>` (live), `AsciiTrieReplayer extends Replayer<TrieEvent>` (scrubber), `TrieVizDemo`. No `Palette` (uncoloured). The highlight locus is a `String` path (not an int), and every `TrieEvent` carries one.

**Tech Stack:** Java 21, Maven, JUnit 5. No new dependencies.

## Global Constraints

- Package `com.gimlism.translucent.trie.viz`; depends only on `trie.events` and `substrate.viz`. No dependency on `hashmap.*`/`arraylist.*`.
- Layout: header `trie: size=<S>`; root line `(root)`; each descendant node on its own line, indented 2 spaces per depth, edge label in quotes; a key node appends `●=<value>` (root included when the `""` key exists); highlight prefix `> ` for the node at the event's path, `  ` otherwise.
- `affectedPath(TrieEvent)` returns the event's `path` (total — every variant has one).
- Both drivers render through the one pure `AsciiTrieRenderer`; thin `Ascii*` subclasses mirror the map/list idiom (incl. a default-renderer convenience constructor on the visualizer).
- `exec:java` default stays `hashmap.demo.VizDemo`.

## File structure

- Create `trie/viz/AsciiTrieRenderer.java`, `AsciiTrieVisualizer.java`, `AsciiTrieReplayer.java`.
- Create `trie/demo/TrieVizDemo.java`.
- Tests under `src/test/java/com/gimlism/translucent/trie/{viz,demo}/`.

---

### Task 1: `AsciiTrieRenderer` — pure renderer (golden strings)

**Files:**
- Create: `src/main/java/com/gimlism/translucent/trie/viz/AsciiTrieRenderer.java`
- Test: `src/test/java/com/gimlism/translucent/trie/viz/AsciiTrieRenderTest.java`

**Interfaces:** `renderTrie(TrieSnapshot, String highlightPath)`, `renderEvent(TrieEvent)`, `static String affectedPath(TrieEvent)`.

- [ ] **Step 1: Write the failing test**

`AsciiTrieRenderTest.java`
```java
package com.gimlism.translucent.trie.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gimlism.translucent.trie.events.CreateNode;
import com.gimlism.translucent.trie.events.MergeEdge;
import com.gimlism.translucent.trie.events.Prune;
import com.gimlism.translucent.trie.events.Put;
import com.gimlism.translucent.trie.events.Remove;
import com.gimlism.translucent.trie.events.SplitEdge;
import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsciiTrieRenderTest {
    private final AsciiTrieRenderer r = new AsciiTrieRenderer();

    // {she=1, shore=2}: root -"sh"-> (non-key) { "e"=1 (key), "ore"=2 (key) }
    private static TrieSnapshot sheShore() {
        var e = new TrieNodeSnapshot(true, 1, List.of());
        var ore = new TrieNodeSnapshot(true, 2, List.of());
        var sh = new TrieNodeSnapshot(false, null,
            List.of(new TrieEdge("e", e), new TrieEdge("ore", ore)));
        var root = new TrieNodeSnapshot(false, null, List.of(new TrieEdge("sh", sh)));
        return new TrieSnapshot(root, 2);
    }

    @Test
    void rendersIndentedTreeWithLabelsAndKeyMarkers() {
        String expected = String.join("\n",
            "trie: size=2",
            "  (root)",
            "    \"sh\"",
            "      \"e\" ●=1",
            "      \"ore\" ●=2");
        assertEquals(expected, r.renderTrie(sheShore(), null));
    }

    @Test
    void highlightsTheNodeAtThePath() {
        String expected = String.join("\n",
            "trie: size=2",
            "  (root)",
            "    \"sh\"",
            ">     \"e\" ●=1",
            "      \"ore\" ●=2");
        assertEquals(expected, r.renderTrie(sheShore(), "she")); // path she -> the "e" node
    }

    @Test
    void nonMatchingPathHighlightsNothing() {
        assertEquals(r.renderTrie(sheShore(), null), r.renderTrie(sheShore(), "zzz"));
    }

    @Test
    void rootKeyShowsValue() {
        var root = new TrieNodeSnapshot(true, 9, List.of());
        String expected = String.join("\n",
            "trie: size=1",
            "  (root) ●=9");
        assertEquals(expected, r.renderTrie(new TrieSnapshot(root, 1), null));
    }

    @Test
    void renderEventPutsLabelAboveTreeAndHighlightsThePath() {
        Put p = new Put("she", 1, null, true, "she", sheShore());
        String out = r.renderEvent(p);
        org.junit.jupiter.api.Assertions.assertTrue(out.startsWith("PUT \"she\"=1 (new)\n"), out);
        org.junit.jupiter.api.Assertions.assertTrue(out.contains("> "), out);
    }

    @Test
    void affectedPathReturnsEachEventsPath() {
        var s = sheShore();
        assertEquals("she", AsciiTrieRenderer.affectedPath(new Put("she", 1, null, true, "she", s)));
        assertEquals("sh", AsciiTrieRenderer.affectedPath(new SplitEdge("shore", "sh", "sh", s)));
        assertEquals("shell", AsciiTrieRenderer.affectedPath(new CreateNode("ll", "shell", s)));
        assertEquals("she", AsciiTrieRenderer.affectedPath(new Remove("she", 1, "she", s)));
        assertEquals("shell", AsciiTrieRenderer.affectedPath(new Prune("ll", "shell", s)));
        assertEquals("shore", AsciiTrieRenderer.affectedPath(new MergeEdge("shore", "shore", s)));
    }
}
```

- [ ] **Step 2: Run — FAIL** (`AsciiTrieRenderer` absent).

- [ ] **Step 3: Implement.**
```java
package com.gimlism.translucent.trie.viz;

import com.gimlism.translucent.trie.events.CreateNode;
import com.gimlism.translucent.trie.events.Descend;
import com.gimlism.translucent.trie.events.MergeEdge;
import com.gimlism.translucent.trie.events.Prune;
import com.gimlism.translucent.trie.events.Put;
import com.gimlism.translucent.trie.events.Remove;
import com.gimlism.translucent.trie.events.SplitEdge;
import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieEvent;
import com.gimlism.translucent.trie.events.TrieEventFormatter;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;
import com.gimlism.translucent.substrate.viz.EventRenderer;

/** Pure renderer: turns immutable trie snapshots/events into a top-down indented ASCII tree. No I/O, no colour. */
public final class AsciiTrieRenderer implements EventRenderer<TrieEvent> {

    /** Header plus the indented N-ary tree; the node at {@code highlightPath} (null for none) is prefixed {@code > }. */
    public String renderTrie(TrieSnapshot snap, String highlightPath) {
        StringBuilder sb = new StringBuilder("trie: size=").append(snap.size());
        appendNode(sb, "", snap.root(), "", 0, highlightPath, true);
        return sb.toString();
    }

    private void appendNode(StringBuilder sb, String label, TrieNodeSnapshot node,
                            String pathSoFar, int depth, String highlightPath, boolean root) {
        boolean hi = highlightPath != null && pathSoFar.equals(highlightPath);
        sb.append('\n').append(hi ? "> " : "  ").append("  ".repeat(depth));
        sb.append(root ? "(root)" : "\"" + label + "\"");
        if (node.key()) sb.append(" ●=").append(node.value());
        for (TrieEdge e : node.children()) {
            appendNode(sb, e.label(), e.target(), pathSoFar + e.label(), depth + 1, highlightPath, false);
        }
    }

    /** The event's one-line label (from {@link TrieEventFormatter}) above the resulting tree. */
    public String renderEvent(TrieEvent e) {
        return TrieEventFormatter.format(e) + "\n" + renderTrie(e.after(), affectedPath(e));
    }

    /** The path (root-to-node label concatenation) an event concerns — every trie event carries one. */
    public static String affectedPath(TrieEvent e) {
        return switch (e) {
            case Descend d -> d.path();
            case CreateNode c -> c.path();
            case SplitEdge s -> s.path();
            case Put p -> p.path();
            case Remove r -> r.path();
            case MergeEdge m -> m.path();
            case Prune pr -> pr.path();
        };
    }
}
```

- [ ] **Step 4: Run — PASS.** **Step 5: Commit** `feat(trie-viz): pure AsciiTrieRenderer (indented N-ary tree)`.

---

### Task 2: `AsciiTrieVisualizer` — live driver

**Files:**
- Create: `src/main/java/com/gimlism/translucent/trie/viz/AsciiTrieVisualizer.java`
- Test: `src/test/java/com/gimlism/translucent/trie/viz/AsciiTrieVisualizerTest.java`

- [ ] **Step 1: Failing test** — attach to a real `RadixTrie`, run `put("she",1); put("shore",2); put("shell",3); remove("shell")`, assert the captured output contains `SPLIT`, `PUT "shell"=3 (new)`, an `"ore" ●=2` node line, a `MERGE`/`PRUNE` on removal, and a `> ` highlight.
```java
package com.gimlism.translucent.trie.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.core.RadixTrie;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AsciiTrieVisualizerTest {
    @Test
    void liveVisualizerRendersInsertAndRemoveFrames() {
        var buf = new ByteArrayOutputStream();
        var t = new RadixTrie<Integer>();
        t.addListener(new AsciiTrieVisualizer(new PrintStream(buf, true, StandardCharsets.UTF_8)));
        t.put("she", 1);
        t.put("shore", 2);
        t.put("shell", 3); // split + create
        t.remove("shell");  // narrated walk + prune + merge
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("SPLIT"), out);
        assertTrue(out.contains("PUT \"shell\"=3 (new)"), out);
        assertTrue(out.contains("\"ore\" ●=2"), out);
        assertTrue(out.contains("PRUNE"), out);
        assertTrue(out.contains("> "), out);        // path highlight present
    }
}
```

- [ ] **Step 2: FAIL.** **Step 3: Implement.**
```java
package com.gimlism.translucent.trie.viz;

import com.gimlism.translucent.trie.events.TrieEvent;
import com.gimlism.translucent.substrate.viz.Visualizer;
import java.io.PrintStream;

/** Live ASCII visualizer for the trie — a {@link Visualizer} wired with an {@link AsciiTrieRenderer}. */
public final class AsciiTrieVisualizer extends Visualizer<TrieEvent> {
    public AsciiTrieVisualizer(PrintStream out, AsciiTrieRenderer renderer) { super(out, renderer); }
    public AsciiTrieVisualizer(PrintStream out) { this(out, new AsciiTrieRenderer()); }
}
```

- [ ] **Step 4: PASS.** **Step 5: Commit** `feat(trie-viz): live AsciiTrieVisualizer`.

---

### Task 3: `AsciiTrieReplayer` — scrubber

**Files:**
- Create: `src/main/java/com/gimlism/translucent/trie/viz/AsciiTrieReplayer.java`
- Test: `src/test/java/com/gimlism/translucent/trie/viz/AsciiTrieReplayerTest.java`

- [ ] **Step 1: Failing test** — record 3 puts via `TrieRecordingListener`, then a `AsciiTrieReplayer`: `run` with `"\nb\nq\n"` shows `── frame 1/N ──`/`2/N` and re-shows frame 1 after back; `autoPlay(out, 0)` renders every frame in order. (Mirror `AsciiListReplayerTest`.)

- [ ] **Step 2: FAIL.** **Step 3: Implement.**
```java
package com.gimlism.translucent.trie.viz;

import com.gimlism.translucent.trie.events.TrieEvent;
import com.gimlism.translucent.substrate.viz.Replayer;
import java.util.List;

/** Step-through ASCII replayer for a recorded trie stream — a {@link Replayer} + {@link AsciiTrieRenderer}. */
public final class AsciiTrieReplayer extends Replayer<TrieEvent> {
    public AsciiTrieReplayer(List<TrieEvent> events, AsciiTrieRenderer renderer) { super(events, renderer); }
}
```

- [ ] **Step 4: PASS.** **Step 5: Commit** `feat(trie-viz): AsciiTrieReplayer step-through and auto-play`.

---

### Task 4: `TrieVizDemo`

**Files:**
- Create: `src/main/java/com/gimlism/translucent/trie/demo/TrieVizDemo.java`
- Test: `src/test/java/com/gimlism/translucent/trie/demo/TrieVizDemoTest.java`

- [ ] **Step 1: Failing test** — `TrieVizDemo.run(ps)` output contains `SPLIT`, `PUT`, `MERGE`, `PRUNE`, `DESCEND`, and a rendered `(root)` line + a `> ` highlight.

- [ ] **Step 2: FAIL.** **Step 3: Implement** (the `TrieDemo` story via `AsciiTrieVisualizer`):
```java
package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.viz.AsciiTrieVisualizer;
import java.io.PrintStream;

/** Same scripted story as {@link TrieDemo}, rendered as live ASCII trees instead of a text log. */
public class TrieVizDemo {
    public static void main(String[] args) { run(System.out); }

    static void run(PrintStream out) {
        var trie = new RadixTrie<Integer>();
        trie.addListener(new AsciiTrieVisualizer(out));
        out.println("== inserting keys with shared prefixes (watch edges split and branch) ==");
        int v = 0;
        for (String key : new String[]{"shore", "she", "shell"}) trie.put(key, v++);
        out.println("== removing keys (watch the walk, then leaves prune and edges merge) ==");
        for (String key : new String[]{"shell", "she"}) trie.remove(key);
    }
}
```

- [ ] **Step 4: PASS** (optionally eyeball `java -cp target/classes com.gimlism.translucent.trie.demo.TrieVizDemo`). **Step 5: Commit** `feat(trie-viz): live ASCII demo of the trie event stream`.

---

## Self-Review

**Spec coverage:** indented N-ary tree, `String` labels, `●=value` key marker, `(root)`, path highlight (incl. non-match → none), no `Palette` → Task 1; `affectedPath` total over the vocabulary → Task 1; live `Visualizer<TrieEvent>` → Task 2; scrubber → Task 3; demo mirroring `TrieDemo` → Task 4. ✓

**Placeholder scan:** Task 3/4 test bodies sketched with exact intent (mirror the list viz tests); Task 1/2 fully written. Production code complete.

**Type consistency:** `AsciiTrieRenderer.renderTrie(TrieSnapshot,String)` / `renderEvent(TrieEvent)` / `static affectedPath(TrieEvent):String`; `AsciiTrieVisualizer(PrintStream[,AsciiTrieRenderer])`; `AsciiTrieReplayer(List<TrieEvent>,AsciiTrieRenderer)`. Golden strings use exact `TrieEventFormatter` labels.

## Notes for the reviewer / final review

- The **`String` path locus** (vs the siblings' `int`) is the design point — it's what makes the narrated `Descend` walk animate. Every `TrieEvent` carries a `path` (uniform since CO4), so `affectedPath` needs no −1/none sentinel; a path that matches no live node (a `Prune`d leaf) just highlights nothing.
- `●` is a non-ASCII marker (like the map/list use `·`/box-drawing); tests assert it as a literal, output is UTF-8.
- Deferred (unchanged): the remaining D4 items (`Palette` detection move, grammar recipe in `substrate/package-info`) and the second trie implementation.
