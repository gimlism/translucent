package com.gimlism.translucent.hashmap.demo;

import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.viz.MapLiveVisualizer;
import com.gimlism.translucent.hashmap.viz.MapWebExporter;
import com.gimlism.translucent.substrate.viz.LiveServer;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * The student sandbox: a running {@link TeachingHashMap} whose every mutation renders live in the
 * browser. Write your own {@code put}/{@code remove} calls in the marked block, then run:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.hashmap.demo.LiveWebVizDemo}
 * and watch the structure change as your code runs. The server keeps running after your code
 * finishes so the final state stays live and scrubbable — stop it with Ctrl-C.
 */
public class LiveWebVizDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        LiveServer server = new LiveServer(MapWebExporter.liveHtml(), "127.0.0.1", DEFAULT_PORT);
        server.start();
        String url = "http://localhost:" + server.port();

        var map = new TeachingHashMap<Integer, String>();
        map.addListener(new MapLiveVisualizer(server::broadcast));

        BrowserLauncher.open(url);
        System.out.println("Serving live at " + url + " — Ctrl-C to stop.");

        // ---------------------------------------------------------------
        // TODO: your mutations here — each call renders live in the browser.
        for (int k : new int[] {0, 8, 16, 24, 32}) {
            map.put(k, "v" + k);
        }
        map.remove(16);
        // ---------------------------------------------------------------

        awaitShutdown(server);
    }

    /** Park the main thread so the JVM stays alive until Ctrl-C; stop the server cleanly on the way out. */
    private static void awaitShutdown(LiveServer server) {
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        try {
            new CountDownLatch(1).await(); // never counted down — Ctrl-C runs the shutdown hook and exits
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
