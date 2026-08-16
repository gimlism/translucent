package com.gimlism.translucent.trie.viz;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.trie.core.RadixTrie;
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

/** Proves the full slice: a RadixTrie mutation surfaces as a live SSE frame over HTTP. */
class TrieLiveVizEndToEndTest {

    @Test
    void aMutationSurfacesAsALiveFrameOverHttp() throws IOException {
        LiveServer server = new LiveServer(TrieWebExporter.liveHtml("RadixTrie"), "127.0.0.1", 0);
        server.start();
        HttpClient client = HttpClient.newHttpClient();
        try {
            var trie = new RadixTrie<Integer>();
            trie.addListener(new TrieLiveVisualizer(server::broadcast));
            trie.put("cat", 1); // last event (Put) becomes the cached snapshot-on-connect frame

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                InputStream bodyStream = client.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/events")).build(),
                        BodyHandlers.ofInputStream()).body();
                try (var r = new BufferedReader(new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String frame = line.substring("data: ".length());
                            assertTrue(frame.contains("\"type\":\"Put\""), "frame carries the Put event");
                            assertTrue(frame.contains("\"highlightPath\":\"cat\""), "frame carries the highlight path");
                            assertTrue(frame.contains("\"trie\":{"), "frame carries the whole-trie snapshot");
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
