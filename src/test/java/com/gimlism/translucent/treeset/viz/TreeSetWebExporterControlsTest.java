package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TreeSetWebExporterControlsTest {

    @Test
    void controlsHtmlEnablesControlsAndLive() {
        String html = TreeSetWebExporter.controlsHtml();
        assertTrue(html.contains("const CONTROLS = true;"), "controls flag on");
        assertTrue(html.contains("const LIVE = true;"), "live flag on");
        assertTrue(html.contains("const DATA = null;"), "no baked frames in live mode");
    }

    @Test
    void liveHtmlLeavesControlsOff() {
        String html = TreeSetWebExporter.liveHtml();
        assertTrue(html.contains("const CONTROLS = false;"), "controls off in plain live mode");
        assertTrue(html.contains("const LIVE = true;"), "live flag still on");
    }

    @Test
    void bakedHtmlLeavesLiveAndControlsOff() {
        String html = TreeSetWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.contains("const LIVE = false;"), "replay mode is not live");
        assertTrue(html.contains("const CONTROLS = false;"), "replay mode has no controls");
    }

    @Test
    void templateCarriesTheCommandBoxAndPostTarget() {
        // The box HTML is static in the template (CONTROLS only unhides it), so any mode carries it;
        // this guards that the Slice D command-box edit actually landed in treeset-viz.html.
        String html = TreeSetWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.contains("id=\"cmd\""), "command input present in the template");
        assertTrue(html.contains("/command"), "POST /command wiring present in the template");
    }
}
