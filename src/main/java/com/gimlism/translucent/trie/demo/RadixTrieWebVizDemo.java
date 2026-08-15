package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.viz.TrieJsonSerializer;
import com.gimlism.translucent.trie.viz.TrieWebExporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Records a small story that exercises the trie's structural events — inserting keys with a shared
 * prefix (edges split and branch), then removing keys (leaves prune and edges merge) — and writes it
 * out as a self-contained HTML web replay. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.trie.demo.RadixTrieWebVizDemo}
 * (optionally {@code -Dexec.args="path/to/out.html"}). The whole-document build is factored into
 * {@link #buildHtml()} so it can be unit-tested without touching the filesystem.
 */
public class RadixTrieWebVizDemo {

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "target/trie-web-viz.html");
        Path parent = out.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(out, buildHtml());
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    /**
     * The self-contained HTML replay for the standard story, built without touching the filesystem.
     * Public because {@code RegenerateDocs} bakes it into {@code docs/viz/} and {@code DocsPagesGoldenTest}
     * pins the committed bytes to it — the seam has a production consumer now, not just a test.
     */
    public static String buildHtml() {
        var trie = new RadixTrie<Integer>();
        var rec = new TrieRecordingListener();
        trie.addListener(rec);

        int v = 1;
        // insert keys sharing the "sh" prefix — watch edges split and branch
        for (String key : new String[] {"shore", "she", "shell"}) {
            trie.put(key, v++);
        }
        // remove keys — watch leaves prune and edges merge
        for (String key : new String[] {"shell", "she"}) {
            trie.remove(key);
        }

        return TrieWebExporter.toHtml(TrieJsonSerializer.toJson(rec.events()), "RadixTrie");
    }
}
