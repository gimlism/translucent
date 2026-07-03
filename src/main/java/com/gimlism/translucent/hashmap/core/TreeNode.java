package com.gimlism.translucent.hashmap.core;

import java.util.Objects;

import com.gimlism.translucent.hashmap.events.Color;
import com.gimlism.translucent.hashmap.events.Direction;

/**
 * A red-black tree node for a treeified bin. Extends {@link Node} so it keeps the
 * insertion-order {@code next} thread (used for iteration and resize) while also
 * participating in the tree via {@code parent/left/right}. Ordering across nodes
 * is by hash, then by the monotonic insertion {@code seq}.
 */
class TreeNode<K, V> extends Node<K, V> {
    TreeNode<K, V> parent;
    TreeNode<K, V> left;
    TreeNode<K, V> right;
    TreeNode<K, V> prev;
    boolean red;
    final long seq;

    TreeNode(int hash, K key, V value, Node<K, V> next, long seq) {
        super(hash, key, value, next);
        this.seq = seq;
    }

    /** Climb to the tree root from this node. */
    TreeNode<K, V> root() {
        TreeNode<K, V> r = this;
        while (r.parent != null) r = r.parent;
        return r;
    }

    /** Total order used for tree INSERTION: hash, then insertion seq. */
    static int cmp(int h1, long s1, int h2, long s2) {
        int c = Integer.compare(h1, h2);
        return c != 0 ? c : Long.compare(s1, s2);
    }

    /** Flip a node's colour, reporting only real changes. */
    static <K, V> void setColor(TreeNode<K, V> n, boolean red, TreeEventSink sink) {
        if (n.red != red) {
            Color oldColor = n.red ? Color.RED : Color.BLACK;
            Color newColor = red ? Color.RED : Color.BLACK;
            n.red = red; // mutate BEFORE emitting so the event's snapshot is a true after-frame
            sink.recolored(n.key, oldColor, newColor);
        }
    }

    static <K, V> TreeNode<K, V> rotateLeft(TreeNode<K, V> root, TreeNode<K, V> p, TreeEventSink sink) {
        TreeNode<K, V> r = p.right;
        p.right = r.left;
        if (r.left != null) r.left.parent = p;
        r.parent = p.parent;
        if (p.parent == null) root = r;
        else if (p == p.parent.left) p.parent.left = r;
        else p.parent.right = r;
        r.left = p;
        p.parent = r;
        sink.rotated(Direction.LEFT, p.key);
        return root;
    }

    static <K, V> TreeNode<K, V> rotateRight(TreeNode<K, V> root, TreeNode<K, V> p, TreeEventSink sink) {
        TreeNode<K, V> l = p.left;
        p.left = l.right;
        if (l.right != null) l.right.parent = p;
        l.parent = p.parent;
        if (p.parent == null) root = l;
        else if (p == p.parent.right) p.parent.right = l;
        else p.parent.left = l;
        l.right = p;
        p.parent = l;
        sink.rotated(Direction.RIGHT, p.key);
        return root;
    }

    /**
     * BST-insert {@code x} (ordered by hash then seq), then restore red-black
     * invariants. Returns the new root. {@code x} must be a fresh node not
     * already present in the tree.
     */
    static <K, V> TreeNode<K, V> insert(TreeNode<K, V> root, TreeNode<K, V> x, TreeEventSink sink) {
        x.left = null;
        x.right = null;
        if (root == null) {
            x.parent = null;
            x.red = false; // first node is the black root
            return x;
        }
        TreeNode<K, V> p = root;
        TreeNode<K, V> parent;
        int dir;
        do {
            parent = p;
            dir = cmp(x.hash, x.seq, p.hash, p.seq);
            p = dir < 0 ? p.left : p.right;
        } while (p != null);
        x.parent = parent;
        if (dir < 0) parent.left = x; else parent.right = x;
        x.red = true;
        return insertFixup(root, x, sink);
    }

