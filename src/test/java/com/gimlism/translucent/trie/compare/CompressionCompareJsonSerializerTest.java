package com.gimlism.translucent.trie.compare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CompressionCompareJsonSerializerTest {
    private static final List<String> CANON = List.of("she", "shell", "shore", "shy");

    private static long count(String s, String literal) {
        return Pattern.compile(Pattern.quote(literal)).matcher(s).results().count();
    }

    /** Split the blob at the radix tree so absorbed flags can be attributed to a specific panel. */
    private static String standardHalf(String json) { return json.substring(0, json.indexOf("\"radix\"")); }
    private static String radixHalf(String json) { return json.substring(json.indexOf("\"radix\"")); }

    @Test
    void emitsKeysCountsAndSavings() {
        String json = CompressionCompareJsonSerializer.toJson(CompressionCompareDemo.compare(CANON));
        assertTrue(json.contains("\"keys\":[\"she\",\"shell\",\"shore\",\"shy\"]"), json);
        assertTrue(json.contains("\"nodes\":10"), json);
        assertTrue(json.contains("\"nodes\":6"), json);
        assertTrue(json.contains("\"saved\":4"), json);
        assertTrue(json.contains("\"pct\":40"), json);
    }

    @Test
    void absorbedTrueCountEqualsSavedAndOnlyInStandardPanel() {
        var c = CompressionCompareDemo.compare(CANON);
        String json = CompressionCompareJsonSerializer.toJson(c);
        assertEquals(c.saved(), count(standardHalf(json), "\"absorbed\":true"),
            "standard panel must mark exactly saved() nodes:\n" + json);
        assertEquals(0, count(radixHalf(json), "\"absorbed\":true"),
            "radix panel must never be marked:\n" + json);
    }

    @Test
    void markedEqualsSavedForASecondShape() {
        // "abc" -> standard root,a,b,c (4) vs radix root,"abc" (2); "a","b" are absorbed -> 2 == saved 2.
        var c = CompressionCompareDemo.compare(List.of("abc"));
        String json = CompressionCompareJsonSerializer.toJson(c);
        assertEquals(2, c.saved(), "sanity: {abc} saves 2");
        assertEquals(c.saved(), count(standardHalf(json), "\"absorbed\":true"), json);
    }

    @Test
    void emptyKeySetIsTwoRootOnlyTreesWithNoMarks() {
        String json = CompressionCompareJsonSerializer.toJson(CompressionCompareDemo.compare(List.of()));
        assertTrue(json.contains("\"keys\":[]"), json);
        assertTrue(json.contains("\"saved\":0"), json);
        assertTrue(json.contains("\"pct\":0"), json);
        assertEquals(0, count(json, "\"absorbed\":true"), json);
    }
}
