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
    // JS-comment-shaped despite landing in markup (<title>/<h1>), not <script>: <title> is an
    // RCDATA element, where an HTML comment is not parsed as one and would render literally in
    // the browser tab. See injectNamed's Javadoc.
    private static final String STRUCTURE_TOKEN = "/*__STRUCTURE__*/";

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
     * As {@link #inject}, plus a {@code STRUCTURE} token naming the structure the page depicts, for a
     * template shared by more than one implementation. Substitutes STRUCTURE, LIVE and CONTROLS —
     * all fixed, caller-chosen strings — and the user-controlled {@code FRAMES} blob LAST, preserving
     * the ordering invariant this class exists to centralise.
     *
     * <p>Deliberately a distinct name rather than a five-argument overload of {@link #inject}: two
     * same-typed positional overloads differing only in arity is a call-site trap.
     *
     * <p>The STRUCTURE token is JS-comment-shaped ({@code /*__STRUCTURE__*}{@code /}) like FRAMES,
     * LIVE and CONTROLS, even though it substitutes into markup ({@code <title>}/{@code <h1>}) rather
     * than a {@code <script>} block, unlike the HTML-comment token convention used elsewhere for
     * markup (e.g. {@code SiteIndex.PAGES_TOKEN}). This is deliberate, not an inconsistency to
     * "fix": {@code <title>} is an RCDATA element, so an HTML comment placed inside it is not parsed
     * as a comment and would render literally in the browser tab. Changing this token to
     * {@code <!--__STRUCTURE__-->} would silently break the page title.
     *
     * <p><b>Only {@code /web/trie-viz.html} carries the STRUCTURE token.</b> The map, list, treeset
     * and compression-compare templates do not, because each depicts exactly one implementation. A
     * later "unification" of {@link #inject} and this method would therefore make {@code require()}
     * throw for those four pages.
     *
     * <p><b>Contract: {@code structureReplacement} must be a caller-controlled constant</b> — like
     * {@code liveReplacement} and {@code controlsReplacement} — never user input or anything derived
     * from it. The only user-controlled channel here is {@code framesReplacement}, substituted LAST
     * for exactly that reason, and its contents are escaped by {@link JsonWriter}. A non-constant
     * structure name could itself carry a template token (e.g. literally {@code /*__FRAMES__*}
     * {@code /}) and have a later substitution injected into it, landing inside {@code <title>} —
     * something no amount of HTML-escaping of {@code structureReplacement} would prevent, since
     * escaping {@code &<>} does not touch a token made of {@code /}, {@code *}, and word characters.
     * Escaping this parameter would therefore make the API look safe for untrusted input while
     * leaving that opening in place; all production call sites already pass compile-time string
     * literals, which is what this contract requires.
     */
    public static String injectNamed(String resource, String framesReplacement,
            String liveReplacement, String controlsReplacement, String structureReplacement) {
        String template = read(resource);
        require(template, resource, FRAMES_TOKEN);
        require(template, resource, LIVE_TOKEN);
        require(template, resource, CONTROLS_TOKEN);
        require(template, resource, STRUCTURE_TOKEN);
        return template
                .replace(STRUCTURE_TOKEN, structureReplacement)
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
