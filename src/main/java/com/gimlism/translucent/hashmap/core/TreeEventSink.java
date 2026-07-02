package com.gimlism.translucent.hashmap.core;

import com.gimlism.translucent.hashmap.events.Color;
import com.gimlism.translucent.hashmap.events.Direction;

/**
 * Callback through which the red-black tree operations report structural
 * changes, so the map can translate them into MapEvents. Keeps the RB algorithm
 * decoupled from the event model.
 */
interface TreeEventSink {
    void rotated(Direction dir, Object pivotKey);

    void recolored(Object nodeKey, Color oldColor, Color newColor);

    /** A sink that ignores everything (used where events aren't wanted). */
    TreeEventSink NONE = new TreeEventSink() {
        @Override public void rotated(Direction dir, Object pivotKey) { }
        @Override public void recolored(Object nodeKey, Color oldColor, Color newColor) { }
    };
}
