package com.gimlism.translucent.trie.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.core.RadixTrie;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AsciiTrieVisualizerTest {
    @Test
    void liveVisualizerRendersInsertAndRemoveFrames() {
        var buf = new ByteArrayOutputStream();
        var t = new RadixTrie<Integer>();
        t.addListener(new AsciiTrieVisualizer(new PrintStream(buf, true, StandardCharsets.UTF_8)));
        t.put("she", 1);
        t.put("shore", 2);
        t.put("shell", 3); // split + create
        t.remove("shell");  // narrated walk + prune + merge
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("SPLIT"), out);
        assertTrue(out.contains("PUT \"shell\"=3 (new)"), out);
        assertTrue(out.contains("\"ore\" ●=2"), out);
        assertTrue(out.contains("PRUNE"), out);
        assertTrue(out.contains("> "), out);        // path highlight present
    }
}
