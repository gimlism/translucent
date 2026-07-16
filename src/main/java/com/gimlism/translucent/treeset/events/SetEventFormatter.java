package com.gimlism.translucent.treeset.events;

/** Human-readable one-line captions for {@link SetEvent}s (mirrors MapEventFormatter). */
public final class SetEventFormatter {
    private SetEventFormatter() { }

    public static String format(SetEvent e) {
        return switch (e) {
            case Compare c -> c.found()
                    ? "compare " + c.element() + " -> found"
                    : "compare " + c.element() + " -> go " + c.went();
            case Add a -> "add " + a.element();
            case Remove r -> "remove " + r.element();
            case Rotation r -> "rotate " + r.dir() + " about " + r.pivot();
            case Recolor r -> "recolor " + r.element() + " " + r.oldColor() + " -> " + r.newColor();
        };
    }
}
