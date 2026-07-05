package com.gimlism.translucent.trie.viz;

import com.gimlism.translucent.substrate.viz.Replayer;
import com.gimlism.translucent.trie.events.TrieEvent;
import java.util.List;

/** Step-through ASCII replayer for a recorded trie stream — a {@link Replayer} + {@link AsciiTrieRenderer}. */
public final class AsciiTrieReplayer extends Replayer<TrieEvent> {
    public AsciiTrieReplayer(List<TrieEvent> events, AsciiTrieRenderer renderer) {
        super(events, renderer);
    }
}
