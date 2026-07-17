package com.gimlism.translucent.substrate.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ColorModeTest {
    @Test
    void detectPicksPlainWhenNoConsole() {
        // tests run without a TTY (System.console() == null), so detect() must be PLAIN
        assertEquals(ColorMode.PLAIN, ColorMode.detect());
    }

    @Test
    void decideModeAnsiOnlyWhenConsoleIsARealTerminal() {
        assertEquals(ColorMode.ANSI, ColorMode.decideMode(null, true, true));
        // JDK 22+: System.console() is non-null even when stdout is redirected;
        // isTerminal() is false there, so we must stay PLAIN.
        assertEquals(ColorMode.PLAIN, ColorMode.decideMode(null, true, false));
        assertEquals(ColorMode.PLAIN, ColorMode.decideMode(null, false, false));
    }

    @Test
    void decideModeHonoursNoColor() {
        // https://no-color.org : any non-empty value disables colour, even on a terminal
        assertEquals(ColorMode.PLAIN, ColorMode.decideMode("1", true, true));
        // an empty value counts as "not set", so colour stays enabled on a terminal
        assertEquals(ColorMode.ANSI, ColorMode.decideMode("", true, true));
    }
}
