package com.gimlism.translucent.arraylist.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.consumer.ListRecordingListener;
import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import org.junit.jupiter.api.Test;

class ListJsonSerializerTest {

    private static long count(String haystack, String needle) {
        long n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) n++;
        return n;
    }

    /** Records the standard story (append past cap → grow → insert → set → remove). */
    private static ListRecordingListener story() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        for (String s : new String[]{"a", "b", "c", "d", "e"}) list.add(s); // 5th append grows 4->6
        list.add(2, "x"); // insert with a shift burst
        list.set(0, "A");
        list.remove(1);   // remove with a shift burst
        return rec;
    }

    @Test
    void frameCountEqualsEventCountAndWrapped() {
        var rec = story();
        String json = ListJsonSerializer.toJson(rec.events());
        assertEquals(rec.events().size(), count(json, "\"event\":"));
        assertTrue(json.startsWith("{\"frames\":["), json);
    }

    @Test
    void appendCarriesTypeLabelAndIndex() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add("a");
        String json = ListJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"type\":\"Append\""), json);
        assertTrue(json.contains("\"label\":\"APPEND a @ 0\""), json); // label comes from ListEventFormatter
        assertTrue(json.contains("\"index\":0"), json);
    }

    @Test
    void insertSetRemoveCarryLabelsAndIndices() {
        String json = ListJsonSerializer.toJson(story().events());
        assertTrue(json.contains("\"type\":\"Insert\""), json);
        assertTrue(json.contains("\"label\":\"INSERT x @ 2\""), json); // index embedded in the label
        assertTrue(json.contains("\"type\":\"Set\""), json);
        assertTrue(json.contains("\"label\":\"SET 0 = A (was a)\""), json);
        assertTrue(json.contains("\"type\":\"RemoveAt\""), json);
        assertTrue(json.contains("\"label\":\"REMOVE @ 1 (was b)\""), json);
    }

    @Test
    void growCarriesOldAndNewCapacity() {
        String json = ListJsonSerializer.toJson(story().events());
        assertTrue(json.contains("\"type\":\"Grow\""), json);
        assertTrue(json.contains("\"oldCapacity\":4"), json);
        assertTrue(json.contains("\"newCapacity\":6"), json);
    }

    @Test
    void shiftCarriesFromAndToIndex() {
        String json = ListJsonSerializer.toJson(story().events());
        assertTrue(json.contains("\"type\":\"Shift\""), json);
        assertTrue(json.contains("\"fromIndex\":"), json);
        assertTrue(json.contains("\"toIndex\":"), json);
    }

    @Test
    void slotsEncodeFilledAndEmpty() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add("a"); // size 1, capacity 4 -> one filled slot, three empty
        String json = ListJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"filled\":true"), json);
        assertTrue(json.contains("\"filled\":false"), json);
        assertTrue(json.contains("\"element\":\"a\""), json);
    }

    @Test
    void nullElementEncodesAsJsonNull() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add(null);
        String json = ListJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"element\":null"), json);
    }

    @Test
    void inlineScriptUnsafeCharsEscaped() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add("</script>");
        String json = ListJsonSerializer.toJson(rec.events());
        assertFalse(json.contains("</script>"), json);   // '<' escaped by JsonWriter
        assertTrue(json.contains("\\u003c"), json);
    }

    @Test
    void toFrameHasNoWrapper() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);
        list.add("a");
        String frame = ListJsonSerializer.toFrame(rec.events().get(0));
        assertFalse(frame.startsWith("{\"frames\""), frame);
        assertTrue(frame.contains("\"type\":\"Append\""), frame);
    }
}
