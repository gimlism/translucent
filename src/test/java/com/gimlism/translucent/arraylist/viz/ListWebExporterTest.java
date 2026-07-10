package com.gimlism.translucent.arraylist.viz;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ListWebExporterTest {

    @Test
    void injectsFramesAndRemovesToken() {
        String marker = "{\"frames\":[{\"probe\":42}]}";
        String html = ListWebExporter.toHtml(marker);
        assertTrue(html.contains(marker), "injected JSON should appear verbatim");
        assertFalse(html.contains("/*__FRAMES__*/"), "token should be gone after injection");
    }

    @Test
    void outputIsSelfContainedHtml() {
        String html = ListWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.startsWith("<!doctype html>"), html.substring(0, Math.min(40, html.length())));
        assertTrue(html.contains("<svg"), "carries the inline SVG stage");
        assertFalse(html.contains("http://"), "no external asset references");
        assertFalse(html.contains("https://"), "no external asset references");
    }
}
