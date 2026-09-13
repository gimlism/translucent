package com.gimlism.translucent.substrate.viz;

import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;

/** Shared demo lifecycle: keep the JVM alive so a live {@link LiveServer} stays serving until Ctrl-C. */
public final class DemoLifecycle {

    private DemoLifecycle() {}

    /** {@link #announcePortFallback(LiveServer, PrintStream)} on {@code System.out}. */
    public static void announcePortFallback(LiveServer server) {
        announcePortFallback(server, System.out);
    }

    /**
     * Explain a moved port, and only a moved port. {@link LiveServer} falls back to an ephemeral
     * port when the one it wanted is taken — which is what lets two live demos run side by side —
     * but it does so silently, leaving a student with an unexplained port number.
     *
     * <p>Prints nothing when the requested port was granted, and nothing when the caller requested
     * port {@code 0}: that asks for any free port, so a differing {@link LiveServer#port()} is the
     * request being honoured rather than denied.
     */
    public static void announcePortFallback(LiveServer server, PrintStream out) {
        int wanted = server.requestedPort();
        if (wanted == 0 || wanted == server.port()) return;
        out.println("Port " + wanted + " was busy — serving on " + server.port() + " instead.");
    }

    /** Park the calling thread until Ctrl-C (or interrupt); stop the server cleanly on the way out. */
    public static void awaitShutdown(LiveServer server) {
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        try {
            new CountDownLatch(1).await(); // never counted down — Ctrl-C runs the shutdown hook and exits
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.stop(); // interrupted (e.g. IDE stop) — the shutdown hook won't fire, so release the port here
        }
    }
}
