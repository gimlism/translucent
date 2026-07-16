package com.gimlism.translucent.treeset.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class TeachingTreeSetRangeTest {

    private TeachingTreeSet<Integer> setOf(int... xs) {
        TeachingTreeSet<Integer> s = new TeachingTreeSet<>();
        for (int x : xs) s.add(x);
        return s;
    }

    @Test
    void subSetIsHalfOpenLikeJdk() {
        TeachingTreeSet<Integer> s = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertEquals(List.of(3, 4, 5, 6), new ArrayList<>(s.subSet(3, 7)));
    }

    @Test
    void inclusiveBoundsMatchJdk() {
        int[] xs = {10, 20, 30, 40, 50, 60};
        TeachingTreeSet<Integer> mine = setOf(xs);
        TreeSet<Integer> oracle = new TreeSet<>();
        for (int x : xs) oracle.add(x);
        assertEquals(new ArrayList<>(oracle.subSet(20, true, 50, false)),
                new ArrayList<>(mine.subSet(20, true, 50, false)));
        assertEquals(new ArrayList<>(oracle.headSet(40, true)),
                new ArrayList<>(mine.headSet(40, true)));
        assertEquals(new ArrayList<>(oracle.tailSet(30, false)),
                new ArrayList<>(mine.tailSet(30, false)));
    }

    @Test
    void rangeViewContainsRespectsBounds() {
        TeachingTreeSet<Integer> s = setOf(1, 2, 3, 4, 5);
        NavigableSet<Integer> mid = s.subSet(2, true, 4, true);
        assertTrue(mid.contains(3));
        assertFalse(mid.contains(1));
        assertFalse(mid.contains(5));
        assertEquals(3, mid.size());
    }

    @Test
    void addInRangeWritesThroughOutOfRangeThrows() {
        TeachingTreeSet<Integer> s = setOf(10, 20, 30);
        NavigableSet<Integer> mid = s.subSet(10, true, 30, false); // [10, 30)
        assertTrue(mid.add(15));
        assertTrue(s.contains(15));
        assertThrows(IllegalArgumentException.class, () -> mid.add(30));
        assertThrows(IllegalArgumentException.class, () -> mid.add(5));
    }

    @Test
    void removeThroughRangeView() {
        TeachingTreeSet<Integer> s = setOf(1, 2, 3, 4, 5);
        NavigableSet<Integer> mid = s.subSet(2, true, 5, false);
        assertTrue(mid.remove(3));
        assertFalse(s.contains(3));
        assertFalse(mid.remove(5)); // out of range -> not present in view
        assertTrue(s.contains(5));
    }

    @Test
    void rangeFirstLastMatchJdk() {
        TeachingTreeSet<Integer> s = setOf(1, 2, 3, 4, 5, 6, 7);
        assertEquals(3, s.subSet(3, true, 6, true).first());
        assertEquals(6, s.subSet(3, true, 6, true).last());
    }
}
