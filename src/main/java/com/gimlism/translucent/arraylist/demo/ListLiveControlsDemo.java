package com.gimlism.translucent.arraylist.demo;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.repl.ListCommandInterpreter;
import com.gimlism.translucent.arraylist.viz.ListLiveVisualizer;
import com.gimlism.translucent.arraylist.viz.ListWebExporter;
import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
import com.gimlism.translucent.substrate.viz.LiveServer;
import java.io.IOException;
import java.util.function.Function;

/**
 * Browser-driven live demo: type commands into the page (a box wired to {@code POST /command}) and
 * watch each mutation render live. The list lives server-side; the browser is the REPL over HTTP,
 * running the SAME {@link ListCommandInterpreter} as {@link ListLiveReplDemo}. No stdin. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListLiveControlsDemo}
 * The list starts empty; the server keeps running until Ctrl-C.
 */
public class ListLiveControlsDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        var list = new TeachingArrayList<String>();
        Function<String, String> handler = commandHandler(list, new ListCommandInterpreter());

        LiveServer server = new LiveServer(ListWebExporter.controlsHtml(), "127.0.0.1", DEFAULT_PORT, handler);
        server.start();
        list.addListener(new ListLiveVisualizer(server::broadcast));

        String url = "http://localhost:" + server.port();
        DemoLifecycle.announcePortFallback(server);
        BrowserLauncher.open(url);
        System.out.println("Live controls — serving at " + url + " — type commands in the browser; Ctrl-C to stop.");

        DemoLifecycle.awaitShutdown(server);
    }

    /**
     * The list-specific command seam (tested): applies each POSTed line to {@code list} via
     * {@code interpreter}, returning the message to show. Serialized behind a private lock because
     * {@link LiveServer}'s virtual-thread executor can dispatch overlapping POSTs onto a
     * non-thread-safe {@link TeachingArrayList}. {@code quit}/{@code exit} return their message but
     * do NOT stop the server — a stray POST must not kill the session (the seam holds no server).
     */
    static Function<String, String> commandHandler(TeachingArrayList<String> list,
            ListCommandInterpreter interpreter) {
        final Object lock = new Object();
        return line -> {
            synchronized (lock) {
                return interpreter.execute(line, list).message();
            }
        };
    }
}
