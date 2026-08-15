package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.trie.core.StandardTrie;
import com.gimlism.translucent.trie.viz.TrieLiveVisualizer;
import com.gimlism.translucent.trie.viz.TrieWebExporter;
import java.io.IOException;

/**
 * The student sandbox: a running {@link StandardTrie} whose every mutation renders live in the
 * browser. Write your own put/remove calls in the marked block, then run:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieLiveWebVizDemo}
 * and watch the chain grow a node per character as your code runs. The server keeps running after
 * your code finishes so the final state stays live and scrubbable — stop it with Ctrl-C.
 *
 * <p><b>Known limitation:</b> every live demo in this repo binds {@value #DEFAULT_PORT} and none of
 * them parses {@code args}, so this demo and {@link RadixTrieLiveWebVizDemo} cannot run at the same
 * time — the second to start fails to bind. Run them one after the other, or compare the two static
 * replay pages ({@code docs/viz/standard-trie.html} and {@code docs/viz/trie.html}), which are plain
 * files and open side by side.
 */
public class StandardTrieLiveWebVizDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        LiveServer server = new LiveServer(TrieWebExporter.liveHtml("StandardTrie"), "127.0.0.1", DEFAULT_PORT);
        server.start();
        String url = "http://localhost:" + server.port();

        var trie = new StandardTrie<Integer>();
        trie.addListener(new TrieLiveVisualizer(server::broadcast));

        BrowserLauncher.open(url);
        System.out.println("Serving live at " + url + " — Ctrl-C to stop.");

        // ---------------------------------------------------------------
        // TODO: your mutations here — each renders live in the browser.
        int v = 1;
        for (String key : new String[] {"shore", "she", "shell"}) { // one node per character
            trie.put(key, v++);
        }
        for (String key : new String[] {"shell", "she"}) { // the prune cascade unwinds the chain
            trie.remove(key);
        }
        // ---------------------------------------------------------------

        DemoLifecycle.awaitShutdown(server);
    }
}
