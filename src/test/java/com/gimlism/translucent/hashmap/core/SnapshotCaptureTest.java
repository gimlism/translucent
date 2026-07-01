package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.gimlism.translucent.hashmap.events.ChainSnapshot;
import com.gimlism.translucent.hashmap.events.EmptyBucket;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import org.junit.jupiter.api.Test;

class SnapshotCaptureTest {
    @Test
    void snapshotReflectsCapacitySizeAndBuckets() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(0, "zero");
        map.put(8, "eight"); // bucket 0 chain of 2
        map.put(1, "one");   // bucket 1 chain of 1
        MapSnapshot snap = map.snapshot();
        assertEquals(8, snap.capacity());
        assertEquals(3, snap.size());
        assertEquals(8, snap.buckets().size());
        assertInstanceOf(ChainSnapshot.class, snap.buckets().get(0));
        assertInstanceOf(ChainSnapshot.class, snap.buckets().get(1));
        assertInstanceOf(EmptyBucket.class, snap.buckets().get(2));
        ChainSnapshot bucket0 = (ChainSnapshot) snap.buckets().get(0);
        assertEquals(2, bucket0.entries().size());
        assertEquals(0, bucket0.entries().get(0).key());
        assertEquals(8, bucket0.entries().get(1).key());
    }

    @Test
    void snapshotIsIndependentOfLaterMutations() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(0, "zero");
        MapSnapshot snap = map.snapshot();
        map.put(8, "eight");
        map.remove(0);
        // earlier snapshot unchanged
        ChainSnapshot bucket0 = (ChainSnapshot) snap.buckets().get(0);
        assertEquals(1, bucket0.entries().size());
        assertEquals("zero", bucket0.entries().get(0).value());
        assertEquals(1, snap.size());
    }
}
