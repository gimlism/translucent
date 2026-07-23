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
}
