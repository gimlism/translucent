package com.gimlism.translucent.arraylist.viz;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
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

/** Proves the full slice: a TeachingArrayList mutation surfaces as a live SSE frame over HTTP. */
class ListLiveVizEndToEndTest {

    @Test
    void aMutationSurfacesAsALiveFrameOverHttp() throws IOException {
        LiveServer server = new LiveServer(ListWebExporter.liveHtml(), "127.0.0.1", 0);
        server.start();
        try {
            var list = new TeachingArrayList<String>(4);
            list.addListener(new ListLiveVisualizer(server::broadcast));
            list.add("z"); // becomes the cached last frame (an Append)

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                HttpClient client = HttpClient.newHttpClient();
                InputStream bodyStream = client.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/events")).build(),
                        BodyHandlers.ofInputStream()).body();
                try (var r = new BufferedReader(new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String frame = line.substring("data: ".length());
                            assertTrue(frame.contains("\"type\":\"Append\""), "frame carries the Append event");
                            assertTrue(frame.contains("\"label\":\"APPEND z @ 0\""), "frame carries the caption");
                            assertTrue(frame.contains("\"list\":{"), "frame carries the whole-list snapshot");
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
