package com.gimlism.translucent.trie.viz;

import com.gimlism.translucent.substrate.viz.WebVizTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assembles a self-contained trie HTML page from {@code /web/trie-viz.html} by delegating token
 * substitution to {@link WebVizTemplate}: a baked {@code FRAMES} JSON blob (from
 * {@link TrieJsonSerializer}), the {@code LIVE} and {@code CONTROLS} mode flags, and the
 * {@code STRUCTURE} name shown in the page title and heading. The template is shared by both trie
 * implementations — the event stream is typed on {@code TrieEvent}, not on the trie that emitted it —
 * so every entry point takes the structure name explicitly and none defaults: a shared exporter
 * quietly labelling every page with one implementation is precisely the ambiguity this page set
 * removes.
 */
public final class TrieWebExporter {

    private static final String TEMPLATE_RESOURCE = "/web/trie-viz.html";

    private TrieWebExporter() {}

    /** The self-contained baked HTML with {@code framesJson} injected; live + controls OFF. */
    public static String toHtml(String framesJson, String structure) {
        return WebVizTemplate.injectNamed(TEMPLATE_RESOURCE, framesJson, "false", "false", structure);
    }

    /** Live mode (SSE), no browser controls: {@code DATA = null}, live ON, controls OFF. */
    public static String liveHtml(String structure) {
        return WebVizTemplate.injectNamed(TEMPLATE_RESOURCE, "null", "true", "false", structure);
    }

    /** Live mode with browser command controls: {@code DATA = null}, live ON, controls ON. */
    public static String controlsHtml(String structure) {
        return WebVizTemplate.injectNamed(TEMPLATE_RESOURCE, "null", "true", "true", structure);
    }

    /** Write {@link #toHtml(String, String)} to {@code out} (UTF-8). */
    public static void writeHtml(String framesJson, String structure, Path out) throws IOException {
        Files.writeString(out, toHtml(framesJson, structure));
    }
}
