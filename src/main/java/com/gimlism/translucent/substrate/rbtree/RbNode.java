package com.gimlism.translucent.substrate.rbtree;

/**
 * Base of a red-black tree node. Self-typed ({@code N extends RbNode<N>}) so the
 * shared {@link RedBlackTree} algorithm returns and assigns the concrete node type
 * with zero casts — the same F-bounded pattern as {@code Enum<E extends Enum<E>>}.
 *
 * <p>The link and colour fields are {@code public} because the rebalancing kernel
 * (this package) and each per-structure consumer (a different package) both
 * manipulate them directly. This is an internal teaching-substrate base, not a
 * general-purpose public API.
 */
public abstract class RbNode<N extends RbNode<N>> {
    public N parent;
    public N left;
    public N right;
    public boolean red;
}
