package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.MapRecordingListener;
import com.gimlism.translucent.hashmap.viz.MapJsonSerializer;
import org.junit.jupiter.api.Test;

class MapJsonSerializerTest {

    private static long count(String haystack, String needle) {
        long n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) n++;
        return n;
    }

    @Test
    void frameCountEqualsEventCountAndTypesAppear() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.put(1, "a");
        map.put(2, "b");

        String json = MapJsonSerializer.toJson(rec.events());
        assertEquals(rec.events().size(), count(json, "\"event\":"));
        assertTrue(json.contains("\"type\":\"Put\""), json);
        assertTrue(json.startsWith("{\"frames\":["), json);
    }

    @Test
    void chainBucketListsEntriesWithHash() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.put(1, "a");

        String json = MapJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"kind\":\"chain\""), json);
        assertTrue(json.contains("\"key\":\"1\""), json);
        assertTrue(json.contains("\"value\":\"a\""), json);
        assertTrue(json.contains("\"hash\":1"), json);
    }

    @Test
    void treeifiedBinSerializesNestedColouredNodes() {
        var map = new TeachingHashMap<Integer, String>(); // cap 8, treeify at 4
        var rec = new MapRecordingListener();
        map.addListener(rec);
        for (int k : new int[]{0, 8, 16, 24}) map.put(k, "v" + k); // all bucket 0 -> treeify

        String json = MapJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"kind\":\"tree\""), json);
        assertTrue(json.contains("\"color\":\"BLACK\""), json);
        assertTrue(json.contains("\"color\":\"RED\""), json);
        assertTrue(json.contains("\"left\":"), json);
        assertTrue(json.contains("\"right\":"), json);
    }

    @Test
    void resizeFrameCarriesNewCapacity() {
        var map = new TeachingHashMap<Integer, String>(); // threshold = 6
        var rec = new MapRecordingListener();
        map.addListener(rec);
        for (int k = 0; k < 7; k++) map.put(k, "v" + k); // size 7 > 6 -> resize to 16

        String json = MapJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"type\":\"Resize\""), json);
        assertTrue(json.contains("\"capacity\":16"), json);
    }

    @Test
    void nullKeyAndValueSerializeAsJsonNull() {
        var map = new TeachingHashMap<Object, Object>();
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.put(null, null);

        String json = MapJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"key\":null"), json);
        assertTrue(json.contains("\"value\":null"), json);
    }
}
