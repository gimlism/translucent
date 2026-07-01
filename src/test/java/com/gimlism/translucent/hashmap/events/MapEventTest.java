package com.gimlism.translucent.hashmap.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MapEventTest {
    private static MapSnapshot emptySnap() {
        return new MapSnapshot(8, 0, 6, List.of());
    }

    @Test
    void putExposesCommonAfterAccessor() {
        MapEvent e = new Put("k", 1, null, 3, true, emptySnap());
        assertEquals(8, e.after().capacity());
    }

    @Test
    void exhaustiveSwitchOverAllEventKinds() {
        List<MapEvent> events = List.of(
            new Put("k", 1, null, 0, true, emptySnap()),
            new Remove("k", 1, 0, emptySnap()),
            new Collision("k", 0, 1, 2, emptySnap()),
            new Resize(8, 16, emptySnap(), emptySnap()),
            new Treeify(0, emptySnap()),
            new Untreeify(0, emptySnap()),
            new Rotation(0, Direction.LEFT, "k", emptySnap()),
            new Recolor(0, "k", Color.RED, Color.BLACK, emptySnap()));
        for (MapEvent e : events) {
            String name = switch (e) {
                case Put p -> "put";
                case Remove r -> "remove";
                case Collision c -> "collision";
                case Resize rs -> "resize";
                case Treeify t -> "treeify";
                case Untreeify u -> "untreeify";
                case Rotation ro -> "rotation";
                case Recolor rc -> "recolor";
            };
            assertTrue(name.length() > 0);
        }
        assertEquals(8, events.size());
    }
}
