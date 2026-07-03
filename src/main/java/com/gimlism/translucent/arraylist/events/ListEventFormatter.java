package com.gimlism.translucent.arraylist.events;

/**
 * Turns a {@link ListEvent} into its canonical one-line human-readable label.
 *
 * <p>Lives in the {@code events} package so every presentation layer (a console
 * logger, a future renderer) can share one formatting of the event vocabulary.
 */
public final class ListEventFormatter {
    private ListEventFormatter() {}

    /** The human-readable line for an event. */
    public static String format(ListEvent event) {
        return switch (event) {
            case Append a -> "APPEND " + a.element() + " @ " + a.index();
            case Insert in -> "INSERT " + in.element() + " @ " + in.index();
            case Set s -> "SET " + s.index() + " = " + s.element() + " (was " + s.previousElement() + ")";
            case RemoveAt r -> "REMOVE @ " + r.index() + " (was " + r.removedElement() + ")";
            case Shift sh -> "SHIFT " + sh.fromIndex() + " -> " + sh.toIndex() + " (" + sh.element() + ")";
            case Grow g -> "GROW cap " + g.oldCapacity() + " -> " + g.newCapacity();
        };
    }
}
