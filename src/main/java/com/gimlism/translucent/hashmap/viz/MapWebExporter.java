package com.gimlism.translucent.hashmap.viz;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assembles the self-contained HTML replay by injecting a serialized {@code frames} JSON blob
 * (from {@link MapJsonSerializer}) into the {@code /web/map-viz.html} template at its
 * {@code /*__FRAMES__*}{@code /} token, plus a {@code /*__LIVE__*}{@code /} flag token that
 * selects baked replay vs. live (SSE) mode. The template carries the whole vanilla-JS/SVG
 * renderer; this class only substitutes the data, so the output is one shareable file with no
 * external assets.
 */
public final class MapWebExporter {

    private static final String TEMPLATE_RESOURCE = "/web/map-viz.html";
    private static final String FRAMES_TOKEN = "/*__FRAMES__*/";
    private static final String LIVE_TOKEN = "/*__LIVE__*/";
    private static final String CONTROLS_TOKEN = "/*__CONTROLS__*/";

    private MapWebExporter() {}

    /** The self-contained baked HTML with {@code framesJson} injected; live + controls OFF. */
    public static String toHtml(String framesJson) {
        return inject(framesJson, "false", "false");
    }

    /** Live mode (SSE), no browser controls: {@code DATA = null}, live ON, controls OFF. */
    public static String liveHtml() {
        return inject("null", "true", "false");
    }

    /** Live mode with browser command controls: {@code DATA = null}, live ON, controls ON. */
    public static String controlsHtml() {
        return inject("null", "true", "true");
    }

    /** Write {@link #toHtml(String)} to {@code out} (UTF-8). */
    public static void writeHtml(String framesJson, Path out) throws IOException {
        Files.writeString(out, toHtml(framesJson));
    }

    private static String inject(String framesReplacement, String liveReplacement, String controlsReplacement) {
        String template = readTemplate();
        requireToken(template, FRAMES_TOKEN);
        requireToken(template, LIVE_TOKEN);
        requireToken(template, CONTROLS_TOKEN);
        return template
                .replace(FRAMES_TOKEN, framesReplacement)
                .replace(LIVE_TOKEN, liveReplacement)
                .replace(CONTROLS_TOKEN, controlsReplacement);
    }

    private static void requireToken(String template, String token) {
        if (!template.contains(token)) {
            throw new IllegalStateException("template " + TEMPLATE_RESOURCE + " is missing token " + token);
        }
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
