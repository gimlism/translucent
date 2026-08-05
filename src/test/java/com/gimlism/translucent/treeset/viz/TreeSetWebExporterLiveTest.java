package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class TreeSetWebExporterLiveTest {

    @Test
    void liveHtmlHasNoBakedFramesAndOpensAnEventSource() {
        String html = TreeSetWebExporter.liveHtml();
        assertTrue(html.toLowerCase(Locale.ROOT).contains("<!doctype html"), "full document");
        assertFalse(html.contains("/*__FRAMES__*/"), "frames token replaced");
        assertFalse(html.contains("/*__LIVE__*/"), "live token replaced");
        assertTrue(html.contains("const DATA = null;"), "no baked frames in live mode");
        assertTrue(html.contains("const LIVE = true;"), "live mode on");
        assertTrue(html.contains("const CONTROLS = false;"), "controls stay off in slice B");
        assertTrue(html.contains("new EventSource(\"/events\")"), "opens the SSE stream");
    }

    @Test
    void bakedHtmlStillInjectsFramesAndTurnsLiveOff() {
        String html = TreeSetWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.contains("const DATA = {\"frames\":[]};"), "baked frames injected");
        assertTrue(html.contains("const LIVE = false;"), "live mode off for the baked file");
    }
}
