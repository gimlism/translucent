package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.events.Color;
import org.junit.jupiter.api.Test;

class PaletteTest {
    @Test
    void plainModeAnnotatesWithMarkersNoEscapes() {
        var p = new Palette(Palette.Mode.PLAIN);
        assertEquals("16(B)", p.node(16, Color.BLACK));
        assertEquals("8(R)", p.node(8, Color.RED));
        assertFalse(p.node(8, Color.RED).contains("\u001b"), "plain mode has no ANSI escapes");
    }

    @Test
    void ansiModeWrapsRedAndLeavesBlackPlain() {
        var p = new Palette(Palette.Mode.ANSI);
        assertEquals("\u001b[31m8\u001b[0m", p.node(8, Color.RED));
        assertEquals("16", p.node(16, Color.BLACK));
    }

    @Test
    void autoFactoryPicksPlainWhenNoConsole() {
        // tests run without a TTY (System.console() == null), so auto() must be PLAIN
        assertEquals(Palette.Mode.PLAIN, Palette.auto().mode());
    }

    @Test
    void decideModeAnsiOnlyWhenConsoleIsARealTerminal() {
        assertEquals(Palette.Mode.ANSI, Palette.decideMode(null, true, true));
        // JDK 22+: System.console() is non-null even when stdout is redirected;
        // isTerminal() is false there, so we must stay PLAIN.
        assertEquals(Palette.Mode.PLAIN, Palette.decideMode(null, true, false));
        assertEquals(Palette.Mode.PLAIN, Palette.decideMode(null, false, false));
    }

    @Test
    void decideModeHonoursNoColor() {
        // https://no-color.org : any non-empty value disables colour, even on a terminal
        assertEquals(Palette.Mode.PLAIN, Palette.decideMode("1", true, true));
        // an empty value counts as "not set", so colour stays enabled on a terminal
        assertEquals(Palette.Mode.ANSI, Palette.decideMode("", true, true));
    }
}
