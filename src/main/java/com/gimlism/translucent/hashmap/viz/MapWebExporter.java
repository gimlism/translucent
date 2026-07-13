package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.substrate.viz.WebVizTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assembles the self-contained HashMap HTML page from {@code /web/map-viz.html} by delegating token
 * substitution to {@link WebVizTemplate}: a baked {@code FRAMES} JSON blob (from
 * {@link MapJsonSerializer}) plus the {@code LIVE} and {@code CONTROLS} mode flags. The template
 * carries the whole vanilla-JS/SVG renderer; this class only chooses the three replacement values.
 */
public final class MapWebExporter {

    private static final String TEMPLATE_RESOURCE = "/web/map-viz.html";

    private MapWebExporter() {}

    /** The self-contained baked HTML with {@code framesJson} injected; live + controls OFF. */
    public static String toHtml(String framesJson) {
        return WebVizTemplate.inject(TEMPLATE_RESOURCE, framesJson, "false", "false");
    }

    /** Live mode (SSE), no browser controls: {@code DATA = null}, live ON, controls OFF. */
    public static String liveHtml() {
        return WebVizTemplate.inject(TEMPLATE_RESOURCE, "null", "true", "false");
    }

    /** Live mode with browser command controls: {@code DATA = null}, live ON, controls ON. */
    public static String controlsHtml() {
        return WebVizTemplate.inject(TEMPLATE_RESOURCE, "null", "true", "true");
    }

    /** Write {@link #toHtml(String)} to {@code out} (UTF-8). */
    public static void writeHtml(String framesJson, Path out) throws IOException {
        Files.writeString(out, toHtml(framesJson));
    }
}
