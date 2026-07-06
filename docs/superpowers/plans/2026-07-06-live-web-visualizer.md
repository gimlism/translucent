# Live Web Visualizer (HashMap) — Slice 1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the offline baked-HTML web replay into a live one — a running JVM streams each `TeachingHashMap` mutation to a browser over SSE, rendered live by the existing SVG renderer, and ships a student `main()` sandbox stub.

**Architecture:** A generic, map-agnostic `LiveServer` (JDK `com.sun.net.httpserver.HttpServer` + Server-Sent Events, virtual-thread executor) serves the existing `map-viz.html` in a new live mode and broadcasts one JSON frame per event. `MapLiveVisualizer` (a `MapEventListener`) serializes each event with a new per-event `MapJsonSerializer.toFrame` and pushes it to the server's `broadcast`. The server caches the last frame and replays it on connect, so late joiners are never blank. The offline exporter path is untouched.

**Tech Stack:** Java 21 (compiled `release=21`, built on JDK 26), Maven, JUnit 5 (Jupiter). JDK-only: `com.sun.net.httpserver`, `java.net.http.HttpClient` (tests), vanilla JS + inline SVG. No new dependencies.

## Global Constraints

- **Zero new dependencies.** Server, SSE framing, and JSON are hand-rolled / JDK-only. No Jackson/Gson, no JS toolchain, no WebSocket library.
- **Java version:** source/target `release=21`; runs on the JDK 26 toolchain. `Executors.newVirtualThreadPerTaskExecutor()` is a Java 21 API — fine at `release=21`.
- **`com.sun.net.httpserver`** is an *exported* JDK API (module `jdk.httpserver`); it needs no `--add-exports`/`--add-modules` on a classpath build.
- **Testing framework:** JUnit 5 Jupiter; package-private test classes named `*Test`; assertions via `org.junit.jupiter.api.Assertions.*` static imports. Run with `mvn test`.
- **The dumb-renderer rule:** all layout/classification/highlight logic lives in Java; the front-end JS stays a dumb renderer of pre-computed frames and is **not** unit-tested (browser-verified only). Live-mode JS (EventSource wiring + follow-tail) is the only new untested surface.
- **Settled-state rule:** a frame's `map` must come from the event's own already-committed `after()` snapshot. `toFrame` reads `e.after()` — do not recompute state.
- **`LiveServer` is map-agnostic:** it must never import or reference any `hashmap`/`arraylist`/`trie` type. It is handed page HTML and frame strings only.
- **Git:** work on branch `feat/live-web-visualizer` (already created); commit per task. Merge convention (later): `gh pr merge N --merge`, no `--delete-branch`.

---

### Task 1: Extract `MapJsonSerializer.toFrame` (per-event serialization)

Split the batch serializer so a single event can be serialized to one frame object, which the live path needs. The baked `toJson` output must stay byte-identical.

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/viz/MapJsonSerializer.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/viz/MapJsonSerializerToFrameTest.java` (create)

**Interfaces:**
- Consumes: existing private `writeFrame(JsonWriter, MapEvent)`, `MapEvent.after()`.
- Produces: `public static String toFrame(MapEvent e)` — the JSON for one `{ "event": {...}, "map": {...} }` object (no `frames` wrapper). `public static String toJson(List<MapEvent> events)` — unchanged behavior: `{ "frames": [ <toFrame>, <toFrame>, ... ] }`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/hashmap/viz/MapJsonSerializerToFrameTest.java`:

