package com.gimlism.translucent.hashmap.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebVizDemoTest {

    @Test
    void buildsASelfContainedPageForTheStandardStory() {
        String html = WebVizDemo.buildHtml();
        assertTrue(html.toLowerCase().contains("<!doctype html"), "full document");
        assertTrue(html.contains("</html>"), "full document");
        assertTrue(html.contains("\"frames\":["), "carries serialized frames");
        // the story collides in one bucket until it treeifies, so at least one tree bin appears
        assertTrue(html.contains("\"kind\":\"tree\""), "the story should treeify a bin");
    }
}
