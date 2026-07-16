package com.gimlism.translucent.substrate.rbtree;

/**
 * Structure-neutral red-black rebalancing over a self-typed {@link RbNode}. Callers
 * own ordering, search, and node creation; they link a fresh red leaf at its BST
 * position and call {@link #insertFixup}, or locate a node and call
 * {@link #deleteFromTree}. This class touches only {@code parent/left/right/red} and
 * reports rotations/recolors through the {@link RbEventSink}.
 *
 * <p>Every colour flip and relink is applied BEFORE the sink fires, so a listener's
 * snapshot is a true after-image (the "snapshot-before-settled" discipline).
 */
public final class RedBlackTree {
    private RedBlackTree() { }

    /** Flip a node's colour, reporting only real changes (mutating before emitting). */
    static <N extends RbNode<N>> void setColor(N n, boolean red, RbEventSink<N> sink) {
        if (n.red != red) {
            Color oldColor = n.red ? Color.RED : Color.BLACK;
            Color newColor = red ? Color.RED : Color.BLACK;
            n.red = red;
            sink.recolored(n, oldColor, newColor);
        }
    }

    static <N extends RbNode<N>> N rotateLeft(N root, N p, RbEventSink<N> sink) {
        N r = p.right;
        p.right = r.left;
        if (r.left != null) r.left.parent = p;
        r.parent = p.parent;
        if (p.parent == null) root = r;
        else if (p == p.parent.left) p.parent.left = r;
        else p.parent.right = r;
        r.left = p;
        p.parent = r;
        sink.rotated(Direction.LEFT, p);
        return root;
    }

    static <N extends RbNode<N>> N rotateRight(N root, N p, RbEventSink<N> sink) {
        N l = p.left;
        p.left = l.right;
        if (l.right != null) l.right.parent = p;
        l.parent = p.parent;
        if (p.parent == null) root = l;
        else if (p == p.parent.right) p.parent.right = l;
        else p.parent.left = l;
        l.right = p;
        p.parent = l;
        sink.rotated(Direction.RIGHT, p);
        return root;
    }

    /**
     * Restore red-black invariants after {@code x} was linked as a red leaf at its
     * BST position. Returns the (possibly new) root.
     */
    public static <N extends RbNode<N>> N insertFixup(N root, N x, RbEventSink<N> sink) {
        while (x.parent != null && x.parent.red) {
            N p = x.parent;
            N g = p.parent; // p is red => not root => g != null
            if (p == g.left) {
                N u = g.right;
                if (u != null && u.red) {
                    setColor(p, false, sink);
                    setColor(u, false, sink);
                    setColor(g, true, sink);
                    x = g;
                } else {
                    if (x == p.right) {
                        x = p;
                        root = rotateLeft(root, x, sink);
                        p = x.parent;
                        g = p.parent;
                    }
                    setColor(p, false, sink);
                    setColor(g, true, sink);
                    root = rotateRight(root, g, sink);
                }
            } else {
                N u = g.left;
                if (u != null && u.red) {
                    setColor(p, false, sink);
                    setColor(u, false, sink);
                    setColor(g, true, sink);
                    x = g;
                } else {
                    if (x == p.left) {
                        x = p;
                        root = rotateRight(root, x, sink);
                        p = x.parent;
                        g = p.parent;
                    }
                    setColor(p, false, sink);
                    setColor(g, true, sink);
                    root = rotateLeft(root, g, sink);
                }
            }
        }
        if (root.red) setColor(root, false, sink);
        return root;
    }
}
