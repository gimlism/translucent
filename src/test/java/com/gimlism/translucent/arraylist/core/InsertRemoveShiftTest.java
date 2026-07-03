package com.gimlism.translucent.arraylist.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gimlism.translucent.arraylist.consumer.ListRecordingListener;
import com.gimlism.translucent.arraylist.events.Grow;
import com.gimlism.translucent.arraylist.events.Insert;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.RemoveAt;
import com.gimlism.translucent.arraylist.events.Shift;
import java.util.ConcurrentModificationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class InsertRemoveShiftTest {
    private static TeachingArrayList<String> of(String... xs) {
        var list = new TeachingArrayList<String>(xs.length == 0 ? 4 : xs.length);
        for (String x : xs) list.add(x);
        return list;
    }

    private static String tag(ListEvent e) {
        return switch (e) {
            case Shift s -> "SHIFT " + s.fromIndex() + "->" + s.toIndex() + "(" + s.element() + ")";
            case Insert in -> "INSERT@" + in.index();
            case RemoveAt r -> "REMOVE@" + r.index();
            default -> e.getClass().getSimpleName();
        };
    }

    @Test
    void insertInMiddleShiftsHighToLowThenInserts() {
        var list = new TeachingArrayList<String>(6);
        for (String x : new String[]{"a", "b", "c", "d"}) list.add(x);
        var rec = new ListRecordingListener();
        list.addListener(rec);

        list.add(2, "X"); // [a,b,c,d] -> [a,b,X,c,d]

        assertEquals(List.of("SHIFT 3->4(d)", "SHIFT 2->3(c)", "INSERT@2"),
            rec.events().stream().map(InsertRemoveShiftTest::tag).toList());
        assertEquals(List.of("a", "b", "X", "c", "d"), list);
        // the first Shift snapshot is mid-slide: capacity 6, size 5, slot 4 already holds d
        Shift first = (Shift) rec.events().get(0);
        assertEquals(5, first.after().size());
    }

    @Test
    void insertAtEndIsAnAppendNotAShift() {
        var list = new TeachingArrayList<String>(4); // spare capacity: no grow on the boundary add
        list.add("a");
        list.add("b");
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add(2, "c"); // index == size -> append semantics
        assertEquals(List.of("Append"),
            rec.events().stream().map(e -> e.getClass().getSimpleName()).toList());
        assertEquals(List.of("a", "b", "c"), list);
    }

    @Test
    void insertTriggeringGrowEmitsGrowThenShiftsThenInsert() {
        var list = of("a", "b", "c", "d"); // capacity 4, full
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add(1, "X"); // must grow 4->6 first, then shift [1,4) right
        var kinds = rec.events().stream().map(e -> e.getClass().getSimpleName()).toList();
        assertEquals("Grow", kinds.get(0));
        assertInstanceOf(Grow.class, rec.events().get(0));
        assertEquals("Insert", kinds.get(kinds.size() - 1));
        assertEquals(List.of("a", "X", "b", "c", "d"), list);
    }

    @Test
    void removeInMiddleShiftsLowToHighThenRemoves() {
        var list = of("a", "b", "c", "d");
        var rec = new ListRecordingListener();
        list.addListener(rec);

        assertEquals("b", list.remove(1)); // [a,b,c,d] -> [a,c,d]

        assertEquals(List.of("SHIFT 2->1(c)", "SHIFT 3->2(d)", "REMOVE@1"),
            rec.events().stream().map(InsertRemoveShiftTest::tag).toList());
        assertEquals(List.of("a", "c", "d"), list);
        // terminal RemoveAt snapshot: size 3, tail slot cleared to EmptySlot
        RemoveAt r = (RemoveAt) rec.events().get(2);
        assertEquals(3, r.after().size());
    }

    @Test
    void removeLastEmitsOnlyRemoveAt() {
        var list = of("a", "b", "c");
        var rec = new ListRecordingListener();
        list.addListener(rec);
        assertEquals("c", list.remove(2));
        assertEquals(List.of("REMOVE@2"),
            rec.events().stream().map(InsertRemoveShiftTest::tag).toList());
        assertEquals(List.of("a", "b"), list);
    }

    @Test
    void structuralChangeDuringIterationFailsFast() {
        var list = of("a", "b", "c");
        var it = list.iterator();
        it.next();
        list.remove(0);                       // structural: modCount bumped
        assertThrows(ConcurrentModificationException.class, it::next);
    }

    @Test
    void iteratorRemoveDeletesThroughRemoveInt() {
        var list = of("a", "b", "c");
        var it = list.iterator();
        it.next();
        it.remove();                          // AbstractList.Itr.remove -> remove(0)
        assertEquals(List.of("b", "c"), list);
    }

    @Test
    void insertRangeCheck() {
        var list = of("a");
        assertThrows(IndexOutOfBoundsException.class, () -> list.add(2, "x"));
        assertThrows(IndexOutOfBoundsException.class, () -> list.add(-1, "x"));
        assertThrows(IndexOutOfBoundsException.class, () -> list.remove(1));
    }
}