    private static <K, V> TreeNode<K, V> insertFixup(TreeNode<K, V> root, TreeNode<K, V> x, TreeEventSink sink) {
        while (x.parent != null && x.parent.red) {
            TreeNode<K, V> p = x.parent;
            TreeNode<K, V> g = p.parent; // p is red => p is not root => g != null
            if (p == g.left) {
                TreeNode<K, V> u = g.right;
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
                TreeNode<K, V> u = g.left;
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

    /**
     * Find the node for {@code key} (with the given hash). Descends by hash; on a
     * hash tie with an unequal key, searches both subtrees (a lookup key has no
     * seq to disambiguate). O(log n) when hashes are distinct.
     */
    static <K, V> TreeNode<K, V> find(TreeNode<K, V> p, int hash, Object key) {
        while (p != null) {
            if (hash < p.hash) {
                p = p.left;
            } else if (hash > p.hash) {
                p = p.right;
            } else if (Objects.equals(p.key, key)) {
                return p;
            } else {
                TreeNode<K, V> r = find(p.right, hash, key);
                if (r != null) return r;
                p = p.left;
            }
        }
        return null;
    }

    /**
     * Build a red-black tree over an already-threaded list of tree nodes (linked
     * by {@code next} in insertion order), inserting each in turn. The
     * {@code next} thread is left intact for iteration. Returns the root.
     */
    static <K, V> TreeNode<K, V> build(TreeNode<K, V> first, TreeEventSink sink) {
        TreeNode<K, V> root = null;
        for (TreeNode<K, V> x = first; x != null; ) {
            @SuppressWarnings("unchecked")
            TreeNode<K, V> next = (TreeNode<K, V>) x.next;
            root = insert(root, x, sink);
            x = next;
        }
        return root;
    }

    /**
     * Remove {@code z} from the red-black tree (pointer-based, preserving node
     * identity), restoring invariants. Returns the new root, or null if empty.
     * Touches only tree links (parent/left/right/red), never next/prev.
     */
    static <K, V> TreeNode<K, V> deleteFromTree(TreeNode<K, V> root, TreeNode<K, V> z, TreeEventSink sink) {
        TreeNode<K, V> y = z;               // node removed or moved
        boolean yWasBlack = !y.red;
        TreeNode<K, V> x;                   // replaces y in the tree (may be null)
        TreeNode<K, V> xParent;             // parent of x (tracked because x may be null)

        if (z.left == null) {
            x = z.right;
            xParent = z.parent;
            root = transplant(root, z, z.right);
        } else if (z.right == null) {
            x = z.left;
            xParent = z.parent;
            root = transplant(root, z, z.left);
        } else {
            y = minimum(z.right);           // in-order successor
            yWasBlack = !y.red;
            x = y.right;
            if (y.parent == z) {
                xParent = y;                // x may be null; its parent becomes y
            } else {
                xParent = y.parent;
                root = transplant(root, y, y.right);
                y.right = z.right;
                y.right.parent = y;
            }
            root = transplant(root, z, y);
            y.left = z.left;
            y.left.parent = y;
            setColor(y, z.red, sink);        // y takes z's colour
        }

        if (yWasBlack) {
            root = deleteFixup(root, x, xParent, sink);
        }
        z.parent = null;
        z.left = null;
        z.right = null;
        return root;
    }

    private static <K, V> TreeNode<K, V> transplant(TreeNode<K, V> root, TreeNode<K, V> u, TreeNode<K, V> v) {
        if (u.parent == null) root = v;
        else if (u == u.parent.left) u.parent.left = v;
        else u.parent.right = v;
        if (v != null) v.parent = u.parent;
        return root;
    }

    private static <K, V> TreeNode<K, V> minimum(TreeNode<K, V> n) {
        while (n.left != null) n = n.left;
        return n;
    }

    // Restore the black-height after removing a black node. x is the (possibly
    // null) node that now carries an extra black; xParent is its parent. When x
    // is null the sibling is guaranteed non-null (removing a black means the
    // sibling subtree has black-height >= 1), so `x == xParent.left` is unambiguous.
    private static <K, V> TreeNode<K, V> deleteFixup(
            TreeNode<K, V> root, TreeNode<K, V> x, TreeNode<K, V> xParent, TreeEventSink sink) {
        while (x != root && (x == null || !x.red)) {
            if (x == xParent.left) {
                TreeNode<K, V> w = xParent.right;                 // sibling
                if (w != null && w.red) {                         // case 1
                    setColor(w, false, sink);
                    setColor(xParent, true, sink);
                    root = rotateLeft(root, xParent, sink);
                    w = xParent.right;
                }
                if (w == null
                        || ((w.left == null || !w.left.red) && (w.right == null || !w.right.red))) { // case 2
                    if (w != null) setColor(w, true, sink);
                    x = xParent;
                    xParent = x.parent;
                } else {
                    if (w.right == null || !w.right.red) {        // case 3
                        if (w.left != null) setColor(w.left, false, sink);
                        setColor(w, true, sink);
                        root = rotateRight(root, w, sink);
                        w = xParent.right;
                    }
                    setColor(w, xParent.red, sink);               // case 4
                    setColor(xParent, false, sink);
                    if (w.right != null) setColor(w.right, false, sink);
                    root = rotateLeft(root, xParent, sink);
                    x = root;
                    xParent = null;
                }
            } else {                                              // mirror image
                TreeNode<K, V> w = xParent.left;
                if (w != null && w.red) {
                    setColor(w, false, sink);
                    setColor(xParent, true, sink);
                    root = rotateRight(root, xParent, sink);
                    w = xParent.left;
                }
                if (w == null
                        || ((w.right == null || !w.right.red) && (w.left == null || !w.left.red))) {
                    if (w != null) setColor(w, true, sink);
                    x = xParent;
                    xParent = x.parent;
                } else {
                    if (w.left == null || !w.left.red) {
                        if (w.right != null) setColor(w.right, false, sink);
                        setColor(w, true, sink);
                        root = rotateLeft(root, w, sink);
                        w = xParent.left;
                    }
                    setColor(w, xParent.red, sink);
                    setColor(xParent, false, sink);
                    if (w.left != null) setColor(w.left, false, sink);
                    root = rotateRight(root, xParent, sink);
                    x = root;
                    xParent = null;
                }
            }
        }
        if (x != null) setColor(x, false, sink);
        return root;
    }
}
