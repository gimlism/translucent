# Live Browser Controls (HashMap) — Slice 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a browser command input that POSTs a raw command line to the live server, which runs it through the same `MapCommandInterpreter` the terminal REPL uses, so a student mutates the HashMap from the page and watches it redraw live.

**Architecture:** One new upstream path — `LiveServer` gains a nullable command-handler `Function<String,String>` and a `POST /command` context; a new `LiveControlsDemo` supplies the map-specific closure (`line → interpreter.execute(line, map).message()`, serialized behind a lock); the `map-viz.html` template gains a command input gated on a new `CONTROLS` token. The mutation → event → broadcast → SSE → SVG-redraw loop from Slices 1–2 is reused verbatim; the POST response carries only text (feedback for read-only commands that fire no frame).

**Tech Stack:** Java 21 (build on JDK 26, `maven.compiler.release=21`), Maven, JDK built-in `com.sun.net.httpserver`, JUnit 5, `java.net.http.HttpClient` for tests. No new dependencies.

## Global Constraints

- Target **Java 21** (`maven.compiler.release=21`); build/test on JDK 26. Run tests with `mvn test`.
- **No new dependencies** — JDK built-ins only.
- `LiveServer` stays **structure-agnostic**: it must not import or reference any map/list/trie type. It holds only an opaque `Function<String,String>` command handler.
- `MapCommandInterpreter.execute` **never throws** (Slice 2 guarantee) — rely on it; do not add try/catch around it in the handler.
- Snapshot-safety rule (recurring project bug pattern): any event emitted mid-operation must see fully-committed state before `snapshot()` runs. Not expected to bite this slice (no core changes), but keep in mind.
- The command-input JS in `map-viz.html` is **untested-by-design** — verified only via the live-viz browser recipe, not JUnit. All Java is TDD.
- Commit after each task with a `feat:` / `test:` message. Do **not** merge or open a PR as part of this plan.

---

## File Structure

- `src/main/java/com/gimlism/translucent/substrate/viz/LiveServer.java` — **modify**: add nullable `commandHandler` field + 4th ctor param (keep the 3-arg ctor delegating with `null`); register `POST /command`; add `handleCommand`.
- `src/test/java/com/gimlism/translucent/substrate/viz/LiveServerTest.java` — **modify**: add POST-handler, null-handler-405, and non-POST-405 tests.
- `src/main/java/com/gimlism/translucent/hashmap/viz/MapWebExporter.java` — **modify**: add `/*__CONTROLS__*/` token + `controlsHtml()`; thread a third replacement through `inject`.
- `src/test/java/com/gimlism/translucent/hashmap/viz/MapWebExporterControlsTest.java` — **create**: assert `controlsHtml()` sets `CONTROLS=true`, `liveHtml()` keeps it `false`.
- `src/main/java/com/gimlism/translucent/hashmap/demo/DemoLifecycle.java` — **create**: shared `awaitShutdown(LiveServer)` (park-until-Ctrl-C + clean stop), extracted from `LiveWebVizDemo` so both live-server demos share it (no verbatim duplication).
- `src/main/java/com/gimlism/translucent/hashmap/demo/LiveWebVizDemo.java` — **modify**: delete its private `awaitShutdown`, call `DemoLifecycle.awaitShutdown(server)`.
- `src/main/java/com/gimlism/translucent/hashmap/demo/LiveControlsDemo.java` — **create**: the browser-driven demo + a static `commandHandler(...)` test seam; parks via `DemoLifecycle.awaitShutdown`.
- `src/test/java/com/gimlism/translucent/hashmap/demo/LiveControlsEndToEndTest.java` — **create**: POST a command over HTTP through the real handler wiring, assert a frame arrives on `/events`.
- `src/main/resources/web/map-viz.html` — **modify**: read `const CONTROLS = /*__CONTROLS__*/;`, render the command input + `fetch` POST when `CONTROLS` is true.

---

