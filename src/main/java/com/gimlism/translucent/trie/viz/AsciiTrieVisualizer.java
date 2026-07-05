package com.gimlism.translucent.trie.viz;

import com.gimlism.translucent.substrate.viz.Visualizer;
import com.gimlism.translucent.trie.events.TrieEvent;
import java.io.PrintStream;

/** Live ASCII visualizer for the trie — a {@link Visualizer} wired with an {@link AsciiTrieRenderer}. */
public final class AsciiTrieVisualizer extends Visualizer<TrieEvent> {
    public AsciiTrieVisualizer(PrintStream out, AsciiTrieRenderer renderer) {
        super(out, renderer);
    }

    public AsciiTrieVisualizer(PrintStream out) {
        this(out, new AsciiTrieRenderer());
    }
}
