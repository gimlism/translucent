package com.gimlism.translucent.substrate.viz;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Generic live-viz transport: serves one HTML page and streams JSON frames to every connected
 * browser over Server-Sent Events. Structure-agnostic — handed the page HTML and a stream of
 * pre-serialized frame strings, it knows nothing about maps, lists, or tries.
 *
 * <p>A virtual-thread-per-task executor makes each held-open SSE connection a cheap parked
 * thread, so there is no pool cap to size. {@link #broadcast} runs on the caller's (mutating)
 * thread and only enqueues, so a slow browser never blocks it; each connection's own thread
 * drains its bounded queue and writes. On connect, the last broadcast frame is replayed, so a
 * late joiner is never blank. A short lock makes "set last frame + enqueue to all" and "seed
 * last frame + register" atomic — no I/O runs inside it.
 */
public final class LiveServer {

    private static final int QUEUE_CAPACITY = 256;

    private final String pageHtml;
    private final Function<String, String> commandHandler; // nullable — null => POST /command 405s
    private final String bindAddr;
    private final int requestedPort;

    private final Set<Conn> conns = ConcurrentHashMap.newKeySet();
    private final Object lock = new Object();
    private String lastFrame; // guarded by lock

    private HttpServer server;
    private ExecutorService executor;
    private int port;

    /** No-controls server (SSE mirror only): a {@code POST /command} returns 405. */
    public LiveServer(String pageHtml, String bindAddr, int port) {
        this(pageHtml, bindAddr, port, null);
    }

    /**
     * @param commandHandler applied to each {@code POST /command} body, its return value sent back
     *     as {@code text/plain}; {@code null} disables the endpoint (405). Kept as an opaque
     *     {@code String -> String} so this server stays structure-agnostic.
     */
    public LiveServer(String pageHtml, String bindAddr, int port, Function<String, String> commandHandler) {
        this.pageHtml = pageHtml;
        this.bindAddr = bindAddr;
        this.requestedPort = port;
        this.commandHandler = commandHandler;
    }

    /** Bind (ephemeral fallback if the requested port is taken), install handlers, start serving. */
    public void start() throws IOException {
        server = bind();
        port = server.getAddress().getPort();
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", this::handleRoot);
        server.createContext("/events", this::handleEvents);
        server.createContext("/command", this::handleCommand);
        server.start();
    }

    private HttpServer bind() throws IOException {
        try {
            return HttpServer.create(new InetSocketAddress(bindAddr, requestedPort), 0);
        } catch (BindException taken) {
            if (requestedPort == 0) throw taken; // already ephemeral — nothing to fall back to
            return HttpServer.create(new InetSocketAddress(bindAddr, 0), 0);
        }
    }

    /** The actually-bound port (differs from the requested one after an ephemeral fallback). */
    public int port() {
        return port;
    }

    /** Live SSE connection count — a test/observability hook. */
    public int openConnections() {
        return conns.size();
    }

    /** Cache this frame (for snapshot-on-connect) and enqueue it to every open connection. */
    public void broadcast(String frameJson) {
        synchronized (lock) {
            lastFrame = frameJson;
            for (Conn c : conns) c.enqueue(frameJson);
        }
    }

    /** Stop serving, release the port, and shut the executor down. */
    public void stop() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
        conns.clear();
    }

    private void handleRoot(HttpExchange ex) throws IOException {
        if (!"/".equals(ex.getRequestURI().getPath())) {
            ex.sendResponseHeaders(404, -1);
            ex.close();
            return;
        }
        byte[] body = pageHtml.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private void handleEvents(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        Conn c = null;
        try {
            ex.sendResponseHeaders(200, 0); // 0 => chunked, open-ended — headers first, per the HttpExchange contract
            OutputStream os = ex.getResponseBody(); // only obtained after the headers are sent
            os.flush(); // push headers to the client now — don't wait for the first frame
            c = new Conn(os);
            synchronized (lock) {
                if (lastFrame != null) c.enqueue(lastFrame); // snapshot-on-connect
                conns.add(c);
            }
            while (true) {
                c.write(c.queue.take()); // parks until the next frame
            }
        } catch (IOException | InterruptedException gone) {
            // client disconnected, or the server is stopping — fall through to cleanup
        } finally {
            if (c != null) conns.remove(c); // c is null if we failed before registering
            ex.close();
        }
    }

    private void handleCommand(HttpExchange ex) throws IOException {
        if (commandHandler == null || !"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            ex.close();
            return;
        }
        String line;
        try (InputStream is = ex.getRequestBody()) {
            line = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        String result = commandHandler.apply(line);
        byte[] body = (result == null ? "" : result).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    /** One SSE connection: its socket out-stream plus a bounded, drop-oldest outbound queue. */
    private static final class Conn {
        final OutputStream os;
        final BlockingQueue<String> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

        Conn(OutputStream os) {
            this.os = os;
        }

        /** Enqueue newest; if full, drop the oldest so a slow viewer converges to the latest state. */
        void enqueue(String frame) {
            if (!queue.offer(frame)) {
                queue.poll();
                queue.offer(frame);
            }
        }

        void write(String frame) throws IOException {
            os.write(("data: " + frame + "\n\n").getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
    }
}
