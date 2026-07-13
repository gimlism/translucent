package com.gimlism.translucent.substrate.viz;

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
}
