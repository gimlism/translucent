package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class MapWebExporterLiveTest {

    @Test
    void liveHtmlHasNoBakedFramesAndOpensAnEventSource() {
        String html = MapWebExporter.liveHtml();
        assertTrue(html.toLowerCase(Locale.ROOT).contains("<!doctype html"), "full document");
        assertFalse(html.contains("/*__FRAMES__*/"), "frames token replaced");
        assertFalse(html.contains("/*__LIVE__*/"), "live token replaced");
        assertTrue(html.contains("const DATA = null;"), "no baked frames in live mode");
        assertTrue(html.contains("const LIVE = true;"), "live mode on");
        assertTrue(html.contains("new EventSource(\"/events\")"), "opens the SSE stream");
    }

    @Test
    void bakedHtmlInjectsFramesAndTurnsLiveOff() {
        String html = MapWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.contains("const DATA = {\"frames\":[]};"), "baked frames injected");
        assertTrue(html.contains("const LIVE = false;"), "live mode off for the baked file");
    }

    @Test
    void bakedFrameDataContainingTheLiveTokenIsNotCorrupted() {
        // Issue #23: a serialized map key/value can contain the literal token as user data. Token
        // substitution must inject the user frames LAST (via the shared WebVizTemplate) so it never
        // rewrites their content.
        String frames = "{\"frames\":[{\"highlightKey\":\"/*__LIVE__*/\"}]}";
        String html = MapWebExporter.toHtml(frames);
        assertTrue(html.contains("\"highlightKey\":\"/*__LIVE__*/\""), "user data containing the LIVE token survives intact");
        assertTrue(html.contains("const LIVE = false;"), "the real LIVE token is still replaced");
    }
}
