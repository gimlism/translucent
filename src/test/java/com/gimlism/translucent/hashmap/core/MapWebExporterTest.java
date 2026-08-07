package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.viz.MapWebExporter;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MapWebExporterTest {

    @Test
    void injectsJsonIntoASelfContainedDocument() {
        String html = MapWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.toLowerCase(Locale.ROOT).contains("<!doctype html"), "must be a full document");
        assertTrue(html.contains("</html>"), "must be a full document");
        assertTrue(html.contains("{\"frames\":[]}"), "must contain the injected JSON");
    }

    @Test
    void consumesTheInjectionToken() {
        String html = MapWebExporter.toHtml("{\"frames\":[]}");
        assertFalse(html.contains("/*__FRAMES__*/"), "the token must be replaced, not left behind");
    }

    @Test
    void isSelfContained_noExternalUrls() {
        String html = MapWebExporter.toHtml("{\"frames\":[]}");
        assertFalse(html.contains("http://"), "no external URLs");
        assertFalse(html.contains("https://"), "no external URLs");
        assertFalse(html.contains("src=\""), "no external script/img src");
    }
}
