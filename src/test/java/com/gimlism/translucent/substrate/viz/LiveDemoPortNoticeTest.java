package com.gimlism.translucent.substrate.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every live demo must explain a moved port. {@link LiveServer} silently falls back to a free port
 * when the one it asked for is taken; a demo that does not say so leaves a student staring at a
 * port number they cannot account for. Two demo Javadocs once resolved that confusion by asserting
 * the fallback did not exist at all, so this is pinned rather than left to habit.
 *
 * <p>Enumerated by filename, so a sixteenth live demo is covered the day it is written.
 */
class LiveDemoPortNoticeTest {

    /** Surefire runs with {@code ${basedir}} as the working directory, so this relative path holds. */
    private static List<Path> liveDemos() throws IOException {
        try (Stream<Path> walk = Files.walk(Path.of("src/main/java"))) {
            return walk.filter(p -> {
                String n = p.getFileName().toString();
                return n.endsWith("Demo.java") && n.contains("Live");
            }).sorted().toList();
        }
    }

    @Test
    void theresAtLeastOneLiveDemoToCheck() throws IOException {
        assertFalse(liveDemos().isEmpty(), "found no live demos — the filename convention must have changed");
    }

    @Test
    void everyLiveDemoAnnouncesAPortFallback() throws IOException {
        for (Path demo : liveDemos()) {
            String src = Files.readString(demo);
            assertTrue(src.contains("announcePortFallback"),
                    demo + " binds a port but never explains a fallback — add "
                            + "DemoLifecycle.announcePortFallback(server);");
        }
    }

    @Test
    void noLiveDemoStillClaimsASecondInstanceCannotBind() throws IOException {
        for (Path demo : liveDemos()) {
            String src = Files.readString(demo);
            assertFalse(src.contains("fails to bind"),
                    demo + " repeats the disproved claim that a second live demo fails to bind; "
                            + "LiveServerTest pins that it falls back and both serve");
        }
    }

    @Test
    void everyLiveDemoUrlUsesTheBoundPortNotTheRequestedConstant() throws IOException {
        for (Path demo : liveDemos()) {
            String src = Files.readString(demo);
            assertTrue(src.contains("server.port()"),
                    demo + " must build its URL from the bound port, never from DEFAULT_PORT");
            assertFalse(src.contains("+ DEFAULT_PORT"),
                    demo + " builds a URL from the requested constant, which is wrong after a fallback");
        }
    }

    /**
     * Only one direction of this is worth pinning. A {@code *Live*Demo} without a port is harmless;
     * a class that binds a port while falling outside the naming convention is the failure that
     * matters, because the guards above enumerate by filename and would never see it.
     */
    @Test
    void nothingBindsAPortFromOutsideTheLiveDemoNamingConvention() throws IOException {
        List<Path> demos = liveDemos();
        try (Stream<Path> walk = Files.walk(Path.of("src/main/java"))) {
            List<Path> strays = walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains("DEFAULT_PORT");
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .filter(p -> !demos.contains(p))
                    .sorted().toList();
            assertEquals(List.of(), strays,
                    "these bind a port but are not named *Live*Demo, so the guards above never check them");
        }
    }
}
