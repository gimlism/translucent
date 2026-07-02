package com.gimlism.translucent.hashmap.events;

/** An immutable, self-contained record of a single map state change. */
public sealed interface MapEvent
        permits Put, Remove, Collision, Resize, Treeify, Untreeify, Rotation, Recolor {
    /** Whole-map snapshot after the operation settled. */
    MapSnapshot after();
}
