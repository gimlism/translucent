package com.gimlism.translucent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A public repo with no licence is not open source: default copyright reserves every right, so a
 * student who clones this to learn from it has no grant to do so. These pin the two halves that can
 * drift apart — the file itself, and the README's claim about what it says.
 */
class LicenseTest {

    private static final Path LICENSE = Path.of("LICENSE");

    /**
     * The README's licence sentence, captured rather than searched for. A bare
     * {@code readme.contains("MIT")} passes on any README mentioning MIT anywhere — including in a
     * sentence about something else — so it would survive the very mutation meant to disprove it.
     */
    private static final Pattern README_LICENCE_LINE = Pattern.compile(
            "^Licensed under the (.+?) License — see \\[LICENSE]\\(LICENSE\\)\\.$", Pattern.MULTILINE);

    /**
     * The public site's front door. Its footer claim, captured rather than searched for, for the
     * same reason as {@link #README_LICENCE_LINE}: a bare {@code contains("MIT")} would survive a
     * relicense so long as the word "MIT" still appeared somewhere else on the page.
     */
    private static final Pattern DOCS_INDEX_LICENCE_CLAIM = Pattern.compile("— (.+?) licensed\\.");

    private static String license() throws IOException {
        return Files.readString(LICENSE, StandardCharsets.UTF_8);
    }

    private static String readme() throws IOException {
        return Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);
    }

    private static String docsIndex() throws IOException {
        return Files.readString(Path.of("docs/index.html"), StandardCharsets.UTF_8);
    }

    @Test
    void licenseFileExists() {
        assertTrue(Files.exists(LICENSE), "LICENSE is missing — the repo cannot be published without it");
    }

    @Test
    void licenseCarriesTheMitGrantAndDisclaimer() throws IOException {
        String text = license();
        assertTrue(text.contains("Permission is hereby granted, free of charge"),
                "LICENSE does not carry the MIT permission grant");
        assertTrue(text.contains("WITHOUT WARRANTY OF ANY KIND"),
                "LICENSE does not carry the MIT warranty disclaimer");
    }

    @Test
    void licenseNamesACopyrightHolderAndYear() throws IOException {
        assertTrue(Pattern.compile("^Copyright \\(c\\) \\d{4} \\S.*$", Pattern.MULTILINE)
                        .matcher(license()).find(),
                "LICENSE has no 'Copyright (c) <year> <holder>' line");
    }

    /**
     * The load-bearing one. Compares the README's claim against what the file declares itself to be,
     * so changing either alone goes red — no hardcoded "MIT" for a mutation to slip past.
     */
    @Test
    void readmeNamesTheLicenceTheFileDeclaresItselfToBe() throws IOException {
        String declared = license().lines().findFirst().orElse("").trim();
        Matcher m = README_LICENCE_LINE.matcher(readme());
        assertTrue(m.find(), "README.md has no 'Licensed under the … License — see [LICENSE](LICENSE).' line");
        assertEquals(declared, m.group(1) + " License",
                "README.md names a different licence than LICENSE declares itself to be");
    }

    /**
     * The public site's front door repeats the same claim in its own words ("— MIT licensed." rather
     * than "Licensed under the MIT License"), so it needs its own pin: {@code DocsPagesGoldenTest}
     * only proves {@code docs/index.html} matches what the generator currently emits, not that what
     * it emits is still true. Derived from LICENSE the same way as the README case — no hardcoded
     * "MIT" for a mutation to slip past.
     */
    @Test
    void docsIndexNamesTheLicenceTheFileDeclaresItselfToBe() throws IOException {
        String declared = license().lines().findFirst().orElse("").trim();
        String licenceName = declared.endsWith(" License")
                ? declared.substring(0, declared.length() - " License".length())
                : declared;
        Matcher m = DOCS_INDEX_LICENCE_CLAIM.matcher(docsIndex());
        assertTrue(m.find(), "docs/index.html has no '— <licence> licensed.' footer claim");
        assertEquals(licenceName, m.group(1),
                "docs/index.html's footer names a different licence than LICENSE declares itself to be");
    }
}
