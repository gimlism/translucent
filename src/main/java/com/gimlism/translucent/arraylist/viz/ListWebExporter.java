package com.gimlism.translucent.arraylist.viz;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assembles the self-contained HTML replay by injecting a serialized {@code frames} JSON blob (from
 * {@link ListJsonSerializer}) into the {@code /web/list-viz.html} template at its
 * {@code /*__FRAMES__*}{@code /} token. The template carries the whole vanilla-JS/SVG renderer; this
 * class only substitutes the data, so the output is one shareable file with no external assets. Slice A
 * has a single token; the live/controls tokens arrive with later slices.
 */
public final class ListWebExporter {

    private static final String TEMPLATE_RESOURCE = "/web/list-viz.html";
    private static final String FRAMES_TOKEN = "/*__FRAMES__*/";

    private ListWebExporter() {}

    /** The self-contained baked HTML with {@code framesJson} injected. */
    public static String toHtml(String framesJson) {
        String template = readTemplate();
        requireToken(template, FRAMES_TOKEN);
        return template.replace(FRAMES_TOKEN, framesJson);
    }

    /** Write {@link #toHtml(String)} to {@code out} (UTF-8). */
    public static void writeHtml(String framesJson, Path out) throws IOException {
        Files.writeString(out, toHtml(framesJson));
    }

    private static void requireToken(String template, String token) {
        if (!template.contains(token)) {
            throw new IllegalStateException("template " + TEMPLATE_RESOURCE + " is missing token " + token);
        }
    }

    private static String readTemplate() {
        try (InputStream in = ListWebExporter.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("template resource not found on classpath: " + TEMPLATE_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
