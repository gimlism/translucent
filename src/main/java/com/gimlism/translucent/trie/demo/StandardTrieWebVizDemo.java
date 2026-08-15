package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.core.StandardTrie;
import com.gimlism.translucent.trie.viz.TrieJsonSerializer;
import com.gimlism.translucent.trie.viz.TrieWebExporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Records the same story as {@link RadixTrieWebVizDemo} — the same keys in the same order — against
 * an uncompressed trie, and writes it out as a self-contained HTML web replay. Opened beside
 * {@code trie.html}, the two pages differ in exactly one variable, so every difference a student sees
 * is caused by compression and nothing else. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieWebVizDemo}
 * (optionally {@code -Dexec.args="path/to/out.html"}). The whole-document build is factored into
 * {@link #buildHtml()} so it can be unit-tested without touching the filesystem.
 */
public class StandardTrieWebVizDemo {

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "target/standard-trie-web-viz.html");
        Path parent = out.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(out, buildHtml());
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    /**
     * The self-contained HTML replay for the standard story, built without touching the filesystem.
     * Public because {@code RegenerateDocs} bakes it into {@code docs/viz/} and {@code
     * DocsPagesGoldenTest} pins the committed bytes to it — the seam has a production consumer now,
     * not just a test.
     */
    public static String buildHtml() {
        var trie = new StandardTrie<Integer>();
        var rec = new TrieRecordingListener();
        trie.addListener(rec);

        int v = 1;
        // insert keys sharing the "sh" prefix — one node appears per character, no edge is compressed
        for (String key : new String[] {"shore", "she", "shell"}) {
            trie.put(key, v++);
        }
        // remove keys — the prune cascade unwinds each dead chain a node at a time
        for (String key : new String[] {"shell", "she"}) {
            trie.remove(key);
        }

        return TrieWebExporter.toHtml(TrieJsonSerializer.toJson(rec.events()), "StandardTrie");
    }
}
