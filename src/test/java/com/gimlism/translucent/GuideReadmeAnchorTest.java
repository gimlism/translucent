package com.gimlism.translucent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Each guide under {@code docs/guide/} links back to its own section of the README with a GitHub
 * heading anchor — a slug hand-typed into the link, not generated from the heading. Nothing
 * recomputes it when the heading changes, so a rename silently stranded {@code trie.md}'s link at
 * the top of the README instead of its section (caught only by review, not by the build). This
 * guards the same mistake by slugifying every README {@code ### } heading and checking each
 * guide's anchor against that set.
 *
 * <p>The slugifier is deliberately minimal — lowercase, drop everything but word characters,
 * spaces and hyphens, turn spaces into hyphens — because that is exactly what the six real
 * headings in {@code README.md} exercise (an em dash and, as of the radix/standard split, a
 * parenthesized qualifier). It is not a general Markdown slug library, and should not grow rules
 * no current heading needs.
 *
 * <p><b>Limits of this guard, by design:</b> the oracle above is this class's own minimal
 * slugifier, not GitHub's real one (e.g. {@code github-slugger}). A heading using a character the
 * two treat differently would pass this suite while the live GitHub anchor still breaks — the
 * anchors this guard currently checks were separately verified against GitHub's actual renderer,
 * but that verification does not repeat automatically as headings change. Also, {@link
 * #SECTION_HEADING} only scans {@code ### } headings, so a guide anchoring a heading at a different
 * level (e.g. {@code ## }) would fail this guard spuriously rather than being checked correctly.
 * Both are accepted as YAGNI for the headings that exist today, not fixed here.
 */
class GuideReadmeAnchorTest {

    private static final Path GUIDE_DIR = Path.of("docs", "guide");
    private static final Path README = Path.of("README.md");

    private static final Pattern SECTION_HEADING = Pattern.compile("(?m)^### (.+)$");
    private static final Pattern README_ANCHOR = Pattern.compile("README\\.md#([\\w-]+)");
    private static final Pattern NOT_SLUG_CHAR = Pattern.compile("[^\\w\\- ]");

    private static String slugify(String heading) {
        String stripped = NOT_SLUG_CHAR.matcher(heading.toLowerCase()).replaceAll("");
        return stripped.replace(' ', '-');
    }

    private static Set<String> readmeHeadingSlugs() throws IOException {
        String readme = Files.readString(README, StandardCharsets.UTF_8);
        Set<String> slugs = new HashSet<>();
        Matcher m = SECTION_HEADING.matcher(readme);
        while (m.find()) slugs.add(slugify(m.group(1)));
        return slugs;
    }

    /**
     * Two places this could pass vacuously, and both are guarded: an empty README heading set
     * (checked before the scan starts) and an empty anchor scan across every guide — a link-format
     * change, a narrowed {@link #README_ANCHOR} pattern, or a renamed bullet section could all make
     * {@code README_ANCHOR} match nothing without {@code docs/guide/} itself being empty, so the
     * count of anchors actually found is checked explicitly after the loop rather than assumed from
     * the loop merely completing. This mirrors
     * {@link ReadmeSiteLinksTest#everyPublishedPageIsLinkedExactlyOncePlusTheLandingPage()}, which
     * exists for the identical reason: an assertion that only iterates a regex match set passes
     * vacuously the moment that set is empty, so the count has to be pinned on purpose.
     */
    @Test
    void everyGuideReadmeAnchorResolvesToARealHeading() throws IOException {
        Set<String> slugs = readmeHeadingSlugs();
        assertFalse(slugs.isEmpty(), README + " has no \"### \" headings — this guard would pass vacuously");

        int anchorsFound = 0;
        try (Stream<Path> files = Files.list(GUIDE_DIR)) {
            for (Path md : files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .toList()) {
                String text = Files.readString(md, StandardCharsets.UTF_8);
                Matcher m = README_ANCHOR.matcher(text);
                while (m.find()) {
                    anchorsFound++;
                    String anchor = m.group(1);
                    assertTrue(slugs.contains(anchor),
                            md + " links README.md#" + anchor + ", which matches no heading in " + README);
                }
            }
        }
        assertTrue(anchorsFound > 0, "no file under " + GUIDE_DIR
                + " names a README.md# anchor — this guard would pass having checked nothing");
    }
}
