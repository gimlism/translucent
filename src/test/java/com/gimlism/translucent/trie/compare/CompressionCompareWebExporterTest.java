package com.gimlism.translucent.trie.compare;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CompressionCompareWebExporterTest {
    private static String html() {
        return CompressionCompareWebExporter.toHtml(
            CompressionCompareJsonSerializer.toJson(
                CompressionCompareDemo.compare(List.of("she", "shell", "shore", "shy"))));
    }

    @Test
    void embedsTheComparisonData() {
        String out = html();
        assertTrue(out.contains("\"keys\":[\"she\",\"shell\",\"shore\",\"shy\"]"), out);
        assertTrue(out.contains("\"saved\":4"), out);
    }

    @Test
    void isSelfContainedStaticPageNoLiveOrControls() {
        String out = html();
        assertFalse(out.contains("EventSource"), "static page must not open an SSE stream");
        assertFalse(out.contains("/command"), "static page must not POST commands");
        assertFalse(out.contains("/*__"), "no template token left behind");
    }

    @Test
    void carriesTheGhostMarkerStyleAndLegend() {
        String out = html();
        assertTrue(out.contains(".node.ghost"), "ghost marker CSS must be present");
        assertTrue(out.contains("collapsed by radix"), "legend text must be present");
    }
}
