package com.gimlism.translucent.trie.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.core.RadixTrie;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AsciiTrieReplayerTest {
    private static TrieRecordingListener recordThreePuts() {
        var t = new RadixTrie<Integer>();
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        t.put("a", 1);
        t.put("b", 2);
        t.put("c", 3);
        return rec;
    }

    @Test
    void steppingForwardBackAndQuitWalksFrames() {
        var rec = recordThreePuts(); // 6 events: each of a,b,c diverges at the root (CreateNode + Put)
        var replayer = new AsciiTrieReplayer(rec.events(), new AsciiTrieRenderer());
        var buf = new ByteArrayOutputStream();
        var in = new ByteArrayInputStream("\nb\nq\n".getBytes(StandardCharsets.UTF_8)); // next, back, quit
        replayer.run(in, new PrintStream(buf, true, StandardCharsets.UTF_8));
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("── frame 1/6 ──"), out);
        assertTrue(out.contains("── frame 2/6 ──"), out);
        int first = out.indexOf("frame 1/6");
        assertTrue(first >= 0 && out.indexOf("frame 1/6", first + 1) > first, "frame 1 re-shown after back");
    }

    @Test
    void autoPlayRendersEveryFrameInOrder() {
        var rec = recordThreePuts();
        var replayer = new AsciiTrieReplayer(rec.events(), new AsciiTrieRenderer());
        var buf = new ByteArrayOutputStream();
        replayer.autoPlay(new PrintStream(buf, true, StandardCharsets.UTF_8), 0);
        String out = buf.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("── frame 1/6 ──"), out);
        assertTrue(out.contains("── frame 3/6 ──"), out);
        assertTrue(out.indexOf("frame 1/6") < out.indexOf("frame 2/6"), out);
        assertTrue(out.indexOf("frame 2/6") < out.indexOf("frame 3/6"), out);
    }
}