## Task 1: `LiveServer` command handler + `POST /command`

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/substrate/viz/LiveServer.java`
- Test: `src/test/java/com/gimlism/translucent/substrate/viz/LiveServerTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - New ctor `public LiveServer(String pageHtml, String bindAddr, int port, java.util.function.Function<String,String> commandHandler)`.
  - Retained ctor `public LiveServer(String pageHtml, String bindAddr, int port)` delegating with `commandHandler = null`.
  - Behavior: `POST /command` reads the UTF-8 request body, calls `commandHandler.apply(body)`, returns the result as `200 text/plain; charset=utf-8`. If `commandHandler == null` **or** the method is not `POST`, returns `405` with no body.

- [ ] **Step 1: Write the failing tests**

Add these imports to `LiveServerTest.java` if not already present: `java.net.http.HttpRequest.BodyPublishers` and (already imported) `HttpResponse.BodyHandlers`. Add a helper and three tests:

```java
    private void startWithHandler(java.util.function.Function<String, String> handler) throws IOException {
        server = new LiveServer("<html></html>", "127.0.0.1", 0, handler);
        server.start();
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(URI.create(base() + path))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(), BodyHandlers.ofString());
    }

    @Test
    void postCommandInvokesTheHandlerAndReturnsItsText() throws Exception {
        startWithHandler(line -> "echo:" + line);
        HttpResponse<String> resp = post("/command", "put 8 v8");
        assertEquals(200, resp.statusCode());
        assertEquals("echo:put 8 v8", resp.body());
    }

    @Test
    void postCommandWithNoHandlerIs405() throws Exception {
        startWithPage("<html></html>"); // 3-arg ctor => null handler
        HttpResponse<String> resp = post("/command", "put 8 v8");
        assertEquals(405, resp.statusCode());
    }

    @Test
    void getOnCommandIs405() throws Exception {
        startWithHandler(line -> "unused");
        HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create(base() + "/command")).GET().build(),
                BodyHandlers.ofString());
        assertEquals(405, resp.statusCode());
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q test -Dtest=LiveServerTest`
Expected: FAIL — the 4-arg constructor does not exist yet (compile error), and `/command` is unmapped.

- [ ] **Step 3: Add the command handler field, ctor, context, and handler**

In `LiveServer.java`:

Add the import near the other `java.util` imports:

```java
import java.util.function.Function;
```

Add the field next to `pageHtml`:

```java
    private final String pageHtml;
    private final Function<String, String> commandHandler; // nullable — null => POST /command 405s
    private final String bindAddr;
    private final int requestedPort;
```

Replace the existing constructor with two:

```java
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
```

Register the context in `start()`, right after the `/events` line:

```java
        server.createContext("/", this::handleRoot);
        server.createContext("/events", this::handleEvents);
        server.createContext("/command", this::handleCommand);
```

Add the handler (place it after `handleEvents`, before the `Conn` class). Note the imports `java.io.InputStream` and `java.io.OutputStream` — `OutputStream` is already imported; add `InputStream`:

```java
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
        byte[] body = commandHandler.apply(line).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
```

Add the missing import with the other `java.io` imports:

```java
import java.io.InputStream;
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q test -Dtest=LiveServerTest`
Expected: PASS — all prior `LiveServerTest` cases plus the three new ones.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/substrate/viz/LiveServer.java \
        src/test/java/com/gimlism/translucent/substrate/viz/LiveServerTest.java
git commit -m "feat(viz): LiveServer POST /command handler (structure-agnostic)"
```

---

## Task 2: `MapWebExporter.controlsHtml()` + `CONTROLS` token

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/viz/MapWebExporter.java`
- Create: `src/test/java/com/gimlism/translucent/hashmap/viz/MapWebExporterControlsTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `public static String controlsHtml()` — HTML with frames `null`, live `true`, controls `true`. Existing `liveHtml()` now yields controls `false`; `toHtml(...)` yields live `false`, controls `false`.

Note: this task adds the `/*__CONTROLS__*/` token to `map-viz.html` **only as a placeholder line** so `inject` can find it; the command-input JS that reads it lands in Task 4. Add this single line inside the existing `<script>` block, right after the `LIVE` line (near line 55 of `map-viz.html`):

```javascript
const LIVE = /*__LIVE__*/;
const CONTROLS = /*__CONTROLS__*/;
```

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/hashmap/viz/MapWebExporterControlsTest.java`:

