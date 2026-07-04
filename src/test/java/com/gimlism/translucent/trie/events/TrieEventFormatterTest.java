package com.gimlism.translucent.trie.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TrieEventFormatterTest {
    private static final TrieSnapshot ANY = new TrieSnapshot(new TrieNodeSnapshot(false, null, List.of()), 0);

    @Test
    void labels() {
        assertEquals("DESCEND \"sh\" -> \"sh\"", TrieEventFormatter.format(new Descend("sh", "sh", ANY)));
        assertEquals("CREATE \"ore\" -> \"shore\"", TrieEventFormatter.format(new CreateNode("ore", "shore", ANY)));
        assertEquals("SPLIT \"ore\" @ \"o\" -> \"sho\"", TrieEventFormatter.format(new SplitEdge("ore", "o", "sho", ANY)));
        assertEquals("PUT \"shore\"=2 (new)", TrieEventFormatter.format(new Put("shore", 2, null, true, "shore", ANY)));
        assertEquals("PUT \"she\"=9 (replaced 8)", TrieEventFormatter.format(new Put("she", 9, 8, false, "she", ANY)));
        assertEquals("REMOVE \"she\" (was 8)", TrieEventFormatter.format(new Remove("she", 8, "she", ANY)));
        assertEquals("MERGE \"shore\" -> \"shore\"", TrieEventFormatter.format(new MergeEdge("shore", "shore", ANY)));
        assertEquals("PRUNE \"e\" <- \"she\"", TrieEventFormatter.format(new Prune("e", "she", ANY)));
    }
}
