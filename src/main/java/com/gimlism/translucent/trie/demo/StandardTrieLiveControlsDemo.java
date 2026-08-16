package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.trie.core.StandardTrie;
import com.gimlism.translucent.trie.repl.TrieCommandInterpreter;
import com.gimlism.translucent.trie.viz.TrieLiveVisualizer;
import com.gimlism.translucent.trie.viz.TrieWebExporter;
import java.io.IOException;
import java.util.function.Function;

/**
 * Browser-driven live demo: type commands into the page (a box wired to {@code POST /command}) and
 * watch each mutation render live. The trie lives server-side; the browser is the REPL over HTTP,
 * running the SAME {@link TrieCommandInterpreter} as {@link StandardTrieLiveReplDemo}. No stdin. Run
 * with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.trie.demo.StandardTrieLiveControlsDemo}
 * The trie starts empty; the server keeps running until Ctrl-C.
 *
 * <p><b>Known limitation:</b> binds {@value #DEFAULT_PORT} and ignores {@code args}, as every live
 * demo here does, so it cannot run alongside another live demo.
 */
public class StandardTrieLiveControlsDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        var trie = new StandardTrie<Integer>();
        Function<String, String> handler = commandHandler(trie, new TrieCommandInterpreter());

        LiveServer server = new LiveServer(TrieWebExporter.controlsHtml("StandardTrie"), "127.0.0.1",
                DEFAULT_PORT, handler);
        server.start();
        trie.addListener(new TrieLiveVisualizer(server::broadcast));

        String url = "http://localhost:" + server.port();
        BrowserLauncher.open(url);
        System.out.println("Live controls — serving at " + url + " — type commands in the browser; Ctrl-C to stop.");

        DemoLifecycle.awaitShutdown(server);
    }

    /**
     * The trie-specific command seam (tested): applies each POSTed line to {@code trie} via
     * {@code interpreter}, returning the message to show. Serialized behind a private lock because
     * {@link LiveServer}'s virtual-thread executor can dispatch overlapping POSTs onto a
     * non-thread-safe {@link StandardTrie}. {@code quit}/{@code exit} return their message but do NOT
     * stop the server — a stray POST must not kill the session (the seam holds no server).
     */
    static Function<String, String> commandHandler(StandardTrie<Integer> trie,
            TrieCommandInterpreter interpreter) {
        final Object lock = new Object();
        return line -> {
            synchronized (lock) {
                return interpreter.execute(line, trie).message();
            }
        };
    }
}