```java
package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.MapRecordingListener;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import org.junit.jupiter.api.Test;

class MapJsonSerializerToFrameTest {

    @Test
    void toFrameEmitsOneFrameObjectForOneEvent() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.put(1, "a");

        String frame = MapJsonSerializer.toFrame(rec.events().get(0));

        assertTrue(frame.startsWith("{"), "one JSON object");
        assertTrue(frame.contains("\"event\":{"), "carries the event descriptor");
        assertTrue(frame.contains("\"map\":{"), "carries the whole-map snapshot");
        assertTrue(frame.contains("\"type\":\"Put\""), "the event type");
        // NOT wrapped in a frames array
        assertTrue(!frame.contains("\"frames\""), "a single frame is not the batch wrapper");
    }

    @Test
    void toJsonIsTheFramesWrapperOfEachToFrame() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.put(1, "a");
        map.put(9, "b"); // collides in bucket 1 at cap 8: Collision + Put

        var events = rec.events();
        var expected = new StringBuilder("{\"frames\":[");
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) expected.append(',');
            expected.append(MapJsonSerializer.toFrame(events.get(i)));
        }
        expected.append("]}");

        assertEquals(expected.toString(), MapJsonSerializer.toJson(events));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=MapJsonSerializerToFrameTest`
Expected: FAIL — compile error, `toFrame` does not exist.

- [ ] **Step 3: Add `toFrame` and keep `toJson` behavior**

In `MapJsonSerializer.java`, add this method directly below the existing `toJson` (the private `writeFrame` it calls already exists):

```java
    /** One event → the JSON for a single {@code { "event":…, "map":… }} frame (no {@code frames} wrapper). */
    public static String toFrame(MapEvent e) {
        JsonWriter w = new JsonWriter();
        writeFrame(w, e);
        return w.toString();
    }
```

`toJson` is unchanged — it still loops `writeFrame` into the `frames` array, so its output equals the concatenation the test builds from `toFrame`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=MapJsonSerializerToFrameTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Run the full suite (guard the baked path)**

Run: `mvn -q test`
Expected: PASS — in particular `WebVizDemoTest` still passes, proving the baked output is unchanged.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/viz/MapJsonSerializer.java \
        src/test/java/com/gimlism/translucent/hashmap/viz/MapJsonSerializerToFrameTest.java
git commit -m "refactor(web-viz): extract MapJsonSerializer.toFrame for per-event serialization"
```

---

### Task 2: `LiveServer` — the generic SSE transport

The heart of the slice: a map-agnostic server that serves one HTML page and streams frames to every browser over SSE, with a virtual-thread executor, per-connection drop-oldest queues, snapshot-on-connect, and ephemeral-port fallback.

**Files:**
- Create: `src/main/java/com/gimlism/translucent/substrate/viz/LiveServer.java`
- Test: `src/test/java/com/gimlism/translucent/substrate/viz/LiveServerTest.java`

**Interfaces:**
- Consumes: JDK `com.sun.net.httpserver.HttpServer`/`HttpExchange`, `Executors.newVirtualThreadPerTaskExecutor()`.
- Produces:
  - `LiveServer(String pageHtml, String bindAddr, int port)` — `port == 0` means always-ephemeral.
  - `void start() throws IOException` — bind (ephemeral fallback if the requested port is taken), install handlers, start.
  - `int port()` — the actually-bound port.
  - `void broadcast(String frameJson)` — cache as last frame and enqueue to all connections; never blocks.
  - `void stop()` — stop serving, release the port, shut the executor down.
  - `int openConnections()` — count of live SSE connections (test/observability hook).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/substrate/viz/LiveServerTest.java`:

