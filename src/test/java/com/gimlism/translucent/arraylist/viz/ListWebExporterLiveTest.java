package com.gimlism.translucent.arraylist.viz;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class ListWebExporterLiveTest {

    @Test
    void liveHtmlHasNoBakedFramesAndOpensAnEventSource() {
        String html = ListWebExporter.liveHtml();
        assertTrue(html.toLowerCase(Locale.ROOT).contains("<!doctype html"), "full document");
        assertFalse(html.contains("/*__FRAMES__*/"), "frames token replaced");
        assertFalse(html.contains("/*__LIVE__*/"), "live token replaced");
        assertTrue(html.contains("const DATA = null;"), "no baked frames in live mode");
        assertTrue(html.contains("const LIVE = true;"), "live mode on");
        assertTrue(html.contains("new EventSource(\"/events\")"), "opens the SSE stream");
    }

    @Test
    void bakedHtmlInjectsFramesAndTurnsLiveOff() {
        String html = ListWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.contains("const DATA = {\"frames\":[]};"), "baked frames injected");
        assertTrue(html.contains("const LIVE = false;"), "live mode off for the baked file");
    }

    @Test
    void bakedFrameDataContainingTheLiveTokenIsNotCorrupted() {
        // A serialized frame can contain the literal token as user data (JsonWriter escapes < and
        // control chars, but not the /*__…__*/ shape). Token substitution must inject the user
        // frames LAST so it never rewrites their content.
        String frames = "{\"frames\":[{\"element\":\"/*__LIVE__*/\"}]}";
        String html = ListWebExporter.toHtml(frames);
        assertTrue(html.contains("\"element\":\"/*__LIVE__*/\""), "user data containing the LIVE token survives intact");
        assertTrue(html.contains("const LIVE = false;"), "the real LIVE token is still replaced");
    }
}
