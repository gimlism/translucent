package com.gimlism.translucent.hashmap.core;

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
}
