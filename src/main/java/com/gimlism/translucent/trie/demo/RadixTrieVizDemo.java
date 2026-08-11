package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.viz.AsciiTrieVisualizer;
import java.io.PrintStream;

/**
 * Same scripted story as {@link RadixTrieDemo}, rendered as live ASCII trees instead of a text log:
 * every event prints the indented N-ary trie with the affected node (by path) highlighted.
 */
public class RadixTrieVizDemo {
    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream out) {
        var trie = new RadixTrie<Integer>();
        trie.addListener(new AsciiTrieVisualizer(out));

        out.println("== inserting keys with shared prefixes (watch edges split and branch) ==");
        int v = 0;
        for (String key : new String[]{"shore", "she", "shell"}) {
            trie.put(key, v++);
        }

        out.println("== removing keys (watch the walk, then leaves prune and edges merge) ==");
        for (String key : new String[]{"shell", "she"}) {
            trie.remove(key);
        }
    }
}
