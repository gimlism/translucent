package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.events.BucketSnapshot;
import com.gimlism.translucent.hashmap.events.ChainSnapshot;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import com.gimlism.translucent.hashmap.events.Resize;
import com.gimlism.translucent.hashmap.events.TreeNodeSnapshot;
import com.gimlism.translucent.hashmap.events.TreeSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeResizeTest {
    @Test
    void treeBinSplitsAcrossResizePreservingEntries() {
        // small cap, high load factor so we control when resize fires
        var map = new TeachingHashMap<Integer, String>(8, 10.0f, 4, 2, 8);
        // keys 0,8,16,24,32,40 all in bucket 0 at cap 8 -> treeify at 4th
        int[] keys = {0, 8, 16, 24, 32, 40};
        for (int k : keys) map.put(k, "v" + k);
        assertTrue(map.isTreeBin(0), "precondition: bucket 0 treeified");

        map.forceResize(); // package-private test hook added in this task

        // capacity doubled to 16: with (cap-1)&hash, 0/16/32 -> bucket 0, 8/24/40 -> bucket 8
        assertEquals(16, map.capacity());
        for (int k : keys) assertEquals("v" + k, map.get(k), "entry preserved across split");
        assertEquals(6, map.size());
    }

    @Test
    void autoResizeRightAfterTreeifyPreservesTreeBin() {
        // default config: cap 8, threshold 6, treeify 4, minTreeify 8
        var map = new TeachingHashMap<Integer, String>();
        map.put(1, "a");   // bucket 1
        map.put(2, "b");   // bucket 2
        map.put(3, "c");   // bucket 3
        // 4 colliding keys into bucket 0; the 4th treeifies bucket 0, and the
        // 7th put overall makes size 7 > threshold 6, auto-firing resize()
        for (int k : new int[]{0, 8, 16, 24}) map.put(k, "v" + k);
        assertEquals(16, map.capacity());
        assertEquals(7, map.size());
        // every entry still reachable (would fail if the tree bin was corrupted by resize)
        assertEquals("a", map.get(1));
        assertEquals("b", map.get(2));
        assertEquals("c", map.get(3));
        for (int k : new int[]{0, 8, 16, 24}) assertEquals("v" + k, map.get(k));
    }

    @Test
    void resizeSplitsTreeBinIntoIndependentHalves() {
        // Structural probe: get() alone cannot detect tree-bin corruption on split,
        // because the pre-fix resize only rethreads `next` and leaves parent/left/right
        // intact, so TreeNode.find still walks the complete (shared) tree from either
        // bucket head. This test reads the Resize after-snapshot, which renders tree
        // bins by traversing the tree. Pre-fix the two halves share one root, so the
        // tree renders under BOTH bucket j and bucket j+oldCap and the total entry
        // count double-counts (> size); post-fix each half is an independent tree and
        // the total equals size.
        var map = new TeachingHashMap<Integer, String>(8, 10.0f, 4, 2, 8);
        int[] keys = {0, 8, 16, 24, 32, 40};
        for (int k : keys) map.put(k, "v" + k);
        assertTrue(map.isTreeBin(0), "precondition: bucket 0 treeified");

        List<Resize> resizes = new ArrayList<>();
        map.addListener(e -> { if (e instanceof Resize r) resizes.add(r); });
        map.forceResize();

        assertEquals(1, resizes.size(), "exactly one resize event");
        MapSnapshot after = resizes.get(0).after();
        assertEquals(map.size(), countEntries(after),
            "after-snapshot entry count must equal size (halves must not share a tree)");
        assertEquals(6, countEntries(after));
    }

    private static int countEntries(MapSnapshot snapshot) {
        int total = 0;
        for (BucketSnapshot bucket : snapshot.buckets()) {
            if (bucket instanceof ChainSnapshot chain) {
                total += chain.entries().size();
            } else if (bucket instanceof TreeSnapshot tree) {
                total += countTree(tree.root());
            }
        }
        return total;
    }

    private static int countTree(TreeNodeSnapshot node) {
        if (node == null) return 0;
        return 1 + countTree(node.left()) + countTree(node.right());
    }
}
