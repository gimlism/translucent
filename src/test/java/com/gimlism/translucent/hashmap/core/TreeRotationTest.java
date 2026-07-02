package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.gimlism.translucent.hashmap.events.Direction;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeRotationTest {
    static final class Rec implements TreeEventSink {
        final List<String> log = new ArrayList<>();
        public void rotated(Direction d, Object p) { log.add("ROT " + d + " " + p); }
        public void recolored(Object k, com.gimlism.translucent.hashmap.events.Color o,
                              com.gimlism.translucent.hashmap.events.Color n) {
            log.add("COL " + k + " " + o + "->" + n);
        }
    }

    // Build   p            and left-rotate about p to get   r
    //        / \                                            / \
    //       a   r                                          p   c
    //          / \                                        / \
    //         b   c                                      a   b
    @Test
    void leftRotationRestructuresAndReports() {
        TreeNode<Integer, String> p = new TreeNode<>(2, 2, "p", null, 0);
        TreeNode<Integer, String> a = new TreeNode<>(1, 1, "a", null, 1);
        TreeNode<Integer, String> r = new TreeNode<>(4, 4, "r", null, 2);
        TreeNode<Integer, String> b = new TreeNode<>(3, 3, "b", null, 3);
        TreeNode<Integer, String> c = new TreeNode<>(5, 5, "c", null, 4);
        p.left = a; a.parent = p;
        p.right = r; r.parent = p;
        r.left = b; b.parent = r;
        r.right = c; c.parent = r;

        Rec rec = new Rec();
        TreeNode<Integer, String> newRoot = TreeNode.rotateLeft(p, p, rec);

        assertSame(r, newRoot);
        assertSame(p, r.left);
        assertSame(c, r.right);
        assertSame(a, p.left);
        assertSame(b, p.right);
        assertSame(r, p.parent);
        assertSame(b, p.right); // b moved under p
        assertEquals(List.of("ROT LEFT 2"), rec.log);
    }

    @Test
    void rightRotationIsInverseOfLeft() {
        TreeNode<Integer, String> r = new TreeNode<>(4, 4, "r", null, 0);
        TreeNode<Integer, String> p = new TreeNode<>(2, 2, "p", null, 1);
        TreeNode<Integer, String> c = new TreeNode<>(5, 5, "c", null, 2);
        TreeNode<Integer, String> a = new TreeNode<>(1, 1, "a", null, 3);
        TreeNode<Integer, String> b = new TreeNode<>(3, 3, "b", null, 4);
        r.left = p; p.parent = r;
        r.right = c; c.parent = r;
        p.left = a; a.parent = p;
        p.right = b; b.parent = p;

        Rec rec = new Rec();
        TreeNode<Integer, String> newRoot = TreeNode.rotateRight(r, r, rec);

        assertSame(p, newRoot);
        assertSame(a, p.left);
        assertSame(r, p.right);
        assertSame(b, r.left);
        assertSame(c, r.right);
        assertEquals(List.of("ROT RIGHT 4"), rec.log);
    }
}
