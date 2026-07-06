package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapWebExporterLiveTest {

    @Test
    void liveHtmlHasNoBakedFramesAndOpensAnEventSource() {
        String html = MapWebExporter.liveHtml();
        assertTrue(html.toLowerCase().contains("<!doctype html"), "full document");
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
}
