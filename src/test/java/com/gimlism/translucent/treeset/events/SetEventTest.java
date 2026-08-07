package com.gimlism.translucent.treeset.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.substrate.events.StructureEvent;
import com.gimlism.translucent.substrate.rbtree.Color;
import com.gimlism.translucent.substrate.rbtree.Direction;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class SetEventTest {

    private static SetSnapshot leaf(int e) {
        return new SetSnapshot(new SetNodeSnapshot(e, false, null, null), 1);
    }

    @Test
    void snapshotRejectsNegativeSize() {
        assertThrows(IllegalArgumentException.class, () -> new SetSnapshot(null, -1));
    }

    @Test
    void emptySnapshotHasNullRoot() {
        SetSnapshot s = new SetSnapshot(null, 0);
        assertNull(s.root());
        assertEquals(0, s.size());
    }

    @Test
    void eventsExposeCovariantAfterSnapshot() {
        SetEvent add = new Add(7, leaf(7));
        StructureEvent asBase = add;
        assertTrue(asBase.after() instanceof SetSnapshot);
        assertEquals(7, ((Add) add).element());
    }

    @Test
    void compareCarriesDirectionAndFoundFlag() {
        Compare walked = new Compare(5, Direction.LEFT, false, leaf(5));
        assertEquals(Direction.LEFT, walked.went());
        Compare landed = new Compare(5, null, true, leaf(5));
        assertNull(landed.went());
        assertTrue(landed.found());
    }

    @Test
    void formatterCaptionsEachEvent() {
        assertTrue(SetEventFormatter.format(new Add(7, leaf(7))).contains("7"));
        assertTrue(SetEventFormatter.format(new Remove(7, leaf(7))).toLowerCase(Locale.ROOT).contains("remove"));
        assertTrue(SetEventFormatter.format(new Rotation(Direction.LEFT, 3, leaf(3))).toLowerCase(Locale.ROOT).contains("rotate"));
        assertTrue(SetEventFormatter.format(new Recolor(3, Color.RED, Color.BLACK, leaf(3))).contains("BLACK"));
        assertTrue(SetEventFormatter.format(new Compare(5, Direction.LEFT, false, leaf(5))).toLowerCase(Locale.ROOT).contains("compare"));
        assertTrue(SetEventFormatter.format(new Compare(5, null, true, leaf(5))).toLowerCase(Locale.ROOT).contains("found"));
    }
}
