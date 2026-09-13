package com.gimlism.translucent.arraylist.demo;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.repl.ListCommandInterpreter;
import com.gimlism.translucent.arraylist.viz.ListLiveVisualizer;
import com.gimlism.translucent.arraylist.viz.ListWebExporter;
import com.gimlism.translucent.substrate.repl.CommandResult;
import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
import com.gimlism.translucent.substrate.viz.LiveServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Interactive terminal REPL for a live {@link TeachingArrayList}: type commands (see {@code help})
 * and watch each mutation render live in the browser — no recompile per change. Reuses Slice B's
 * {@link LiveServer} for the SSE stream. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListLiveReplDemo}
 * The list starts empty; the server stops when you type {@code quit} or send EOF (Ctrl-D).
 */
public class ListLiveReplDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        LiveServer server = new LiveServer(ListWebExporter.liveHtml(), "127.0.0.1", DEFAULT_PORT);
        server.start();
        String url = "http://localhost:" + server.port();
        DemoLifecycle.announcePortFallback(server);

        var list = new TeachingArrayList<String>();
        list.addListener(new ListLiveVisualizer(server::broadcast));

        BrowserLauncher.open(url);
        System.out.println("Live REPL — serving at " + url);
        System.out.println("Type 'help' for commands, 'quit' to stop.");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            runRepl(in, System.out, list, new ListCommandInterpreter());
        } finally {
            server.stop();
        }
    }

    /**
     * Read-eval-print loop (test seam — no server or browser): read a line, execute it, print the
     * message, until a {@code quit} result or EOF. Blank-line results (empty message) print nothing.
     */
    static void runRepl(BufferedReader in, PrintStream out, TeachingArrayList<String> list,
            ListCommandInterpreter interpreter) throws IOException {
        String line;
        while ((line = in.readLine()) != null) { // EOF (Ctrl-D) ends the loop
            CommandResult r = interpreter.execute(line, list);
            if (!r.message().isEmpty()) {
                out.println(r.message());
            }
            if (r.quit()) {
                return;
            }
        }
    }
}