```java
package com.gimlism.translucent.substrate.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LiveServerTest {

    private LiveServer server;
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    private void startWithPage(String page) throws IOException {
        server = new LiveServer(page, "127.0.0.1", 0);
        server.start();
    }

    private String base() {
        return "http://127.0.0.1:" + server.port();
    }

    private HttpResponse<InputStream> openEvents() throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create(base() + "/events")).build(),
                BodyHandlers.ofInputStream());
    }

    /** Read the next SSE {@code data:} payload from an open stream. */
    private static String nextData(BufferedReader r) throws IOException {
        String line;
        while ((line = r.readLine()) != null) {
            if (line.startsWith("data: ")) return line.substring("data: ".length());
        }
        throw new IOException("stream closed before a data line");
    }

    @Test
    void servesThePageAtRoot() throws Exception {
        startWithPage("<html>PAGE-BODY</html>");
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create(base() + "/")).build(), BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("PAGE-BODY"));
    }

    @Test
    void replaysTheLastFrameOnConnect() throws Exception {
        startWithPage("<html></html>");
        server.broadcast("{\"f\":0}");
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            var r = new BufferedReader(new InputStreamReader(openEvents().body(), StandardCharsets.UTF_8));
            assertEquals("{\"f\":0}", nextData(r));
        });
    }

    @Test
    void deliversFramesBroadcastAfterConnect() throws Exception {
        startWithPage("<html></html>");
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            var r = new BufferedReader(new InputStreamReader(openEvents().body(), StandardCharsets.UTF_8));
            // wait until the server has registered this connection, then broadcast
            while (server.openConnections() < 1) Thread.sleep(10);
            server.broadcast("{\"f\":1}");
            assertEquals("{\"f\":1}", nextData(r));
        });
    }

    @Test
    void aSecondRequestIsServedWhileAnSseConnectionStaysOpen() throws Exception {
        startWithPage("<html>PAGE</html>");
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            HttpResponse<InputStream> sse = openEvents();      // held open, never completes
            HttpResponse<String> page = client.send(           // must not block behind the SSE handler
                    HttpRequest.newBuilder(URI.create(base() + "/")).build(), BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            sse.body().close();
        });
    }

    @Test
    void stopReleasesTheServer() throws Exception {
        startWithPage("<html></html>");
        server.stop();
        assertThrows(IOException.class, () -> client.send(
                HttpRequest.newBuilder(URI.create(base() + "/")).build(), BodyHandlers.ofString()));
    }

    @Test
    void broadcastReachesTwoConcurrentConnections() throws Exception {
        startWithPage("<html></html>");
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            var a = new BufferedReader(new InputStreamReader(openEvents().body(), StandardCharsets.UTF_8));
            var b = new BufferedReader(new InputStreamReader(openEvents().body(), StandardCharsets.UTF_8));
            while (server.openConnections() < 2) Thread.sleep(10);
            server.broadcast("{\"f\":7}");
            List<String> got = new ArrayList<>();
            got.add(nextData(a));
            got.add(nextData(b));
            assertEquals(List.of("{\"f\":7}", "{\"f\":7}"), got);
        });
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=LiveServerTest`
Expected: FAIL — compile error, `LiveServer` does not exist.

- [ ] **Step 3: Write `LiveServer`**

Create `src/main/java/com/gimlism/translucent/substrate/viz/LiveServer.java`:

```java
package com.gimlism.translucent.substrate.viz;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
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
    private final String bindAddr;
    private final int requestedPort;

    private final Set<Conn> conns = ConcurrentHashMap.newKeySet();
    private final Object lock = new Object();
    private String lastFrame; // guarded by lock

    private HttpServer server;
    private ExecutorService executor;
    private int port;

    public LiveServer(String pageHtml, String bindAddr, int port) {
        this.pageHtml = pageHtml;
        this.bindAddr = bindAddr;
        this.requestedPort = port;
    }

    /** Bind (ephemeral fallback if the requested port is taken), install handlers, start serving. */
    public void start() throws IOException {
        server = bind();
        port = server.getAddress().getPort();
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", this::handleRoot);
        server.createContext("/events", this::handleEvents);
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
        ex.sendResponseHeaders(200, 0); // 0 => chunked, open-ended
        Conn c = new Conn(ex.getResponseBody());
        synchronized (lock) {
            if (lastFrame != null) c.enqueue(lastFrame); // snapshot-on-connect
            conns.add(c);
        }
        try {
            while (true) {
                c.write(c.queue.take()); // parks until the next frame
            }
        } catch (IOException | InterruptedException gone) {
            // client disconnected, or the server is stopping — fall through to cleanup
        } finally {
            conns.remove(c);
            ex.close();
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=LiveServerTest`
Expected: PASS (6 tests). If `aSecondRequestIsServedWhileAnSseConnectionStaysOpen` times out, the executor is not virtual-thread-per-task — verify `server.setExecutor(...)` runs before `server.start()`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/substrate/viz/LiveServer.java \
        src/test/java/com/gimlism/translucent/substrate/viz/LiveServerTest.java
