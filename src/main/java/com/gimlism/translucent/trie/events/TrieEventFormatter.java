package com.gimlism.translucent.trie.events;

/**
 * Turns a {@link TrieEvent} into its canonical one-line human-readable label.
 *
 * <p>Lives in the {@code events} package so every presentation layer (the console
 * logger, a future renderer) shares one formatting of the event vocabulary.
 */
public final class TrieEventFormatter {
    private TrieEventFormatter() {}

    /** The human-readable line for an event. */
    public static String format(TrieEvent event) {
        return switch (event) {
            case Descend d -> "DESCEND \"" + d.label() + "\" -> \"" + d.path() + "\"";
            case CreateNode c -> "CREATE \"" + c.label() + "\" -> \"" + c.path() + "\"";
            case SplitEdge s -> "SPLIT \"" + s.originalLabel() + "\" @ \"" + s.commonPrefix()
                + "\" -> \"" + s.path() + "\"";
            case Put p -> p.newKey()
                ? "PUT \"" + p.key() + "\"=" + p.value() + " (new)"
                : "PUT \"" + p.key() + "\"=" + p.value() + " (replaced " + p.previousValue() + ")";
            case Remove r -> "REMOVE \"" + r.key() + "\" (was " + r.removedValue() + ")";
            case MergeEdge m -> "MERGE \"" + m.mergedLabel() + "\" -> \"" + m.path() + "\"";
            case Prune pr -> "PRUNE \"" + pr.label() + "\" <- \"" + pr.path() + "\"";
        };
    }
}
