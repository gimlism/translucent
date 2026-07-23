# Compression-compare collapse-marker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mark the fat `StandardTrie` panel's absorbed nodes (the ones radix collapses) with a `·` gutter glyph and a legend line, so the compression-compare view shows *which* nodes disappear.

**Architecture:** `CompressionCompareRenderer` already builds each panel from `AsciiTrieRenderer.renderTrie(snap, null)` (one line per node, pre-order DFS). This slice adds a parallel pre-order walk that flags the non-root non-key single-child nodes, then rewrites those lines' 2-char gutter from `"  "` to `"· "` (width-preserving, so columns/stacked/gutter math are untouched) and appends a legend banner line. `AsciiTrieRenderer` stays byte-unchanged.

**Tech Stack:** Java 21, Maven, JUnit 5.

## Global Constraints

- Java 21; build/test with `mvn` (the source of truth — Eclipse/LSP phantom errors are a known repo gotcha; ignore them).
- **Protected surface — must stay BYTE-UNCHANGED:** all of `trie/core` (incl. `StandardTrie`, `RadixTrie`), `trie/events`, `substrate/**`, and `trie/viz/AsciiTrieRenderer.java`. This slice touches ONLY `trie/compare/CompressionCompareRenderer.java` and `trie/compare/CompressionCompareRendererTest.java`.
- Marker glyph is `·` (U+00B7 middle dot); it replaces the first space of the 2-char gutter, so `"  "` → `"· "` and **line width is unchanged**.
- Only the **standard** (left) panel is marked; the radix panel is never marked.
- **Invariant:** the number of marked nodes equals `Comparison.saved()` (standard total − radix total). The legend count is derived from the walk, not hardcoded.
- Canonical `{she,shell,shore,shy}`: standard 10, radix 6, saved 4; the four absorbed nodes are `"s"`, the inner `"l"` (under key `"she"`), `"o"`, `"r"`.
- Every commit message ends with these two trailer lines (append to each commit; shown once here):
  ```
  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01MSszF69NvJChF7ufjSxapm
  ```
- Full suite must stay green: `mvn -q test`.

## File Structure

- `trie/compare/CompressionCompareRenderer.java` — **modify**: add `absorbedFlags`/`collectAbsorbed` walk, `markAbsorbed`, legend banner line; thread the absorbed count through. Reuse `AsciiTrieRenderer` unchanged.
- `trie/compare/CompressionCompareRendererTest.java` — **modify**: update banner + columns goldens (marking changes the output by design), update the empty-keys test, add three discriminating tests.