git commit -m "feat(web-viz): LiveServer — generic SSE transport with snapshot-on-connect"
```

---

### Task 3: `MapLiveVisualizer` — event listener → frame sink

The HashMap-specific glue: a `MapEventListener` that serializes each event to one frame and hands it to a sink (the server's `broadcast`).

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/viz/MapLiveVisualizer.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/viz/MapLiveVisualizerTest.java`

**Interfaces:**
- Consumes: `MapJsonSerializer.toFrame(MapEvent)` (Task 1), `MapEventListener` / `MapEvent`, `java.util.function.Consumer<String>`.
- Produces: `MapLiveVisualizer(Consumer<String> sink)` implementing `MapEventListener`; `onEvent(e)` calls `sink.accept(MapJsonSerializer.toFrame(e))`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/hashmap/viz/MapLiveVisualizerTest.java`:

```java
package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.MapRecordingListener;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class MapLiveVisualizerTest {

    @Test
    void emitsOneFramePerEventEqualToToFrame() {
        var frames = new ArrayList<String>();
        var rec = new MapRecordingListener();
        var map = new TeachingHashMap<Integer, String>();
        map.addListener(new MapLiveVisualizer(frames::add));
        map.addListener(rec);

        map.put(1, "a");
        map.put(9, "b"); // collides in bucket 1 at cap 8: Collision + Put

        assertTrue(frames.size() >= 2, "at least one frame per mutation event");
        assertEquals(rec.events().size(), frames.size(), "exactly one frame per event");
        for (int i = 0; i < frames.size(); i++) {
            assertEquals(MapJsonSerializer.toFrame(rec.events().get(i)), frames.get(i),
                    "frame " + i + " equals the serializer's toFrame of that event");
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=MapLiveVisualizerTest`
Expected: FAIL — compile error, `MapLiveVisualizer` does not exist.

- [ ] **Step 3: Write `MapLiveVisualizer`**

Create `src/main/java/com/gimlism/translucent/hashmap/viz/MapLiveVisualizer.java`:

```java
package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.MapEventListener;
import java.util.function.Consumer;

/**
 * Live twin of {@code MapRecordingListener}: instead of buffering the stream, it serializes each
 * event to one frame ({@link MapJsonSerializer#toFrame}) and hands it to a sink — normally a
 * {@code LiveServer}'s {@code broadcast}. Stateless; the server owns snapshot-on-connect.
 *
 * <p>Per the {@link MapEventListener} contract it neither mutates the map nor throws:
 * serialization is a pure read of the event's settled snapshot.
 */
public final class MapLiveVisualizer implements MapEventListener {

    private final Consumer<String> sink;

    public MapLiveVisualizer(Consumer<String> sink) {
        this.sink = sink;
    }

    @Override
    public void onEvent(MapEvent event) {
        sink.accept(MapJsonSerializer.toFrame(event));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=MapLiveVisualizerTest`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/viz/MapLiveVisualizer.java \
        src/test/java/com/gimlism/translucent/hashmap/viz/MapLiveVisualizerTest.java
git commit -m "feat(web-viz): MapLiveVisualizer — stream one frame per event to a sink"
```

---

### Task 4: Live mode in the template + `MapWebExporter.liveHtml()`

Give `map-viz.html` a live mode behind a second injection token, and add `MapWebExporter.liveHtml()`. The Java side is tested; the JS is browser-verified (dumb-renderer rule).

**Files:**
- Modify: `src/main/resources/web/map-viz.html`
- Modify: `src/main/java/com/gimlism/translucent/hashmap/viz/MapWebExporter.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/viz/MapWebExporterLiveTest.java` (create)

**Interfaces:**
- Consumes: the template resource `/web/map-viz.html` with tokens `/*__FRAMES__*/` and `/*__LIVE__*/`.
- Produces: `MapWebExporter.liveHtml()` → template with `DATA = null` and `LIVE = true`; `MapWebExporter.toHtml(json)` → template with `DATA = json` and `LIVE = false` (existing signature and baked behavior preserved).

- [ ] **Step 1: Edit the template — add the live flag and the live button**

In `src/main/resources/web/map-viz.html`, change the data line (currently `const DATA = /*__FRAMES__*/;`) to add the live flag directly beneath it:

```javascript
const DATA = /*__FRAMES__*/;
const LIVE = /*__LIVE__*/;
```

In the `#bar` div, add a hidden "jump to live" button after the counter span (currently `<span id="counter"></span>`):

```html
    <span id="counter"></span>
    <button id="live" hidden></button>
```

- [ ] **Step 2: Edit the template — follow-tail state, meta refresh, live wiring**

Change the element lookups block to add `liveBtn` (after the `const playBtn = ...` line):

```javascript
const playBtn = document.getElementById("play");
const liveBtn = document.getElementById("live");
```

Change `let idx = 0, timer = null;` to add follow-tail state:

```javascript
let idx = 0, timer = null, followTail = true;
```

Replace the existing `render()` and `go(n)` functions:

```javascript
function render() {
  const f = frames[idx];
  caption.textContent = f ? f.event.label : "(no events to replay)";
  counter.textContent = frames.length ? `frame ${idx + 1} / ${frames.length}` : "";
  prevBtn.disabled = idx <= 0;
  nextBtn.disabled = idx >= frames.length - 1;
  if (f) renderFrame(f); else svg.innerHTML = "";
  // light cross-fade
  stage.style.opacity = "0";
  requestAnimationFrame(() => requestAnimationFrame(() => { stage.style.opacity = "1"; }));
}

function go(n) { idx = Math.max(0, Math.min(frames.length - 1, n)); render(); }
```

with:

```javascript
function refreshMeta() {
  counter.textContent = frames.length ? `frame ${idx + 1} / ${frames.length}` : "";
  prevBtn.disabled = idx <= 0;
  nextBtn.disabled = idx >= frames.length - 1;
  const behind = frames.length - 1 - idx;
  liveBtn.hidden = !(LIVE && behind > 0);
  liveBtn.textContent = `⏭ ${behind} new — jump to live`;
}

function render() {
  const f = frames[idx];
  caption.textContent = f ? f.event.label
      : (LIVE ? "(waiting for live events…)" : "(no events to replay)");
  if (f) renderFrame(f); else svg.innerHTML = "";
  // light cross-fade
  stage.style.opacity = "0";
  requestAnimationFrame(() => requestAnimationFrame(() => { stage.style.opacity = "1"; }));
  refreshMeta();
}

function go(n) {
  idx = Math.max(0, Math.min(frames.length - 1, n));
  followTail = idx >= frames.length - 1;   // reaching the end re-enables auto-advance
  render();
}
```

Finally, replace the last line of the script (currently just `render();`) with the live wiring:

```javascript
liveBtn.onclick = () => { stop(); go(frames.length - 1); };

if (LIVE) {
  const es = new EventSource("/events");
  es.onmessage = ev => {
    let f;
    try { f = JSON.parse(ev.data); } catch (_) { return; }
    frames.push(f);
    if (followTail) go(frames.length - 1);   // full render + cross-fade
    else refreshMeta();                        // studying an older frame: update counter + badge only
  };
}
render();
```

- [ ] **Step 3: Add `liveHtml()` and the two-token injector; write the failing test**

Replace the body of `MapWebExporter.java` between the class open and `readTemplate()` (the `TOKEN` constant, `toHtml`, and `writeHtml`) with a two-token injector:

```java
    private static final String TEMPLATE_RESOURCE = "/web/map-viz.html";
    private static final String FRAMES_TOKEN = "/*__FRAMES__*/";
    private static final String LIVE_TOKEN = "/*__LIVE__*/";

    private MapWebExporter() {}

    /** The self-contained baked HTML with {@code framesJson} injected and live mode OFF. */
    public static String toHtml(String framesJson) {
        return inject(framesJson, "false");
    }

    /** The HTML for live mode: no baked frames ({@code DATA = null}), live mode ON (opens an EventSource). */
    public static String liveHtml() {
        return inject("null", "true");
    }

    /** Write {@link #toHtml(String)} to {@code out} (UTF-8). */
    public static void writeHtml(String framesJson, Path out) throws IOException {
        Files.writeString(out, toHtml(framesJson));
    }

    private static String inject(String framesReplacement, String liveReplacement) {
        String template = readTemplate();
        if (!template.contains(FRAMES_TOKEN)) {
            throw new IllegalStateException("template " + TEMPLATE_RESOURCE + " is missing token " + FRAMES_TOKEN);
        }
        if (!template.contains(LIVE_TOKEN)) {
            throw new IllegalStateException("template " + TEMPLATE_RESOURCE + " is missing token " + LIVE_TOKEN);
        }
        return template.replace(FRAMES_TOKEN, framesReplacement).replace(LIVE_TOKEN, liveReplacement);
    }
```

(Leave `readTemplate()` unchanged below this.)

Create `src/test/java/com/gimlism/translucent/hashmap/viz/MapWebExporterLiveTest.java`:

```java
package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapWebExporterLiveTest {

    @Test
    void liveHtmlHasNoBakedFramesAndOpensAnEventSource() {
        String html = MapWebExporter.liveHtml();
        assertTrue(html.toLowerCase().contains("<!doctype html"), "full document");
        assertFalse(html.contains("/*__FRAMES__*/"), "frames token replaced");
        assertFalse(html.contains("/*__LIVE__*/"), "live token replaced");
        assertTrue(html.contains("const DATA = null;"), "no baked frames in live mode");
        assertTrue(html.contains("const LIVE = true;"), "live mode on");
        assertTrue(html.contains("new EventSource(\"/events\")"), "opens the SSE stream");
    }

    @Test
    void bakedHtmlInjectsFramesAndTurnsLiveOff() {
        String html = MapWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.contains("const DATA = {\"frames\":[]};"), "baked frames injected");
        assertTrue(html.contains("const LIVE = false;"), "live mode off for the baked file");
    }
}
```

- [ ] **Step 4: Run the new test and the existing exporter/demo tests**

Run: `mvn -q test -Dtest=MapWebExporterLiveTest,WebVizDemoTest`
Expected: PASS. `WebVizDemoTest` confirms the baked path still produces a self-contained page with `"frames":[`.

- [ ] **Step 5: Run the full suite**

Run: `mvn -q test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/web/map-viz.html \
        src/main/java/com/gimlism/translucent/hashmap/viz/MapWebExporter.java \
        src/test/java/com/gimlism/translucent/hashmap/viz/MapWebExporterLiveTest.java
git commit -m "feat(web-viz): live mode in the template + MapWebExporter.liveHtml()"
```

---

### Task 5: `LiveWebVizDemo` student stub + `BrowserLauncher` + end-to-end test

Wire the whole stack into a runnable student sandbox, and prove the pipeline end-to-end (map mutation → SSE → browser-visible JSON) with one integration test.

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/demo/BrowserLauncher.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/demo/LiveWebVizDemo.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/viz/LiveVizEndToEndTest.java` (create)

**Interfaces:**
- Consumes: `LiveServer` (Task 2), `MapLiveVisualizer` (Task 3), `MapWebExporter.liveHtml()` (Task 4), `TeachingHashMap`.
- Produces: `LiveWebVizDemo.main(String[])` (runnable stub); `BrowserLauncher.open(String url)` (best-effort, never throws).

- [ ] **Step 1: Write the failing end-to-end test**

Create `src/test/java/com/gimlism/translucent/hashmap/viz/LiveVizEndToEndTest.java`:

```java
package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.substrate.viz.LiveServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Proves the full slice: a TeachingHashMap mutation surfaces as a live SSE frame over HTTP. */
class LiveVizEndToEndTest {

    @Test
    void aMutationSurfacesAsALiveFrameOverHttp() throws IOException {
        LiveServer server = new LiveServer(MapWebExporter.liveHtml(), "127.0.0.1", 0);
        server.start();
        try {
            var map = new TeachingHashMap<Integer, String>();
            map.addListener(new MapLiveVisualizer(server::broadcast));
            map.put(42, "x"); // becomes the cached last frame

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                HttpClient client = HttpClient.newHttpClient();
                InputStream body = client.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/events")).build(),
                        BodyHandlers.ofInputStream()).body();
                var r = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String frame = line.substring("data: ".length());
                        assertTrue(frame.contains("\"type\":\"Put\""), "frame carries the Put event");
                        assertTrue(frame.contains("\"highlightKey\":\"42\""), "frame highlights the put key");
                        assertTrue(frame.contains("\"map\":{"), "frame carries the whole-map snapshot");
                        return;
                    }
                }
                throw new IOException("no data line received");
            });
        } finally {
            server.stop();
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=LiveVizEndToEndTest`
Expected: PASS actually — this test uses only Tasks 2–4 classes, which already exist. Run it to confirm the integration holds before adding the demo. If it FAILS, fix the integration before proceeding.

- [ ] **Step 3: Write `BrowserLauncher`**

Create `src/main/java/com/gimlism/translucent/hashmap/demo/BrowserLauncher.java`:

```java
package com.gimlism.translucent.hashmap.demo;

import java.awt.Desktop;
import java.net.URI;

/** Best-effort "open this URL in the default browser"; a silent no-op (prints instead) when unavailable. */
final class BrowserLauncher {

    private BrowserLauncher() {}

    static void open(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
        } catch (Exception headlessOrUnsupported) {
            // fall through to the printed hint
        }
        System.out.println("Open " + url + " in your browser.");
    }
}
```

- [ ] **Step 4: Write `LiveWebVizDemo`**

Create `src/main/java/com/gimlism/translucent/hashmap/demo/LiveWebVizDemo.java`:

```java
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
```

- [ ] **Step 5: Compile and run the full suite**

Run: `mvn -q test`
Expected: PASS (all tests, including `LiveVizEndToEndTest`).

- [ ] **Step 6: Manually verify the live demo in a browser (dumb-renderer verification)**

Run: `mvn -q exec:java -Dexec.mainClass=com.gimlism.translucent.hashmap.demo.LiveWebVizDemo`
Expected: a browser opens to `http://localhost:7070`; the buckets fill in as the scripted `put`s run, one bucket collides and treeifies, then `remove(16)` updates it. Scrub back with ◀ prev — new frames stop auto-advancing and the `jump to live` button appears; click it to resume follow-tail. Stop with Ctrl-C; the console shows a clean exit.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/demo/BrowserLauncher.java \
        src/main/java/com/gimlism/translucent/hashmap/demo/LiveWebVizDemo.java \
        src/test/java/com/gimlism/translucent/hashmap/viz/LiveVizEndToEndTest.java
git commit -m "feat(web-viz): LiveWebVizDemo student sandbox + end-to-end live-frame test"
```

---

## Notes for the implementer

- **`com.sun.net.httpserver` is fine.** It is an exported, supported JDK API. If your IDE flags it as "internal API", that warning is wrong for this package — the build will compile without any `--add-exports`.
- **SSE payloads must be single-line.** `JsonWriter` already escapes `\n`/`\r` (and control chars) to `\uXXXX`, so a frame never contains a raw newline and each `data:` line is well-formed. Do not add pretty-printing.
- **Reconnect duplicates a frame.** `EventSource` auto-reconnects on drop; on reconnect the server replays the cached last frame, so the browser appends a duplicate of the latest frame. Acceptable for Slice 1 (localhost, rare); noted for Slice 2.
- **Do not test the front-end JS.** Per the dumb-renderer rule, the live-mode JS is verified in the browser (Task 5, Step 6), not with a unit test.
