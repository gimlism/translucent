package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapWebExporterControlsTest {

    @Test
    void controlsHtmlEnablesControlsAndLive() {
        String html = MapWebExporter.controlsHtml();
        assertTrue(html.contains("const CONTROLS = true;"), "controls flag on");
        assertTrue(html.contains("const LIVE = true;"), "live flag on");
        assertTrue(html.contains("const DATA = null;"), "no baked frames in live mode");
    }

    @Test
    void liveHtmlLeavesControlsOff() {
        String html = MapWebExporter.liveHtml();
        assertTrue(html.contains("const CONTROLS = false;"), "controls flag off in plain live mode");
        assertTrue(html.contains("const LIVE = true;"), "live flag still on");
    }

    @Test
    void bakedHtmlLeavesLiveAndControlsOff() {
        String html = MapWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.contains("const LIVE = false;"), "replay mode is not live");
        assertTrue(html.contains("const CONTROLS = false;"), "replay mode has no controls");
    }
}