The current renderer and test are the slice-1 versions (PR #42). Reference points in `CompressionCompareRenderer.java`: `render(Comparison, int)` at lines 28-34; `banner(...)` at 36-42; `panel(...)` at 44-54; `width`/`columns`/`stacked` at 56-91.

---

### Task 1: Mark absorbed nodes + legend in the renderer

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/trie/compare/CompressionCompareRenderer.java`
- Test: `src/test/java/com/gimlism/translucent/trie/compare/CompressionCompareRendererTest.java` (update `bannerLeadsWithKeysCountsAndSavings` and `columnsInterleaveBothTreesWithSavings`)

**Interfaces:**
- Consumes: `CompressionCompareDemo.Comparison` (`standardSnapshot()`, `radixSnapshot()`, `standardNodes()`, `radixNodes()`, `saved()`, `keys()`); `AsciiTrieRenderer.renderTrie(TrieSnapshot, String)`; `TrieSnapshot.root()`; `TrieNodeSnapshot(boolean key, Object value, List<TrieEdge> children)` with `key()`/`children()`; `TrieEdge(String label, TrieNodeSnapshot target)` with `target()`.
- Produces: `render(Comparison)` / `render(Comparison, int)` output now marks absorbed standard-panel nodes with a `"· "` gutter and includes a legend banner line `  · = collapsed by radix (N nodes)`.

- [ ] **Step 1: Update the two existing goldens to the marked-expected output (RED)**

In `CompressionCompareRendererTest.java`, replace the `bannerLeadsWithKeysCountsAndSavings` body's `assertTrue` prefix (lines 21-24) so it also expects the legend line:

```java
        assertTrue(out.startsWith(
            "compression compare: {she, shell, shore, shy}\n"
            + "  standard = 10   radix = 6   saved = 4 (40%)\n"
            + "  · = collapsed by radix (4 nodes)\n"),
            "banner mismatch, got:\n" + out);
```

Replace the `columnsInterleaveBothTreesWithSavings` expected block (lines 30-45) with the marked golden:

```java
        assertEquals(String.join("\n",
            "compression compare: {she, shell, shore, shy}",
            "standard = 10 radix = 6 saved = 4 (40%)",
            "· = collapsed by radix (4 nodes)",
            "",
            "standard (10) radix (6)",
            "(root) (root)",
            "· \"s\" \"sh\"",
            "\"h\" \"e\" ●=0",
            "\"e\" ●=0 \"ll\" ●=1",
            "· \"l\" \"ore\" ●=2",
            "\"l\" ●=1 \"y\" ●=3",
            "· \"o\"",
            "· \"r\"",
            "\"e\" ●=2",
            "\"y\" ●=3"),
            normalize(out));
```

- [ ] **Step 2: Run the two tests to verify they fail**

Run: `mvn -q test -Dtest=CompressionCompareRendererTest#bannerLeadsWithKeysCountsAndSavings+columnsInterleaveBothTreesWithSavings`
Expected: FAIL — the current renderer emits neither the legend line nor the `·` markers.

- [ ] **Step 3: Implement marking + legend in the renderer**

In `CompressionCompareRenderer.java`, add two imports below the existing `TrieSnapshot` import (line 3):

```java
import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
```

Replace the `render(Comparison, int)` method (lines 28-34) with:

```java
    /** Render, falling back from columns to stacked when the two panels would exceed {@code maxWidth}. */
    public String render(CompressionCompareDemo.Comparison c, int maxWidth) {
        List<Boolean> absorbed = absorbedFlags(c.standardSnapshot());
        List<String> left = panel("standard (" + c.standardNodes() + ")", c.standardSnapshot());
        markAbsorbed(left, absorbed);
        List<String> right = panel("radix (" + c.radixNodes() + ")", c.radixSnapshot());
        String body = gutter(left) + width(right) <= maxWidth ? columns(left, right) : stacked(left, right);
        return banner(c, countTrue(absorbed)) + "\n\n" + body;
    }
```

Replace the `banner(Comparison)` method (lines 36-42) with a version that takes the absorbed count and adds the legend line:

```java
    private String banner(CompressionCompareDemo.Comparison c, int absorbedCount) {
        long pct = Math.round(100.0 * c.saved() / c.standardNodes());
        return "compression compare: {" + String.join(", ", c.keys()) + "}\n"
            + "  standard = " + c.standardNodes()
            + "   radix = " + c.radixNodes()
            + "   saved = " + c.saved() + " (" + pct + "%)\n"
            + "  · = collapsed by radix (" + absorbedCount + " nodes)";
    }
```

Add these four private helpers (place them after `panel(...)`, before `gutter(...)`):

```java
    /**
     * One flag per node in the standard trie, in the same pre-order {@code renderTrie} emits its lines
     * (root first, then children in {@code children()} order): {@code true} for a node radix absorbs —
     * a non-root, non-key node with exactly one child (part of a collapsible chain).
     */
    private List<Boolean> absorbedFlags(TrieSnapshot snap) {
        List<Boolean> flags = new ArrayList<>();
        collectAbsorbed(snap.root(), true, flags);
        return flags;
    }

    private void collectAbsorbed(TrieNodeSnapshot node, boolean root, List<Boolean> flags) {
        flags.add(!root && !node.key() && node.children().size() == 1);
        for (TrieEdge e : node.children()) collectAbsorbed(e.target(), false, flags);
    }

    /**
     * Swap the {@code "  "} gutter of each absorbed node's line for {@code "· "} (width unchanged, so
     * layout is unaffected). {@code panel.get(0)} is the header; tree line {@code i} is {@code panel.get(i+1)}
     * and maps to {@code flags.get(i)} because {@code renderTrie} emits one line per node in walk order.
     */
    private void markAbsorbed(List<String> panel, List<Boolean> flags) {
        for (int i = 0; i < flags.size(); i++) {
            if (flags.get(i)) {
                panel.set(i + 1, "· " + panel.get(i + 1).substring(2));
            }
        }
    }

    private int countTrue(List<Boolean> flags) {
        int n = 0;
        for (boolean b : flags) if (b) n++;
        return n;
    }
```

Update the class Javadoc (lines 8-17): append one sentence before the closing `*/` describing the marker, e.g.:

```
 * Nodes the radix trie absorbs (non-root, non-key, single-child) are marked in the standard panel
 * with a {@code ·} gutter glyph and counted in a legend line; that count equals {@code saved()}.
```

- [ ] **Step 4: Run the two updated tests to verify they pass**

Run: `mvn -q test -Dtest=CompressionCompareRendererTest#bannerLeadsWithKeysCountsAndSavings+columnsInterleaveBothTreesWithSavings`
Expected: PASS. (If the columns golden fails, print the actual `normalize(out)` and check it against the expected block above before editing anything — the marked node set is pinned by the design; a mismatch is a renderer bug, not a bad golden.)

- [ ] **Step 5: Run the whole renderer test class (confirm the unchanged tests still pass)**

Run: `mvn -q test -Dtest=CompressionCompareRendererTest`
Expected: PASS for all 5 current tests. `everyRightColumnLineStartsAtTheSameGutter` and `narrowMaxWidthFallsBackToStacked` must still pass unchanged — marking preserves line width (so the gutter is identical) and the radix panel is unmarked. `emptyKeySetRendersTwoRootOnlyPanels` still passes (its assertions don't touch the legend yet; strengthened in Task 2).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/compare/CompressionCompareRenderer.java \
        src/test/java/com/gimlism/translucent/trie/compare/CompressionCompareRendererTest.java
git commit -m "feat(trie): mark radix-absorbed nodes in the compression-compare view"
```

---

### Task 2: Discriminating coverage + runtime-verify

**Files:**
- Test: `src/test/java/com/gimlism/translucent/trie/compare/CompressionCompareRendererTest.java` (strengthen empty-keys test; add three tests)
- Verify (no code change): run the demo.

**Interfaces:**
- Consumes: the marking behavior from Task 1.

- [ ] **Step 1: Add a helper and strengthen the empty-keys test**

At the top of `CompressionCompareRendererTest.java`, add a private helper below `normalize`:

```java
    private static String firstLineWith(String out, String token) {
        return out.lines().filter(l -> l.contains(token)).findFirst().orElseThrow();
    }
```

Replace the `emptyKeySetRendersTwoRootOnlyPanels` body (lines 76-83) so it also pins the empty legend and that the root is never marked:

```java
        String out = renderer.render(CompressionCompareDemo.compare(List.of()));
        assertTrue(out.contains("saved = 0 (0%)"), "expected zero-savings banner:\n" + out);
        assertTrue(out.contains("· = collapsed by radix (0 nodes)"), "expected empty legend:\n" + out);
        // The root is never absorbed, so no root line is marked.
        assertTrue(out.lines().filter(l -> l.contains("(root)")).noneMatch(l -> l.startsWith("·")),
            "root must not be marked:\n" + out);
        // Both root-only panels appear (two "(root)" occurrences, on the single interleaved roots row).
        long roots = out.lines().flatMap(l -> {
            int c = (l.length() - l.replace("(root)", "").length()) / "(root)".length();
            return java.util.stream.IntStream.range(0, c).boxed();
        }).count();
        assertEquals(2, roots, "expected exactly two roots:\n" + out);
```

- [ ] **Step 2: Run the empty test to verify it passes**

Run: `mvn -q test -Dtest=CompressionCompareRendererTest#emptyKeySetRendersTwoRootOnlyPanels`
Expected: PASS.

- [ ] **Step 3: Add `markedCountEqualsSaved` (the invariant)**

Add to `CompressionCompareRendererTest.java`:

```java
    @Test
    void markedCountEqualsSaved() {
        var c = CompressionCompareDemo.compare(CANON);
        String out = renderer.render(c);
        // Every absorbed node's line begins with the marker at column 0 (the legend line begins with a
        // space, so it is excluded). The count of marked lines is exactly the nodes radix saves.
        long marked = out.lines().filter(l -> l.startsWith("·")).count();
        assertEquals(c.saved(), marked, "marked-node count must equal saved():\n" + out);
    }
```

- [ ] **Step 4: Add `radixPanelIsNeverMarked`**

Add:

```java
    @Test
    void radixPanelIsNeverMarked() {
        String out = renderer.render(CompressionCompareDemo.compare(CANON));
        List<String> lines = out.lines().toList();
        int headerIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("radix (6)")) { headerIdx = i; break; }
        }
        assertTrue(headerIdx >= 0, "no columns header found:\n" + out);
        int gutter = lines.get(headerIdx).indexOf("radix (6)");
        // Slice the radix column (header down) at the gutter: it must carry no marker.
        boolean rightHasMarker = lines.subList(headerIdx, headerIdx + 7).stream()
            .anyMatch(l -> l.substring(gutter).contains("·"));
        assertTrue(!rightHasMarker, "radix panel must not be marked:\n" + out);
    }
```

- [ ] **Step 5: Add `marksTheAbsorbedNodesNotTheKeptOnes`**

Add (uses stacked mode via `maxWidth=20` so the standard panel is isolated from the radix panel; `firstLineWith` returns the standard-panel line since it precedes the radix panel):

```java
    @Test
    void marksTheAbsorbedNodesNotTheKeptOnes() {
        // Stacked layout isolates the standard panel (it precedes the radix panel), so firstLineWith
        // finds the standard node line for each token.
        String out = renderer.render(CompressionCompareDemo.compare(CANON), 20);
        // Absorbed (non-key, single-child): "s", the inner "l", "o", "r" are marked.
        assertTrue(firstLineWith(out, "\"s\"").startsWith("·"), "\"s\" should be marked:\n" + out);
        assertTrue(firstLineWith(out, "\"o\"").startsWith("·"), "\"o\" should be marked:\n" + out);
        assertTrue(firstLineWith(out, "\"r\"").startsWith("·"), "\"r\" should be marked:\n" + out);
        // Kept nodes: root, the branch "h" (3 children), and the key "she" ("e" ●=0) are NOT marked.
        assertTrue(!firstLineWith(out, "(root)").startsWith("·"), "root should not be marked:\n" + out);
        assertTrue(!firstLineWith(out, "\"h\"").startsWith("·"), "branch \"h\" should not be marked:\n" + out);
        assertTrue(!firstLineWith(out, "\"e\" ●=0").startsWith("·"), "key \"she\" should not be marked:\n" + out);
    }
```

- [ ] **Step 6: Run the full renderer test class**

Run: `mvn -q test -Dtest=CompressionCompareRendererTest`
Expected: PASS (8 tests: 5 existing + 3 new).

- [ ] **Step 7: Confirm `markedCountEqualsSaved` is non-vacuous**

Temporarily weaken the predicate in `CompressionCompareRenderer.collectAbsorbed` — change `!root && !node.key() && node.children().size() == 1` to `false` — and run:

Run: `mvn -q test -Dtest=CompressionCompareRendererTest#markedCountEqualsSaved`
Expected: FAIL (`marked` becomes 0, `saved()` is 4). Then REVERT the predicate to `!root && !node.key() && node.children().size() == 1` exactly and re-run to green. Confirm `git diff src/main` is empty before continuing.

- [ ] **Step 8: Runtime-verify the demo and run the full suite**

Run:
```bash
mvn -q -DskipTests compile
mvn -q exec:java -Dexec.mainClass=com.gimlism.translucent.trie.compare.CompressionCompareDemo
```
Expected: the printed view shows the legend line `  · = collapsed by radix (4 nodes)` and the four `·` markers on the standard panel's `"s"`, inner `"l"`, `"o"`, `"r"` rows, with the radix panel unmarked. Paste the actual output into your report.

Run: `mvn -q test`
Expected: PASS — full suite green (510 prior + 3 new renderer tests = 513).

- [ ] **Step 9: Commit**

```bash
git add src/test/java/com/gimlism/translucent/trie/compare/CompressionCompareRendererTest.java
git commit -m "test(trie): pin collapse-marker (count==saved, radix unmarked, right nodes)"
```

---

## Self-Review

**1. Spec coverage:**
- Mark non-root non-key single-child standard nodes → Task 1 `collectAbsorbed`. ✓
- Only standard panel marked; radix unmarked → Task 1 (radix panel not passed to `markAbsorbed`); pinned by `radixPanelIsNeverMarked` (Task 2). ✓
- `·` gutter, width-preserving → Task 1 `markAbsorbed` (`"· " + substring(2)`). ✓
- Legend line `· = collapsed by radix (N nodes)`, N from the walk → Task 1 `banner(c, countTrue(absorbed))`. ✓
- Default-on (replaces slice-1 output) → Task 1 updates banner + columns goldens. ✓
- Marked-count ≡ saved invariant → `markedCountEqualsSaved` + non-vacuity (Task 2). ✓
- Empty keys → `(0 nodes)`, root unmarked → Task 2 empty test. ✓
- Right nodes marked / kept nodes not → `marksTheAbsorbedNodesNotTheKeptOnes` (Task 2). ✓
- Alignment/stacked unchanged → verified passing in Task 1 Step 5. ✓
- AsciiTrieRenderer + core/events/substrate byte-unchanged → Global Constraints; only `trie/compare` touched. ✓

**2. Placeholder scan:** No TBD/TODO; every code step shows full code; commands have expected output. ✓

**3. Type consistency:** `absorbedFlags(TrieSnapshot)→List<Boolean>`, `collectAbsorbed(TrieNodeSnapshot, boolean, List<Boolean>)`, `markAbsorbed(List<String>, List<Boolean>)`, `countTrue(List<Boolean>)→int`, `banner(Comparison, int)` — consistent across Task 1. Snapshot accessors (`root()`, `key()`, `children()`, `target()`) match the record signatures. `firstLineWith(String, String)→String` defined once in Task 2 Step 1 and used in Step 5. ✓
