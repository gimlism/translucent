package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.Recolor;
import com.gimlism.translucent.hashmap.events.Rotation;
import com.gimlism.translucent.substrate.events.StructureEventListener;
import com.gimlism.translucent.substrate.rbtree.RbEventSink;
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

    // Faithfulness pin (replay-and-compare): a single deterministic treeify build is
    // driven TWICE over the identical (hash, seq) node sequence -- once through the map's
    // put() path, whose sinkFor turns each kernel callback into a Rotation/Recolor map
    // event, and once directly through the raw substrate kernel (TreeNode.build with a
    // recording RbEventSink<TreeNode<...>> capturing the kernel's own enums).
    //
    // Both drive the same red-black insertion (ordered purely by (hash, seq), see
    // TreeNode.cmp), so the two callback streams MUST line up element-for-element.
    // Comparing by enum .name() means a sinkFor that swaps a direction, swaps
    // oldColor/newColor, or drops an event flips or shortens the map stream but not the
    // raw one, breaking the match. There is no longer any bridge() to swap -- the map
    // emits the substrate enums straight through -- so this pins that sinkFor's wiring
    // stays faithful. A same-cardinality check like "direction == LEFT || direction ==
    // RIGHT" is vacuously true for a 2-value enum and can't catch that -- do not reduce
    // this back to that shape.
    @Test
    void mapEventStreamFaithfullyMirrorsKernelStream() {
        // n == treeifyThreshold below: the n-th put's chain length hits the
        // threshold, triggering exactly one treeifyBin build of n nodes (seq 0..n-1)
        // and no further individual tree inserts.
        int n = 8;
        // capacity 16 >= minTreeifyCapacity 8, so treeifyBin builds rather than
        // resizing; and n=8 stays under threshold 16*0.75=12, so no resize
        // interleaves with the build either. 8 ascending (hash-tied, seq-ordered)
        // inserts are enough to force both a rotation and a recolor.
        TeachingHashMap<CollidingKey, String> map =
                new TeachingHashMap<>(16, 0.75f, n, 2, 8);
        List<MapEvent> events = new ArrayList<>();
        map.addListener((StructureEventListener<MapEvent>) events::add);
        for (int i = 0; i < n; i++) {
            map.put(new CollidingKey(i), "v" + i);
        }
        List<String> bridged = events.stream()
                .filter(e -> e instanceof Rotation || e instanceof Recolor)
                .map(e -> e instanceof Rotation r
                        ? "ROT:" + r.direction().name()
                        : "COL:" + ((Recolor) e).oldColor().name() + "->" + ((Recolor) e).newColor().name())
                .toList();

        // Independent raw replay of the identical node sequence, recording the
        // substrate (pre-bridge) enums.
        List<String> raw = new ArrayList<>();
        RbEventSink<TreeNode<CollidingKey, String>> rawSink = new RbEventSink<>() {
            @Override public void rotated(
                    com.gimlism.translucent.substrate.rbtree.Direction dir,
                    TreeNode<CollidingKey, String> pivot) {
                raw.add("ROT:" + dir.name());
            }
            @Override public void recolored(TreeNode<CollidingKey, String> node,
                    com.gimlism.translucent.substrate.rbtree.Color oldColor,
                    com.gimlism.translucent.substrate.rbtree.Color newColor) {
                raw.add("COL:" + oldColor.name() + "->" + newColor.name());
            }
        };
        int rawHash = new CollidingKey(0).hashCode(); // Node.hash is the raw hashCode, no spreading
        TreeNode<CollidingKey, String> first = null;
        TreeNode<CollidingKey, String> prev = null;
        for (int i = 0; i < n; i++) {
            TreeNode<CollidingKey, String> t = new TreeNode<>(rawHash, new CollidingKey(i), "v" + i, null, i);
            if (prev == null) first = t; else prev.setNext(t);
            prev = t;
        }
        TreeNode.build(first, rawSink);

        assertFalse(raw.isEmpty(), "raw replay should force at least one rotation/recolor");
        assertTrue(raw.stream().anyMatch(s -> s.startsWith("ROT:")), "expected at least one rotation");
        assertTrue(raw.stream().anyMatch(s -> s.startsWith("COL:")), "expected at least one recolor");

        // The pin: element-wise equality means a swapped bridge() (which flips a
        // Direction/Color .name() in `bridged` but not in `raw`) fails this assertion.
        assertEquals(raw, bridged,
                "bridged map event stream must match the raw substrate stream element-wise "
                        + "-- a swapped bridge() would flip a Direction/Color .name() here");
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
