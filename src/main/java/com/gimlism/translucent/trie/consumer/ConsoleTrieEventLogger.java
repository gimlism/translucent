package com.gimlism.translucent.trie.consumer;

import com.gimlism.translucent.trie.events.TrieEvent;
import com.gimlism.translucent.trie.events.TrieEventFormatter;
import com.gimlism.translucent.trie.events.TrieEventListener;
import java.io.PrintStream;

/** Prints a human-readable line per event (formatting only; no invariant checking). */
public class ConsoleTrieEventLogger implements TrieEventListener {
    private final PrintStream out;

    public ConsoleTrieEventLogger() {
        this(System.out);
    }

    public ConsoleTrieEventLogger(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onEvent(TrieEvent event) {
        out.println(TrieEventFormatter.format(event));
    }
}
