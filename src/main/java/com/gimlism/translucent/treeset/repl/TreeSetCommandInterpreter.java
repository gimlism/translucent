package com.gimlism.translucent.treeset.repl;

import com.gimlism.translucent.substrate.repl.CommandResult;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import java.util.Locale;

/**
 * Parses one command line and applies it to a {@link TeachingTreeSet}, returning a
 * {@link CommandResult}. Pure: it performs no I/O and holds no reference to the live server —
 * mutations it makes fire {@code SetEvent}s that any attached listener (e.g.
 * {@code TreeSetLiveVisualizer}) broadcasts, so live rendering is a side-effect of the listener,
 * not of this class. {@link #execute} never throws: every malformed input becomes an error message.
 *
 * <p>Elements are {@code Integer} — a single token, natural ordering; a set has no key/value, so
 * every verb parses exactly one integer (or takes no argument). Unlike the map/list/trie REPLs,
 * the comparison reads ({@code contains}, {@code lower}/{@code floor}/{@code ceiling}/{@code higher})
 * DO narrate: they emit {@code Compare} events as they walk the red-black tree, so the browser
 * animates the comparison cursor live. This is the shared entry point a browser-controls front-end
 * will reuse in a later slice.
 */
public final class TreeSetCommandInterpreter {

    /** Parse {@code line}, apply it to {@code set}, and return the result to show the user. */
    public CommandResult execute(String line, TeachingTreeSet<Integer> set) {
        if (line == null) {
            return CommandResult.of(""); // null (e.g. an empty POST body) — treat as a blank no-op
        }
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return CommandResult.of(""); // blank line: silent no-op
        }
        String[] parts = trimmed.split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1] : "";

        switch (cmd) {
            case "add": {
                Integer e = soleInt(rest);
                if (e == null) {
                    return usageOrParse(rest, "add");
                }
                return CommandResult.of(set.add(e) ? "added " + e : e + " already present");
            }
            case "remove": {
                Integer e = soleInt(rest);
                if (e == null) {
                    return usageOrParse(rest, "remove");
                }
                return CommandResult.of(set.remove(e) ? "removed " + e : e + " not found");
            }
            case "contains": {
                Integer e = soleInt(rest);
                if (e == null) {
                    return usageOrParse(rest, "contains");
                }
                return CommandResult.of("contains " + e + " → " + set.contains(e));
            }
            case "lower":
            case "floor":
            case "ceiling":
            case "higher":
                return bound(set, rest, cmd);
            case "first":
                if (!rest.isEmpty()) {
                    return CommandResult.of("usage: first");
                }
                return CommandResult.of("first → " + (set.isEmpty() ? "(empty)" : set.first()));
            case "last":
                if (!rest.isEmpty()) {
                    return CommandResult.of("usage: last");
                }
                return CommandResult.of("last → " + (set.isEmpty() ? "(empty)" : set.last()));
            case "pollfirst": {
                if (!rest.isEmpty()) {
                    return CommandResult.of("usage: pollFirst");
                }
                Integer e = set.pollFirst();
                return CommandResult.of("pollFirst → " + (e != null ? e : "(empty)"));
            }
            case "polllast": {
                if (!rest.isEmpty()) {
                    return CommandResult.of("usage: pollLast");
                }
                Integer e = set.pollLast();
                return CommandResult.of("pollLast → " + (e != null ? e : "(empty)"));
            }
            case "size":
                if (!rest.isEmpty()) {
                    return CommandResult.of("usage: size");
                }
                return CommandResult.of("size = " + set.size());
            case "help":
                if (!rest.isEmpty()) {
                    return CommandResult.of("usage: help");
                }
                return CommandResult.of(helpText());
            case "quit":
            case "exit":
                if (!rest.isEmpty()) {
                    return CommandResult.of("usage: quit");
                }
                return CommandResult.quitting("bye");
            default:
                return CommandResult.of("unknown command: '" + cmd + "' (type 'help')");
        }
    }

    /** Apply the navigation query {@code verb} (lower/floor/ceiling/higher) to {@code set}. */
    private static CommandResult bound(TeachingTreeSet<Integer> set, String rest, String verb) {
        Integer e = soleInt(rest);
        if (e == null) {
            return usageOrParse(rest, verb);
        }
        Integer r = switch (verb) {
            case "lower" -> set.lower(e);
            case "floor" -> set.floor(e);
            case "ceiling" -> set.ceiling(e);
            default -> set.higher(e);
        };
        return CommandResult.of(verb + " " + e + " → " + (r != null ? r : "none"));
    }

    /**
     * The single {@code Integer} in {@code rest}, or {@code null} if {@code rest} is empty, carries
     * more than one whitespace-separated token, or does not parse as an int. An element is one
     * token, so a trailing token is malformed input, not a space-bearing element.
     */
    private static Integer soleInt(String rest) {
        if (rest.isEmpty()) {
            return null;
        }
        String[] tokens = rest.split("\\s+", 2);
        if (tokens.length > 1) {
            return null; // trailing extra token
        }
        try {
            return Integer.valueOf(tokens[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The specific message for a single-int verb whose {@code soleInt} came back {@code null}. */
    private static CommandResult usageOrParse(String rest, String verb) {
        if (rest.isEmpty() || rest.split("\\s+", 2).length > 1) {
            return CommandResult.of("usage: " + verb + " <int>");
        }
        return CommandResult.of("not an integer: '" + rest + "'");
    }

    /** One-line overview plus the grammar, one line per command. */
    public String helpText() {
        return String.join("\n",
                "TeachingTreeSet live REPL — type commands; mutations render live in the browser.",
                "  add <int>        insert an element (integer)",
                "  remove <int>     remove an element",
                "  contains <int>   test membership (the comparison walk animates live)",
                "  first            smallest element (no viz change)",
                "  last             largest element (no viz change)",
                "  lower <int>      greatest element < arg (walk animates live)",
                "  floor <int>      greatest element ≤ arg (walk animates live)",
                "  ceiling <int>    least element ≥ arg (walk animates live)",
                "  higher <int>     least element > arg (walk animates live)",
                "  pollFirst        remove and return the smallest element",
                "  pollLast         remove and return the largest element",
                "  size             number of elements (no viz change)",
                "  help             show this help",
                "  quit             end the session (alias: exit)");
    }
}
