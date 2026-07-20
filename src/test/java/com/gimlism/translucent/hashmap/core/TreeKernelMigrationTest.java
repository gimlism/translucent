package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.events.Color;
import com.gimlism.translucent.hashmap.events.Direction;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.Recolor;
import com.gimlism.translucent.hashmap.events.Rotation;
import com.gimlism.translucent.substrate.events.StructureEventListener;
import com.gimlism.translucent.substrate.rbtree.RbNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the substrate-kernel migration: TreeNode inherits its RB structure from the
 * shared kernel, and rotation/recolor events surface as map events with correctly
 * bridged Direction/Color enums.
 */
class TreeKernelMigrationTest {

    // Structural pin: a TreeNode IS an RbNode, so its links/colour come from the
    // shared base — not a regrown local copy.
    @Test
    void treeNodeInheritsFromSubstrateKernel() {
        TreeNode<Integer, String> n = new TreeNode<>(1, 1, "a", null, 0);
        assertInstanceOf(RbNode.class, n, "TreeNode must extend the shared RbNode base");
        // The inherited fields are usable directly (public on RbNode):
        n.red = true;
        n.left = null;
        n.right = null;
        n.parent = null;
        assertTrue(n.red);
    }

    // Bridge pin: driving the map hard enough to treeify a bin and rebalance it
    // emits Rotation/Recolor MAP events whose enums are the map's own (bridged from
    // the substrate kernel's enums inside sinkFor).
    @Test
    void rotationAndRecolorSurfaceAsBridgedMapEvents() {
        TeachingHashMap<CollidingKey, String> map = new TeachingHashMap<>();
        List<MapEvent> events = new ArrayList<>();
        map.addListener((StructureEventListener<MapEvent>) events::add);

        // All keys collide into one bucket, forcing treeify + RB rebalancing.
        for (int i = 0; i < 12; i++) {
            map.put(new CollidingKey(i), "v" + i);
        }

        List<Rotation> rotations = events.stream()
                .filter(e -> e instanceof Rotation).map(e -> (Rotation) e).toList();
        List<Recolor> recolors = events.stream()
                .filter(e -> e instanceof Recolor).map(e -> (Recolor) e).toList();

        assertTrue(!rotations.isEmpty(), "treeify should force at least one rotation");
        assertTrue(!recolors.isEmpty(), "treeify should force at least one recolor");
        // Every emitted enum is the map's own (bridged), never the substrate's:
        for (Rotation r : rotations) {
            assertTrue(r.direction() == Direction.LEFT || r.direction() == Direction.RIGHT,
                    "rotation direction must be a map Direction");
        }
        for (Recolor r : recolors) {
            assertTrue(r.oldColor() != null && r.newColor() != null);
            assertTrue(r.newColor() == Color.RED || r.newColor() == Color.BLACK,
                    "recolor colour must be a map Color");
        }
    }

    // A key whose hashCode collides for every instance, so all entries land in one
    // bucket and the bin treeifies.
    private static final class CollidingKey {
        final int id;
        CollidingKey(int id) { this.id = id; }
        @Override public int hashCode() { return 42; }
        @Override public boolean equals(Object o) {
            return o instanceof CollidingKey k && k.id == id;
        }
    }
}
