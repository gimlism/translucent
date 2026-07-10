package com.gimlism.translucent.arraylist.demo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ListWebVizDemoTest {

    @Test
    void buildHtmlEmbedsTheWholeStory() {
        String html = ListWebVizDemo.buildHtml();
        assertTrue(html.contains("<!doctype html>"), "self-contained page");
        assertTrue(html.contains("\"frames\":["), "frames injected");
        assertFalse(html.contains("/*__FRAMES__*/"), "token replaced");
        // the story exercises every event type at least once
        assertTrue(html.contains("\"type\":\"Grow\""), html);
        assertTrue(html.contains("\"type\":\"Shift\""), html);
        assertTrue(html.contains("\"type\":\"Insert\""), html);
        assertTrue(html.contains("\"type\":\"Set\""), html);
        assertTrue(html.contains("\"type\":\"RemoveAt\""), html);
        assertTrue(html.contains("\"element\":\"x\""), "inserted element present");
    }
}
