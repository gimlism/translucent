package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.trie.core.StandardTrie;
import com.gimlism.translucent.trie.viz.AsciiTrieVisualizer;
import java.io.PrintStream;

/**
 * Same scripted story as {@link StandardTrieDemo}, rendered as live ASCII trees instead of a text
 * log: every event prints the indented N-ary trie with the affected node (by path) highlighted. Run
 * it beside {@link RadixTrieVizDemo} — same keys, same order — and the depth difference is the whole
 * argument for compression.
 */
public class StandardTrieVizDemo {
    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream out) {
        var trie = new StandardTrie<Integer>();
        trie.addListener(new AsciiTrieVisualizer(out));

        out.println("== inserting keys with shared prefixes (watch one node appear per character) ==");
        int v = 0;
        for (String key : new String[]{"shore", "she", "shell"}) {
            trie.put(key, v++);
        }

        out.println("== removing keys (watch the walk, then the prune cascade unwind the chain) ==");
        for (String key : new String[]{"shell", "she"}) {
            trie.remove(key);
        }
    }
}
