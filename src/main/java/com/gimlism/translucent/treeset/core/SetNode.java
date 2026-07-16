package com.gimlism.translucent.treeset.core;

import com.gimlism.translucent.substrate.rbtree.RbNode;

/**
 * A red-black tree node holding one set element. Self-typed through
 * {@link RbNode} so the shared rebalancing kernel manipulates {@code SetNode} links
 * directly. No value, no insertion thread — unlike the map's {@code TreeNode}, the
 * element is the whole payload.
 */
class SetNode<E> extends RbNode<SetNode<E>> {
    final E element;

    SetNode(E element) {
        this.element = element;
    }
}
