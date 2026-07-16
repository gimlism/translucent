package com.gimlism.translucent.treeset.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class TeachingTreeSetDescendingTest {

    private TeachingTreeSet<Integer> setOf(int... xs) {
        TeachingTreeSet<Integer> s = new TeachingTreeSet<>();
        for (int x : xs) s.add(x);
        return s;
    }

    @Test
    void descendingIteratorWalksReverseOrder() {
        TeachingTreeSet<Integer> s = setOf(3, 1, 4, 1, 5, 9, 2, 6);
        List<Integer> out = new ArrayList<>();
        s.descendingIterator().forEachRemaining(out::add);
        assertEquals(List.of(9, 6, 5, 4, 3, 2, 1), out);
    }

    @Test
    void descendingSetIteratesReverseAndReflectsFirstLast() {
        TeachingTreeSet<Integer> s = setOf(10, 20, 30);
        NavigableSet<Integer> d = s.descendingSet();
        assertEquals(List.of(30, 20, 10), new ArrayList<>(d));
        assertEquals(30, d.first());
        assertEquals(10, d.last());
        assertEquals(Integer.valueOf(20), d.ceiling(20));
        // In descending order, "higher than 20" means the next smaller real element.
        assertEquals(Integer.valueOf(10), d.higher(20));
    }

    @Test
    void descendingSetWritesThrough() {
        TeachingTreeSet<Integer> s = setOf(1, 2, 3);
        NavigableSet<Integer> d = s.descendingSet();
        d.add(4);
        assertTrue(s.contains(4));
        d.remove(2);
        assertFalse(s.contains(2));
        assertEquals(List.of(1, 3, 4), new ArrayList<>(s));
    }

    @Test
    void descendingSetMatchesJdk() {
        int[] xs = {50, 20, 80, 10, 30, 70, 90};
        TeachingTreeSet<Integer> mine = setOf(xs);
        TreeSet<Integer> oracle = new TreeSet<>();
        for (int x : xs) oracle.add(x);
        assertEquals(new ArrayList<>(oracle.descendingSet()), new ArrayList<>(mine.descendingSet()));
    }
}
