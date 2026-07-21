package com.gimlism.translucent.treeset.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.substrate.rbtree.Direction;
import com.gimlism.translucent.treeset.events.Compare;
import com.gimlism.translucent.treeset.events.Remove;
import com.gimlism.translucent.treeset.events.SetEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class TeachingTreeSetNavigationTest {

    private TeachingTreeSet<Integer> setOf(int... xs) {
        TeachingTreeSet<Integer> s = new TeachingTreeSet<>();
        for (int x : xs) s.add(x);
        return s;
    }

    @Test
    void firstAndLastAreMinAndMax() {
        TeachingTreeSet<Integer> s = setOf(20, 5, 40, 1, 30);
        assertEquals(1, s.first());
        assertEquals(40, s.last());
    }

    @Test
    void firstOnEmptyThrows() {
        assertThrows(NoSuchElementException.class, () -> new TeachingTreeSet<Integer>().first());
        assertThrows(NoSuchElementException.class, () -> new TeachingTreeSet<Integer>().last());
    }

    @Test
    void relativeNavigatorsMatchJdk() {
        int[] xs = {10, 20, 30, 40, 50};
        TeachingTreeSet<Integer> mine = setOf(xs);
        TreeSet<Integer> oracle = new TreeSet<>();
        for (int x : xs) oracle.add(x);
        for (int q = 5; q <= 55; q += 5) {
            assertEquals(oracle.lower(q), mine.lower(q), "lower " + q);
            assertEquals(oracle.floor(q), mine.floor(q), "floor " + q);
            assertEquals(oracle.ceiling(q), mine.ceiling(q), "ceiling " + q);
            assertEquals(oracle.higher(q), mine.higher(q), "higher " + q);
        }
    }

    @Test
    void comparisonNavigatorsNarrateCompare() {
        TeachingTreeSet<Integer> s = setOf(10, 5, 15);
        List<SetEvent> log = new ArrayList<>();
        s.addListener(log::add);
        s.ceiling(12);
        assertTrue(log.stream().allMatch(e -> e instanceof Compare), "ceiling narrates only Compare frames");
        assertTrue(log.size() > 0, "at least one comparison happened");
    }

    @Test
    void navigationCompareCarriesVisitedNodeNotQuery() {
        // Pin for the relative-navigator walk (sibling of the add/contains pins in AddTest):
        // each Compare carries the visited node, never the query. ceiling(7) on {10,5,15} visits
        // 10 then 5 — both distinct from 7 — so an inversion (node.element -> e) would collapse
        // the path to [7,7] and fail here.
        TeachingTreeSet<Integer> s = setOf(10, 5, 15);
        List<SetEvent> log = new ArrayList<>();
        s.addListener(log::add);
        assertEquals(10, s.ceiling(7));
        List<Compare> walk = log.stream()
                .filter(Compare.class::isInstance).map(Compare.class::cast).toList();
        assertEquals(List.of(10, 5), walk.stream().map(Compare::element).toList(),
                "Compare carries the visited node (10, then 5) — never the query 7");
        assertEquals(List.of(Direction.LEFT, Direction.RIGHT),
                walk.stream().map(Compare::went).toList(), "branch taken at each visited node");
    }

    @Test
    void endpointOpsDoNotNarrateCompare() {
        TeachingTreeSet<Integer> s = setOf(10, 5, 15);
        List<SetEvent> log = new ArrayList<>();
        s.addListener(log::add);
        s.first();
        s.last();
        assertTrue(log.isEmpty(), "first/last are unconditional walks — no Compare");
    }

    @Test
    void pollFirstAndPollLastRemoveEndpointsAndEmitRemove() {
        TeachingTreeSet<Integer> s = setOf(10, 5, 15);
        List<SetEvent> log = new ArrayList<>();
        s.addListener(log::add);
        assertEquals(5, s.pollFirst());
        assertEquals(15, s.pollLast());
        assertEquals(1, s.size());
        assertEquals(10, s.first());
        assertTrue(log.stream().anyMatch(e -> e instanceof Remove r && r.element().equals(5)));
        assertTrue(log.stream().anyMatch(e -> e instanceof Remove r && r.element().equals(15)));
        SetInvariants.assertValid(s);
    }

    @Test
    void pollOnEmptyReturnsNull() {
        TeachingTreeSet<Integer> s = new TeachingTreeSet<>();
        assertNull(s.pollFirst());
        assertNull(s.pollLast());
    }
}
