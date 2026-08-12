package com.gimlism.translucent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * The repo's front door: a numbered index of every demo, so exploring translucent does not require
 * knowing a fully-qualified class name up front. Run with plain {@code mvn exec:java} (it is the
 * {@code exec.mainClass} default in {@code pom.xml}).
 *
 * <p>The catalog below is written out longhand rather than generated from a naming pattern: it is
 * the index students read, so it should say exactly what it means. {@code LauncherCatalogTest}
 * resolves every entry with {@code Class.forName}, so a stale entry fails the build rather than
 * sending a student at a class that no longer exists.
 *
 * <p>Deliberately a top-level class — every other type lives in a per-structure subpackage, and
 * sitting at the root is the signal that this is where you start.
 */
public final class Launcher {

    private Launcher() { }

    /** One runnable demo: which structure it shows, in which mode, and the class that runs it. */
    public record Entry(String structure, String mode, String fqcn) { }

    private static final String ARRAYLIST = "com.gimlism.translucent.arraylist.demo.";
    private static final String HASHMAP = "com.gimlism.translucent.hashmap.demo.";
    private static final String TREESET = "com.gimlism.translucent.treeset.demo.";
    private static final String TRIE = "com.gimlism.translucent.trie.demo.";

    /**
     * Every demo, ordered by teaching difficulty — a growable array is the gentlest first contact
     * with "the data structure has an inside", and the trie's compression comparison is the payoff
     * that only makes sense once tries do.
     */
    public static final List<Entry> CATALOG = List.of(
            new Entry("ArrayList", "text log", ARRAYLIST + "ListDemo"),
            new Entry("ArrayList", "ASCII replay", ARRAYLIST + "ListVizDemo"),
            new Entry("ArrayList", "web replay (writes .html)", ARRAYLIST + "ListWebVizDemo"),
            new Entry("ArrayList", "live web", ARRAYLIST + "ListLiveWebVizDemo"),
            new Entry("ArrayList", "terminal REPL", ARRAYLIST + "ListLiveReplDemo"),
            new Entry("ArrayList", "browser REPL", ARRAYLIST + "ListLiveControlsDemo"),

            new Entry("HashMap", "text log", HASHMAP + "MapDemo"),
            new Entry("HashMap", "ASCII replay", HASHMAP + "MapVizDemo"),
            new Entry("HashMap", "web replay (writes .html)", HASHMAP + "MapWebVizDemo"),
            new Entry("HashMap", "live web", HASHMAP + "MapLiveWebVizDemo"),
            new Entry("HashMap", "terminal REPL", HASHMAP + "MapLiveReplDemo"),
            new Entry("HashMap", "browser REPL", HASHMAP + "MapLiveControlsDemo"),

            new Entry("TreeSet", "text log", TREESET + "TreeSetDemo"),
            new Entry("TreeSet", "ASCII replay", TREESET + "TreeSetVizDemo"),
            new Entry("TreeSet", "web replay (writes .html)", TREESET + "TreeSetWebVizDemo"),
            new Entry("TreeSet", "live web", TREESET + "TreeSetLiveWebVizDemo"),
            new Entry("TreeSet", "terminal REPL", TREESET + "TreeSetLiveReplDemo"),
            new Entry("TreeSet", "browser REPL", TREESET + "TreeSetLiveControlsDemo"),

            new Entry("Trie (radix)", "text log", TRIE + "RadixTrieDemo"),
            new Entry("Trie (radix)", "ASCII replay", TRIE + "RadixTrieVizDemo"),
            new Entry("Trie (radix)", "web replay (writes .html)", TRIE + "RadixTrieWebVizDemo"),
            new Entry("Trie (radix)", "live web", TRIE + "RadixTrieLiveWebVizDemo"),
            new Entry("Trie (radix)", "terminal REPL", TRIE + "RadixTrieLiveReplDemo"),
            new Entry("Trie (radix)", "browser REPL", TRIE + "RadixTrieLiveControlsDemo"),

            new Entry("Trie (compression)", "standard vs radix, side by side",
                    "com.gimlism.translucent.trie.compare.CompressionCompareDemo"));

    /** The command that runs {@code entry} directly, without going through this menu. */
    public static String commandFor(Entry entry) {
        return "mvn exec:java -Dexec.mainClass=" + entry.fqcn();
    }

    public static void main(String[] args) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            run(in, System.out, Launcher::launch);
        }
    }

    /**
     * Show the menu, read one choice, hand it to {@code runner} (test seam — no reflection). Returns
     * after a single demo starts: the live ones hold the JVM until Ctrl-C, so there is no menu to
     * come back to. {@code q}/{@code quit} and EOF (Ctrl-D) leave without launching anything.
     */
    static void run(BufferedReader in, PrintStream out, Consumer<Entry> runner) throws IOException {
        printMenu(out);
        prompt(out);
        String line;
        while ((line = in.readLine()) != null) { // EOF (Ctrl-D) ends the loop
            String choice = line.strip();
            if (choice.equalsIgnoreCase("q") || choice.equalsIgnoreCase("quit")) {
                return;
            }
            if (!choice.isEmpty()) { // blank line: no error, just draw the prompt again
                Entry entry = select(choice);
                if (entry != null) {
                    // Print the direct command first: this menu is a shortcut, not a dependency,
                    // and a student who reads the source should leave it behind after one session.
                    out.println();
                    out.println("→ " + commandFor(entry));
                    out.println("  (run that next time to skip this menu)");
                    out.println();
                    runner.accept(entry);
                    return;
                }
                out.println("not a choice: '" + choice + "' — enter 1-" + CATALOG.size() + ", or q to quit");
            }
            // Every path that loops re-draws the prompt: without it the cursor sits on a bare line
            // after the user's Enter and the launcher reads as hung.
            prompt(out);
        }
    }

    /** The input prompt. Separate from {@link #printMenu} so every retry can re-draw just this. */
    private static void prompt(PrintStream out) {
        out.print("Pick a number (or q to quit): ");
    }

    /** The entry {@code choice} names, or {@code null} if it is not a number in range. */
    static Entry select(String choice) {
        int n;
        try {
            n = Integer.parseInt(choice);
        } catch (NumberFormatException e) {
            return null; // not a number at all
        }
        return n >= 1 && n <= CATALOG.size() ? CATALOG.get(n - 1) : null;
    }

    /** The numbered index, grouped under a heading per structure. */
    static void printMenu(PrintStream out) {
        out.println();
        out.println("translucent — watch a data structure work");
        out.println();
        String heading = null;
        for (int i = 0; i < CATALOG.size(); i++) {
            Entry entry = CATALOG.get(i);
            if (!entry.structure().equals(heading)) {
                heading = entry.structure();
                out.println("  " + heading);
            }
            out.printf("   %2d  %s%n", i + 1, entry.mode());
        }
        out.println();
    }

    /** Invoke the chosen demo's {@code main} in this JVM. */
    private static void launch(Entry entry) {
        try {
            Class.forName(entry.fqcn()).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException e) {
            // The demo itself failed — surface its exception, not the reflection wrapper.
            throw new IllegalStateException(entry.fqcn() + " failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not start " + entry.fqcn(), e);
        }
    }
}
