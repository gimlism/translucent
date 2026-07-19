package com.gimlism.translucent.treeset.viz;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
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

/** Proves the full slice: a TeachingTreeSet mutation surfaces as a live SSE frame over HTTP. */
class TreeSetLiveVizEndToEndTest {

    @Test
    void aMutationSurfacesAsALiveFrameOverHttp() throws IOException {
        LiveServer server = new LiveServer(TreeSetWebExporter.liveHtml(), "127.0.0.1", 0);
        server.start();
        HttpClient client = HttpClient.newHttpClient();
        try {
            var set = new TeachingTreeSet<Integer>();
            set.addListener(new TreeSetLiveVisualizer(server::broadcast));
            set.add(7); // the last event of this add becomes the cached snapshot-on-connect frame

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                InputStream bodyStream = client.send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/events")).build(),
                        BodyHandlers.ofInputStream()).body();
                // try-with-resources on the reader: closing the client (below) while /events is still
                // open would hang the suite (PR #30). Reader and client closes are coupled.
                try (var r = new BufferedReader(new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String frame = line.substring("data: ".length());
                            // Type-agnostic: add(7)'s last event may be Add or the root-blacken Recolor;
                            // both carry the whole-set snapshot with element 7. The TreeSet has no
                            // CreateNode (Add is the creation), so do NOT assert one.
                            assertTrue(frame.contains("\"set\":{"), "frame carries the whole-set snapshot");
                            assertTrue(frame.contains("\"element\":\"7\""), "frame carries the added element");
                            return;
                        }
                    }
                    throw new IOException("no data line received");
                }
            });
        } finally {
            server.stop();  // close any still-open SSE stream first (e.g. if a timeout abandoned the reader mid-read)...
            client.close(); // ...so this graceful close has no in-flight /events request to await (matches LiveServerTest)
        }
    }
}
