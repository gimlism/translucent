package com.gimlism.translucent.hashmap.repl;

import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.substrate.repl.CommandResult;
import java.util.Locale;

/**
 * Parses one command line and applies it to a {@link TeachingHashMap}, returning a
 * {@link CommandResult}. Pure: it performs no I/O and holds no reference to the live server —
 * mutations it makes fire {@code MapEvent}s that any attached listener (e.g. {@code
 * MapLiveVisualizer}) broadcasts, so live rendering is a side-effect of the listener, not of this
 * class. {@link #execute} never throws: every malformed input becomes an error message.
 *
 * <p>Keys are {@code Integer}, values are {@code String} (the rest of the line after the key,
 * interior spaces preserved). This is the shared entry point a browser-controls front-end will
 * reuse in a later slice.
 */
public final class MapCommandInterpreter {

    /** Parse {@code line}, apply it to {@code map}, and return the result to show the user. */
    public CommandResult execute(String line, TeachingHashMap<Integer, String> map) {
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
            case "put": {
                String[] kv = rest.split("\\s+", 2);
                if (kv.length < 2) {
                    return CommandResult.of("usage: put <int-key> <value>");
                }
                Integer key = parseKey(kv[0]);
                if (key == null) {
                    return CommandResult.of("not an integer: '" + kv[0] + "'");
                }
                String old = map.put(key, kv[1]);
                return CommandResult.of(old == null
                        ? "put " + key + " = " + kv[1]
                        : "set " + key + " = " + kv[1] + " (was " + old + ")");
            }
            case "remove": {
                Integer key = parseKey(rest);
                if (key == null) {
                    return CommandResult.of(rest.isEmpty() ? "usage: remove <int-key>" : "not an integer: '" + rest + "'");
                }
                String old = map.remove(key);
                return CommandResult.of(old != null ? "removed " + key : key + " not found");
            }
            case "get": {
                Integer key = parseKey(rest);
                if (key == null) {
                    return CommandResult.of(rest.isEmpty() ? "usage: get <int-key>" : "not an integer: '" + rest + "'");
                }
                String v = map.get(key);
                return CommandResult.of("get " + key + " → " + (v != null ? v : "absent"));
            }
            case "containskey":
            case "contains": {
                Integer key = parseKey(rest);
                if (key == null) {
                    return CommandResult.of(rest.isEmpty() ? "usage: containsKey <int-key>" : "not an integer: '" + rest + "'");
                }
                return CommandResult.of("containsKey " + key + " → " + map.containsKey(key));
            }
            case "size":
                return CommandResult.of("size = " + map.size());
            case "clear": {
                int n = map.size();
                map.clear();
                return CommandResult.of("cleared (" + n + " entries removed)");
            }
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
    private static Integer parseKey(String token) {
        try {
            return Integer.valueOf(token.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** One-line overview plus the grammar, one line per command. */
    public String helpText() {
        return String.join("\n",
                "TeachingHashMap live REPL — type commands; mutations render live in the browser.",
                "  put <int-key> <value>   insert or update a key (value may contain spaces)",
                "  remove <int-key>        remove a key",
                "  get <int-key>           look up a key (prints the value; no viz change)",
                "  containsKey <int-key>   test membership (alias: contains)",
                "  size                    number of entries",
                "  clear                   remove all entries",
                "  help                    show this help",
                "  quit                    end the session (alias: exit)");
    }
}
