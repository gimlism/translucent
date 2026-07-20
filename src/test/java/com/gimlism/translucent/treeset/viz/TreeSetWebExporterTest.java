package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TreeSetWebExporterTest {

    @Test
    void toHtmlInjectsFramesAndBakesFlagsFalse() {
        String frames = "{\"frames\":[]}";
        String html = TreeSetWebExporter.toHtml(frames);

        assertTrue(html.contains("<!doctype html>"), "expected a full document");
        assertTrue(html.contains("</html>"), "expected closing html tag");
        assertTrue(html.contains("const DATA = " + frames + ";"), html);
        assertTrue(html.contains("const LIVE = false;"), html);
        assertTrue(html.contains("const CONTROLS = false;"), html);
        assertFalse(html.contains("/*__FRAMES__*/"), "FRAMES token not substituted");
        assertFalse(html.contains("/*__LIVE__*/"), "LIVE token not substituted");
        assertFalse(html.contains("/*__CONTROLS__*/"), "CONTROLS token not substituted");
    }

    @Test
    void writeHtmlWritesTheSameDocumentToDisk(@TempDir Path dir) throws IOException {
        String frames = "{\"frames\":[]}";
        Path out = dir.resolve("treeset.html");
        TreeSetWebExporter.writeHtml(frames, out);
        assertEquals(TreeSetWebExporter.toHtml(frames), Files.readString(out));
    }
}
