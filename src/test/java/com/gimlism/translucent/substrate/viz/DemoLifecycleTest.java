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

    @Test
    void saysNothingWhenTheRequestedPortWasFree() throws Exception {
        LiveServer first = started(0);
        int free = first.port();
        first.stop(); // release it, then ask for that exact port and get it

        LiveServer server = started(free);
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
