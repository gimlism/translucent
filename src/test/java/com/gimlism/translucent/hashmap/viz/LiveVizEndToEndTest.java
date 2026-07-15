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
        HttpClient client = HttpClient.newHttpClient();
        try {
            var map = new TeachingHashMap<Integer, String>();
            map.addListener(new MapLiveVisualizer(server::broadcast));
            map.put(42, "x"); // becomes the cached last frame

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                InputStream body = client.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/events")).build(),
                        BodyHandlers.ofInputStream()).body();
                try (var r = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
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
                }
            });
        } finally {
            client.close();
            server.stop();
        }
    }
}
