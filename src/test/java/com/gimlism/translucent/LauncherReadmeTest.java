package com.gimlism.translucent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The README's command table is the reference a student copy-pastes from, and prose has no
 * compiler. Pinning it to {@link Launcher#CATALOG} means a demo cannot be renamed or added
 * without the table being updated in the same commit.
 */
class LauncherReadmeTest {

    private static String readme() throws IOException {
        return Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);
    }

    @Test
    void readmeListsEveryDemoInTheCatalog() throws IOException {
        String readme = readme();
        for (Launcher.Entry e : Launcher.CATALOG) {
            assertTrue(readme.contains(e.fqcn()),
                    "README.md is missing " + e.fqcn() + " — regenerate the command table");
        }
    }

    /**
     * The reverse direction, and the one a student actually feels: a row naming a demo that no
     * longer exists still copy-pastes, and then fails with ClassNotFoundException. Checking only
     * that every catalog entry appears would let a deleted demo linger in the table forever.
     */
    @Test
    void readmeNamesNoDemoThatIsNotInTheCatalog() throws IOException {
        Set<String> known = Launcher.CATALOG.stream().map(Launcher.Entry::fqcn).collect(Collectors.toSet());
        Matcher m = Pattern.compile("com\\.gimlism\\.translucent\\.[A-Za-z0-9_.]+").matcher(readme());
        while (m.find()) {
            assertTrue(known.contains(m.group()),
                    "README.md names " + m.group() + ", which is not in Launcher.CATALOG");
        }
    }

    @Test
    void readmeTellsYouHowToOpenTheLauncher() throws IOException {
        assertTrue(readme().contains("mvn exec:java"), "README.md should show the front-door command");
    }
}
