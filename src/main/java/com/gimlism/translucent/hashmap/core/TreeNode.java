package com.gimlism.translucent.hashmap.core;

import java.util.Objects;

import com.gimlism.translucent.substrate.rbtree.RbEventSink;
import com.gimlism.translucent.substrate.rbtree.RbNode;
import com.gimlism.translucent.substrate.rbtree.RedBlackTree;

/**
 * A red-black tree node for a treeified bin. Extends the shared {@link RbNode}
 * kernel base (contributing {@code parent/left/right/red} and, via
 * {@link RedBlackTree}, the rotate/fixup/delete algorithm) and implements
 * {@link Node} so it keeps the insertion-order {@code next} thread used for
 * iteration and resize. Ordering across nodes is by hash, then by the monotonic
 * insertion {@code seq}.
 */
class TreeNode<K, V> extends RbNode<TreeNode<K, V>> implements Node<K, V> {
    final int hash;
    final K key;
    V value;
    Node<K, V> next;
    TreeNode<K, V> prev;
    final long seq;

    TreeNode(int hash, K key, V value, Node<K, V> next, long seq) {
        this.hash = hash;
        this.key = key;
        this.value = value;
        this.next = next;
        this.seq = seq;
    }

    @Override public int hash() { return hash; }
    @Override public K getKey() { return key; }
    @Override public V getValue() { return value; }
    @Override public Node<K, V> next() { return next; }
    @Override public void setNext(Node<K, V> next) { this.next = next; }

    @Override
    public V setValue(V newValue) {
        V old = value;
        value = newValue;
        return old;
    }

    @Override public boolean equals(Object o) { return Entries.equals(this, o); }
    @Override public int hashCode() { return Entries.hashCode(this); }
    @Override public String toString() { return Entries.toString(this); }

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

    /**
     * BST-insert {@code x} (ordered by hash then seq), then restore red-black
     * invariants via the shared kernel. Returns the new root. {@code x} must be a
     * fresh node not already present in the tree.
     */
    static <K, V> TreeNode<K, V> insert(
            TreeNode<K, V> root, TreeNode<K, V> x, RbEventSink<TreeNode<K, V>> sink) {
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
        return RedBlackTree.insertFixup(root, x, sink);
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
    static <K, V> TreeNode<K, V> build(TreeNode<K, V> first, RbEventSink<TreeNode<K, V>> sink) {
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
     * Remove {@code z} from the red-black tree via the shared kernel (pointer-based,
     * preserving node identity). Returns the new root, or null if empty. Touches only
     * tree links (parent/left/right/red), never next/prev.
     */
    static <K, V> TreeNode<K, V> deleteFromTree(
            TreeNode<K, V> root, TreeNode<K, V> z, RbEventSink<TreeNode<K, V>> sink) {
        return RedBlackTree.deleteFromTree(root, z, sink);
    }
}
