package com.gimlism.translucent.substrate.viz;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Shared assembler for the self-contained live-viz HTML pages. Reads a classpath template and
 * substitutes the three tokens every structure's page carries: a user-controlled {@code FRAMES}
 * data blob and two fixed mode flags {@code LIVE} and {@code CONTROLS}.
 *
 * <p>The fixed flags are replaced FIRST and the user-controlled {@code FRAMES} blob LAST, so a
 * frame whose serialized data happens to contain a token literal can never be rewritten by a
 * later substitution. Centralizing the order here makes it the single source of truth for every
 * per-structure exporter (map/list/trie), and is the fix for the earlier map-side ordering bug.
 */
public final class WebVizTemplate {

    private static final String FRAMES_TOKEN = "/*__FRAMES__*/";
    private static final String LIVE_TOKEN = "/*__LIVE__*/";
    private static final String CONTROLS_TOKEN = "/*__CONTROLS__*/";
    private static final String DATA_TOKEN = "/*__DATA__*/";

    private WebVizTemplate() {}

    /**
     * Read {@code resource}, require all three tokens, and substitute them — fixed {@code LIVE}
     * and {@code CONTROLS} first, user-controlled {@code FRAMES} last.
     *
     * @param resource absolute classpath path, e.g. {@code /web/list-viz.html}
     */
    public static String inject(String resource, String framesReplacement,
            String liveReplacement, String controlsReplacement) {
        String template = read(resource);
        require(template, resource, FRAMES_TOKEN);
        require(template, resource, LIVE_TOKEN);
        require(template, resource, CONTROLS_TOKEN);
        return template
                .replace(LIVE_TOKEN, liveReplacement)
                .replace(CONTROLS_TOKEN, controlsReplacement)
                .replace(FRAMES_TOKEN, framesReplacement); // user data LAST
    }

    /**
     * Read {@code resource} and substitute the single {@code DATA} token with {@code data}, for a
     * static page that carries one baked data blob and no frame-replay flags. The three-token
     * {@link #inject} stays the right tool for live (FRAMES/LIVE/CONTROLS) pages.
     */
    public static String injectStatic(String resource, String data) {
        return injectToken(resource, DATA_TOKEN, data);
    }

    /**
     * Read {@code resource}, require {@code token}, and substitute it. The single-token workhorse
     * behind {@link #injectStatic}, exposed for pages that are not visualisations: the site index
     * substitutes an HTML comment token because its replacement lands in markup rather than inside
     * a {@code <script>}.
     *
     * <p>Deliberately single-token. {@link #inject} stays separate because ORDER is its invariant —
     * user data substituted last so a frame containing a token literal cannot be rewritten — and a
     * general n-token helper could not enforce that.
     */
    public static String injectToken(String resource, String token, String replacement) {
        String template = read(resource);
        require(template, resource, token);
        return template.replace(token, replacement);
    }

    private static void require(String template, String resource, String token) {
        if (!template.contains(token)) {
            throw new IllegalStateException("template " + resource + " is missing token " + token);
        }
    }

    private static String read(String resource) {
        try (InputStream in = WebVizTemplate.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("template resource not found on classpath: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
