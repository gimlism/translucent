package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.trie.consumer.ConsoleTrieEventLogger;
import com.gimlism.translucent.trie.core.RadixTrie;
import java.io.PrintStream;

/** Scripted demonstration of the teaching radix-trie event stream. */
public class RadixTrieDemo {
    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream out) {
        var trie = new RadixTrie<Integer>();
        trie.addListener(new ConsoleTrieEventLogger(out));

        out.println("== inserting keys with shared prefixes (watch edges split and branch) ==");
        int v = 0;
        for (String key : new String[]{"shore", "she", "shell"}) { // create, split, descend
            trie.put(key, v++);
        }

        out.println("== removing keys (watch leaves prune and single-child edges merge) ==");
        for (String key : new String[]{"shell", "she"}) { // prune, then prune + merge
            trie.remove(key);
        }
    }
}
