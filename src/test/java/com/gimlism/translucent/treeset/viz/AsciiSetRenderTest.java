package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.substrate.rbtree.Color;
import com.gimlism.translucent.substrate.rbtree.Direction;
import com.gimlism.translucent.substrate.viz.ColorMode;
import com.gimlism.translucent.treeset.events.Add;
import com.gimlism.translucent.treeset.events.Compare;
import com.gimlism.translucent.treeset.events.Recolor;
import com.gimlism.translucent.treeset.events.Remove;
import com.gimlism.translucent.treeset.events.Rotation;
import com.gimlism.translucent.treeset.events.SetNodeSnapshot;
import com.gimlism.translucent.treeset.events.SetSnapshot;
import org.junit.jupiter.api.Test;

class AsciiSetRenderTest {
    private final AsciiSetRenderer r = new AsciiSetRenderer(ColorMode.PLAIN);

    private static SetNodeSnapshot node(Object e, boolean red, SetNodeSnapshot l, SetNodeSnapshot rt) {
        return new SetNodeSnapshot(e, red, l, rt);
    }
    private static SetNodeSnapshot leaf(Object e, boolean red) {
        return node(e, red, null, null);
    }
    // 20(B) { left: 10(R), right: 30(R) }
    private static SetSnapshot threeNodeTree() {
        return new SetSnapshot(node(20, false, leaf(10, true), leaf(30, true)), 3);
    }

    @Test
    void emptySetRendersHeaderOnly() {
        assertEquals("set: size=0", r.renderSet(new SetSnapshot(null, 0), null));
    }

    @Test
    void singleNodeRendersRootWithColourMarker() {
        assertEquals("set: size=1\n  42(B)", r.renderSet(new SetSnapshot(leaf(42, false), 1), null));
    }

    @Test
    void rendersSidewaysRightAboveNodeLeftBelow() {
        String expected = String.join("\n",
            "set: size=3",
            "      ┌─ 30(R)",
            "  20(B)",
            "      └─ 10(R)");
        assertEquals(expected, r.renderSet(threeNodeTree(), null));
    }

    @Test
    void highlightMarksTheAffectedNodeInTheLeftGutter() {
        String expected = String.join("\n",
            "set: size=3",
            "      ┌─ 30(R)",
            "  20(B)",
            ">     └─ 10(R)");   // "> " + 4-space depth indent + connector
        assertEquals(expected, r.renderSet(threeNodeTree(), 10));
    }

    @Test
    void addEventCaptionAboveTreeHighlightsTheNewElement() {
        String out = r.renderEvent(new Add(30, threeNodeTree()));
        assertTrue(out.startsWith("add 30\n"), out);
        assertTrue(out.contains(">     ┌─ 30(R)"), out); // the new node is marked
    }

    @Test
    void compareEventHighlightsTheVisitedNode() {
        String out = r.renderEvent(new Compare(20, Direction.LEFT, false, threeNodeTree()));
        assertTrue(out.startsWith("compare 20 -> go LEFT\n"), out);
        assertTrue(out.contains("> 20(B)"), out); // root is the visited/highlighted node
    }

    @Test
    void removeEventHighlightsNothing() {
        // 10 is gone from after(); nothing is marked
        SetSnapshot after = new SetSnapshot(node(20, false, null, leaf(30, true)), 2);
        String out = r.renderEvent(new Remove(10, after));
        assertTrue(out.startsWith("remove 10\n"), out);
        assertFalse(out.contains("> "), out);
    }

    @Test
    void ansiModeWrapsRedElementsInEscapes() {
        var ansi = new AsciiSetRenderer(ColorMode.ANSI);
        String out = ansi.renderSet(new SetSnapshot(leaf(10, true), 1), null);
        assertTrue(out.contains("\u001b[31m10\u001b[0m"), out);
    }

    @Test
    void affectedElementIsTheTouchedNodeAndNullForRemove() {
        SetSnapshot s = new SetSnapshot(leaf(1, false), 1);
        assertEquals(10, AsciiSetRenderer.affectedElement(new Add(10, s)));
        assertEquals(20, AsciiSetRenderer.affectedElement(new Compare(20, Direction.LEFT, false, s)));
        assertEquals(30, AsciiSetRenderer.affectedElement(new Rotation(Direction.LEFT, 30, s)));
        assertEquals(40, AsciiSetRenderer.affectedElement(new Recolor(40, Color.RED, Color.BLACK, s)));
        assertNull(AsciiSetRenderer.affectedElement(new Remove(50, s)));
    }
}
