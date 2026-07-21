package com.gimlism.translucent.hashmap.core;

import com.gimlism.translucent.substrate.rbtree.RbEventSink;
import java.util.ArrayList;
import java.util.List;

/**
 * Records rotations/recolours reported by the shared RB kernel, for event-emission
 * assertions. Shared across the map's tree tests (insert + delete) so neither has to
 * reach across into the other's internals.
 */
final class RecordingRbSink implements RbEventSink<TreeNode<Integer, String>> {
    final List<String> log = new ArrayList<>();

    @Override
    public void rotated(com.gimlism.translucent.substrate.rbtree.Direction d, TreeNode<Integer, String> p) {
        log.add("ROT " + d + " " + p.getKey());
    }

    @Override
    public void recolored(TreeNode<Integer, String> n,
            com.gimlism.translucent.substrate.rbtree.Color o,
            com.gimlism.translucent.substrate.rbtree.Color newColor) {
        log.add("COL " + n.getKey() + " " + o + "->" + newColor);
    }
}
