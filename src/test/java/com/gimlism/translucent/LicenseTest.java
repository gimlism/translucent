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

    private static String license() throws IOException {
        return Files.readString(LICENSE, StandardCharsets.UTF_8);
    }

    private static String readme() throws IOException {
        return Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);
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
}
