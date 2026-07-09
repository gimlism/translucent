package com.gimlism.translucent.hashmap.demo;

import com.gimlism.translucent.substrate.viz.LiveServer;
import java.util.concurrent.CountDownLatch;

/** Shared demo lifecycle: keep the JVM alive so a live {@link LiveServer} stays serving until Ctrl-C. */
final class DemoLifecycle {

    private DemoLifecycle() {}

    /** Park the calling thread until Ctrl-C (or interrupt); stop the server cleanly on the way out. */
    static void awaitShutdown(LiveServer server) {
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        try {
            new CountDownLatch(1).await(); // never counted down — Ctrl-C runs the shutdown hook and exits
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.stop(); // interrupted (e.g. IDE stop) — the shutdown hook won't fire, so release the port here
        }
    }
}
