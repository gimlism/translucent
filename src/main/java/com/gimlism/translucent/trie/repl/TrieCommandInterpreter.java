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
 * <p>Keys are {@code String} — a single token (no spaces), values are {@code Integer}. This inverts
 * the HashMap REPL (Integer key, String value): here only {@code put}'s value is parsed. The read
 * verbs ({@code get}/{@code remove}/{@code containsKey}) take one key token and reject a trailing
 * one as a usage error rather than treating it as a space-bearing key — so extra tokens surface as
 * malformed input, matching the Map/List REPLs (where the key/index parse rejects them). This is
 * the shared entry point a browser-controls front-end will reuse in a later slice.
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
                String key = soleKey(rest);
                if (key == null) {
                    return CommandResult.of("usage: remove <key>");
                }
                Integer old = trie.remove(key);
                return CommandResult.of(old != null ? "removed " + key : key + " not found");
            }
            case "get": {
                String key = soleKey(rest);
                if (key == null) {
                    return CommandResult.of("usage: get <key>");
                }
                Integer v = trie.get(key);
                return CommandResult.of("get " + key + " → " + (v != null ? v : "absent"));
            }
            case "containskey":
            case "contains": {
                String key = soleKey(rest);
                if (key == null) {
                    return CommandResult.of("usage: containsKey <key>");
                }
                return CommandResult.of("containsKey " + key + " → " + trie.containsKey(key));
            }
            case "keyswithprefix":
            case "keys": {
                // A prefix is one token, but empty is allowed (lists all keys); reject only extras.
                if (!rest.isEmpty() && rest.split("\\s+", 2).length > 1) {
                    return CommandResult.of("usage: keysWithPrefix [prefix]");
                }
                List<String> keys = trie.keysWithPrefix(rest); // "" → all keys
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

    /**
     * The single key token in {@code rest} (already free of leading whitespace), or {@code null} if
     * {@code rest} is empty or carries more than one whitespace-separated token. A key is one token,
     * so a trailing token is malformed input (a usage error), not a space-bearing key.
     */
    private static String soleKey(String rest) {
        if (rest.isEmpty()) {
            return null;
        }
        String[] tokens = rest.split("\\s+", 2);
        return tokens.length == 1 ? tokens[0] : null;
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
                "  quit                     end the session (alias: exit)");
    }
}
