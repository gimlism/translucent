package com.gimlism.translucent.substrate.rbtree;

/**
 * Callback through which {@link RedBlackTree} reports structural changes, so a
 * consumer can translate them into its own event vocabulary. Keeps the RB algorithm
 * decoupled from any one structure's event model. The node is passed uncast (type
 * {@code N}) so the consumer can read whatever it stores on the node (an element, a
 * key, …).
 */
public interface RbEventSink<N extends RbNode<N>> {
    void rotated(Direction dir, N pivot);

    void recolored(N node, Color oldColor, Color newColor);

    /** A sink that ignores everything (used where events aren't wanted). */
    static <N extends RbNode<N>> RbEventSink<N> none() {
        return new RbEventSink<>() {
            @Override public void rotated(Direction dir, N pivot) { }
            @Override public void recolored(N node, Color oldColor, Color newColor) { }
        };
    }
}
