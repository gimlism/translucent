package com.gimlism.translucent.hashmap.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class MapWebVizDemoTest {

    @Test
    void buildsASelfContainedPageForTheStandardStory() {
        String html = MapWebVizDemo.buildHtml();
        assertTrue(html.toLowerCase(Locale.ROOT).contains("<!doctype html"), "full document");
        assertTrue(html.contains("</html>"), "full document");
        assertTrue(html.contains("\"frames\":["), "carries serialized frames");
        // the story collides in one bucket until it treeifies, so at least one tree bin appears
        assertTrue(html.contains("\"kind\":\"tree\""), "the story should treeify a bin");
        // the story's second half removes keys until bucket 0 untreeifies back to a chain
        assertTrue(html.contains("\"type\":\"Untreeify\""), "the story should untreeify a bin");
    }
}
