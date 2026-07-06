package com.gimlism.translucent.hashmap.viz;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assembles the self-contained HTML replay by injecting a serialized {@code frames} JSON blob
 * (from {@link MapJsonSerializer}) into the {@code /web/map-viz.html} template at its single
 * {@code /*__FRAMES__*}{@code /} token. The template carries the whole vanilla-JS/SVG renderer;
 * this class only substitutes the data, so the output is one shareable file with no external
 * assets.
 */
public final class MapWebExporter {

    private static final String TEMPLATE_RESOURCE = "/web/map-viz.html";
    private static final String TOKEN = "/*__FRAMES__*/";

    private MapWebExporter() {}

    /** The self-contained HTML document with {@code framesJson} injected at the template token. */
    public static String toHtml(String framesJson) {
        String template = readTemplate();
        if (!template.contains(TOKEN)) {
            throw new IllegalStateException("template " + TEMPLATE_RESOURCE + " is missing token " + TOKEN);
        }
        return template.replace(TOKEN, framesJson);
    }

    /** Write {@link #toHtml(String)} to {@code out} (UTF-8). */
    public static void writeHtml(String framesJson, Path out) throws IOException {
        Files.writeString(out, toHtml(framesJson));
    }

    private static String readTemplate() {
        try (InputStream in = MapWebExporter.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("template resource not found on classpath: " + TEMPLATE_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
