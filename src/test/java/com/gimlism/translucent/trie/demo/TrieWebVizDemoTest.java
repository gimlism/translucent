package com.gimlism.translucent.trie.demo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrieWebVizDemoTest {

    @Test
    void buildHtmlIsSelfContainedAndExercisesInsertThenRemove() {
        String html = TrieWebVizDemo.buildHtml();

        // self-contained baked document, no unsubstituted tokens
        assertTrue(html.contains("<!doctype html>"), "expected a full document");
        assertTrue(html.contains("</html>"), "expected closing html tag");
        assertTrue(html.contains("\"frames\":["), "expected an injected frames blob");
        assertFalse(html.contains("/*__"), "no template token should survive");

        // the insert story split/branched a shared prefix — an "sh" edge and both branch labels
        assertTrue(html.contains("\"label\":\"sh\""), "expected the shared \"sh\" prefix edge");
        assertTrue(html.contains("\"label\":\"ore\""), "expected the \"ore\" branch (shore)");

        // the remove story fired the compression cleanup events
        assertTrue(html.contains("\"type\":\"Put\""), "expected inserts");
        assertTrue(html.contains("\"type\":\"Remove\""), "expected removes");
        assertTrue(html.contains("\"type\":\"Prune\""), "expected a leaf prune from the removals");
    }
}
