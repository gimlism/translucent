package com.gimlism.translucent;

import com.gimlism.translucent.arraylist.demo.ListWebVizDemo;
import com.gimlism.translucent.hashmap.demo.MapWebVizDemo;
import com.gimlism.translucent.treeset.demo.TreeSetWebVizDemo;
import com.gimlism.translucent.trie.compare.CompressionCompareDemo;
import com.gimlism.translucent.trie.compare.CompressionCompareJsonSerializer;
import com.gimlism.translucent.trie.compare.CompressionCompareWebExporter;
import com.gimlism.translucent.trie.demo.TrieWebVizDemo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bakes every visualisation into {@code docs/viz/} as a self-contained HTML page. These are
 * committed, so a student can open one straight off disk — no JDK, no Maven, no server, nothing to
 * install. The pages carry their own data and styling; there are no external requests at all, which
 * is what makes {@code file://} work.
 *
 * <p>Run after changing any demo story:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.RegenerateDocs}
 *
 * <p>{@code DocsPagesGoldenTest} pins the committed bytes to {@link #pages()}, so forgetting to run
 * this fails the build rather than shipping a page that disagrees with the code it depicts.
 */
public final class RegenerateDocs {

    private RegenerateDocs() { }

    /** Where the baked pages live. Kept out of the site root so guide prose can sit beside them. */
    static final Path DIR = Path.of("docs", "viz");

    /** Named in every golden failure so the fix is in the error, not in a document somewhere. */
    static final String REGENERATE_HINT =
            "regenerate with: mvn exec:java -Dexec.mainClass=com.gimlism.translucent.RegenerateDocs";

    /** The canonical key set for the compression comparison, mirroring the exporter's own demo. */
    private static final List<String> COMPRESSION_KEYS = List.of("she", "shell", "shore", "shy");

    /**
     * Every page, by filename. Insertion-ordered so regeneration and the goldens agree on order,
     * and deliberately the single source of truth: the test compares against exactly the strings
     * this method produced, so a page cannot be verified against a different generator than the one
     * that wrote it.
     */
    static Map<String, String> pages() {
        var pages = new LinkedHashMap<String, String>();
        pages.put("list.html", ListWebVizDemo.buildHtml());
        pages.put("map.html", MapWebVizDemo.buildHtml());
        pages.put("treeset.html", TreeSetWebVizDemo.buildHtml());
        pages.put("trie.html", TrieWebVizDemo.buildHtml());
        pages.put("compression-compare.html", CompressionCompareWebExporter.toHtml(
                CompressionCompareJsonSerializer.toJson(CompressionCompareDemo.compare(COMPRESSION_KEYS))));
        return pages;
    }

    public static void main(String[] args) throws IOException {
        Files.createDirectories(DIR);
        for (Map.Entry<String, String> page : pages().entrySet()) {
            Path out = DIR.resolve(page.getKey());
            Files.writeString(out, page.getValue(), StandardCharsets.UTF_8);
            System.out.println("wrote " + out);
        }
    }
}
