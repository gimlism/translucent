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

    private static String firstLineWith(String out, String token) {
        return out.lines().filter(l -> l.contains(token)).findFirst().orElseThrow();
    }

    @Test
    void bannerLeadsWithKeysCountsAndSavings() {
        String out = renderer.render(CompressionCompareDemo.compare(CANON));
        assertTrue(out.startsWith(
            "compression compare: {she, shell, shore, shy}\n"
            + "  standard = 10   radix = 6   saved = 4 (40%)\n"
            + "  · = collapsed by radix (4 nodes)\n"),
            "banner mismatch, got:\n" + out);
    }

    @Test
    void columnsInterleaveBothTreesWithSavings() {
        String out = renderer.render(CompressionCompareDemo.compare(CANON));
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
    }

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

    @Test
    void emptyKeySetRendersTwoRootOnlyPanels() {
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
    }

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

    @Test
    void markedCountEqualsSaved() {
        var c = CompressionCompareDemo.compare(CANON);
        String out = renderer.render(c);
        // Every absorbed node's line begins with the marker at column 0 (the legend line begins with a
        // space, so it is excluded). The count of marked lines is exactly the nodes radix saves.
        long marked = out.lines().filter(l -> l.startsWith("·")).count();
        assertEquals(c.saved(), marked, "marked-node count must equal saved():\n" + out);

        // A second, independent shape: "abc" -> root,a,b,c (standard 4) vs root,"abc" (radix 2);
        // "a" and "b" are non-key single-child, so 2 marked == saved 2.
        var single = CompressionCompareDemo.compare(List.of("abc"));
        String singleOut = renderer.render(single);
        assertEquals(single.saved(), singleOut.lines().filter(l -> l.startsWith("·")).count(),
            "marked-node count must equal saved() for {abc}:\n" + singleOut);
    }

    @Test
    void radixPanelIsNeverMarked() {
        var c = CompressionCompareDemo.compare(CANON);
        String out = renderer.render(c);
        List<String> lines = out.lines().toList();
        int headerIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("radix (6)")) { headerIdx = i; break; }
        }
        assertTrue(headerIdx >= 0, "no columns header found:\n" + out);
        int gutter = lines.get(headerIdx).indexOf("radix (6)");
        // Slice the radix column (header down) at the gutter: it must carry no marker.
        boolean rightHasMarker = lines.subList(headerIdx, headerIdx + c.radixNodes() + 1).stream()
            .anyMatch(l -> l.substring(gutter).contains("·"));
        assertTrue(!rightHasMarker, "radix panel must not be marked:\n" + out);
    }

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
}
