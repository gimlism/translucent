package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.gimlism.translucent.substrate.rbtree.Color;
import com.gimlism.translucent.substrate.viz.ColorMode;
import org.junit.jupiter.api.Test;

class PaletteTest {
    @Test
    void plainModeAnnotatesWithMarkersNoEscapes() {
        var p = new Palette(ColorMode.PLAIN);
        assertEquals("16(B)", p.node(16, Color.BLACK));
        assertEquals("8(R)", p.node(8, Color.RED));
        assertFalse(p.node(8, Color.RED).contains("\u001b"), "plain mode has no ANSI escapes");
    }

    @Test
    void ansiModeWrapsRedAndLeavesBlackPlain() {
        var p = new Palette(ColorMode.ANSI);
        assertEquals("\u001b[31m8\u001b[0m", p.node(8, Color.RED));
        assertEquals("16", p.node(16, Color.BLACK));
    }

    @Test
    void autoFactoryPicksPlainWhenNoConsole() {
        // tests run without a TTY (System.console() == null), so auto() must be PLAIN
        assertEquals(ColorMode.PLAIN, Palette.auto().mode());
    }
}
