package com.gimlism.translucent.substrate.viz;

import java.io.Console;

/**
 * Whether ASCII renderers should emit ANSI colour, decided from the runtime
 * environment. Structure-agnostic: this is only about the output channel
 * (terminal vs pipe/redirect, and the NO_COLOR convention), never about a node's
 * own colour. Promoted from the HashMap's {@code Palette} so the TreeSet renderer
 * can share the (subtle) detection logic.
 */
public enum ColorMode {
    PLAIN, ANSI;

    /** ANSI when attached to a real terminal, PLAIN otherwise (pipes, capture, tests). */
    public static ColorMode detect() {
        Console console = System.console();
        boolean terminal = console != null && isTerminal(console);
        return decideMode(System.getenv("NO_COLOR"), console != null, terminal);
    }

    /**
     * The colour decision from raw environment inputs. Package-visible for testing.
     *
     * @param noColorEnv     value of the {@code NO_COLOR} env var (null if unset)
     * @param consolePresent whether {@link System#console()} returned non-null
     * @param terminal       whether that console is a real terminal (see {@link #isTerminal})
     */
    static ColorMode decideMode(String noColorEnv, boolean consolePresent, boolean terminal) {
        if (noColorEnv != null && !noColorEnv.isEmpty()) return PLAIN; // no-color.org
        if (!consolePresent) return PLAIN;                             // pipe / redirect / no tty
        return terminal ? ANSI : PLAIN;
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
