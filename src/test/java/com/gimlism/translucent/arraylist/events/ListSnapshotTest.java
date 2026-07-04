package com.gimlism.translucent.arraylist.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ListSnapshotTest {
    @Test
    void slotsSizeMustEqualCapacity() {
        assertThrows(IllegalArgumentException.class,
            () -> new ListSnapshot(3, 1, List.of(new FilledSlot("a"))));
    }

    @Test
    void sizeMustBeWithinCapacity() {
        assertThrows(IllegalArgumentException.class,
            () -> new ListSnapshot(1, 2, List.of(new FilledSlot("a"))));
    }

    @Test
    void emptySlotIsDistinctFromFilledNull() {
        SlotSnapshot empty = new EmptySlot();
        SlotSnapshot filledNull = new FilledSlot(null);
        assertNotEquals(empty, filledNull);
        assertEquals(new FilledSlot(null), filledNull);
    }

    @Test
    void snapshotIsImmutableCopyOfSlots() {
        var slots = new java.util.ArrayList<SlotSnapshot>();
        slots.add(new FilledSlot("a"));
        slots.add(new EmptySlot());
        var snap = new ListSnapshot(2, 1, slots);
        slots.set(0, new FilledSlot("mutated"));           // mutate the source list
        assertEquals(new FilledSlot("a"), snap.slots().get(0)); // snapshot unaffected
        assertThrows(UnsupportedOperationException.class,
            () -> snap.slots().add(new EmptySlot()));      // and unmodifiable
    }
}
