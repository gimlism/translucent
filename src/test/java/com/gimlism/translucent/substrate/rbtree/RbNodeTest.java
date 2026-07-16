package com.gimlism.translucent.substrate.rbtree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RbNodeTest {

    /** Minimal concrete node proving the self-type base is usable by a consumer. */
    static final class IntNode extends RbNode<IntNode> {
        final int key;
        IntNode(int key) { this.key = key; }
    }

    @Test
    void freshNodeHasNullLinksAndIsNotRed() {
        IntNode n = new IntNode(5);
        assertNull(n.parent);
        assertNull(n.left);
        assertNull(n.right);
        assertFalse(n.red);
    }

    @Test
    void linksAreTheConcreteNodeType() {
        IntNode a = new IntNode(1);
        IntNode b = new IntNode(2);
        a.right = b;
        b.parent = a;
        // No cast needed: a.right is IntNode, so .key is directly reachable.
        assertEquals(2, a.right.key);
        assertSame(a, b.parent);
    }

    @Test
    void noneSinkSwallowsCallbacks() {
        IntNode n = new IntNode(7);
        RbEventSink<IntNode> sink = RbEventSink.none();
        // Must not throw and must not record anything observable.
        sink.rotated(Direction.LEFT, n);
        sink.recolored(n, Color.RED, Color.BLACK);
    }

    @Test
    void recordingSinkSeesNodesUncast() {
        IntNode pivot = new IntNode(3);
        List<Integer> rotations = new ArrayList<>();
        RbEventSink<IntNode> sink = new RbEventSink<>() {
            @Override public void rotated(Direction dir, IntNode p) { rotations.add(p.key); }
            @Override public void recolored(IntNode node, Color o, Color n) { }
        };
        sink.rotated(Direction.RIGHT, pivot);
        assertEquals(List.of(3), rotations);
    }
}
