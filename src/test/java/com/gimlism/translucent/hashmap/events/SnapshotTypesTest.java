package com.gimlism.translucent.hashmap.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotTypesTest {
    @Test
    void mapSnapshotHoldsBuckets() {
        var chain = new ChainSnapshot(List.of(new EntrySnapshot("a", 1, 97)));
        var snap = new MapSnapshot(8, 1, 6, List.of(new EmptyBucket(), chain));
        assertEquals(8, snap.capacity());
        assertEquals(1, snap.size());
        assertInstanceOf(EmptyBucket.class, snap.buckets().get(0));
        assertInstanceOf(ChainSnapshot.class, snap.buckets().get(1));
    }

    @Test
    void bucketSnapshotIsSealedOverThreeCases() {
        BucketSnapshot b = new TreeSnapshot(
            new TreeNodeSnapshot("k", "v", Color.BLACK, null, null));
        String kind = switch (b) {
            case EmptyBucket e -> "empty";
            case ChainSnapshot c -> "chain";
            case TreeSnapshot t -> "tree";
        };
        assertEquals("tree", kind);
    }

    @Test
    void chainSnapshotListIsUnmodifiable() {
        var chain = new ChainSnapshot(List.copyOf(new ArrayList<>(
            List.of(new EntrySnapshot("a", 1, 97)))));
        assertThrows(UnsupportedOperationException.class,
            () -> chain.entries().add(new EntrySnapshot("b", 2, 98)));
    }
}
