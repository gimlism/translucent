package com.gimlism.translucent.trie.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.repl.TrieCommandInterpreter;
import com.gimlism.translucent.trie.viz.TrieLiveVisualizer;
import com.gimlism.translucent.trie.viz.TrieWebExporter;
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

/** Proves Slice D: a browser POST /command mutates the trie and surfaces as a live SSE frame. */
class RadixTrieLiveControlsEndToEndTest {

    @Test
    void aPostedCommandMutatesTheTrieAndSurfacesAsALiveFrame() throws IOException {
        var trie = new RadixTrie<Integer>();
        Function<String, String> handler = RadixTrieLiveControlsDemo.commandHandler(trie, new TrieCommandInterpreter());
        LiveServer server = new LiveServer(TrieWebExporter.controlsHtml("RadixTrie"), "127.0.0.1", 0, handler);
        server.start();
        HttpClient client = HttpClient.newHttpClient();
        try {
            trie.addListener(new TrieLiveVisualizer(server::broadcast));

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
                String base = "http://127.0.0.1:" + server.port();

                // open the SSE stream first, then wait until the server has registered it
                InputStream body = client.send(
                        HttpRequest.newBuilder(URI.create(base + "/events")).build(),
                        BodyHandlers.ofInputStream()).body();
                try (var r = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
                    while (server.openConnections() < 1) Thread.sleep(10);

                    // POST a command; the response is the interpreter's text
                    var resp = client.send(HttpRequest.newBuilder(URI.create(base + "/command"))
                                    .POST(BodyPublishers.ofString("put cat 1", StandardCharsets.UTF_8)).build(),
                            BodyHandlers.ofString());
                    assertEquals("put cat = 1", resp.body());

                    // the mutation surfaces on the SSE stream. The first put into an EMPTY trie emits
                    // a leading CreateNode frame before the Put (the trie's grow-then-append analog),
                    // and the reader connected BEFORE the POST so it sees frames incrementally — so
                    // read PAST any leading frame until the Put frame itself shows up.
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String frame = line.substring("data: ".length());
                            if (frame.contains("\"type\":\"Put\"")) {
                                return; // the POST's mutation surfaced as a live Put frame
                            }
                        }
                    }
                    throw new IOException("no Put frame received");
                }
            });
        } finally {
            client.close();
            server.stop();
        }
    }

    @Test
    void quitReturnsItsMessageWithoutStoppingOrMutating() {
        var trie = new RadixTrie<Integer>();
        Function<String, String> handler = RadixTrieLiveControlsDemo.commandHandler(trie, new TrieCommandInterpreter());
        handler.apply("put a 1"); // trie now has one key
        assertEquals("bye", handler.apply("quit"));
        assertEquals("bye", handler.apply("exit"));
        // the seam holds no server reference, so quit/exit cannot stop the session; and they are
        // pure reads through the interpreter, so the trie is untouched.
        assertEquals(1, trie.size(), "quit/exit leave the trie unchanged");
    }
}
