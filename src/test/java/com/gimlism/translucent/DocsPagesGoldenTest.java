package com.gimlism.translucent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The pages under {@code docs/} — the landing page and the visualisations under {@code viz/} — are
 * committed build output — a student opens them straight off disk with no JVM. That only works if
 * they stay honest, so each one is pinned byte-for-byte to the generator that produced it. A demo
 * whose story changes fails here rather than shipping a page that quietly disagrees with the code
 * it claims to show.
 */
class DocsPagesGoldenTest {

    private static Stream<String> pageNames() {
        // Deliberately NOT cached, though every invocation below rebuilds all six pages to read one.
        // The committed bytes come from a first pages() call in RegenerateDocs.main's fresh JVM; these
        // assertions run against later calls in this one. That gap is what catches a generator acquiring
        // lazily-initialised static state — a memoised layout, an id counter — whose first call disagrees
        // with its later ones. Caching to a single call would compare first-to-first and see nothing.
        // The whole class costs ~30ms, so the coverage is far cheaper than the drift it guards against.
        return RegenerateDocs.pages().keySet().stream();
    }

    @ParameterizedTest(name = "docs/{0}")
    @MethodSource("pageNames")
    void committedPageMatchesItsGenerator(String name) throws IOException {
        Path committed = RegenerateDocs.DIR.resolve(name);
        assertTrue(Files.exists(committed), committed + " is missing — " + RegenerateDocs.REGENERATE_HINT);
        assertEquals(RegenerateDocs.pages().get(name), Files.readString(committed, StandardCharsets.UTF_8),
                committed + " is stale — " + RegenerateDocs.REGENERATE_HINT);
    }
}
