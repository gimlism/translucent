package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.events.ChainSnapshot;
import com.gimlism.translucent.hashmap.events.EmptyBucket;
import com.gimlism.translucent.hashmap.events.EntrySnapshot;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import com.gimlism.translucent.hashmap.events.Put;
import com.gimlism.translucent.hashmap.events.Resize;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsciiEventRenderTest {
    private final AsciiMapRenderer r = new AsciiMapRenderer(new Palette(Palette.Mode.PLAIN));

    @Test
    void renderEventPutsLabelAboveMapAndHighlightsBucket() {
        var snap = new MapSnapshot(2, 1, 1,
            List.of(new EmptyBucket(),
                    new ChainSnapshot(List.of(new EntrySnapshot(1, "one", 1)))));
        var put = new Put(1, "one", null, 1, true, snap);
        String out = r.renderEvent(put);
        String expected = String.join("\n",
            "PUT 1=one -> bucket 1 (new)",  // ConsoleMapEventLogger.format(put)
            "map: cap=2 size=1 threshold=1",
            "  [0] ·",
            "> [1] 1=one");                 // bucket 1 highlighted
        assertEquals(expected, out);
    }

    @Test
    void affectedBucketIsMinusOneForResize() {
        var snap = new MapSnapshot(0, 0, 0, List.of());
        assertEquals(-1, AsciiMapRenderer.affectedBucket(new Resize(8, 16, snap, snap)));
    }

    @Test
    void affectedBucketReadsBucketIndexForBucketEvents() {
        var snap = new MapSnapshot(0, 0, 0, List.of());
        assertEquals(3, AsciiMapRenderer.affectedBucket(new Put("k", "v", null, 3, true, snap)));
    }
}
