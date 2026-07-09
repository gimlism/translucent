package com.gimlism.translucent.hashmap.demo;

import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.repl.MapCommandInterpreter;
import com.gimlism.translucent.hashmap.viz.MapLiveVisualizer;
import com.gimlism.translucent.hashmap.viz.MapWebExporter;
import com.gimlism.translucent.substrate.viz.LiveServer;
import java.io.IOException;
import java.util.function.Function;

/**
 * Browser-driven live demo: type commands into the page (a box wired to {@code POST /command}) and
 * watch each mutation render live. The map lives server-side; the browser is the REPL over HTTP,
 * running the SAME {@link MapCommandInterpreter} as {@link LiveReplDemo}. No stdin. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.hashmap.demo.LiveControlsDemo}
 * The map starts empty; the server keeps running until Ctrl-C.
 */
public class LiveControlsDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        var map = new TeachingHashMap<Integer, String>();
        Function<String, String> handler = commandHandler(map, new MapCommandInterpreter());

        LiveServer server = new LiveServer(MapWebExporter.controlsHtml(), "127.0.0.1", DEFAULT_PORT, handler);
        server.start();
        map.addListener(new MapLiveVisualizer(server::broadcast));

        String url = "http://localhost:" + server.port();
        BrowserLauncher.open(url);
        System.out.println("Live controls — serving at " + url + " — type commands in the browser; Ctrl-C to stop.");

        DemoLifecycle.awaitShutdown(server);
    }

    /**
     * The map-specific command seam (tested): applies each POSTed line to {@code map} via
     * {@code interpreter}, returning the message to show. Serialized behind a private lock because
     * {@link LiveServer}'s virtual-thread executor can dispatch overlapping POSTs onto a
     * non-thread-safe map. {@code quit}/{@code exit} return their message but do NOT stop the
     * server — a stray POST must not kill the session.
     */
    static Function<String, String> commandHandler(TeachingHashMap<Integer, String> map,
            MapCommandInterpreter interpreter) {
        final Object lock = new Object();
        return line -> {
            synchronized (lock) {
                return interpreter.execute(line, map).message();
            }
        };
    }
}
