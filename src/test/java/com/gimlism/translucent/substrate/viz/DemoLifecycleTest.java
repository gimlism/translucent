package com.gimlism.translucent.substrate.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The port-fallback notice. {@link LiveServer} quietly moves to a free port when the one it was
 * asked for is taken; without this notice a student sees an unexplained port number and has no way
 * to tell a fallback from a typo.
 */
class DemoLifecycleTest {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

    private String printed() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static LiveServer started(int port) throws IOException {
        LiveServer s = new LiveServer("<html>x</html>", "127.0.0.1", port);
        s.start();
        return s;
    }

    /**
     * Start on a port we asked for <em>and were granted</em>. Probing for a free port means binding
     * one and releasing it, which leaves a window for another process to take it before we ask
     * again — and losing that race would fire a real fallback and fail the caller's assertion while
     * the implementation was correct. Retrying closes the window instead of assuming it away.
     */
    private static LiveServer startOnAGrantedPort() throws IOException {
        for (int attempt = 0; attempt < 10; attempt++) {
            LiveServer probe = started(0);
            int candidate = probe.port();
            probe.stop();

            LiveServer server = started(candidate);
            if (server.port() == candidate) return server;
            server.stop(); // lost the race for that port — probe for another
        }
        throw new IllegalStateException("could not be granted a specific port in 10 attempts");
    }

    @Test
    void saysNothingWhenTheRequestedPortWasFree() throws Exception {
        LiveServer server = startOnAGrantedPort();
        try {
            DemoLifecycle.announcePortFallback(server, out);
            assertEquals("", printed(), "no fallback happened, so there is nothing to explain");
        } finally {
            server.stop();
        }
    }

    @Test
    void namesBothPortsWhenTheRequestedPortWasTaken() throws Exception {
        LiveServer holder = started(0);
        int taken = holder.port();

        LiveServer server = started(taken);
        try {
            DemoLifecycle.announcePortFallback(server, out);

            String notice = printed();
            assertTrue(notice.contains(String.valueOf(taken)),
                    "should name the port it wanted, got: " + notice);
            assertTrue(notice.contains(String.valueOf(server.port())),
                    "should name the port it got, got: " + notice);
        } finally {
            server.stop();
            holder.stop();
        }
    }

    @Test
    void saysNothingWhenAnEphemeralPortWasRequestedDeliberately() throws Exception {
        // port 0 means "any free port", so port() never matches requestedPort() — but that is
        // the caller getting what they asked for, not a fallback. Naive != would misfire here.
        LiveServer server = started(0);
        try {
            DemoLifecycle.announcePortFallback(server, out);
            assertEquals("", printed(), "port 0 is a request for any port, not a preference that was denied");
        } finally {
            server.stop();
        }
    }
}
