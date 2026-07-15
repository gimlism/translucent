package com.gimlism.translucent.arraylist.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.repl.ListCommandInterpreter;
import com.gimlism.translucent.arraylist.viz.ListLiveVisualizer;
import com.gimlism.translucent.arraylist.viz.ListWebExporter;
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

/** Proves Slice D: a browser POST /command mutates the list and surfaces as a live SSE frame. */
class ListLiveControlsEndToEndTest {

    @Test
    void aPostedCommandMutatesTheListAndSurfacesAsALiveFrame() throws IOException {
        var list = new TeachingArrayList<String>();
        Function<String, String> handler = ListLiveControlsDemo.commandHandler(list, new ListCommandInterpreter());
        LiveServer server = new LiveServer(ListWebExporter.controlsHtml(), "127.0.0.1", 0, handler);
        server.start();
        HttpClient client = HttpClient.newHttpClient();
        try {
            list.addListener(new ListLiveVisualizer(server::broadcast));

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
                                    .POST(BodyPublishers.ofString("add hi", StandardCharsets.UTF_8)).build(),
                            BodyHandlers.ofString());
                    assertEquals("appended \"hi\" at 0", resp.body());

                    // the mutation's frame arrives on the SSE stream — the very first add() on an
                    // empty (no-arg-constructed) list also emits a leading Grow frame (cap 0 -> 10),
                    // so skip past that one and keep reading until the Append frame itself shows up
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String frame = line.substring("data: ".length());
                            if (frame.contains("\"type\":\"Append\"")) {
                                return; // the POST's mutation surfaced as a live Append frame
                            }
                        }
                    }
                    throw new IOException("no Append frame received");
                }
            });
        } finally {
            client.close();
            server.stop();
        }
    }

    @Test
    void quitReturnsItsMessageWithoutStoppingOrMutating() {
        var list = new TeachingArrayList<String>();
        Function<String, String> handler = ListLiveControlsDemo.commandHandler(list, new ListCommandInterpreter());
        handler.apply("add a"); // list now has one element
        assertEquals("bye", handler.apply("quit"));
        assertEquals("bye", handler.apply("exit"));
        // the seam holds no server reference, so quit/exit cannot stop the session; and they are
        // pure reads through the interpreter, so the list is untouched.
        assertEquals(1, list.size(), "quit/exit leave the list unchanged");
    }
}
