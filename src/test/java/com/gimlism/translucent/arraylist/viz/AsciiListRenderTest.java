package com.gimlism.translucent.arraylist.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gimlism.translucent.arraylist.events.Append;
import com.gimlism.translucent.arraylist.events.EmptySlot;
import com.gimlism.translucent.arraylist.events.FilledSlot;
import com.gimlism.translucent.arraylist.events.Grow;
import com.gimlism.translucent.arraylist.events.ListSnapshot;
import com.gimlism.translucent.arraylist.events.RemoveAt;
import com.gimlism.translucent.arraylist.events.Set;
import com.gimlism.translucent.arraylist.events.Shift;
import com.gimlism.translucent.arraylist.events.SlotSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsciiListRenderTest {
    private final AsciiListRenderer r = new AsciiListRenderer();

    private static ListSnapshot snap(int cap, int size, Object... elems) {
        var slots = new java.util.ArrayList<SlotSnapshot>();
        for (int i = 0; i < cap; i++) slots.add(i < size ? new FilledSlot(elems[i]) : new EmptySlot());
        return new ListSnapshot(cap, size, slots);
    }

    @Test
    void rendersRowWithHighlightAndEmptyTail() {
        var s = snap(6, 5, "a", "b", "X", "c", "d");
        String expected = String.join("\n",
            "list: cap=6 size=5",
            "[ a | b |>X<| c | d | · ]");
        assertEquals(expected, r.renderList(s, 2));
    }

    @Test
    void rendersRowWithNoHighlight() {
        var s = snap(4, 2, "a", "b");
        String expected = String.join("\n",
            "list: cap=4 size=2",
            "[ a | b | · | · ]");
        assertEquals(expected, r.renderList(s, -1));
    }

    @Test
    void rendersEmptyCapacityAsBrackets() {
        String expected = String.join("\n",
            "list: cap=0 size=0",
            "[]");
        assertEquals(expected, r.renderList(new ListSnapshot(0, 0, List.of()), -1));
    }

    @Test
    void renderEventPutsLabelAboveRowAndHighlightsAffectedSlot() {
        var s = snap(6, 5, "a", "b", "c", "d", "e");
        String expected = String.join("\n",
            "APPEND e @ 4",
            "list: cap=6 size=5",
            "[ a | b | c | d |>e<| · ]");
        assertEquals(expected, r.renderEvent(new Append("e", 4, s)));
    }

    @Test
    void growHighlightsNothing() {
        var s = snap(6, 4, "a", "b", "c", "d");
        String expected = String.join("\n",
            "GROW cap 4 -> 6",
            "list: cap=6 size=4",
            "[ a | b | c | d | · | · ]");
        assertEquals(expected, r.renderEvent(new Grow(4, 6, snap(4, 4, "a", "b", "c", "d"), s)));
    }

    @Test
    void affectedIndexPerEventType() {
        var s = snap(4, 3, "a", "b", "c");
        assertEquals(2, AsciiListRenderer.affectedIndex(new Append("c", 2, s)));
        assertEquals(1, AsciiListRenderer.affectedIndex(new Set(1, "old", "b", s)));
        assertEquals(0, AsciiListRenderer.affectedIndex(new RemoveAt(0, "a", s)));
        assertEquals(3, AsciiListRenderer.affectedIndex(new Shift(2, 3, "b", s))); // destination
        assertEquals(-1, AsciiListRenderer.affectedIndex(new Grow(2, 4, s, s)));
    }
}
