package com.gimlism.translucent.trie.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gimlism.translucent.substrate.events.StructureEvent;
import com.gimlism.translucent.substrate.events.StructureSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrieSnapshotTest {
    private static TrieSnapshot leafKey(String label, Object v) {
        return new TrieSnapshot(
            new TrieNodeSnapshot(false, null,
                List.of(new TrieEdge(label, new TrieNodeSnapshot(true, v, List.of())))), 1);
    }

    @Test
    void snapshotAndEventAreStructureTypes() {
        TrieSnapshot snap = leafKey("hi", 7);
        assertInstanceOf(StructureSnapshot.class, snap);
        StructureEvent e = new Put("hi", 7, null, true, snap);
        assertInstanceOf(StructureEvent.class, e);
        assertEquals(snap, e.after());
    }

    @Test
    void rejectsNullRootAndNegativeSize() {
        var root = new TrieNodeSnapshot(false, null, List.of());
        assertThrows(NullPointerException.class, () -> new TrieSnapshot(null, 0));
        assertThrows(IllegalArgumentException.class, () -> new TrieSnapshot(root, -1));
    }

    @Test
    void childrenListIsImmutable() {
        var kids = new java.util.ArrayList<TrieEdge>();
        var node = new TrieNodeSnapshot(false, null, kids);
        kids.add(new TrieEdge("x", new TrieNodeSnapshot(true, 1, List.of())));
        assertEquals(0, node.children().size());               // defensive copy
        assertThrows(UnsupportedOperationException.class, () -> node.children().add(null));
    }
}
