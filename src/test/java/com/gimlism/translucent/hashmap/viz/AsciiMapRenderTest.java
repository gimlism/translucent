package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gimlism.translucent.hashmap.events.BucketSnapshot;
import com.gimlism.translucent.hashmap.events.ChainSnapshot;
import com.gimlism.translucent.hashmap.events.EmptyBucket;
import com.gimlism.translucent.substrate.rbtree.Color;
import com.gimlism.translucent.hashmap.events.EntrySnapshot;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import com.gimlism.translucent.hashmap.events.TreeNodeSnapshot;
import com.gimlism.translucent.hashmap.events.TreeSnapshot;
import com.gimlism.translucent.substrate.viz.ColorMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsciiMapRenderTest {
    private final AsciiMapRenderer r = new AsciiMapRenderer(new Palette(ColorMode.PLAIN));

    @Test
    void rendersHeaderEmptyAndChainWithHighlight() {
        List<BucketSnapshot> buckets = List.of(
            new ChainSnapshot(List.of(new EntrySnapshot(0, "zero", 0), new EntrySnapshot(8, "eight", 8))),
            new EmptyBucket(),
            new ChainSnapshot(List.of(new EntrySnapshot(2, "two", 2))));
        var snap = new MapSnapshot(3, 3, 2, buckets);
        String expected = String.join("\n",
            "map: cap=3 size=3 threshold=2",
            "  [0] 0=zero -> 8=eight",
            "  [1] ·",
            "> [2] 2=two");
        assertEquals(expected, r.renderMap(snap, 2));
    }

    @Test
    void rendersTreeBucketIndentedUnderHeader() {
        TreeNodeSnapshot root = new TreeNodeSnapshot(16, "v16", Color.BLACK,
            new TreeNodeSnapshot(8, "v8", Color.RED, null, null),
            new TreeNodeSnapshot(24, "v24", Color.RED, null, null));
        var snap = new MapSnapshot(1, 3, 0, List.of(new TreeSnapshot(root)));
        String expected = String.join("\n",
            "map: cap=1 size=3 threshold=0",
            "> [0] tree:",
            "          ┌─ 24(R)",
            "      16(B)",
            "          └─ 8(R)");
        assertEquals(expected, r.renderMap(snap, 0));
    }
}
