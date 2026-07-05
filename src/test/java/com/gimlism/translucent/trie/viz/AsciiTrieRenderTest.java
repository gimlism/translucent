package com.gimlism.translucent.trie.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.events.CreateNode;
import com.gimlism.translucent.trie.events.MergeEdge;
import com.gimlism.translucent.trie.events.Prune;
import com.gimlism.translucent.trie.events.Put;
import com.gimlism.translucent.trie.events.Remove;
import com.gimlism.translucent.trie.events.SplitEdge;
import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class AsciiTrieRenderTest {
    private final AsciiTrieRenderer r = new AsciiTrieRenderer();

    // {she=1, shore=2}: root -"sh"-> (non-key) { "e"=1 (key), "ore"=2 (key) }
    private static TrieSnapshot sheShore() {
        var e = new TrieNodeSnapshot(true, 1, List.of());
        var ore = new TrieNodeSnapshot(true, 2, List.of());
        var sh = new TrieNodeSnapshot(false, null,
            List.of(new TrieEdge("e", e), new TrieEdge("ore", ore)));
        var root = new TrieNodeSnapshot(false, null, List.of(new TrieEdge("sh", sh)));
        return new TrieSnapshot(root, 2);
    }

    @Test
    void rendersIndentedTreeWithLabelsAndKeyMarkers() {
        String expected = String.join("\n",
            "trie: size=2",
            "  (root)",
            "    \"sh\"",
            "      \"e\" ●=1",
            "      \"ore\" ●=2");
        assertEquals(expected, r.renderTrie(sheShore(), null));
    }

    @Test
    void highlightsTheNodeAtThePath() {
        String expected = String.join("\n",
            "trie: size=2",
            "  (root)",
            "    \"sh\"",
            ">     \"e\" ●=1",
            "      \"ore\" ●=2");
        assertEquals(expected, r.renderTrie(sheShore(), "she")); // path she -> the "e" node
    }

    @Test
    void nonMatchingPathHighlightsNothing() {
        assertEquals(r.renderTrie(sheShore(), null), r.renderTrie(sheShore(), "zzz"));
    }

    @Test
    void rootKeyShowsValue() {
        var root = new TrieNodeSnapshot(true, 9, List.of());
        String expected = String.join("\n",
            "trie: size=1",
            "  (root) ●=9");
        assertEquals(expected, r.renderTrie(new TrieSnapshot(root, 1), null));
    }

    @Test
    void pruneFrameHighlightsTheParentThatLostTheChild() {
        // after Prune "ll" <- "shell": the "e" (she) node has lost its "ll" child but survives
        var e = new TrieNodeSnapshot(true, 1, List.of());
        var ore = new TrieNodeSnapshot(true, 3, List.of());
        var sh = new TrieNodeSnapshot(false, null,
            List.of(new TrieEdge("e", e), new TrieEdge("ore", ore)));
        var root = new TrieNodeSnapshot(false, null, List.of(new TrieEdge("sh", sh)));
        var afterPrune = new TrieSnapshot(root, 2);

        String out = r.renderEvent(new Prune("ll", "shell", afterPrune));
        String expected = String.join("\n",
            "PRUNE \"ll\" <- \"shell\"",
            "trie: size=2",
            "  (root)",
            "    \"sh\"",
            ">     \"e\" ●=1",   // the parent "she", highlighted — not the vanished "shell"
            "      \"ore\" ●=3");
        assertEquals(expected, out);
    }

    @Test
    void renderEventPutsLabelAboveTreeAndHighlightsThePath() {
        Put p = new Put("she", 1, null, true, "she", sheShore());
        String out = r.renderEvent(p);
        assertTrue(out.startsWith("PUT \"she\"=1 (new)\n"), out);
        assertTrue(out.contains("> "), out);
    }

    @Test
    void affectedPathReturnsEachEventsPath() {
        var s = sheShore();
        assertEquals("she", AsciiTrieRenderer.affectedPath(new Put("she", 1, null, true, "she", s)));
        assertEquals("sh", AsciiTrieRenderer.affectedPath(new SplitEdge("shore", "sh", "sh", s)));
        assertEquals("shell", AsciiTrieRenderer.affectedPath(new CreateNode("ll", "shell", s)));
        assertEquals("she", AsciiTrieRenderer.affectedPath(new Remove("she", 1, "she", s)));
        // a Prune's leaf is gone from after(); highlight the parent that lost the child ("shell" - "ll")
        assertEquals("she", AsciiTrieRenderer.affectedPath(new Prune("ll", "shell", s)));
        assertEquals("shore", AsciiTrieRenderer.affectedPath(new MergeEdge("shore", "shore", s)));
    }
}
