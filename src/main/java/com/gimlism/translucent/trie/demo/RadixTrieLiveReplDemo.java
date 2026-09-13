package com.gimlism.translucent.trie.demo;

import com.gimlism.translucent.substrate.repl.CommandResult;
import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.repl.TrieCommandInterpreter;
import com.gimlism.translucent.trie.viz.TrieLiveVisualizer;
import com.gimlism.translucent.trie.viz.TrieWebExporter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Interactive terminal REPL for a live {@link RadixTrie}: type commands (see {@code help}) and
 * watch each mutation render live in the browser — no recompile per change. Reuses Slice B's
 * {@link LiveServer} for the SSE stream. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.trie.demo.RadixTrieLiveReplDemo}
 * The trie starts empty; the server stops when you type {@code quit} or send EOF (Ctrl-D).
 *
 * <p><b>Known limitation:</b> binds {@value #DEFAULT_PORT} and ignores {@code args}, as every live
 * demo here does, so it cannot run alongside another live demo.
 */
public class RadixTrieLiveReplDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        LiveServer server = new LiveServer(TrieWebExporter.liveHtml("RadixTrie"), "127.0.0.1", DEFAULT_PORT);
        server.start();
        String url = "http://localhost:" + server.port();
        DemoLifecycle.announcePortFallback(server);

        var trie = new RadixTrie<Integer>();
        trie.addListener(new TrieLiveVisualizer(server::broadcast));

        BrowserLauncher.open(url);
        System.out.println("Live REPL — serving at " + url);
        System.out.println("Type 'help' for commands, 'quit' to stop.");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            runRepl(in, System.out, trie, new TrieCommandInterpreter());
        } finally {
            server.stop();
        }
    }

    /**
     * Read-eval-print loop (test seam — no server or browser): read a line, execute it, print the
     * message, until a {@code quit} result or EOF. Blank-line results (empty message) print nothing.
     */
    static void runRepl(BufferedReader in, PrintStream out, RadixTrie<Integer> trie,
            TrieCommandInterpreter interpreter) throws IOException {
        String line;
        while ((line = in.readLine()) != null) { // EOF (Ctrl-D) ends the loop
            CommandResult r = interpreter.execute(line, trie);
            if (!r.message().isEmpty()) {
                out.println(r.message());
            }
            if (r.quit()) {
                return;
            }
        }
    }
}