```java
package com.gimlism.translucent.hashmap.viz;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapWebExporterControlsTest {

    @Test
    void controlsHtmlEnablesControlsAndLive() {
        String html = MapWebExporter.controlsHtml();
        assertTrue(html.contains("const CONTROLS = true;"), "controls flag on");
        assertTrue(html.contains("const LIVE = true;"), "live flag on");
        assertTrue(html.contains("const DATA = null;"), "no baked frames in live mode");
    }

    @Test
    void liveHtmlLeavesControlsOff() {
        String html = MapWebExporter.liveHtml();
        assertTrue(html.contains("const CONTROLS = false;"), "controls flag off in plain live mode");
        assertTrue(html.contains("const LIVE = true;"), "live flag still on");
    }

    @Test
    void bakedHtmlLeavesLiveAndControlsOff() {
        String html = MapWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.contains("const LIVE = false;"), "replay mode is not live");
        assertTrue(html.contains("const CONTROLS = false;"), "replay mode has no controls");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test -Dtest=MapWebExporterControlsTest`
Expected: FAIL — `controlsHtml` does not exist (compile error) and the `CONTROLS` token is not yet substituted.

- [ ] **Step 3: Add the token line to the template**

In `src/main/resources/web/map-viz.html`, change the two-line block:

```javascript
const DATA = /*__FRAMES__*/;
const LIVE = /*__LIVE__*/;
```

to:

```javascript
const DATA = /*__FRAMES__*/;
const LIVE = /*__LIVE__*/;
const CONTROLS = /*__CONTROLS__*/;
```

- [ ] **Step 4: Thread the controls flag through `MapWebExporter`**

In `MapWebExporter.java`:

Add the token constant next to the others:

```java
    private static final String FRAMES_TOKEN = "/*__FRAMES__*/";
    private static final String LIVE_TOKEN = "/*__LIVE__*/";
    private static final String CONTROLS_TOKEN = "/*__CONTROLS__*/";
```

Update the three public methods and `inject` to carry a third replacement:

```java
    /** The self-contained baked HTML with {@code framesJson} injected; live + controls OFF. */
    public static String toHtml(String framesJson) {
        return inject(framesJson, "false", "false");
    }

    /** Live mode (SSE), no browser controls: {@code DATA = null}, live ON, controls OFF. */
    public static String liveHtml() {
        return inject("null", "true", "false");
    }

    /** Live mode with browser command controls: {@code DATA = null}, live ON, controls ON. */
    public static String controlsHtml() {
        return inject("null", "true", "true");
    }
```

Replace `inject` (keep the `writeHtml` method unchanged):

```java
    private static String inject(String framesReplacement, String liveReplacement, String controlsReplacement) {
        String template = readTemplate();
        requireToken(template, FRAMES_TOKEN);
        requireToken(template, LIVE_TOKEN);
        requireToken(template, CONTROLS_TOKEN);
        return template
                .replace(FRAMES_TOKEN, framesReplacement)
                .replace(LIVE_TOKEN, liveReplacement)
                .replace(CONTROLS_TOKEN, controlsReplacement);
    }

    private static void requireToken(String template, String token) {
        if (!template.contains(token)) {
            throw new IllegalStateException("template " + TEMPLATE_RESOURCE + " is missing token " + token);
        }
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q test -Dtest=MapWebExporterControlsTest,MapWebExporterLiveTest`
Expected: PASS — new controls test plus the existing live-exporter test.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/viz/MapWebExporter.java \
        src/main/resources/web/map-viz.html \
        src/test/java/com/gimlism/translucent/hashmap/viz/MapWebExporterControlsTest.java
git commit -m "feat(viz): CONTROLS token + MapWebExporter.controlsHtml()"
```

---

## Task 3: shared `DemoLifecycle` + `LiveControlsDemo` + headless end-to-end test

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/demo/DemoLifecycle.java`
- Modify: `src/main/java/com/gimlism/translucent/hashmap/demo/LiveWebVizDemo.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/demo/LiveControlsDemo.java`
- Create: `src/test/java/com/gimlism/translucent/hashmap/demo/LiveControlsEndToEndTest.java`

