package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.Color;

/** Renders a tree node's key with its red-black colour, in ANSI colour or plain text. */
public final class Palette {
    public enum Mode { PLAIN, ANSI }

    private static final String RED = "\u001b[31m";
    private static final String RESET = "\u001b[0m";

    private final Mode mode;

    public Palette(Mode mode) {
        this.mode = mode;
    }

    public Mode mode() {
        return mode;
    }

    /** A node label: {@code key(R)}/{@code key(B)} in PLAIN, or ANSI-red key in ANSI. */
    public String node(Object key, Color color) {
        if (mode == Mode.PLAIN) {
            return key + (color == Color.RED ? "(R)" : "(B)");
        }
        return color == Color.RED ? RED + key + RESET : String.valueOf(key);
    }

    /** ANSI when attached to a real terminal, PLAIN otherwise (pipes, capture, tests). */
    public static Palette auto() {
        return new Palette(System.console() != null ? Mode.ANSI : Mode.PLAIN);
    }
}
