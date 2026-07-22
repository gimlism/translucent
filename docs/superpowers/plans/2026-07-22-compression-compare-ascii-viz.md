# Compression-compare ASCII viz Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A pure ASCII renderer that draws the fat `StandardTrie` beside the compressed `RadixTrie` with a savings banner, making #41's measured compression visible in the terminal.

**Architecture:** Both tries share `TrieSnapshot`, so the existing `AsciiTrieRenderer.renderTrie(snap, null)` already draws either one. This slice adds one pure `CompressionCompareRenderer` that renders each tree, strips its `"trie: size=N"` header, and interleaves the two panels into columns (falling back to stacked past a width guard). `CompressionCompareDemo.Comparison` is extended to carry the two snapshots so the renderer is a pure function of the comparison result.

**Tech Stack:** Java 21, Maven, JUnit 5.

## Global Constraints

- Java 21; build/test with `mvn` (the source of truth — Eclipse/LSP may show phantom errors; ignore them).
- **Protected surface — must stay BYTE-UNCHANGED:** all of `trie/core` (incl. `StandardTrie`, `RadixTrie`), `trie/events`, `substrate/**`, and `trie/viz/AsciiTrieRenderer.java`. This slice does not widen the shared renderer's API — it reuses `renderTrie` verbatim.
- New code lives in `com.gimlism.translucent.trie.compare` (same package as `CompressionCompareDemo`, so `Comparison` needs no import there).
- Every commit message ends with these two trailer lines (append to each commit in this plan; shown once here, not repeated per step):
  ```
  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm
  ```
- Full suite must stay green: `mvn -q test`.

## File Structure

- `trie/compare/CompressionCompareDemo.java` — **modify**: extend the `Comparison` record with two `TrieSnapshot` fields; populate them in both `compare()` branches; `main` renders.
- `trie/compare/CompressionCompareRenderer.java` — **create**: the pure renderer (banner + panels + columns/stacked).
- `trie/compare/CompressionCompareRendererTest.java` — **create**: golden, alignment, width-guard, empty-keys tests.
- `trie/compare/CompressionCompareDemoTest.java` — untouched (reads accessors only).

---

### Task 1: Extend `Comparison` to carry the two snapshots

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/trie/compare/CompressionCompareDemo.java` (record at lines 20-29; empty branch 33-39; return 50-53)
- Test: `src/test/java/com/gimlism/translucent/trie/compare/CompressionCompareDemoTest.java` (add one test)

**Interfaces:**
- Produces: `CompressionCompareDemo.Comparison(int standardNodes, int radixNodes, List<String> keys, TrieSnapshot standardSnapshot, TrieSnapshot radixSnapshot)` with existing `saved()`, `standardNodes()`, `radixNodes()`, `keys()` accessors plus new `standardSnapshot()`, `radixSnapshot()`. `compare(List<String>)` returns it fully populated.

- [ ] **Step 1: Write the failing test**

Add to `CompressionCompareDemoTest.java` (add import `com.gimlism.translucent.trie.compare.TrieMetrics` is not needed — use the accessor; add import `com.gimlism.translucent.trie.events.TrieSnapshot` and `static org.junit.jupiter.api.Assertions.assertNotNull`):

```java
    @Test
    void comparisonCarriesEachTriesFinalSnapshot() {
        var c = CompressionCompareDemo.compare(CANON);
        TrieSnapshot std = c.standardSnapshot();
        TrieSnapshot rad = c.radixSnapshot();
        assertNotNull(std);
        assertNotNull(rad);
        // The carried snapshots are the very ones the counts were derived from.
        assertEquals(c.standardNodes(), TrieMetrics.nodeCount(std));
        assertEquals(c.radixNodes(), TrieMetrics.nodeCount(rad));
    }
```

(Add `import com.gimlism.translucent.trie.events.TrieSnapshot;` and `import static org.junit.jupiter.api.Assertions.assertNotNull;` at the top. `TrieMetrics` is in the same package — no import.)

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=CompressionCompareDemoTest`
Expected: FAIL — compile error, `Comparison` has no method `standardSnapshot()`.

- [ ] **Step 3: Extend the record**

In `CompressionCompareDemo.java`, replace the record header (lines 20-24) so it reads:

```java
    /** Result of comparing the two tries on one key set. */
    public record Comparison(int standardNodes, int radixNodes, List<String> keys,
                             TrieSnapshot standardSnapshot, TrieSnapshot radixSnapshot) {
        public Comparison {
            keys = List.copyOf(keys);
        }
```

(`TrieSnapshot` is already imported at line 7. `saved()` stays as-is.)

- [ ] **Step 4: Populate both branches**

Replace the empty-keys branch body (lines 33-39) with:

```java
        if (keys.isEmpty()) {
            // No keys inserted -> both tries hold only the root (no events to read a snapshot from).
            // Count it through the same root-included metric so this stays consistent with the
            // non-empty path rather than hardcoding the root's contribution.
            var rootOnly = new TrieSnapshot(new TrieNodeSnapshot(false, null, List.of()), 0);
            int rootCount = TrieMetrics.nodeCount(rootOnly);
            return new Comparison(rootCount, rootCount, keys, rootOnly, rootOnly);
        }
```

Replace the non-empty return (lines 50-53) with:

```java
        var standardSnap = lastSnapshot(standardRec);
        var radixSnap = lastSnapshot(radixRec);
        return new Comparison(
            TrieMetrics.nodeCount(standardSnap),
            TrieMetrics.nodeCount(radixSnap),
            keys, standardSnap, radixSnap);
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q test -Dtest=CompressionCompareDemoTest`
Expected: PASS (all 5 tests, including the new one and the untouched count/empty tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/compare/CompressionCompareDemo.java \
        src/test/java/com/gimlism/translucent/trie/compare/CompressionCompareDemoTest.java
git commit -m "feat(trie): carry each trie's final snapshot on Comparison"
```

---

### Task 2: `CompressionCompareRenderer` — banner + columns layout

**Files:**
- Create: `src/main/java/com/gimlism/translucent/trie/compare/CompressionCompareRenderer.java`
- Test: `src/test/java/com/gimlism/translucent/trie/compare/CompressionCompareRendererTest.java`

**Interfaces:**
- Consumes: `CompressionCompareDemo.Comparison` (from Task 1); `AsciiTrieRenderer.renderTrie(TrieSnapshot, String)`.
- Produces: `new CompressionCompareRenderer().render(Comparison)` → `String`; `render(Comparison, int maxWidth)` → `String`.

For the canonical set `{she, shell, shore, shy}` (standard 10 nodes, radix 6), the default `render` produces this (columns; gutter = widest-left(19) + 3 = 22):

```
compression compare: {she, shell, shore, shy}
  standard = 10   radix = 6   saved = 4 (40%)

standard (10)          radix (6)
  (root)                (root)
    "s"                   "sh"
      "h"                   "e" ●=0
        "e" ●=0               "ll" ●=1
          "l"                 "ore" ●=2
            "l" ●=1           "y" ●=3
        "o"
          "r"
            "e" ●=2
        "y" ●=3
```

- [ ] **Step 1: Write the failing banner test**

Create `CompressionCompareRendererTest.java`:

```java
package com.gimlism.translucent.trie.compare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CompressionCompareRendererTest {
    private static final List<String> CANON = List.of("she", "shell", "shore", "shy");
    private final CompressionCompareRenderer renderer = new CompressionCompareRenderer();

    private static String normalize(String s) {
        return s.lines().map(l -> l.replaceAll(" +", " ").strip()).collect(Collectors.joining("\n"));
    }

    @Test
    void bannerLeadsWithKeysCountsAndSavings() {
        String out = renderer.render(CompressionCompareDemo.compare(CANON));
        assertTrue(out.startsWith(
            "compression compare: {she, shell, shore, shy}\n"
            + "  standard = 10   radix = 6   saved = 4 (40%)\n"),
            "banner mismatch, got:\n" + out);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=CompressionCompareRendererTest`
Expected: FAIL — `CompressionCompareRenderer` does not exist (compile error).

- [ ] **Step 3: Write the renderer (banner + panels + columns)**

Create `CompressionCompareRenderer.java`:

```java
package com.gimlism.translucent.trie.compare;

import com.gimlism.translucent.trie.events.TrieSnapshot;
import com.gimlism.translucent.trie.viz.AsciiTrieRenderer;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the two tries from a {@link CompressionCompareDemo.Comparison} side by side (fat
 * {@code StandardTrie} left, compressed {@code RadixTrie} right) under a savings banner, so the
 * nodes the radix compression removes are visible. Pure: no I/O, no colour. Reuses
 * {@link AsciiTrieRenderer} verbatim (its API is not widened); a per-tree panel is that renderer's
 * output with its {@code "trie: size=N"} header line swapped for a {@code standard (N)} / {@code radix (N)}
 * label. Columns when they fit within {@code maxWidth}; stacked otherwise.
 */
public final class CompressionCompareRenderer {
    private static final int DEFAULT_MAX_WIDTH = 100;
    private static final int GAP = 3;
    private final AsciiTrieRenderer trees = new AsciiTrieRenderer();

    /** Render with the default width guard (~100 columns). */
    public String render(CompressionCompareDemo.Comparison c) {
        return render(c, DEFAULT_MAX_WIDTH);
    }

    /** Render, falling back from columns to stacked when the two panels would exceed {@code maxWidth}. */
    public String render(CompressionCompareDemo.Comparison c, int maxWidth) {
        List<String> left = panel("standard (" + c.standardNodes() + ")", c.standardSnapshot());
        List<String> right = panel("radix (" + c.radixNodes() + ")", c.radixSnapshot());
        String body = gutter(left) + width(right) <= maxWidth ? columns(left, right) : stacked(left, right);
        return banner(c) + "\n\n" + body;
    }

    private String banner(CompressionCompareDemo.Comparison c) {
        long pct = Math.round(100.0 * c.saved() / c.standardNodes());
        return "compression compare: {" + String.join(", ", c.keys()) + "}\n"
            + "  standard = " + c.standardNodes()
            + "   radix = " + c.radixNodes()
            + "   saved = " + c.saved() + " (" + pct + "%)";
    }

    /** A header label followed by the tree body, i.e. {@code renderTrie} minus its "trie: size=" line. */
    private List<String> panel(String header, TrieSnapshot snap) {
        String full = trees.renderTrie(snap, null);
        List<String> lines = new ArrayList<>();
        lines.add(header);
        // full = "trie: size=N\n<line>\n<line>..." ; drop the header line, keep the tree verbatim.
        for (String line : full.substring(full.indexOf('\n') + 1).split("\n", -1)) {
            lines.add(line);
        }
        return lines;
    }

    private int gutter(List<String> left) {
        return width(left) + GAP;
    }

    private int width(List<String> lines) {
        int w = 0;
        for (String l : lines) w = Math.max(w, l.length());
        return w;
    }

    private String columns(List<String> left, List<String> right) {
        int gutter = gutter(left);
        int n = Math.max(left.size(), right.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            String l = i < left.size() ? left.get(i) : "";
            String r = i < right.size() ? right.get(i) : "";
            if (r.isEmpty()) {
                sb.append(l);                                   // right exhausted: no trailing padding
            } else {
                sb.append(l).append(" ".repeat(gutter - l.length())).append(r);
            }
            if (i < n - 1) sb.append('\n');
        }
        return sb.toString();
    }

    private String stacked(List<String> left, List<String> right) {
        return withColonHeader(left) + "\n\n" + withColonHeader(right);
    }

    private String withColonHeader(List<String> panel) {
        StringBuilder sb = new StringBuilder(panel.get(0)).append(':');   // "standard (10):"
        for (int i = 1; i < panel.size(); i++) sb.append('\n').append(panel.get(i));
        return sb.toString();
    }
}
```

- [ ] **Step 4: Run banner test to verify it passes**

Run: `mvn -q test -Dtest=CompressionCompareRendererTest`
Expected: PASS.

- [ ] **Step 5: Add the columns golden test (space-normalized)**

Add to `CompressionCompareRendererTest.java`:

```java
    @Test
    void columnsInterleaveBothTreesWithSavings() {
        String out = renderer.render(CompressionCompareDemo.compare(CANON));
        assertEquals(String.join("\n",
            "compression compare: {she, shell, shore, shy}",
            "standard = 10 radix = 6 saved = 4 (40%)",
            "",
            "standard (10) radix (6)",
            "(root) (root)",
            "\"s\" \"sh\"",
            "\"h\" \"e\" ●=0",
            "\"e\" ●=0 \"ll\" ●=1",
            "\"l\" \"ore\" ●=2",
            "\"l\" ●=1 \"y\" ●=3",
            "\"o\"",
            "\"r\"",
            "\"e\" ●=2",
            "\"y\" ●=3"),
            normalize(out));
    }
```

- [ ] **Step 6: Run it to verify it passes**

Run: `mvn -q test -Dtest=CompressionCompareRendererTest`
Expected: PASS. (If it fails, the discrepancy is in the golden vs. real trie shape — verify the actual `normalize(out)` printed in the failure against the design's expected tree before changing anything; the tree shape itself is pinned by `AsciiTrieRenderer` tests and Task 1, so a mismatch here means a renderer bug, not a bad golden.)

- [ ] **Step 7: Add the alignment test (right column sliced at the gutter is a clean radix panel)**

Add to `CompressionCompareRendererTest.java`:

```java
    @Test
    void everyRightColumnLineStartsAtTheSameGutter() {
        String out = renderer.render(CompressionCompareDemo.compare(CANON));
        List<String> lines = out.lines().toList();
        int headerIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("radix (6)")) { headerIdx = i; break; }
        }
        assertTrue(headerIdx >= 0, "no columns header found:\n" + out);
        int gutter = lines.get(headerIdx).indexOf("radix (6)");
        // The 7 rows from the header down carry the whole radix panel; slicing each at the gutter
        // must reproduce a clean radix render (proves a constant gutter and unshifted right content).
        String rightColumn = lines.subList(headerIdx, headerIdx + 7).stream()
            .map(l -> l.substring(gutter))
            .collect(Collectors.joining("\n"));
        assertEquals(String.join("\n",
            "radix (6)",
            "  (root)",
            "    \"sh\"",
            "      \"e\" ●=0",
            "        \"ll\" ●=1",
            "      \"ore\" ●=2",
            "      \"y\" ●=3"),
            rightColumn);
    }
```

- [ ] **Step 8: Add the empty-keys test**

Add to `CompressionCompareRendererTest.java`:

```java
    @Test
    void emptyKeySetRendersTwoRootOnlyPanels() {
        String out = renderer.render(CompressionCompareDemo.compare(List.of()));
        assertTrue(out.contains("saved = 0 (0%)"), "expected zero-savings banner:\n" + out);
        // Both root-only panels appear (two "(root)" occurrences, on the single interleaved roots row).
        long roots = out.lines().flatMap(l -> {
            int c = (l.length() - l.replace("(root)", "").length()) / "(root)".length();
            return java.util.stream.IntStream.range(0, c).boxed();
        }).count();
        assertEquals(2, roots, "expected exactly two roots:\n" + out);
    }
```

- [ ] **Step 9: Run the full renderer test class**

Run: `mvn -q test -Dtest=CompressionCompareRendererTest`
Expected: PASS (banner, columns golden, alignment, empty-keys — 4 tests).

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/compare/CompressionCompareRenderer.java \
        src/test/java/com/gimlism/translucent/trie/compare/CompressionCompareRendererTest.java
git commit -m "feat(trie): CompressionCompareRenderer side-by-side columns view"
```

---

### Task 3: Width guard — stacked fallback

**Files:**
- Test: `src/test/java/com/gimlism/translucent/trie/compare/CompressionCompareRendererTest.java` (add one test)
- (No production change — the `stacked` branch already exists from Task 2; this task pins it.)

**Interfaces:**
- Consumes: `render(Comparison, int maxWidth)` from Task 2.

- [ ] **Step 1: Write the failing width-guard test**

Add to `CompressionCompareRendererTest.java`:

```java
    @Test
    void narrowMaxWidthFallsBackToStacked() {
        // Canonical columns need gutter(22)+widestRight(16)=38; a 20-col cap forces stacked.
        String out = renderer.render(CompressionCompareDemo.compare(CANON), 20);
        // Stacked headers carry a trailing colon and sit on their own lines.
        assertTrue(out.contains("standard (10):"), "expected stacked standard header:\n" + out);
        assertTrue(out.contains("radix (6):"), "expected stacked radix header:\n" + out);
        // No physical line mixes both panels (that would be columns).
        assertTrue(out.lines().noneMatch(l -> l.contains("standard (10)") && l.contains("radix (6)")),
            "panels must not share a line when stacked:\n" + out);
        // The radix panel starts strictly after the standard tree: standard's unique "r" node
        // (radix has "ore", never a lone "r") precedes the radix header.
        assertTrue(out.indexOf("\"r\"") < out.indexOf("radix (6):"),
            "radix panel must follow the whole standard tree:\n" + out);
    }
```

- [ ] **Step 2: Run test to verify it passes immediately**

Run: `mvn -q test -Dtest=CompressionCompareRendererTest#narrowMaxWidthFallsBackToStacked`
Expected: PASS (the `stacked` branch was implemented in Task 2; this test proves it triggers and is correct).

Note: this test does not go red-then-green because the fallback code already exists — its value is pinning the branch. To confirm it is non-vacuous, temporarily change the guard in `render` from `<= maxWidth` to `<= maxWidth * 100`, re-run, and confirm this test FAILS (columns rendered instead); then revert the change and re-run to green.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/gimlism/translucent/trie/compare/CompressionCompareRendererTest.java
git commit -m "test(trie): pin the stacked fallback past the columns width guard"
```

---

### Task 4: Wire `CompressionCompareDemo.main` to render, and runtime-verify

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/trie/compare/CompressionCompareDemo.java` (`main`, lines 62-67)

**Interfaces:**
- Consumes: `CompressionCompareRenderer.render(Comparison)` from Task 2.

- [ ] **Step 1: Replace `main` to print the rendered view**

Replace the `main` method body (lines 62-67) with:

```java
    public static void main(String[] args) {
        System.out.println(new CompressionCompareRenderer().render(
            compare(List.of("she", "shell", "shore", "shy"))));
    }
```

(Remove the now-unused `printf`; no imports change — `List` and `CompressionCompareRenderer` are in-package/already available.)

- [ ] **Step 2: Compile and run the demo to verify the real output**

Run:
```bash
mvn -q -DskipTests compile
mvn -q exec:java -Dexec.mainClass=com.gimlism.translucent.trie.compare.CompressionCompareDemo
```
Expected: the side-by-side columns view prints — banner `standard = 10   radix = 6   saved = 4 (40%)`, then the fat standard tree on the left and the compressed radix tree on the right, roots aligned. (If `exec:java` is unavailable, run via `java -cp target/classes com.gimlism.translucent.trie.compare.CompressionCompareDemo`.)

- [ ] **Step 3: Verify a dramatic single-key case by hand (optional sanity)**

Run (throwaway, in `jshell` or a scratch main) `CompressionCompareDemo.compare(List.of("internationalization"))` through the renderer and confirm it degrades to **stacked** (one 21-node chain would overflow columns at the default 100-col guard) rather than wrapping. This exercises the width guard on real data, not just the forced-`maxWidth` test.

- [ ] **Step 4: Run the full suite**

Run: `mvn -q test`
Expected: PASS — the prior 504 tests plus the 6 added here (1 in Task 1, 4 in Task 2, 1 in Task 3) = 510.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/compare/CompressionCompareDemo.java
git commit -m "feat(trie): CompressionCompareDemo main prints the side-by-side view"
```

---

## Self-Review

**1. Spec coverage:**
- Static side-by-side render of both final trees → Task 2 (columns) + Task 4 (demo). ✓
- Reuse `AsciiTrieRenderer` byte-unchanged → Task 2 `panel()` strips the header via string surgery; no edit to `AsciiTrieRenderer`. ✓
- Columns layout with gutter = widest-left + gap → Task 2 `columns()`/`gutter()`. ✓
- Width guard → stacked fallback → Task 2 (`stacked`) + Task 3 (pin). ✓
- Extend `Comparison` with two snapshots → Task 1. ✓
- No collapse marker → not implemented (deferred, per spec non-goals). ✓
- Banner (keys, counts, saved %) → Task 2 `banner()`. ✓
- Tests: golden (T2 step 5), width guard (T3), alignment (T2 step 7), empty keys (T2 step 8). ✓
- Protected surface byte-unchanged → Global Constraints; no task touches core/events/substrate/AsciiTrieRenderer. ✓

**2. Placeholder scan:** No TBD/TODO; every code step shows full code; commands have expected output. ✓

**3. Type consistency:** `Comparison(int, int, List<String>, TrieSnapshot, TrieSnapshot)` defined in Task 1 and consumed by `render`/`banner`/`panel` in Task 2 with matching accessor names (`standardNodes`, `radixNodes`, `keys`, `saved`, `standardSnapshot`, `radixSnapshot`). `render(Comparison)` / `render(Comparison, int)` consistent across Tasks 2-4. `renderTrie(TrieSnapshot, String)` matches the existing `AsciiTrieRenderer` signature (null highlight). ✓
