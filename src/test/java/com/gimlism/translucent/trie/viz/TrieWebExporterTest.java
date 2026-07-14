package com.gimlism.translucent.trie.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrieWebExporterTest {

    @Test
    void toHtmlInjectsFramesAndBakesFlagsFalse() {
        String frames = "{\"frames\":[]}";
        String html = TrieWebExporter.toHtml(frames);

        // full self-contained document
        assertTrue(html.contains("<!doctype html>"), "expected a full document");
        assertTrue(html.contains("</html>"), "expected closing html tag");
        // the frames blob is injected verbatim
        assertTrue(html.contains("const DATA = " + frames + ";"), html);
        // fixed flags baked off for a static replay
        assertTrue(html.contains("const LIVE = false;"), html);
        assertTrue(html.contains("const CONTROLS = false;"), html);
        // no template token survives the injection
        assertFalse(html.contains("/*__FRAMES__*/"), "FRAMES token not substituted");
        assertFalse(html.contains("/*__LIVE__*/"), "LIVE token not substituted");
        assertFalse(html.contains("/*__CONTROLS__*/"), "CONTROLS token not substituted");
    }

    @Test
    void writeHtmlWritesTheSameDocumentToDisk(@TempDir Path dir) throws IOException {
        String frames = "{\"frames\":[]}";
        Path out = dir.resolve("trie.html");
        TrieWebExporter.writeHtml(frames, out);
        assertEquals(TrieWebExporter.toHtml(frames), Files.readString(out));
    }
}
