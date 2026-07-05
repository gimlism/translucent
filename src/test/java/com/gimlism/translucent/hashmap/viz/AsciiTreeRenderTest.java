package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gimlism.translucent.hashmap.events.Color;
import com.gimlism.translucent.hashmap.events.TreeNodeSnapshot;
import org.junit.jupiter.api.Test;

class AsciiTreeRenderTest {
    private static TreeNodeSnapshot leaf(Object k, Color c) {
        return new TreeNodeSnapshot(k, "v" + k, c, null, null);
    }

    @Test
    void rendersSidewaysWithConnectorsAndDepthIndent() {
        // 16(B) { left: 8(B){ left:4(R), right:12(R) }, right: 24(B) }
        TreeNodeSnapshot tree = new TreeNodeSnapshot(16, "v16", Color.BLACK,
            new TreeNodeSnapshot(8, "v8", Color.BLACK, leaf(4, Color.RED), leaf(12, Color.RED)),
            leaf(24, Color.BLACK));
        var r = new AsciiMapRenderer(new Palette(Palette.Mode.PLAIN));
        String expected = String.join("\n",
            "    ┌─ 24(B)",
            "16(B)",
            "        ┌─ 12(R)",
            "    └─ 8(B)",
            "        └─ 4(R)");
        assertEquals(expected, r.renderTree(tree));
    }

    @Test
    void singleNodeRendersJustTheRoot() {
        var r = new AsciiMapRenderer(new Palette(Palette.Mode.PLAIN));
        assertEquals("42(B)", r.renderTree(leaf(42, Color.BLACK)));
    }
}
