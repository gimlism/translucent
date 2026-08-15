package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.viz.TrieLiveVisualizer;
import com.gimlism.translucent.trie.viz.TrieWebExporter;
import java.io.IOException;

/**
 * The student sandbox: a running {@link RadixTrie} whose every mutation renders live in the browser.
 * Write your own put/remove calls in the marked block, then run:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.trie.demo.RadixTrieLiveWebVizDemo}
 * and watch the trie change as your code runs. The server keeps running after your code finishes so
 * the final state stays live and scrubbable — stop it with Ctrl-C.
 *
 * <p><b>Known limitation:</b> every live demo in this repo binds {@value #DEFAULT_PORT} and none of
 * them parses {@code args}, so this demo and {@link StandardTrieLiveWebVizDemo} cannot run at the
 * same time — the second to start fails to bind. Run them one after the other, or compare the two
 * static replay pages ({@code docs/viz/trie.html} and {@code docs/viz/standard-trie.html}), which
 * are plain files and open side by side.
 */
public class RadixTrieLiveWebVizDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        LiveServer server = new LiveServer(TrieWebExporter.liveHtml("RadixTrie"), "127.0.0.1", DEFAULT_PORT);
        server.start();
        String url = "http://localhost:" + server.port();

        var trie = new RadixTrie<Integer>();
        trie.addListener(new TrieLiveVisualizer(server::broadcast));

        BrowserLauncher.open(url);
        System.out.println("Serving live at " + url + " — Ctrl-C to stop.");

        // ---------------------------------------------------------------
        // TODO: your mutations here — each renders live in the browser.
        int v = 1;
        for (String key : new String[] {"shore", "she", "shell"}) { // edges split and branch
            trie.put(key, v++);
        }
        for (String key : new String[] {"shell", "she"}) { // leaves prune and edges merge
            trie.remove(key);
        }
        // ---------------------------------------------------------------

        DemoLifecycle.awaitShutdown(server);
    }
}
