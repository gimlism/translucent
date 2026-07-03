package com.gimlism.translucent.arraylist.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.consumer.ListRecordingListener;
import com.gimlism.translucent.arraylist.events.Append;
import com.gimlism.translucent.arraylist.events.Grow;
import org.junit.jupiter.api.Test;

class LazyAllocationTest {
    @Test
    void freshNoArgListAllocatesNothing() {
        var list = new TeachingArrayList<String>();
        assertEquals(0, list.size());
        assertEquals(0, list.snapshot().capacity());          // nothing allocated
        assertTrue(list.snapshot().slots().isEmpty());
    }

    @Test
    void firstAddJumpsStraightToTen() {
        var list = new TeachingArrayList<String>();
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add("a");
        Grow g = assertInstanceOf(Grow.class, rec.events().get(0));
        assertEquals(0, g.oldCapacity());
        assertEquals(10, g.newCapacity());
        assertEquals(0, g.before().capacity());
        assertEquals(0, g.before().size());
        assertEquals(10, g.after().capacity());
        assertEquals(0, g.after().size());                    // element not placed yet
        Append a = assertInstanceOf(Append.class, rec.events().get(1));
        assertEquals(10, a.after().capacity());
        assertEquals(1, a.after().size());
    }

    @Test
    void secondGrowFollowsOneAndAHalfFromTen() {
        var list = new TeachingArrayList<Integer>();
        var rec = new ListRecordingListener();
        list.addListener(rec);
        for (int i = 0; i < 11; i++) list.add(i);             // caps 0->10, then 10->15 (size 11 <= 15)
        var caps = rec.events().stream()
            .filter(e -> e instanceof Grow).map(e -> ((Grow) e).newCapacity()).toList();
        assertEquals(java.util.List.of(10, 15), caps);
        for (int i = 0; i < 11; i++) assertEquals(i, list.get(i));
    }

    @Test
    void explicitZeroCapacityGrowsByFormulaNotToTen() {
        var list = new TeachingArrayList<Integer>(0);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add(1); // explicit-zero path: 0 -> 1 (not the default-sentinel jump to 10)
        Grow g = assertInstanceOf(Grow.class, rec.events().get(0));
        assertEquals(0, g.oldCapacity());
        assertEquals(1, g.newCapacity());
    }
}
