package com.gimlism.translucent.hashmap.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class EventFormatterTest {
    private static MapSnapshot snap() {
        return new MapSnapshot(0, 0, 6, List.of());
    }

    @Test
    void formatsPutAndRemove() {
        assertEquals("PUT a=1 -> bucket 3 (new)",
            EventFormatter.format(new Put("a", 1, null, 3, true, snap())));
        assertEquals("REMOVE a -> bucket 3 (was 1)",
            EventFormatter.format(new Remove("a", 1, 3, snap())));
    }

    @Test
    void formatsTreeEvents() {
        assertEquals("TREEIFY bucket 0",
            EventFormatter.format(new Treeify(0, snap())));
        assertEquals("UNTREEIFY bucket 0",
            EventFormatter.format(new Untreeify(0, snap())));
        assertEquals("ROTATE LEFT @ 7 (bucket 0)",
            EventFormatter.format(new Rotation(0, Direction.LEFT, 7, snap())));
        assertEquals("RECOLOR 7 RED -> BLACK (bucket 0)",
            EventFormatter.format(new Recolor(0, 7, Color.RED, Color.BLACK, snap())));
    }
}
