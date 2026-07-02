package com.gimlism.translucent.hashmap.consumer;

import com.gimlism.translucent.hashmap.events.Collision;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.MapEventListener;
import com.gimlism.translucent.hashmap.events.Put;
import com.gimlism.translucent.hashmap.events.Recolor;
import com.gimlism.translucent.hashmap.events.Remove;
import com.gimlism.translucent.hashmap.events.Resize;
import com.gimlism.translucent.hashmap.events.Rotation;
import com.gimlism.translucent.hashmap.events.Treeify;
import com.gimlism.translucent.hashmap.events.Untreeify;
import java.io.PrintStream;

/** Prints a human-readable line per event. Validates the stream end to end. */
public class ConsoleEventLogger implements MapEventListener {
    private final PrintStream out;

    public ConsoleEventLogger() {
        this(System.out);
    }

    public ConsoleEventLogger(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onEvent(MapEvent event) {
        out.println(format(event));
    }

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
