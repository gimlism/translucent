package com.gimlism.translucent.treeset.viz;

import com.gimlism.translucent.substrate.viz.WebVizTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assembles the self-contained TeachingTreeSet HTML page from {@code /web/treeset-viz.html} by
 * delegating token substitution to {@link WebVizTemplate}: a baked {@code FRAMES} JSON blob (from
 * {@link TreeSetJsonSerializer}) plus the {@code LIVE} and {@code CONTROLS} mode flags — both {@code
 * false} for a static replay. The template carries the whole vanilla-JS/SVG renderer; this class only
 * chooses the three replacement values. Fourth consumer of {@link WebVizTemplate}, alongside the map,
 * list, and trie exporters. Slice A exposes only the static {@code toHtml}; {@code liveHtml}/{@code
 * controlsHtml} arrive with Slices B/D.
 */
public final class TreeSetWebExporter {

    private static final String TEMPLATE_RESOURCE = "/web/treeset-viz.html";

    private TreeSetWebExporter() {}

    /** The self-contained baked HTML with {@code framesJson} injected; live + controls OFF. */
    public static String toHtml(String framesJson) {
        return WebVizTemplate.inject(TEMPLATE_RESOURCE, framesJson, "false", "false");
    }

    /** Live mode (SSE), no browser controls: {@code DATA = null}, live ON, controls OFF. */
    public static String liveHtml() {
        return WebVizTemplate.inject(TEMPLATE_RESOURCE, "null", "true", "false");
    }

    /** Write {@link #toHtml(String)} to {@code out} (UTF-8). */
    public static void writeHtml(String framesJson, Path out) throws IOException {
        Files.writeString(out, toHtml(framesJson));
    }
}
