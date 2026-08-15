package com.gimlism.translucent.trie.repl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.substrate.repl.CommandResult;
import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.core.PrefixMap;
import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.core.StandardTrie;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TrieCommandInterpreterTest {

    private final TrieCommandInterpreter interp = new TrieCommandInterpreter();
    private final RadixTrie<Integer> trie = new RadixTrie<>();

    @Test
    void putNewKeyInsertsAndReports() {
        CommandResult r = interp.execute("put shore 1", trie);
        assertEquals("put shore = 1", r.message());
        assertFalse(r.quit());
        assertEquals(1, trie.get("shore"));
    }

    @Test
    void putExistingKeyReportsSetWithOldValue() {
        interp.execute("put shore 1", trie);
        CommandResult r = interp.execute("put shore 9", trie);
        assertEquals("set shore = 9 (was 1)", r.message());
        assertEquals(9, trie.get("shore"));
    }

    @Test
    void removePresentAndAbsent() {
        interp.execute("put shore 1", trie);
        assertEquals("removed shore", interp.execute("remove shore", trie).message());
        assertFalse(trie.containsKey("shore"));
        assertEquals("shore not found", interp.execute("remove shore", trie).message());
    }

    @Test
    void getPresentAndAbsent() {
        interp.execute("put shore 1", trie);
        assertEquals("get shore → 1", interp.execute("get shore", trie).message());
        assertEquals("get gone → absent", interp.execute("get gone", trie).message());
    }

    @Test
    void containsKeyWithAlias() {
        interp.execute("put shore 1", trie);
        assertEquals("containsKey shore → true", interp.execute("containsKey shore", trie).message());
        assertEquals("containsKey gone → false", interp.execute("contains gone", trie).message());
    }

    @Test
    void keysWithPrefixMatchesEmptyListsAllAndNoMatch() {
        interp.execute("put she 1", trie);
        interp.execute("put shell 2", trie);
        interp.execute("put shore 3", trie);
        // a real prefix → matching keys, lexicographic (matches RadixTrie.keysWithPrefix order)
        assertEquals("keysWithPrefix \"sh\" → [she, shell, shore]",
                interp.execute("keysWithPrefix sh", trie).message());
        // empty prefix → all keys (alias 'keys')
        assertEquals("keysWithPrefix \"\" → [she, shell, shore]",
                interp.execute("keys", trie).message());
        // no match → empty list
        assertEquals("keysWithPrefix \"xyz\" → []",
                interp.execute("keysWithPrefix xyz", trie).message());
        // a second token is rejected (a prefix is a single token; empty is still allowed above)
        assertEquals("usage: keysWithPrefix [prefix]",
                interp.execute("keysWithPrefix sh extra", trie).message());
    }

    @Test
    void sizeCountsKeys() {
        interp.execute("put she 1", trie);
        interp.execute("put shore 2", trie);
        assertEquals("size = 2", interp.execute("size", trie).message());
    }

    @Test
    void quitAndExitSetQuitFlag() {
        assertTrue(interp.execute("quit", trie).quit());
        assertTrue(interp.execute("exit", trie).quit());
        assertEquals("bye", interp.execute("quit", trie).message());
    }

    @Test
    void commandWordIsCaseInsensitiveAndWhitespaceTolerant() {
        assertEquals("put shore = 1", interp.execute("  PuT   shore   1  ", trie).message());
        assertEquals(1, trie.get("shore"));
    }

    @Test
    void blankLineIsANoOpWithEmptyMessage() {
        CommandResult r = interp.execute("   ", trie);
        assertEquals("", r.message());
        assertFalse(r.quit());
        assertEquals(0, trie.size());
    }

    @Test
    void nullLineIsANoOpWithoutThrowing() {
        CommandResult r = interp.execute(null, trie);
        assertEquals("", r.message());
        assertFalse(r.quit());
        assertEquals(0, trie.size());
    }

    @Test
    void errorPathsLeaveTrieUnchangedAndReport() {
        assertEquals("usage: put <key> <int-value>", interp.execute("put shore", trie).message());
        assertEquals("not an integer: 'x'", interp.execute("put shore x", trie).message());
        // multi-token value: the whole rest-after-key must parse as one int, so this fails
        assertEquals("not an integer: '1 2'", interp.execute("put shore 1 2", trie).message());
        assertEquals("unknown command: 'frobnicate' (type 'help')",
                interp.execute("frobnicate shore", trie).message());
        assertFalse(interp.execute("put shore", trie).quit());
        assertEquals(0, trie.size(), "no error path mutated the trie");
    }

    @Test
    void errorPathsForRemoveGetContainsKey() {
        assertEquals("usage: remove <key>", interp.execute("remove", trie).message());
        assertEquals("usage: get <key>", interp.execute("get", trie).message());
        assertEquals("usage: containsKey <key>", interp.execute("contains", trie).message());
        assertEquals(0, trie.size(), "no error path mutated the trie");
    }

    @Test
    void extraTokensOnReadsAreRejectedAsUsageErrors() {
        interp.execute("put shore 1", trie);
        // a key is a single token; a trailing token is malformed input, not a space-bearing key
        assertEquals("usage: get <key>", interp.execute("get shore extra", trie).message());
        assertEquals("usage: remove <key>", interp.execute("remove shore extra", trie).message());
        assertEquals("usage: containsKey <key>",
                interp.execute("containsKey shore extra", trie).message());
        // the reject path is inert: nothing was removed, the trie is untouched
        assertEquals(1, trie.size(), "a rejected read mutated nothing");
        assertEquals(1, trie.get("shore"));
    }

    @Test
    void helpListsEveryCommandWord() {
        String help = interp.execute("help", trie).message();
        for (String word : new String[] {
                "put", "remove", "get", "containsKey", "keysWithPrefix", "size", "help", "quit"}) {
            assertTrue(help.contains(word), "help mentions " + word);
        }
    }

    @Test
    void aMutationThroughTheInterpreterEmitsAtLeastOneEvent() {
        var rec = new TrieRecordingListener();
        trie.addListener(rec);
        interp.execute("put shore 1", trie); // first insert into an empty trie → ≥1 event
        assertFalse(rec.events().isEmpty(), "a put emits at least one event");
    }

    @Test
    void readsThroughTheInterpreterEmitNothing() {
        interp.execute("put shore 1", trie);
        var rec = new TrieRecordingListener();
        trie.addListener(rec);
        interp.execute("get shore", trie);
        interp.execute("containsKey shore", trie);
        interp.execute("keysWithPrefix sh", trie);
        interp.execute("size", trie);
        assertTrue(rec.events().isEmpty(), "reads emit no event");
    }

    /**
     * The help line's structure name is derived from the trie it was handed, not hardcoded. Nothing
     * pinned that before: helpListsEveryCommandWord asserts only that each command WORD appears, so
     * a help text naming the wrong trie would ship green. Parameterised over both implementations
     * because a derivation that is wrong for one and right for the other is the failure shape.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("bothTries")
    void helpNamesTheTrieItWasHanded(String expectedName, PrefixMap<Integer> trie) {
        String help = interp.execute("help", trie).message();
        assertTrue(help.startsWith(expectedName + " live REPL"),
                "help must name the structure it was handed; got: " + help.lines().findFirst().orElse(""));
    }

    static Stream<Arguments> bothTries() {
        return Stream.of(
                Arguments.of("RadixTrie", new RadixTrie<Integer>()),
                Arguments.of("StandardTrie", new StandardTrie<Integer>()));
    }
}
