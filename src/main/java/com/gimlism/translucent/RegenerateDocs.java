package com.gimlism.translucent;

import com.gimlism.translucent.arraylist.demo.ListWebVizDemo;
import com.gimlism.translucent.hashmap.demo.MapWebVizDemo;
import com.gimlism.translucent.treeset.demo.TreeSetWebVizDemo;
import com.gimlism.translucent.trie.compare.CompressionCompareDemo;
import com.gimlism.translucent.trie.compare.CompressionCompareJsonSerializer;
import com.gimlism.translucent.trie.compare.CompressionCompareWebExporter;
import com.gimlism.translucent.trie.demo.RadixTrieWebVizDemo;
import com.gimlism.translucent.trie.demo.StandardTrieWebVizDemo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Bakes the published site into {@code docs/} — a landing page plus one self-contained HTML page per
 * visualisation. These are committed, so a student opens one straight off disk with no JDK, no
 * Maven, and no server. The pages carry their own data and styling; there are no external requests
 * at all, which is what makes {@code file://} work.
 *
 * <p>Run after changing any demo story:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.RegenerateDocs}
 *
 * <p>{@code DocsPagesGoldenTest} pins the committed bytes to {@link #pages()}, so forgetting to run
 * this fails the build rather than shipping a page that disagrees with the code it depicts.
 */
public final class RegenerateDocs {

    private RegenerateDocs() { }

    /**
     * The published site root — what GitHub Pages serves. Keys in {@link #pages()} are paths
     * relative to this, so one map addresses both the landing page at the root and the
     * visualisations under {@code viz/}, and one golden test covers them all.
     */
    static final Path DIR = Path.of("docs");

    /** Named in every golden failure so the fix is in the error, not in a document somewhere. */
    static final String REGENERATE_HINT =
            "regenerate with: mvn exec:java -Dexec.mainClass=com.gimlism.translucent.RegenerateDocs";

    /**
     * The landing page's key in {@link #pages()}, and the one path {@code SiteIndexTest} reads
     * directly rather than discovering by scanning {@link #DIR}. Unlike {@code Page.path()}, a typo
     * here cannot self-heal: {@code main()} would simply write the new filename alongside the old
     * one, and GitHub Pages serves {@code index.html} at a directory root and nothing else, so the
     * filename itself — not just its content — is the contract. One constant, shared by the writer
     * and the reader, so the two cannot silently disagree.
     */
    static final String INDEX_KEY = "index.html";

    /** The canonical key set for the compression comparison, mirroring the exporter's own demo. */
    private static final List<String> COMPRESSION_KEYS = List.of("she", "shell", "shore", "shy");

    /**
     * One published visualisation: where it lands, how the landing page names and describes it, the
     * guide it belongs to, and the generator that bakes it.
     *
     * @param path  where the page lands, relative to {@link #DIR} — e.g. {@code viz/list.html}
     * @param title the landing page's link text
     * @param blurb the one line under that link
     * @param guide the matching guide relative to {@link #DIR}, or {@code null} where none exists
     * @param html  the generator, invoked per call and never memoised — see {@link #pages()}
     */
    record Page(String path, String title, String blurb, String guide, Supplier<String> html) { }

    /**
     * Every visualisation, in the teaching order {@code Launcher.CATALOG} uses. Deliberately the
     * single source of truth twice over: {@link #pages()} bakes these, and {@code SiteIndex} lists
     * them, so adding a structure is one entry here and both the page and its index row follow.
     */
    static List<Page> vizPages() {
        return List.of(
                new Page("viz/list.html", "ArrayList",
                        "A growable array, copying itself into a bigger one when it fills.",
                        "guide/list.md", ListWebVizDemo::buildHtml),
                new Page("viz/map.html", "HashMap",
                        "Buckets and chains — and a red-black tree once a chain grows too long.",
                        "guide/map.md", MapWebVizDemo::buildHtml),
                new Page("viz/treeset.html", "TreeSet",
                        "A red-black tree rotating and recolouring to stay balanced.",
                        "guide/treeset.md", TreeSetWebVizDemo::buildHtml),
                new Page("viz/standard-trie.html", "Standard trie",
                        "One node per character — the chain a radix trie compresses away.",
                        null, StandardTrieWebVizDemo::buildHtml),
                new Page("viz/trie.html", "Radix trie",
                        "A radix trie whose edges split and merge as keys arrive and leave.",
                        "guide/trie.md", RadixTrieWebVizDemo::buildHtml),
                new Page("viz/compression-compare.html", "Trie compression",
                        "A fat trie beside its compressed form, and what the compression saves.",
                        null, RegenerateDocs::compressionCompareHtml));
    }

    private static String compressionCompareHtml() {
        return CompressionCompareWebExporter.toHtml(CompressionCompareJsonSerializer.toJson(
                CompressionCompareDemo.compare(COMPRESSION_KEYS)));
    }

    /**
     * Every page by path. Insertion-ordered so regeneration and the goldens agree on order, and
     * deliberately the single source of truth: the test compares against exactly the strings this
     * method produced, so a page cannot be verified against a different generator than the one that
     * wrote it.
     */
    static Map<String, String> pages() {
        var pages = new LinkedHashMap<String, String>();
        for (Page page : vizPages()) {
            pages.put(page.path(), page.html().get());
        }
        pages.put(INDEX_KEY, SiteIndex.build(vizPages()));   // last: it links everything above
        return pages;
    }

    public static void main(String[] args) throws IOException {
        for (Map.Entry<String, String> page : pages().entrySet()) {
            Path out = DIR.resolve(page.getKey());
            Files.createDirectories(out.getParent());
            Files.writeString(out, page.getValue(), StandardCharsets.UTF_8);
            System.out.println("wrote " + out);
        }
    }
}
