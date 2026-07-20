package com.gimlism.translucent.treeset.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.repl.TreeSetCommandInterpreter;
import com.gimlism.translucent.treeset.viz.TreeSetLiveVisualizer;
import com.gimlism.translucent.treeset.viz.TreeSetWebExporter;
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

/**
 * Proves Slice D: a browser POST /command mutates the set and surfaces as a live SSE frame — and,
 * the point of this slice, a POSTed comparison read narrates as a live Compare frame (no sibling
 * has this — reads are silent in map/list/trie).
 */
class TreeSetLiveControlsEndToEndTest {

    @Test
    void aPostedAddMutatesTheSetAndSurfacesAsALiveAddFrame() throws IOException {
        var set = new TeachingTreeSet<Integer>();
        Function<String, String> handler =
                TreeSetLiveControlsDemo.commandHandler(set, new TreeSetCommandInterpreter());
        LiveServer server = new LiveServer(TreeSetWebExporter.controlsHtml(), "127.0.0.1", 0, handler);
        server.start();
        HttpClient client = HttpClient.newHttpClient();
        try {
            set.addListener(new TreeSetLiveVisualizer(server::broadcast));

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                String base = "http://127.0.0.1:" + server.port();

                // open the SSE stream first, then wait until the server has registered it
                InputStream body = client.send(
                        HttpRequest.newBuilder(URI.create(base + "/events")).build(),
                        BodyHandlers.ofInputStream()).body();
                try (var r = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                    while (server.openConnections() < 1) Thread.sleep(10);

                    var resp = client.send(HttpRequest.newBuilder(URI.create(base + "/command"))
                                    .POST(BodyPublishers.ofString("add 30", StandardCharsets.UTF_8)).build(),
                            BodyHandlers.ofString());
                    assertEquals("added 30", resp.body());

                    // The reader connected BEFORE the POST, so it sees frames incrementally. The
                    // TreeSet has NO CreateNode — add IS the creation — so the Add frame appears
                    // directly (unlike the trie, which reads past a leading CreateNode to find Put).
                    // add 30 is a root insert (root == null branch), which emits ONLY Add — no
                    // fixup, so no Recolor either; scan until the Add frame shows up.
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String frame = line.substring("data: ".length());
                            if (frame.contains("\"type\":\"Add\"")) {
                                return; // the POST's mutation surfaced as a live Add frame
                            }
                        }
                    }
                    throw new IOException("no Add frame received");
                }
            });
        } finally {
            client.close();
            server.stop();
        }
    }

    @Test
    void aPostedComparisonReadNarratesAsALiveCompareFrame() throws IOException {
        var set = new TeachingTreeSet<Integer>();
        Function<String, String> handler =
                TreeSetLiveControlsDemo.commandHandler(set, new TreeSetCommandInterpreter());
        LiveServer server = new LiveServer(TreeSetWebExporter.controlsHtml(), "127.0.0.1", 0, handler);
        server.start();
        HttpClient client = HttpClient.newHttpClient();
        try {
            set.addListener(new TreeSetLiveVisualizer(server::broadcast));

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                String base = "http://127.0.0.1:" + server.port();

                InputStream body = client.send(
                        HttpRequest.newBuilder(URI.create(base + "/events")).build(),
                        BodyHandlers.ofInputStream()).body();
                try (var r = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                    while (server.openConnections() < 1) Thread.sleep(10);

                    // add 30 is a root insert (root == null branch) that emits ONLY Add — no fixup,
                    // so no Recolor and NO Compare. Thus the only Compare frame in the stream is the
                    // one the read emits as it searches.
                    // If reads were silent, no Compare frame would surface and this test would time out.
                    client.send(HttpRequest.newBuilder(URI.create(base + "/command"))
                                    .POST(BodyPublishers.ofString("add 30", StandardCharsets.UTF_8)).build(),
                            BodyHandlers.ofString());
                    var resp = client.send(HttpRequest.newBuilder(URI.create(base + "/command"))
                                    .POST(BodyPublishers.ofString("contains 30", StandardCharsets.UTF_8)).build(),
                            BodyHandlers.ofString());
                    assertEquals("contains 30 → true", resp.body());

                    // The first (and only) Compare frame must be the read's — proof that a typed
                    // comparison read animates live.
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String frame = line.substring("data: ".length());
                            if (frame.contains("\"type\":\"Compare\"")) {
                                return; // the POSTed read narrated as a live Compare frame
                            }
                        }
                    }
                    throw new IOException("no Compare frame received");
                }
            });
        } finally {
            client.close();
            server.stop();
        }
    }

    @Test
    void quitReturnsItsMessageWithoutStoppingOrMutating() {
        var set = new TeachingTreeSet<Integer>();
        Function<String, String> handler =
                TreeSetLiveControlsDemo.commandHandler(set, new TreeSetCommandInterpreter());
        handler.apply("add 1"); // set now has one element
        assertEquals("bye", handler.apply("quit"));
        assertEquals("bye", handler.apply("exit"));
        // the seam holds no server reference, so quit/exit cannot stop the session; and they are
        // pure reads through the interpreter, so the set is untouched.
        assertEquals(1, set.size(), "quit/exit leave the set unchanged");
    }
}
