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
     * The delegation is the point: one classpath reader, one missing-token message. If these two
     * ever diverge, the generic helper has grown a second behaviour and the five baked viz goldens
     * are no longer guaranteed byte-identical.
     */
    @Test
    void injectStaticIsInjectTokenOnTheDataToken() {
        String viaStatic = WebVizTemplate.injectStatic("/web/webviztemplate-static.html", "{\"x\":1}");
        String viaToken = WebVizTemplate.injectToken("/web/webviztemplate-static.html", "/*__DATA__*/", "{\"x\":1}");
        assertEquals(viaToken, viaStatic);
    }
}
