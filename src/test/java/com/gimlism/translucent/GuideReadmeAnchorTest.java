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
 * spaces and hyphens, turn spaces into hyphens — because that is exactly what the four real
 * headings in {@code README.md} exercise (an em dash and, as of the radix/standard split, a
 * parenthesized qualifier). It is not a general Markdown slug library, and should not grow rules
 * no current heading needs.
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
     * Both the heading set and each guide's anchor scan iterate, so an empty README or an empty
     * guide directory would pass this vacuously — the same failure mode {@link GuideEventMapTest}
     * guards against for its own registry.
     */
    @Test
    void everyGuideReadmeAnchorResolvesToARealHeading() throws IOException {
        Set<String> slugs = readmeHeadingSlugs();
        assertFalse(slugs.isEmpty(), README + " has no \"### \" headings — this guard would pass vacuously");

        try (Stream<Path> files = Files.list(GUIDE_DIR)) {
            for (Path md : files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .toList()) {
                String text = Files.readString(md, StandardCharsets.UTF_8);
                Matcher m = README_ANCHOR.matcher(text);
                while (m.find()) {
                    String anchor = m.group(1);
                    assertTrue(slugs.contains(anchor),
                            md + " links README.md#" + anchor + ", which matches no heading in " + README);
                }
            }
        }
    }
}
