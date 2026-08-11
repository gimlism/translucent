package com.gimlism.translucent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

class LauncherCatalogTest {

    /**
     * The registry is the repo's index — if it lies, the launcher sends students at a class that
     * isn't there. Resolving every entry makes it self-verifying against any future rename.
     */
    @Test
    void everyCatalogedClassActuallyExists() {
        for (Launcher.Entry e : Launcher.CATALOG) {
            assertDoesNotThrow(() -> Class.forName(e.fqcn()), e.fqcn());
        }
    }

    /**
     * Not just "a method called main": {@code Launcher} invokes it reflectively with a null
     * receiver, so an instance {@code main} would resolve here and then blow up at runtime — the
     * one failure this guard exists to make impossible.
     */
    @Test
    void everyCatalogedClassHasARunnableMain() {
        for (Launcher.Entry e : Launcher.CATALOG) {
            Method main = assertDoesNotThrow(
                    () -> Class.forName(e.fqcn()).getMethod("main", String[].class), e.fqcn());
            assertTrue(Modifier.isStatic(main.getModifiers()), e.fqcn() + ": main must be static");
            assertTrue(Modifier.isPublic(main.getModifiers()), e.fqcn() + ": main must be public");
            assertEquals(void.class, main.getReturnType(), e.fqcn() + ": main must return void");
        }
    }

    @Test
    void catalogCoversTheFourBySixGridPlusCompressionCompare() {
        assertEquals(25, Launcher.CATALOG.size(), "4 structures x 6 modes + compression-compare");
        for (String structure : List.of("ArrayList", "HashMap", "TreeSet", "Trie (radix)")) {
            long modes = Launcher.CATALOG.stream().filter(e -> e.structure().equals(structure)).count();
            assertEquals(6, modes, structure + " should offer all six modes");
        }
    }

    @Test
    void catalogIsOrderedByTeachingDifficulty() {
        List<String> order = Launcher.CATALOG.stream().map(Launcher.Entry::structure).distinct().toList();
        assertEquals(List.of("ArrayList", "HashMap", "TreeSet", "Trie (radix)", "Trie (compression)"), order);
    }

    @Test
    void commandForIsACopyPastableMavenInvocation() {
        Launcher.Entry first = Launcher.CATALOG.get(0);
        String cmd = Launcher.commandFor(first);
        assertTrue(cmd.startsWith("mvn exec:java -Dexec.mainClass="), cmd);
        assertTrue(cmd.endsWith(first.fqcn()), cmd);
    }
}
