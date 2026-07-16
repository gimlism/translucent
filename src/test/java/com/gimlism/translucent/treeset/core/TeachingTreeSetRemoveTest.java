package com.gimlism.translucent.treeset.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.treeset.events.Compare;
import com.gimlism.translucent.treeset.events.Remove;
import com.gimlism.translucent.treeset.events.SetEvent;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class TeachingTreeSetRemoveTest {

    @Test
    void removeReturnsTrueOnlyWhenPresent() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{10, 5, 15}) set.add(k);
        assertTrue(set.remove(5));
        assertFalse(set.remove(5));
        assertFalse(set.remove(999));
        assertEquals(2, set.size());
        SetInvariants.assertValid(set);
    }

    @Test
    void absentRemoveIsSilentButPresentRemoveNarratesThenMarks() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{10, 5, 15}) set.add(k);
        List<SetEvent> silent = new ArrayList<>();
        set.addListener(silent::add);
        set.remove(999);
        assertTrue(silent.stream().allMatch(e -> e instanceof Compare),
                "an absent remove narrates the failed walk only (no Remove marker)");
        assertFalse(silent.stream().anyMatch(e -> e instanceof Remove));

        List<SetEvent> log = new ArrayList<>();
        set.addListener(log::add);
        set.remove(5);
        assertTrue(log.stream().anyMatch(e -> e instanceof Compare), "walk narrated");
        SetEvent last = log.get(log.size() - 1);
        assertTrue(last instanceof Remove r && r.element().equals(5), "terminal Remove marker last");
    }

    @Test
    void removeFrameReportsFinalSize() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{10, 5, 15, 3, 7}) set.add(k);
        List<SetEvent> log = new ArrayList<>();
        set.addListener(log::add);
        set.remove(5);
        // Every emitted frame during the remove reports the post-removal size (4).
        for (SetEvent e : log) {
            if (!(e instanceof Compare)) {
                assertEquals(4, e.after().size(), "rebalance/terminal frames report final size");
            }
        }
    }

    @Test
    void randomAddRemoveMatchesJdkOracle() {
        TeachingTreeSet<Integer> mine = new TeachingTreeSet<>();
        TreeSet<Integer> oracle = new TreeSet<>();
        Random rng = new Random(20260716L);
        for (int i = 0; i < 3000; i++) {
            int k = rng.nextInt(200);
            if (rng.nextBoolean()) {
                assertEquals(oracle.add(k), mine.add(k), "add " + k);
            } else {
                assertEquals(oracle.remove(k), mine.remove(k), "remove " + k);
            }
            assertEquals(oracle.size(), mine.size());
            SetInvariants.assertValid(mine);
        }
        assertEquals(new ArrayList<>(oracle), new ArrayList<>(mine));
    }

    @Test
    void iteratorRemoveDeletesLastReturned() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{1, 2, 3, 4, 5}) set.add(k);
        Iterator<Integer> it = set.iterator();
        while (it.hasNext()) {
            if (it.next() % 2 == 0) it.remove();
        }
        assertEquals(List.of(1, 3, 5), new ArrayList<>(set));
        SetInvariants.assertValid(set);
    }

    @Test
    void iteratorIsFailFastAfterExternalMutation() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{1, 2, 3}) set.add(k);
        Iterator<Integer> it = set.iterator();
        it.next();
        set.add(99);
        assertThrows(ConcurrentModificationException.class, it::next);
    }
}
