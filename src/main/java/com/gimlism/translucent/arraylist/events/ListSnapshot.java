package com.gimlism.translucent.arraylist.events;

import com.gimlism.translucent.substrate.events.StructureSnapshot;
import java.util.List;

/** Immutable whole-list state at a point in time. */
public record ListSnapshot(int capacity, int size, List<SlotSnapshot> slots) implements StructureSnapshot {
    public ListSnapshot {
        slots = List.copyOf(slots);
        if (slots.size() != capacity) {
            throw new IllegalArgumentException(
                "slots.size() (" + slots.size() + ") must equal capacity (" + capacity + ")");
        }
        if (size < 0 || size > capacity) {
            throw new IllegalArgumentException(
                "size (" + size + ") must be in [0, capacity (" + capacity + ")]");
        }
    }
}
