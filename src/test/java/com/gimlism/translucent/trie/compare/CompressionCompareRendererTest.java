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
}
