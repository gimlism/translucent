package com.gimlism.translucent.trie.repl;

import com.gimlism.translucent.substrate.repl.CommandResult;
import com.gimlism.translucent.trie.core.RadixTrie;
import java.util.List;
import java.util.Locale;

/**
 * Parses one command line and applies it to a {@link RadixTrie}, returning a {@link CommandResult}.
 * Pure: it performs no I/O and holds no reference to the live server — mutations it makes fire
 * {@code TrieEvent}s that any attached listener (e.g. {@code TrieLiveVisualizer}) broadcasts, so
 * live rendering is a side-effect of the listener, not of this class. {@link #execute} never
 * throws: every malformed input becomes an error message.
 *
 * <p>Keys are {@code String} (the first token — no spaces, no parsing), values are {@code Integer}.
 * This inverts the HashMap REPL (Integer key, String value): here only {@code put}'s value is
 * parsed, and {@code get}/{@code remove}/{@code containsKey} take the raw key token. This is the
 * shared entry point a browser-controls front-end will reuse in a later slice.
 */
public final class TrieCommandInterpreter {

    /** Parse {@code line}, apply it to {@code trie}, and return the result to show the user. */
    public CommandResult execute(String line, RadixTrie<Integer> trie) {
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
                    return CommandResult.of("usage: put <key> <int-value>");
                }
                Integer value = parseValue(kv[1]);
                if (value == null) {
                    return CommandResult.of("not an integer: '" + kv[1] + "'");
                }
                Integer old = trie.put(kv[0], value);
                return CommandResult.of(old == null
                        ? "put " + kv[0] + " = " + value
                        : "set " + kv[0] + " = " + value + " (was " + old + ")");
            }
            case "remove": {
                if (rest.isEmpty()) {
                    return CommandResult.of("usage: remove <key>");
                }
                Integer old = trie.remove(rest);
                return CommandResult.of(old != null ? "removed " + rest : rest + " not found");
            }
            case "get": {
                if (rest.isEmpty()) {
                    return CommandResult.of("usage: get <key>");
                }
                Integer v = trie.get(rest);
                return CommandResult.of("get " + rest + " → " + (v != null ? v : "absent"));
            }
            case "containskey":
            case "contains": {
                if (rest.isEmpty()) {
                    return CommandResult.of("usage: containsKey <key>");
                }
                return CommandResult.of("containsKey " + rest + " → " + trie.containsKey(rest));
            }
            case "keyswithprefix":
            case "keys": {
                List<String> keys = trie.keysWithPrefix(rest); // empty rest → "" → all keys
                return CommandResult.of("keysWithPrefix \"" + rest + "\" → " + keys);
            }
            case "size":
                return CommandResult.of("size = " + trie.size());
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
    private static Integer parseValue(String token) {
        try {
            return Integer.valueOf(token.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** One-line overview plus the grammar, one line per command. */
    public String helpText() {
        return String.join("\n",
                "RadixTrie live REPL — type commands; mutations render live in the browser.",
                "  put <key> <int-value>    insert or update a key (String key, integer value)",
                "  remove <key>             remove a key",
                "  get <key>                look up a key (prints the value; no viz change)",
                "  containsKey <key>        test membership (alias: contains; no viz change)",
                "  keysWithPrefix [prefix]  list keys under a prefix, all keys if omitted "
                        + "(alias: keys; no viz change)",
                "  size                     number of keys (no viz change)",
                "  help                     show this help",
                "  quit                     stop the server and exit (alias: exit)");
    }
}
