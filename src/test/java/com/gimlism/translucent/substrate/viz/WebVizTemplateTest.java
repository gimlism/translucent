package com.gimlism.translucent.substrate.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebVizTemplateTest {

    private static final String T = "/web/webviztemplate-test.html";

    @Test
    void substitutesAllThreeTokens() {
        String out = WebVizTemplate.inject(T, "{\"frames\":[]}", "true", "false");
        assertTrue(out.contains("LIVE=true CONTROLS=false DATA={\"frames\":[]}"), out);
        assertFalse(out.contains("/*__"), "no token left behind");
    }

    @Test
    void missingTokenThrowsNamingTheToken() {
        var e = assertThrows(IllegalStateException.class,
                () -> WebVizTemplate.inject("/web/webviztemplate-missing.html", "x", "true", "true"));
        assertTrue(e.getMessage().contains("/*__CONTROLS__*/"), e.getMessage());
    }

    @Test
    void missingResourceThrows() {
        var e = assertThrows(IllegalStateException.class,
                () -> WebVizTemplate.inject("/web/does-not-exist.html", "x", "true", "true"));
        assertTrue(e.getMessage().contains("not found"), e.getMessage());
    }

    @Test
    void userFramesContainingATokenLiteralAreNotCorrupted() {
        // Issue #23 at the helper level: a frames blob literally containing the LIVE/CONTROLS
        // token must be injected verbatim because FRAMES is substituted LAST.
        String frames = "{\"e\":\"/*__LIVE__*/ and /*__CONTROLS__*/\"}";
        String out = WebVizTemplate.inject(T, frames, "true", "true");
        assertTrue(out.contains(frames), "user token literals survive intact");
        assertTrue(out.startsWith("LIVE=true CONTROLS=true "), "the real flag tokens are still replaced");
    }

    @Test
    void injectStaticSubstitutesTheDataToken() {
        String out = WebVizTemplate.injectStatic("/web/webviztemplate-static.html", "{\"x\":1}");
        assertTrue(out.contains("DATA={\"x\":1}"), out);
        assertFalse(out.contains("/*__"), "no token left behind");
    }

    @Test
    void injectStaticMissingDataTokenThrowsNamingIt() {
        // webviztemplate-missing.html has no /*__DATA__*/ token.
        var e = assertThrows(IllegalStateException.class,
                () -> WebVizTemplate.injectStatic("/web/webviztemplate-missing.html", "x"));
        assertTrue(e.getMessage().contains("/*__DATA__*/"), e.getMessage());
    }

    @Test
    void injectTokenSubstitutesAnArbitraryToken() {
        String out = WebVizTemplate.injectToken("/web/webviztemplate-static.html", "/*__DATA__*/", "9");
        assertTrue(out.contains("DATA=9"), out);
        assertFalse(out.contains("/*__"), "no token left behind");
    }

    @Test
    void injectTokenMissingTokenThrowsNamingIt() {
        var e = assertThrows(IllegalStateException.class,
                () -> WebVizTemplate.injectToken("/web/webviztemplate-static.html", "<!--__PAGES__-->", "x"));
        assertTrue(e.getMessage().contains("<!--__PAGES__-->"), e.getMessage());
    }

    /**
     * Guards output agreement, not delegation itself: for the same template and data, {@link
     * WebVizTemplate#injectStatic} and {@link WebVizTemplate#injectToken} on {@code DATA_TOKEN}
     * must produce byte-identical strings. If {@code injectToken}'s substitution semantics ever
     * change without {@code injectStatic} following, this goes red, because both calls are made
     * against real input here — so the two paths necessarily observe the change.
     *
     * <p>This does NOT prove {@code injectStatic} still calls {@code injectToken} under the hood.
     * A correct standalone reimplementation of {@code injectStatic} — one that duplicates the read
     * and substitute logic instead of delegating — would produce the same bytes for this
     * well-formed input and pass just as green; only {@link #injectStaticMissingDataTokenThrowsNamingIt}
     * pins the error-path behaviour (rejecting a missing token) that delegation is actually meant to
     * buy. A future reader must not treat this test as proof that delegation survives.
     */
    @Test
    void injectStaticIsInjectTokenOnTheDataToken() {
        String viaStatic = WebVizTemplate.injectStatic("/web/webviztemplate-static.html", "{\"x\":1}");
        String viaToken = WebVizTemplate.injectToken("/web/webviztemplate-static.html", "/*__DATA__*/", "{\"x\":1}");
        assertEquals(viaToken, viaStatic);
    }

    @Test
    void injectNamedSubstitutesTheStructureNameAndTheThreeStandardTokens() {
        String html = WebVizTemplate.injectNamed("/web/trie-viz.html", "{\"frames\":[]}",
                "false", "false", "StandardTrie");

        assertTrue(html.contains("<title>StandardTrie — replay</title>"), "the page title names the trie");
        assertTrue(html.contains("<h1>StandardTrie — web replay</h1>"), "the heading names the trie");
        assertFalse(html.contains("/*__"), "no template token may survive");
    }

    /**
     * The other direction. Only trie-viz.html carries a STRUCTURE token, so injectNamed must reject a
     * template that lacks one rather than returning a page silently missing its name — the same
     * contract inject() already enforces for FRAMES/LIVE/CONTROLS.
     */
    @Test
    void injectNamedRejectsATemplateWithNoStructureToken() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> WebVizTemplate.injectNamed("/web/map-viz.html", "{\"frames\":[]}",
                        "false", "false", "TeachingHashMap"));
        assertTrue(e.getMessage().contains("__STRUCTURE__"), e.getMessage());
    }
}
