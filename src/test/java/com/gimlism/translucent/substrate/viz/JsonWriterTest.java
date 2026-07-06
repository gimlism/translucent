package com.gimlism.translucent.substrate.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void escapesLessThanToProtectInlineScriptEmbedding() {
        String json = new JsonWriter().value("</script>").toString();
        assertEquals("\"\\u003c/script>\"", json);
    }

    @Test
    void escapesLineAndParagraphSeparatorsForInlineScriptEmbedding() {
        // U+2028/U+2029 are valid unescaped in JSON but are line terminators in pre-ES2019 JS,
        // and this JSON is embedded in an inline <script>. (char)0x2028/0x2029 avoid unicode
        // escapes in this test source.
        String input = "a" + ((char) 0x2028) + "b" + ((char) 0x2029) + "c";
        String json = new JsonWriter().value(input).toString();
        assertFalse(json.indexOf((char) 0x2028) >= 0, "raw U+2028 must not survive");
        assertFalse(json.indexOf((char) 0x2029) >= 0, "raw U+2029 must not survive");
        assertTrue(json.contains("u2028"), "U+2028 escaped as \\u2028");
        assertTrue(json.contains("u2029"), "U+2029 escaped as \\u2029");
    }
}
