package com.gimlism.translucent.substrate.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JsonWriterTest {

    @Test
    void writesNestedObjectWithCommasAndTypes() {
        String json = new JsonWriter()
                .beginObject()
                .name("n").value(42L)
                .name("ok").value(true)
                .name("items").beginArray()
                    .value("a").value("b")
                .endArray()
                .name("child").beginObject()
                    .name("x").nullValue()
                .endObject()
                .endObject()
                .toString();
        assertEquals("{\"n\":42,\"ok\":true,\"items\":[\"a\",\"b\"],\"child\":{\"x\":null}}", json);
    }

    @Test
    void escapesStringsAndNullValue() {
        String json = new JsonWriter()
                .beginObject()
                .name("s").value("he\"ll\\o\n\t")
                .name("gone").value((String) null)
                .endObject()
                .toString();
        assertEquals("{\"s\":\"he\\\"ll\\\\o\\n\\t\",\"gone\":null}", json);
    }

    @Test
    void escapesControlCharactersAsUnicode() {
        String json = new JsonWriter().value("").toString();
        assertEquals("\"\\u0001\"", json);
    }

    @Test
    void emptyContainers() {
        assertEquals("{}", new JsonWriter().beginObject().endObject().toString());
        assertEquals("[]", new JsonWriter().beginArray().endArray().toString());
    }
}
