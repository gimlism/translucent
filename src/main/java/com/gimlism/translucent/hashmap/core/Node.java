package com.gimlism.translucent.hashmap.core;

import java.util.Map;

/**
 * A single map entry, in either a plain chain ({@link ChainNode}) or a treeified
 * bin ({@link TreeNode}). The {@code next} thread links entries in insertion order
 * for iteration and resize; {@code hash} is the raw key hashCode (no spreading).
 */
interface Node<K, V> extends Map.Entry<K, V> {
    int hash();

    Node<K, V> next();

    void setNext(Node<K, V> next);
}
