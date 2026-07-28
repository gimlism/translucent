package com.gimlism.translucent.trie.compare;

import com.gimlism.translucent.substrate.viz.WebVizTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Assembles the self-contained compression-compare HTML page from
 * {@code /web/compression-compare-viz.html} by injecting one baked {@code DATA} blob (from
 * {@link CompressionCompareJsonSerializer}) via {@link WebVizTemplate#injectStatic}. Static: no live
 * SSE, no controls — the {@code StandardTrie} and {@code RadixTrie} emit different event streams and
 * cannot animate in lockstep, so the page renders the two final trees once.
 */
public final class CompressionCompareWebExporter {

    private static final String TEMPLATE_RESOURCE = "/web/compression-compare-viz.html";

    private CompressionCompareWebExporter() {}

    /** The self-contained HTML with {@code dataJson} injected. */
    public static String toHtml(String dataJson) {
        return WebVizTemplate.injectStatic(TEMPLATE_RESOURCE, dataJson);
    }

    /** Write {@link #toHtml(String)} for {@code comparison} to {@code out} (UTF-8). */
    public static void writeHtml(CompressionCompareDemo.Comparison comparison, Path out) throws IOException {
        Files.writeString(out, toHtml(CompressionCompareJsonSerializer.toJson(comparison)));
    }

    /**
     * Write the canonical {@code {she, shell, shore, shy}} page to {@code args[0]} (default
     * {@code target/compression-compare.html}) for manual browser inspection.
     */
    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "target/compression-compare.html");
        writeHtml(CompressionCompareDemo.compare(List.of("she", "shell", "shore", "shy")), out);
        System.out.println("wrote " + out.toAbsolutePath());
    }
}
