package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.gimlism.translucent.hashmap.events.ChainSnapshot;
import com.gimlism.translucent.hashmap.events.Color;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import com.gimlism.translucent.hashmap.events.TreeNodeSnapshot;
import com.gimlism.translucent.hashmap.events.TreeSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeSnapshotTest {
    @Test
    void treeBinRendersAsTreeSnapshotWithBlackRoot() {
        var map = new TeachingHashMap<Integer, String>();
        for (int k : new int[]{0, 8, 16, 24, 32}) map.put(k, "v" + k);
        MapSnapshot snap = map.snapshot();
        assertInstanceOf(TreeSnapshot.class, snap.buckets().get(0));
        TreeSnapshot ts = (TreeSnapshot) snap.buckets().get(0);
        assertEquals(Color.BLACK, ts.root().color(), "root must be black");
        // all five keys present in the snapshot tree
        List<Object> keys = new ArrayList<>();
        collect(ts.root(), keys);
        assertEquals(5, keys.size());
        assertEquals(List.of(0, 8, 16, 24, 32), keys.stream().sorted().toList());
    }

    @Test
    void chainBinStillRendersAsChainSnapshot() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(1, "a");
        map.put(9, "b"); // bucket 1, length 2, no treeify
        MapSnapshot snap = map.snapshot();
        assertInstanceOf(ChainSnapshot.class, snap.buckets().get(1));
    }

    private static void collect(TreeNodeSnapshot n, List<Object> out) {
        if (n == null) return;
        collect(n.left(), out);
        out.add(n.key());
        collect(n.right(), out);
    }
}
