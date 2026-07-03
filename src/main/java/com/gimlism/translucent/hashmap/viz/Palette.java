package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.Color;
import java.io.Console;

/** Renders a tree node's key with its red-black colour, in ANSI colour or plain text. */
public final class Palette {
    public enum Mode { PLAIN, ANSI }

    private static final String RED = "\u001b[31m";
    private static final String RESET = "\u001b[0m";

    private final Mode mode;

    public Palette(Mode mode) {
        this.mode = mode;
    }

    public Mode mode() {
        return mode;
    }

    /** A node label: {@code key(R)}/{@code key(B)} in PLAIN, or ANSI-red key in ANSI. */
    public String node(Object key, Color color) {
        if (mode == Mode.PLAIN) {
            return key + (color == Color.RED ? "(R)" : "(B)");
        }
        return color == Color.RED ? RED + key + RESET : String.valueOf(key);
    }

    /** ANSI when attached to a real terminal, PLAIN otherwise (pipes, capture, tests). */
    public static Palette auto() {
        Console console = System.console();
        boolean terminal = console != null && isTerminal(console);
        return new Palette(decideMode(System.getenv("NO_COLOR"), console != null, terminal));
    }

    /**
     * The colour decision from raw environment inputs. Package-visible for testing.
     *
     * @param noColorEnv     value of the {@code NO_COLOR} env var (null if unset)
     * @param consolePresent whether {@link System#console()} returned non-null
     * @param terminal       whether that console is a real terminal (see {@link #isTerminal})
     */
    static Mode decideMode(String noColorEnv, boolean consolePresent, boolean terminal) {
        if (noColorEnv != null && !noColorEnv.isEmpty()) return Mode.PLAIN; // no-color.org
        if (!consolePresent) return Mode.PLAIN;                             // pipe / redirect / no tty
        return terminal ? Mode.ANSI : Mode.PLAIN;
    }

    /**
     * Whether the console is attached to a terminal. Uses {@code Console.isTerminal()}
     * (added in JDK 22, where {@link System#console()} is non-null even under
     * redirection) reflectively, since this module compiles against release 21; on
     * JDK 21 a non-null console already implies a terminal.
     *
     * <p>A missing method means JDK 21, so we keep the pre-22 semantics (terminal).
     * Any other reflective or security failure means we cannot confirm a terminal,
     * so we fail closed to avoid leaking ANSI escapes into redirected output.
     */
    private static boolean isTerminal(Console console) {
        try {
            return (Boolean) Console.class.getMethod("isTerminal").invoke(console);
        } catch (NoSuchMethodException e) {
            return true; // JDK 21: no isTerminal(); a non-null console is a terminal
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false; // JDK 22+ reflective/security failure: fail closed (no ANSI)
        }
    }
}
