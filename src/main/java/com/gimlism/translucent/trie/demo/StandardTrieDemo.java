package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.trie.consumer.ConsoleTrieEventLogger;
import com.gimlism.translucent.trie.core.StandardTrie;
import java.io.PrintStream;

/**
 * Scripted demonstration of the teaching standard-trie event stream — the same keys, in the same
 * order, as {@link RadixTrieDemo}, so the only difference you see is the structure itself.
 */
public class StandardTrieDemo {
    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream out) {
        var trie = new StandardTrie<Integer>();
        trie.addListener(new ConsoleTrieEventLogger(out));

        out.println("== inserting keys with shared prefixes (watch one node appear per character) ==");
        int v = 0;
        for (String key : new String[]{"shore", "she", "shell"}) { // create a chain, then descend it
            trie.put(key, v++);
        }

        out.println("== removing keys (watch the prune cascade unwind the chain) ==");
        for (String key : new String[]{"shell", "she"}) { // prune back to the last surviving branch
            trie.remove(key);
        }
    }
}
