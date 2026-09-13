package com.gimlism.translucent.substrate.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        if (server != null) server.stop(); // closes any still-open SSE streams first...
        client.close();                    // ...so this graceful close has no in-flight request to await
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

    @Test
    void postCommandWithANullHandlerResultReturnsEmpty200() throws Exception {
        startWithHandler(line -> null);
        HttpResponse<String> resp = post("/command", "anything");
        assertEquals(200, resp.statusCode());
        assertEquals("", resp.body());
    }

    // ---------------------------------------------------------------------
    // Port binding. Every other test here requests port 0 (ephemeral), so the
    // "requested port is taken" branch of bind() went uncovered for two months
    // — long enough for two demo Javadocs to claim, falsely, that a second live
    // demo "fails to bind". These pin the behaviour those docs got wrong.
    // ---------------------------------------------------------------------

    @Test
    void aSecondServerRequestingATakenPortFallsBackToAnEphemeralOneAndBothServe() throws Exception {
        startWithPage("<html>FIRST</html>");
        int taken = server.port(); // a port genuinely in use right now — no assumption about 7070

        LiveServer second = new LiveServer("<html>SECOND</html>", "127.0.0.1", taken);
        second.start();
        try {
            assertNotEquals(taken, second.port(), "second server should have fallen back off the taken port");

            // Both answer HTTP concurrently — the claim "cannot run at the same time" is false.
            assertTrue(get("http://127.0.0.1:" + taken + "/").contains("FIRST"));
            assertTrue(get("http://127.0.0.1:" + second.port() + "/").contains("SECOND"));
        } finally {
            second.stop();
        }
    }

    @Test
    void aServerReportsThePortItWasAskedForEvenAfterFallingBack() throws Exception {
        startWithPage("<html>FIRST</html>");
        int taken = server.port();

        LiveServer second = new LiveServer("<html>SECOND</html>", "127.0.0.1", taken);
        second.start();
        try {
            assertEquals(taken, second.requestedPort(), "requestedPort() should report what was asked for");
            assertNotEquals(second.requestedPort(), second.port(), "and port() what was actually bound");
        } finally {
            second.stop();
        }
    }

    private String get(String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).build(), BodyHandlers.ofString()).body();
    }
}
