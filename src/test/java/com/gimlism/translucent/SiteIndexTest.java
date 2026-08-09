package com.gimlism.translucent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * {@code DocsPagesGoldenTest} compares committed bytes against generator output, so it only ever
 * asks about files the page list already names. These check the other direction: a file sitting
 * under {@code docs/} that no {@code Page} claims is linked from nowhere, and would sail past
 * everything else in the suite.
 */
class SiteIndexTest {

    private static String index() throws IOException {
        return Files.readString(RegenerateDocs.DIR.resolve("index.html"), StandardCharsets.UTF_8);
    }

    private static List<Path> filesIn(String subdir, String suffix) throws IOException {
        try (Stream<Path> found = Files.list(RegenerateDocs.DIR.resolve(subdir))) {
            return found.filter(p -> p.getFileName().toString().endsWith(suffix)).sorted().toList();
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
