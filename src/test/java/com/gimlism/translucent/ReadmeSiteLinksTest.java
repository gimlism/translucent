package com.gimlism.translucent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The README's hosted links are hand-written, and each one is a public 404 waiting to happen.
 * {@code docs/index.html} is generated from {@link RegenerateDocs#vizPages()}, so it follows a new
 * visualisation on its own; the README does not. Without this, a sixth page ships with the README
 * still listing five, and a mistyped path fails in a reader's browser rather than in the build.
 *
 * <p>Note the asymmetry that makes the hand-written side the dangerous one: {@code Page.path()}
 * self-heals, because {@code main()} writes wherever a page points. A README URL points at whatever
 * was typed, and nothing writes it.
 *
 * <p>Deliberately offline. This pins the README to the page list, not to GitHub being up — a
 * network assertion would fail on a train, and would be testing someone else's uptime.
 */
class ReadmeSiteLinksTest {

    /**
     * Where Pages serves the site, from {@code source[path]=/docs} on the default branch. The README
     * is checked against this constant, so the two cannot drift apart silently.
     */
    private static final String SITE = "https://gimlism.github.io/translucent/";

    /** Stops at markdown's closing delimiters so a link's URL is captured without its syntax. */
    private static final Pattern HOSTED_URL =
            Pattern.compile("https://gimlism\\.github\\.io[^\\s)\\]]*");

    private static String readme() throws IOException {
        return Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);
    }

    private static Set<String> hostedUrlsInReadme() throws IOException {
        Matcher m = HOSTED_URL.matcher(readme());
        Set<String> found = new LinkedHashSet<>();
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }

    @Test
    void readmeLinksEveryPublishedVisualisation() throws IOException {
        String readme = readme();
        for (RegenerateDocs.Page page : RegenerateDocs.vizPages()) {
            assertTrue(readme.contains(SITE + page.path()),
                    "README.md does not link " + SITE + page.path()
                            + " — the site index lists this page but the README does not");
        }
    }

    /**
     * The reverse, and the direction a reader actually reaches: an invented URL resolves to nothing,
     * and unlike a missing link it stays invisible until someone clicks it. The landing page counts
     * under both spellings — Pages serves it at the directory root, which is how the README addresses
     * it, but naming the file is not wrong either.
     */
    @Test
    void readmeInventsNoHostedUrl() throws IOException {
        Set<String> real = new HashSet<>(Set.of(SITE, SITE + RegenerateDocs.INDEX_KEY));
        for (RegenerateDocs.Page page : RegenerateDocs.vizPages()) {
            real.add(SITE + page.path());
        }
        for (String url : hostedUrlsInReadme()) {
            assertTrue(real.contains(url),
                    "README.md links " + url + ", which is not a page this build publishes");
        }
    }

    /**
     * Both checks above iterate over what they find, so both pass vacuously if the pattern stops
     * matching — a renamed host, or a markdown change that swallows the URLs. Pinning the count
     * turns that silence into a failure.
     */
    @Test
    void everyPublishedPageIsLinkedExactlyOncePlusTheLandingPage() throws IOException {
        assertEquals(RegenerateDocs.vizPages().size() + 1, hostedUrlsInReadme().size(),
                "expected one hosted URL per visualisation plus the landing page, in " + SITE);
    }
}
