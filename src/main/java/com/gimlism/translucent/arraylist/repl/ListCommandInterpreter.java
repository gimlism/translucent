package com.gimlism.translucent.arraylist.repl;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.substrate.repl.CommandResult;
import java.util.Locale;

/**
 * Parses one command line and applies it to a {@link TeachingArrayList}, returning a
 * {@link CommandResult}. Pure: it performs no I/O and holds no reference to the live server —
 * mutations it makes fire {@code ListEvent}s that any attached listener (e.g. {@code
 * ListLiveVisualizer}) broadcasts, so live rendering is a side-effect of the listener, not of
 * this class. {@link #execute} never throws: every malformed input becomes an error message.
 *
 * <p>Elements are {@code String} (the rest of the line after the verb/index, interior spaces
 * preserved), indices are {@code int}. The grammar reflects the list's real API — two insertion
 * ops as distinct verbs ({@code add} appends, {@code insert} places at an index), no
 * {@code clear}/{@code contains} (the structure has neither). This is the shared entry point a
 * browser-controls front-end will reuse in a later slice.
 */
public final class ListCommandInterpreter {

    /** Parse {@code line}, apply it to {@code list}, and return the result to show the user. */
    public CommandResult execute(String line, TeachingArrayList<String> list) {
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
                if (rest.isEmpty()) {
                    return CommandResult.of("usage: add <value>");
                }
                list.add(rest);
                return CommandResult.of("appended \"" + rest + "\" at " + (list.size() - 1));
            }
            case "insert": {
                String[] iv = rest.split("\\s+", 2);
                if (iv.length < 2) {
                    return CommandResult.of("usage: insert <index> <value>");
                }
                Integer index = parseIndex(iv[0]);
                if (index == null) {
                    return CommandResult.of("not an integer: '" + iv[0] + "'");
                }
                try {
                    list.add(index, iv[1]);
                } catch (IndexOutOfBoundsException e) {
                    return outOfRange(index, list);
                }
                return CommandResult.of("inserted \"" + iv[1] + "\" at " + index);
            }
            case "set": {
                String[] iv = rest.split("\\s+", 2);
                if (iv.length < 2) {
                    return CommandResult.of("usage: set <index> <value>");
                }
                Integer index = parseIndex(iv[0]);
                if (index == null) {
                    return CommandResult.of("not an integer: '" + iv[0] + "'");
                }
                try {
                    String old = list.set(index, iv[1]);
                    return CommandResult.of("set " + index + " = \"" + iv[1] + "\" (was \"" + old + "\")");
                } catch (IndexOutOfBoundsException e) {
                    return outOfRange(index, list);
                }
            }
            case "remove": {
                if (rest.isEmpty()) {
                    return CommandResult.of("usage: remove <index>");
                }
                Integer index = parseIndex(rest);
                if (index == null) {
                    return CommandResult.of("not an integer: '" + rest + "'");
                }
                try {
                    String old = list.remove((int) index);
                    return CommandResult.of("removed \"" + old + "\" at " + index);
                } catch (IndexOutOfBoundsException e) {
                    return outOfRange(index, list);
                }
            }
            case "get": {
                if (rest.isEmpty()) {
                    return CommandResult.of("usage: get <index>");
                }
                Integer index = parseIndex(rest);
                if (index == null) {
                    return CommandResult.of("not an integer: '" + rest + "'");
                }
                try {
                    return CommandResult.of("get " + index + " → \"" + list.get(index) + "\"");
                } catch (IndexOutOfBoundsException e) {
                    return outOfRange(index, list);
                }
            }
            case "size":
                return CommandResult.of("size = " + list.size());
            case "help":
                return CommandResult.of(helpText());
            case "quit":
            case "exit":
                return CommandResult.quitting("bye");
            default:
                return CommandResult.of("unknown command: '" + cmd + "' (type 'help')");
        }
    }

    /** {@code Integer} value of {@code token}, or {@code null} if it isn't a valid int. */
    private static Integer parseIndex(String token) {
        try {
            return Integer.valueOf(token.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Uniform out-of-range message including the offending index and the current size. */
    private static CommandResult outOfRange(int index, TeachingArrayList<String> list) {
        return CommandResult.of("index out of range: " + index + " (size " + list.size() + ")");
    }

    /** One-line overview plus the grammar, one line per command. */
    public String helpText() {
        return String.join("\n",
                "TeachingArrayList live REPL — type commands; mutations render live in the browser.",
                "  add <value>             append a value to the end (value may contain spaces)",
                "  insert <index> <value>  insert a value, shifting survivors right",
                "  set <index> <value>     overwrite the value at an index",
                "  remove <index>          remove the value at an index, shifting survivors left",
                "  get <index>             look up a value (prints it; no viz change)",
                "  size                    number of elements",
                "  help                    show this help",
                "  quit                    end the session (alias: exit)");
    }
}
