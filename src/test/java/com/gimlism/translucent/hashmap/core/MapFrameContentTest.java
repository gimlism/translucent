package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.gimlism.translucent.hashmap.consumer.MapRecordingListener;
import com.gimlism.translucent.hashmap.events.ChainSnapshot;
import com.gimlism.translucent.hashmap.events.EntrySnapshot;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.Resize;
import com.gimlism.translucent.hashmap.events.TreeSnapshot;
import com.gimlism.translucent.hashmap.events.Treeify;
import com.gimlism.translucent.hashmap.events.Untreeify;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Asserts the CONTENTS of the announce/settle snapshot frames and chain order across a
 * resize (the review found these were only counted/ordered as events, never inspected).
 */
class MapFrameContentTest {

    private static <T extends MapEvent> T firstOf(MapRecordingListener rec, Class<T> type) {
        return rec.events().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private static List<Object> chainKeys(Object bucket) {
        return assertInstanceOf(ChainSnapshot.class, bucket).entries().stream()
            .map(EntrySnapshot::key).toList();
    }

    @Test
    void treeifyFrameIsTheAnnounceChainNotYetATree() {
        var map = new TeachingHashMap<Integer, String>(); // treeify at 4, minTreeify 8 == cap 8
        var rec = new MapRecordingListener();
        map.addListener(rec);
        for (int k : new int[]{0, 8, 16, 24}) map.put(k, "v" + k);
        // the Treeify frame is emitted BEFORE conversion: bucket 0 is still a chain of all four
        Treeify t = firstOf(rec, Treeify.class);
        assertEquals(List.of(0, 8, 16, 24), chainKeys(t.after().buckets().get(0)));
        // and once the burst settles the live bucket is a tree
        assertInstanceOf(TreeSnapshot.class, map.snapshot().buckets().get(0));
    }

    @Test
    void untreeifyFrameIsTheSettledSurvivorChain() {
        var map = new TeachingHashMap<Integer, String>(8, 100.0f, 4, 2, 8);
        for (int k : new int[]{0, 8, 16, 24}) map.put(k, "v" + k); // treeify
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.remove(0); // 4 -> 3, still a tree
        map.remove(8); // 3 -> 2 (<= untreeifyThreshold) -> untreeify
        // the Untreeify frame shows the settled survivor chain, in insertion order
        Untreeify u = firstOf(rec, Untreeify.class);
        assertEquals(List.of(16, 24), chainKeys(u.after().buckets().get(0)));
    }

    @Test
    void resizePreservesChainInsertionOrder() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(0, "a");   // bucket 0
        map.put(16, "b");  // bucket 0 too (0 & 7 == 16 & 7 == 0), tail-appended -> [0, 16]
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.forceResize(); // cap 8 -> 16; 0 & 15 == 16 & 15 == 0, so both stay in bucket 0

        assertEquals(List.of(0, 16), chainKeys(map.snapshot().buckets().get(0)));
        // the Resize frame carries the same order (head-prepend would reverse it to [16, 0])
        Resize rz = firstOf(rec, Resize.class);
        assertEquals(List.of(0, 16), chainKeys(rz.after().buckets().get(0)));
    }
}
