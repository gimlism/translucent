package com.gimlism.translucent.treeset.demo;

import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.repl.TreeSetCommandInterpreter;
import com.gimlism.translucent.treeset.viz.TreeSetLiveVisualizer;
import com.gimlism.translucent.treeset.viz.TreeSetWebExporter;
import java.io.IOException;
import java.util.function.Function;

/**
 * Browser-driven live demo: type commands into the page (a box wired to {@code POST /command}) and
 * watch each mutation render live — and, because the set narrates comparison reads, watch a
 * {@code contains}/{@code floor} walk animate the comparison cursor live. The set lives server-side;
 * the browser is the REPL over HTTP, running the SAME {@link TreeSetCommandInterpreter} as
 * {@link TreeSetLiveReplDemo}. No stdin. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.treeset.demo.TreeSetLiveControlsDemo}
 * The set starts empty; the server keeps running until Ctrl-C.
 */
public class TreeSetLiveControlsDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        var set = new TeachingTreeSet<Integer>();
        Function<String, String> handler = commandHandler(set, new TreeSetCommandInterpreter());

        LiveServer server = new LiveServer(TreeSetWebExporter.controlsHtml(), "127.0.0.1", DEFAULT_PORT, handler);
        server.start();
        set.addListener(new TreeSetLiveVisualizer(server::broadcast));

        String url = "http://localhost:" + server.port();
        DemoLifecycle.announcePortFallback(server);
        BrowserLauncher.open(url);
        System.out.println("Live controls — serving at " + url + " — type commands in the browser; Ctrl-C to stop.");

        DemoLifecycle.awaitShutdown(server);
    }

    /**
     * The set-specific command seam (tested): applies each POSTed line to {@code set} via
     * {@code interpreter}, returning the message to show. Serialized behind a private lock because
     * {@link LiveServer}'s virtual-thread executor can dispatch overlapping POSTs onto a
     * non-thread-safe {@link TeachingTreeSet}. {@code quit}/{@code exit} return their message but do
     * NOT stop the server — a stray POST must not kill the session (the seam holds no server).
     */
    static Function<String, String> commandHandler(TeachingTreeSet<Integer> set,
            TreeSetCommandInterpreter interpreter) {
        final Object lock = new Object();
        return line -> {
            synchronized (lock) {
                return interpreter.execute(line, set).message();
            }
        };
    }
}
