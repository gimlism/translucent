package com.gimlism.translucent.treeset.demo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TreeSetWebVizDemoTest {

    @Test
    void buildHtmlBakesTheStoryIntoASelfContainedPage() {
        String html = TreeSetWebVizDemo.buildHtml();
        assertTrue(html.contains("<!doctype html>"), "self-contained document");
        assertTrue(html.contains("</html>"), html);
        assertTrue(html.contains("const LIVE = false;"), "static replay");
        assertTrue(html.contains("\"type\":\"Add\""), "baked frames present");
        assertTrue(html.contains("\"element\":\"50\""), "the story's elements are serialized");
        assertTrue(html.contains("\"label\":\"remove 30\""), "the removal is in the story");
        assertFalse(html.contains("/*__FRAMES__*/"), "no unsubstituted token");
    }
}
