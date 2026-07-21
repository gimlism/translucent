package com.gimlism.translucent.treeset.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.substrate.rbtree.Direction;
import com.gimlism.translucent.treeset.events.Add;
import com.gimlism.translucent.treeset.events.Compare;
import com.gimlism.translucent.treeset.events.SetEvent;
import com.gimlism.translucent.treeset.events.SetEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class TeachingTreeSetAddTest {

    private List<SetEvent> record(TeachingTreeSet<Integer> set) {
        List<SetEvent> log = new ArrayList<>();
        set.addListener(log::add);
        return log;
    }

    @Test
    void addReturnsTrueThenFalseOnDuplicate() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        assertTrue(set.add(5));
        assertFalse(set.add(5));
        assertEquals(1, set.size());
    }

    @Test
    void iteratesInSortedOrder() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{8, 3, 10, 1, 6, 14, 4, 7, 13}) set.add(k);
        List<Integer> out = new ArrayList<>(set);
        List<Integer> expected = new ArrayList<>(out);
        Collections.sort(expected);
        assertEquals(expected, out);
        SetInvariants.assertValid(set);
    }

    @Test
    void staysBalancedUnderManyInserts() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k = 0; k < 200; k++) set.add(k);
        SetInvariants.assertValid(set);
        assertEquals(200, set.size());
    }

    @Test
    void firstEventOnEmptyAddIsAddNotCompare() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        List<SetEvent> log = record(set);
        set.add(42);
        assertInstanceOf(Add.class, log.get(0), "first-ever add emits Add (no Compare on an empty tree)");
    }

    @Test
    void secondAddNarratesCompareThenAdd() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        set.add(10);
        List<SetEvent> log = record(set);
        set.add(5);
        assertInstanceOf(Compare.class, log.get(0), "second add compares against the root first");
        assertTrue(log.stream().anyMatch(e -> e instanceof Add), "then adds");
    }

    @Test
    void duplicateAddNarratesCompareButEmitsNoAdd() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        set.add(10);
        List<SetEvent> log = record(set);
        set.add(10);
        assertTrue(log.stream().anyMatch(e -> e instanceof Compare c && c.found()), "compare finds it");
        assertFalse(log.stream().anyMatch(e -> e instanceof Add), "no Add for a duplicate");
    }

    @Test
    void containsNarratesCompareWithoutStructuralEvent() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{10, 5, 15}) set.add(k);
        List<SetEvent> log = record(set);
        assertTrue(set.contains(15));
        assertTrue(log.stream().allMatch(e -> e instanceof Compare), "reads emit only Compare frames");
        assertTrue(log.stream().anyMatch(e -> e instanceof Compare c && c.found()));
    }

    // Regression pin for the core walk semantics: each Compare frame carries the element of the
    // node the walk is STANDING ON (the visited node), never the search key. A past core inversion
    // (node.element -> e) emitted the search key instead and survived every narration test — those
    // assert the frame TYPE and found() but never the element VALUE. These pin the value against the
    // real descent. Tree {10,5,15} is a fixed RB shape (root 10 black, 5 left, 15 right, no
    // rotations), so searching the absent 7 visits 10 then 5 — both distinct from the query.
    @Test
    void containsCompareCarriesVisitedNodeNotSearchKey() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{10, 5, 15}) set.add(k);
        List<SetEvent> log = record(set);
        assertFalse(set.contains(7));
        List<Compare> walk = compares(log);
        assertEquals(List.of(10, 5), walk.stream().map(Compare::element).toList(),
                "Compare carries the visited node (10, then 5) — never the absent search key 7");
        assertEquals(List.of(Direction.LEFT, Direction.RIGHT),
                walk.stream().map(Compare::went).toList(), "branch taken at each visited node");
        assertTrue(walk.stream().noneMatch(Compare::found), "no visited node equals the absent key");

        // Found key: the intermediate frame still visits a node (10) distinct from the key (15).
        TeachingTreeSet<Integer> found = new TeachingTreeSet<>();
        for (int k : new int[]{10, 5, 15}) found.add(k);
        List<SetEvent> foundLog = record(found);
        assertTrue(found.contains(15));
        List<Compare> foundWalk = compares(foundLog);
        assertEquals(List.of(10, 15), foundWalk.stream().map(Compare::element).toList(),
                "visited 10 (intermediate, != key) then landed on 15");
        Compare terminal = foundWalk.get(foundWalk.size() - 1);
        assertTrue(terminal.found() && terminal.went() == null, "terminal compare is the equal hit");
    }

    @Test
    void addDescentCompareCarriesVisitedNodeNotNewElement() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{10, 5, 15}) set.add(k);
        List<SetEvent> log = record(set);
        assertTrue(set.add(7));   // absent -> descends past 10, 5 then links
        List<Compare> walk = compares(log);
        assertEquals(List.of(10, 5), walk.stream().map(Compare::element).toList(),
                "the add-descent's Compare frames carry the visited nodes, not the new element 7");
        assertEquals(List.of(Direction.LEFT, Direction.RIGHT),
                walk.stream().map(Compare::went).toList(), "branch taken at each visited node");
    }

    private static List<Compare> compares(List<SetEvent> log) {
        return log.stream().filter(Compare.class::isInstance).map(Compare.class::cast).toList();
    }

    @Test
    void containsOnEmptySetEmitsNothing() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        List<SetEvent> log = record(set);
        assertFalse(set.contains(1));
        assertTrue(log.isEmpty(), "no walk starts on an empty set");
    }

    @Test
    void everyEventSnapshotIsATrueAfterImage() {
        // Snapshot-before-settled: the Add frame already contains the new element,
        // and mid-fixup Rotation/Recolor frames render the rebalanced tree (climbed
        // from the event's node, not a stale cached root).
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        List<SetEvent> log = record(set);
        for (int k : new int[]{10, 20, 30}) set.add(k); // 30 forces a rotation
        for (SetEvent e : log) {
            if (e instanceof Add a) {
                assertTrue(containsElement(a.after().root(), a.element()),
                        "Add frame must already contain the added element");
            }
        }
        // Last frame is the fully balanced tree: root 20.
        SetEvent last = log.get(log.size() - 1);
        assertEquals(20, last.after().root().element());
        SetInvariants.assertValid(set);
    }

    private static boolean containsElement(
            com.gimlism.translucent.treeset.events.SetNodeSnapshot n, Object e) {
        if (n == null) return false;
        return n.element().equals(e) || containsElement(n.left(), e) || containsElement(n.right(), e);
    }

    @Test
    void comparatorControlsOrder() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>(Comparator.reverseOrder());
        for (int k : new int[]{1, 2, 3, 4, 5}) set.add(k);
        assertEquals(List.of(5, 4, 3, 2, 1), new ArrayList<>(set));
        assertEquals(Comparator.reverseOrder(), set.comparator());
        SetInvariants.assertValid(set);
    }

    @Test
    void matchesJdkTreeSetIterationOnAdds() {
        TeachingTreeSet<Integer> mine = new TeachingTreeSet<>();
        TreeSet<Integer> oracle = new TreeSet<>();
        int[] xs = {50, 20, 80, 10, 30, 70, 90, 25, 5, 60, 40, 85, 15};
        for (int x : xs) { mine.add(x); oracle.add(x); }
        assertEquals(new ArrayList<>(oracle), new ArrayList<>(mine));
        assertEquals(oracle.size(), mine.size());
    }

    @Test
    void clearEmptiesTheSet() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{1, 2, 3}) set.add(k);
        set.clear();
        assertEquals(0, set.size());
        assertTrue(set.isEmpty());
        assertFalse(set.iterator().hasNext());
    }

    @Test
    void clearFromWithinAListenerIsRejected() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        set.add(1); // seed so the next add emits a Compare mid-operation
        set.addListener(e -> set.clear()); // a listener that illegally mutates during dispatch
        assertThrows(ConcurrentModificationException.class, () -> set.add(2));
    }

    @Test
    void mutatingFromWithinAReadNarrationListenerIsRejected() {
        TeachingTreeSet<Integer> set = new TeachingTreeSet<>();
        for (int k : new int[]{10, 5, 15}) set.add(k);
        SetEventListener bad = e -> set.add(99); // illegal: mutate during a read's Compare narration
        set.addListener(bad);
        // contains() narrates Compare via emitRead, so the guard now catches a mutating read-listener.
        assertThrows(ConcurrentModificationException.class, () -> set.contains(15));
        set.removeListener(bad);
        assertEquals(3, set.size(), "the rejected read did not mutate the set");
        assertFalse(set.contains(99));
    }
}
