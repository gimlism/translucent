package com.gimlism.translucent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * {@code DocsPagesGoldenTest} compares committed bytes against generator output, so it only ever
 * asks about files the page list already names. These check the directions that leaves open. Two
 * cases scan disk outward to the index — every baked {@code docs/viz/*.html} and every
 * {@code docs/guide/*.md} must be linked — but cover only those two directories, so a stray
 * {@code docs/about.html} or {@code docs/viz/foo.svg} is not this suite's concern. A third scans
 * the other way, index outward to disk: every non-null {@code Page.guide()} must name a file that
 * exists, because unlike {@code Page.path()} — which {@code pages()} writes to, so a typo simply
 * creates the file there — a guide is hand-written prose that already exists or doesn't, and only
 * this direction would notice a guide field pointing at nothing.
 */
class SiteIndexTest {

    private static String index() throws IOException {
        return Files.readString(RegenerateDocs.DIR.resolve(RegenerateDocs.INDEX_KEY), StandardCharsets.UTF_8);
    }

    /**
     * Regular files only. A directory whose name ends in the suffix is not a page, and letting one
     * through makes this over-fire rather than under-fire — the extra entry becomes a link the index
     * is required to carry, so the scan reports a page missing that was never a page. Filtering
     * costs nothing and keeps the failure message honest.
     */
    private static List<Path> filesIn(String subdir, String suffix) throws IOException {
        try (Stream<Path> found = Files.list(RegenerateDocs.DIR.resolve(subdir))) {
            return found.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(suffix))
                    .sorted().toList();
        }
    }

    @Test
    void everyBakedVizPageIsLinkedFromTheIndex() throws IOException {
        String index = index();
        List<Path> baked = filesIn("viz", ".html");
        assertFalse(baked.isEmpty(), "no pages under docs/viz — this check would be vacuous");
        for (Path page : baked) {
            String href = "viz/" + page.getFileName();
            assertTrue(index.contains("\"" + href + "\""),
                    "docs/index.html does not link " + href + " — " + RegenerateDocs.REGENERATE_HINT);
        }
    }

    @Test
    void everyGuideIsLinkedFromTheIndex() throws IOException {
        String index = index();
        List<Path> guides = filesIn("guide", ".md");
        assertFalse(guides.isEmpty(), "no guides under docs/guide — this check would be vacuous");
        for (Path guide : guides) {
            String href = SiteIndex.GUIDE_BASE + "guide/" + guide.getFileName();
            assertTrue(index.contains("\"" + href + "\""),
                    "docs/index.html does not link " + href
                            + " — add the guide to the matching Page in RegenerateDocs.vizPages()");
        }
    }

    /**
     * The direction {@link #everyGuideIsLinkedFromTheIndex()} cannot see: a {@code Page.guide()}
     * that names a file which does not exist. That check only scans the filesystem outward — it
     * confirms every real {@code .md} is linked, but an extra, bogus link pointing at nothing is
     * invisible to a scan that starts from what is actually on disk. The two directions are not
     * symmetric, either: a typo in {@code Page.path()} is self-healing, because {@code pages()}
     * writes whatever path a {@code Page} names, so the file simply gets created there. Guides are
     * not generated — they are hand-written prose that already exists — so only {@code guide} can
     * point at a file nobody wrote, and only this direction can catch it.
     */
    @Test
    void everyGuideNamesAFileThatExists() {
        List<RegenerateDocs.Page> withGuide =
                RegenerateDocs.vizPages().stream().filter(page -> page.guide() != null).toList();
        assertFalse(withGuide.isEmpty(), "no page carries a guide — this check would be vacuous");
        for (RegenerateDocs.Page page : withGuide) {
            Path guidePath = RegenerateDocs.DIR.resolve(page.guide());
            // isRegularFile, not exists: a directory satisfies exists() and would pass here while
            // the published link resolved to something that is not the guide. Verified — with
            // docs/guide/trie.md replaced by a directory of that name, this class went 5 run,
            // 0 failed. The contract is "names a readable file", so assert exactly that.
            assertTrue(Files.isRegularFile(guidePath),
                    "Page \"" + page.title() + "\" names guide \"" + page.guide()
                            + "\" but " + guidePath + " is not a readable file");
        }
    }

    /**
     * The two agree by convention only: {@link RegenerateDocs#main} writes whatever key
     * {@link RegenerateDocs#pages()} uses, and this class reads {@link RegenerateDocs#INDEX_KEY}
     * regardless of what that key actually is. If the two drifted — say {@code pages()} started
     * keying the landing page {@code "home.html"} — {@code main()} would write {@code docs/home.html}
     * and leave the stale {@code docs/index.html} on disk; nothing deletes it. The golden test would
     * still pass, because it only iterates whatever keys {@code pages()} currently has. This class's
     * other three tests would still pass too, because they would silently keep reading the orphaned
     * old file, which still contains every viz and guide link. Only this assertion — that
     * {@code pages()} actually contains {@code INDEX_KEY} — would catch it. Unlike a {@code
     * Page.path()} typo, which is self-healing because {@code pages()} writes whatever path a
     * {@code Page} names, the index's filename cannot self-heal: it IS the contract with GitHub
     * Pages, which serves {@code index.html} at a directory root and nothing else, so a renamed key
     * is not a differently-named page — it is a 404.
     */
    @Test
    void pagesContainsTheKeyThisClassReads() {
        Map<String, String> pages = RegenerateDocs.pages();
        assertTrue(pages.containsKey(RegenerateDocs.INDEX_KEY),
                "RegenerateDocs.pages() has no \"" + RegenerateDocs.INDEX_KEY
                        + "\" entry — SiteIndexTest reads that exact key, and GitHub Pages only "
                        + "serves a directory root under that exact filename, so a drift here "
                        + "silently orphans docs/" + RegenerateDocs.INDEX_KEY);
    }

    /**
     * The promise the landing page makes to a student opening it off a clone with no network: it
     * fetches nothing. Every absolute URL in the page must be a github.com link the reader chooses
     * to click, never something the browser retrieves to render.
     */
    @Test
    void theIndexFetchesNothing() throws IOException {
        String index = index();
        assertFalse(index.contains("<script"), "the index needs no JavaScript; it must not carry any");
        assertFalse(index.contains("<link rel=\"stylesheet\""), "styles must be inline, not fetched");
        Matcher url = Pattern.compile("https?://[^\"'\\s>]+").matcher(index);
        while (url.find()) {
            assertTrue(url.group().startsWith("https://github.com/"),
                    url.group() + " is not a github.com link — the page must fetch nothing");
        }
    }
}
