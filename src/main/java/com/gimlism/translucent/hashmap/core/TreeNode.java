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
            sink.recolored(n.key, n.red ? Color.RED : Color.BLACK, red ? Color.RED : Color.BLACK);
            n.red = red;
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
}
