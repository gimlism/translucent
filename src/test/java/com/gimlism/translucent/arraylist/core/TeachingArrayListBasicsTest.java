package com.gimlism.translucent.arraylist.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.consumer.ListRecordingListener;
import com.gimlism.translucent.arraylist.events.Append;
import com.gimlism.translucent.arraylist.events.EmptySlot;
import com.gimlism.translucent.arraylist.events.FilledSlot;
import com.gimlism.translucent.arraylist.events.Grow;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListSnapshot;
import com.gimlism.translucent.arraylist.events.Set;
import java.util.ConcurrentModificationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeachingArrayListBasicsTest {
    @Test
    void addGetSetSizeRoundTrip() {
        var list = new TeachingArrayList<String>(4);
        assertTrue(list.add("a"));
        assertTrue(list.add("b"));
        assertEquals(2, list.size());
        assertEquals("a", list.get(0));
        assertEquals("b", list.set(1, "B"));
        assertEquals("B", list.get(1));
        assertNull(new TeachingArrayList<String>(4).stream().findFirst().orElse(null));
    }

    @Test
    void getOutOfRangeThrows() {
        var list = new TeachingArrayList<String>(4);
        list.add("a");
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> list.set(1, "x"));
    }

    @Test
    void nullElementsAreStorable() {
        var list = new TeachingArrayList<String>(4);
        list.add(null);
        assertEquals(1, list.size());
        assertNull(list.get(0));
        // snapshot models it as a FilledSlot(null), not an EmptySlot
        assertEquals(new FilledSlot(null), list.snapshot().slots().get(0));
        assertInstanceOf(EmptySlot.class, list.snapshot().slots().get(1));
    }

    @Test
    void appendEmitsAppendEventWithSettledSnapshot() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add("a");
        assertEquals(1, rec.events().size());
        Append a = assertInstanceOf(Append.class, rec.events().get(0));
        assertEquals("a", a.element());
        assertEquals(0, a.index());
        assertEquals(1, a.after().size());
        assertEquals(4, a.after().capacity());
    }

    @Test
    void setEmitsSetEventAndDoesNotBumpModCount() {
        var list = new TeachingArrayList<String>(4);
        list.add("a");
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.set(0, "A");
        Set s = assertInstanceOf(Set.class, rec.events().get(0));
        assertEquals(0, s.index());
        assertEquals("a", s.previousElement());
        assertEquals("A", s.element());
        // set is non-structural: iterating and then set() must not fail-fast
        var it = list.iterator();
        list.set(0, "A2");
        assertEquals("A2", it.next());
    }

    @Test
    void growthFollowsOneAndAHalfFromEagerCapacity() {
        var list = new TeachingArrayList<Integer>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        for (int i = 0; i < 10; i++) list.add(i);   // caps 4 -> 6 -> 9 -> 13
        List<Integer> grownCaps = rec.events().stream()
            .filter(e -> e instanceof Grow).map(e -> ((Grow) e).newCapacity()).toList();
        assertEquals(List.of(6, 9, 13), grownCaps);
        // Grow leads its Append and carries the pre-append size in `after`
        int firstGrow = indexOfFirst(rec.events(), Grow.class);
        Grow g = (Grow) rec.events().get(firstGrow);
        assertEquals(4, g.oldCapacity());
        assertEquals(6, g.newCapacity());
        assertEquals(4, g.after().size());          // element not placed yet
        assertInstanceOf(Append.class, rec.events().get(firstGrow + 1));
        assertEquals(6, ((Append) rec.events().get(firstGrow + 1)).after().capacity());
        // all elements preserved in order
        for (int i = 0; i < 10; i++) assertEquals(i, list.get(i));
    }

    @Test
    void snapshotIsIndependentOfLaterMutation() {
        var list = new TeachingArrayList<String>(4);
        list.add("a");
        ListSnapshot snap = list.snapshot();
        list.add("b");
        list.set(0, "Z");
        assertEquals(1, snap.size());
        assertEquals(new FilledSlot("a"), snap.slots().get(0));
    }

    @Test
    void reentrantMutationFromListenerIsRejected() {
        var list = new TeachingArrayList<String>(4);
        list.addListener(e -> list.add("reentrant")); // structural mutation during dispatch
        assertThrows(ConcurrentModificationException.class, () -> list.add("a"));
    }

    private static int indexOfFirst(List<ListEvent> events, Class<?> type) {
        for (int i = 0; i < events.size(); i++) if (type.isInstance(events.get(i))) return i;
        return -1;
    }
}
