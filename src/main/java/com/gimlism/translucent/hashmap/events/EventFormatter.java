package com.gimlism.translucent.hashmap.events;

/**
 * Turns a {@link MapEvent} into its canonical one-line human-readable label.
 *
 * <p>Lives in the {@code events} package so every presentation layer
 * ({@code consumer}'s logger, {@code viz}'s renderer) can share one formatting
 * of the event vocabulary without depending on each other.
 */
public final class EventFormatter {
    private EventFormatter() {}

    /** The human-readable line for an event. */
    public static String format(MapEvent event) {
        return switch (event) {
            case Put p -> p.newEntry()
                ? "PUT " + p.key() + "=" + p.value() + " -> bucket " + p.bucketIndex() + " (new)"
                : "PUT " + p.key() + "=" + p.value() + " -> bucket " + p.bucketIndex()
                    + " (replaced " + p.previousValue() + ")";
            case Remove r -> "REMOVE " + r.key() + " -> bucket " + r.bucketIndex()
                + " (was " + r.removedValue() + ")";
            case Collision c -> "COLLISION " + c.key() + " -> bucket " + c.bucketIndex()
                + " (chain len " + c.chainLengthBefore() + " -> " + c.chainLengthAfter() + ")";
            case Resize rs -> "RESIZE " + rs.oldCapacity() + " -> " + rs.newCapacity();
            case Treeify t -> "TREEIFY bucket " + t.bucketIndex();
            case Untreeify u -> "UNTREEIFY bucket " + u.bucketIndex();
            case Rotation ro -> "ROTATE " + ro.direction() + " @ " + ro.pivotKey()
                + " (bucket " + ro.bucketIndex() + ")";
            case Recolor rc -> "RECOLOR " + rc.nodeKey() + " " + rc.oldColor()
                + " -> " + rc.newColor() + " (bucket " + rc.bucketIndex() + ")";
        };
    }
}