**Interfaces:**
- Consumes: `LiveServer(String, String, int, Function<String,String>)` (Task 1), `MapWebExporter.controlsHtml()` (Task 2), existing `TeachingHashMap`, `MapCommandInterpreter`, `MapLiveVisualizer`, `BrowserLauncher`.
- Produces:
  - `static void DemoLifecycle.awaitShutdown(LiveServer server)` — park the calling thread until Ctrl-C, stopping the server cleanly on the way out. Package-private, shared by `LiveWebVizDemo` and `LiveControlsDemo`.
  - `static java.util.function.Function<String,String> LiveControlsDemo.commandHandler(TeachingHashMap<Integer,String> map, MapCommandInterpreter interpreter)` — the serialized `line → interpreter.execute(line, map).message()` closure. This is the demo's tested seam (like `LiveReplDemo.runRepl`); `main` and the end-to-end test both use it.

- [ ] **Step 1: Extract `DemoLifecycle.awaitShutdown` (pure move, no behavior change)**

Create `src/main/java/com/gimlism/translucent/hashmap/demo/DemoLifecycle.java` with the lifecycle logic lifted verbatim from `LiveWebVizDemo`:

```java
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
```

Then edit `LiveWebVizDemo.java`: delete its private `awaitShutdown` method and the now-unused `import java.util.concurrent.CountDownLatch;`, and change the call site from `awaitShutdown(server);` to:

```java
        DemoLifecycle.awaitShutdown(server);
```

- [ ] **Step 2: Verify the extraction compiles and the suite is still green**

Run: `mvn -q test`
Expected: PASS — `awaitShutdown` is untested (it parks forever), so this is a behavior-preserving move; the whole suite stays green. If compilation fails, check the `CountDownLatch` import was removed from `LiveWebVizDemo` and added to `DemoLifecycle`.

- [ ] **Step 3: Commit the extraction**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/demo/DemoLifecycle.java \
        src/main/java/com/gimlism/translucent/hashmap/demo/LiveWebVizDemo.java
git commit -m "refactor(demo): extract shared DemoLifecycle.awaitShutdown"
```

- [ ] **Step 4: Write the failing end-to-end test**

Create `src/test/java/com/gimlism/translucent/hashmap/demo/LiveControlsEndToEndTest.java`:

```java
package com.gimlism.translucent.hashmap.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.repl.MapCommandInterpreter;
import com.gimlism.translucent.hashmap.viz.MapLiveVisualizer;
import com.gimlism.translucent.hashmap.viz.MapWebExporter;
import com.gimlism.translucent.substrate.viz.LiveServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Proves Slice 3: a browser POST /command mutates the map and surfaces as a live SSE frame. */
class LiveControlsEndToEndTest {

