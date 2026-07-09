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
                try (var r = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
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
                }
            });
        } finally {
            server.stop();
        }
    }
}