    @Test
    void aPostedCommandMutatesTheMapAndSurfacesAsALiveFrame() throws IOException {
        var map = new TeachingHashMap<Integer, String>();
        Function<String, String> handler = LiveControlsDemo.commandHandler(map, new MapCommandInterpreter());
        LiveServer server = new LiveServer(MapWebExporter.controlsHtml(), "127.0.0.1", 0, handler);
        server.start();
        try {
            map.addListener(new MapLiveVisualizer(server::broadcast));

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                HttpClient client = HttpClient.newHttpClient();
                String base = "http://127.0.0.1:" + server.port();

                // open the SSE stream first, then wait until the server has registered it
                InputStream body = client.send(
                        HttpRequest.newBuilder(URI.create(base + "/events")).build(),
                        BodyHandlers.ofInputStream()).body();
                var r = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
                while (server.openConnections() < 1) Thread.sleep(10);

                // POST a command; the response is the interpreter's text
                var resp = client.send(HttpRequest.newBuilder(URI.create(base + "/command"))
                                .POST(BodyPublishers.ofString("put 42 x", StandardCharsets.UTF_8)).build(),
                        BodyHandlers.ofString());
                assertEquals("put 42 = x", resp.body());

                // the mutation's frame arrives on the SSE stream
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String frame = line.substring("data: ".length());
                        assertTrue(frame.contains("\"type\":\"Put\""), "frame carries the Put event");
                        assertTrue(frame.contains("\"highlightKey\":\"42\""), "frame highlights the put key");
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

- [ ] **Step 5: Run the test to verify it fails**

Run: `mvn -q test -Dtest=LiveControlsEndToEndTest`
Expected: FAIL — `LiveControlsDemo` does not exist yet (compile error).

- [ ] **Step 6: Create `LiveControlsDemo`**

Create `src/main/java/com/gimlism/translucent/hashmap/demo/LiveControlsDemo.java`:

```java
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
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `mvn -q test -Dtest=LiveControlsEndToEndTest`
Expected: PASS — the POST returns `put 42 = x` and a `Put` frame arrives on `/events`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/demo/LiveControlsDemo.java \
        src/test/java/com/gimlism/translucent/hashmap/demo/LiveControlsEndToEndTest.java
git commit -m "feat(demo): LiveControlsDemo — browser-driven live HashMap over POST /command"
```

---

## Task 4: Command input in the template (untested-by-design, browser-verified)

**Files:**
- Modify: `src/main/resources/web/map-viz.html`

**Interfaces:**
- Consumes: `const CONTROLS` (Task 2), `POST /command` (Task 1), the existing `#bar`, `caption`, and `if (LIVE) { EventSource(...) }` block.
- Produces: no Java surface — DOM + `fetch`. Verified by the browser recipe, not JUnit.

This task has **no JUnit step** — the renderer JS is untested-by-design. Verification is Step 3 (browser recipe). Keep the edit minimal and inside the existing structure.

- [ ] **Step 1: Add the command input markup**

In `map-viz.html`, inside `<div id="bar"> ... </div>`, after the `#counter` span and before the `#live` button (around line 47–48), add a hidden-by-default command group:

```html
    <span id="counter"></span>
    <input id="cmd" type="text" placeholder="put 8 v8 · remove 8 · get 8 · help" autocomplete="off" hidden>
    <button id="run" hidden>Run</button>
    <span id="cmdout"></span>
    <button id="live" hidden></button>
```

Add matching CSS in the `<style>` block (after the `button` rules, ~line 21):

```css
  #cmd { font:inherit; padding:6px 10px; border:1px solid var(--cellb); border-radius:8px;
         background:var(--card); color:var(--fg); min-width:220px; }
  #cmdout { color:var(--muted); font-variant-numeric: tabular-nums; }
```

- [ ] **Step 2: Wire the input to `POST /command`**

In the `<script>`, add element handles next to the others (after `liveBtn`, ~line 64):

```javascript
const cmdInput = document.getElementById("cmd");
const runBtn = document.getElementById("run");
const cmdOut = document.getElementById("cmdout");
```

Then, inside the existing `if (LIVE) { ... }` block (after the `EventSource` wiring, before the closing brace ~line 199), add the controls, gated on `CONTROLS`:

```javascript
  if (CONTROLS) {
    cmdInput.hidden = false;
    runBtn.hidden = false;
    const submit = () => {
      const line = cmdInput.value;
      if (!line.trim()) return;
      cmdInput.value = "";
      fetch("/command", { method: "POST", body: line })
        .then(r => r.text())
        .then(msg => { cmdOut.textContent = msg; })
        .catch(() => { cmdOut.textContent = "(command failed to reach the server)"; });
    };
    runBtn.onclick = submit;
    cmdInput.addEventListener("keydown", e => { if (e.key === "Enter") submit(); });
  }
```

Note: the viz redraw is NOT driven here — it arrives via the existing `es.onmessage` SSE path. This block only sends the line and shows the text reply.

- [ ] **Step 3: Browser-verify (the only verification for this task)**

Follow the project's live-viz browser recipe:

```bash
# ensure the template is copied onto the classpath, then run the demo headless so BrowserLauncher no-ops
mvn -q process-classes
mvn -q exec:java -Dexec.mainClass=com.gimlism.translucent.hashmap.demo.LiveControlsDemo \
    -Dexec.args="" -Djava.awt.headless=true &
# (controller) connect the claude-in-chrome MCP to http://localhost:7070
```

Then, via the Chrome MCP:
1. Confirm the command input + Run button render (they must be visible — `CONTROLS` is true).
2. Type `put 0 a`, press Enter → `#cmdout` shows `put 0 = a`; a bucket-0 cell appears in the SVG; the frame counter climbs.
3. Type `put 8 b`, `put 16 c`, `put 24 d` → chain then RB-tree renders in bucket 0; each `#cmdout` line updates.
4. Type `get 8` → `#cmdout` shows `get 8 → b`; **no new frame** (read-only — proves the text channel carries the only feedback for reads).
5. Type `remove 8` → `#cmdout` shows `removed 8`; the SVG redraws.
6. Type `help` → `#cmdout` shows the first line of help (single-line span; acceptable).
7. Check the browser console shows **zero errors**.
8. Screenshot a couple of frames as evidence.

Then stop the demo and clean up:

```bash
pkill -f LiveControlsDemo
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/web/map-viz.html
git commit -m "feat(viz): browser command input POSTing to /command (gated on CONTROLS)"
```

---

## Task 5: Full-suite green + whole-branch review prep

**Files:** none (verification only).

- [ ] **Step 1: Run the full suite**

Run: `mvn -q test`
Expected: PASS — all prior tests plus the ~5 new Java tests from Tasks 1–3 (suite count climbs from 265). If the count or any assertion is off, fix before proceeding.

- [ ] **Step 2: Confirm `LiveServer` is still structure-agnostic**

Run: `grep -nE "import com.gimlism.translucent.(hashmap|arraylist|trie)" src/main/java/com/gimlism/translucent/substrate/viz/LiveServer.java`
Expected: **no output** — the substrate must not import any concrete structure. (An empty result confirms agnosticism held.)

- [ ] **Step 3: Confirm the older demos changed only as intended**

Run: `git diff --stat main -- src/main/java/com/gimlism/translucent/hashmap/demo/LiveReplDemo.java src/main/java/com/gimlism/translucent/hashmap/demo/LiveWebVizDemo.java`
Expected: `LiveReplDemo.java` — **not listed** (untouched). `LiveWebVizDemo.java` — listed with a small delta (only the `awaitShutdown` extraction: deleted method + import, one changed call site). Both still serve `liveHtml()` → `CONTROLS=false` → no command box. If `LiveReplDemo.java` appears, or `LiveWebVizDemo`'s delta is more than the extraction, investigate.

- [ ] **Step 4: Commit any stray fixes, then hand off to the whole-branch review**

If Steps 1–3 surfaced fixes, commit them with a clear message. Otherwise nothing to commit. The branch is ready for the whole-branch (opus) review and PR per the project's merge convention (`gh pr merge N --merge`, no `--delete-branch`).

---

## Self-Review (completed against the spec)

- **Spec coverage:** §A LiveServer handler → Task 1. §B LiveControlsDemo + lock → Task 3. §C CONTROLS token + `controlsHtml()` + template input → Tasks 2 & 4. § "Why text response exists" → asserted in Task 3 (POST returns `put 42 = x`) and browser step 4 (`get` shows text, no frame). § Concurrency lock → Task 3 `commandHandler` seam. § Error handling: null-handler 405 + non-POST 405 → Task 1; `quit` doesn't stop server → documented in `commandHandler` Javadoc (interpreter's `quit()` flag is simply not read on this path). § Testing: LiveServer unit + MapWebExporter unit + headless end-to-end → Tasks 1–3; untested-by-design JS → Task 4. § Files touched → all mapped in File Structure.
- **Placeholder scan:** every code step shows complete code; no TBD/TODO; browser-recipe step lists concrete commands + concrete checks.
- **Type consistency:** `commandHandler(TeachingHashMap<Integer,String>, MapCommandInterpreter) → Function<String,String>` used identically in Task 3's test and demo; `controlsHtml()`, `LIVE`/`CONTROLS`/`DATA` flag strings consistent across Tasks 2 and 4; the 4-arg `LiveServer` ctor signature identical in Tasks 1 and 3.
