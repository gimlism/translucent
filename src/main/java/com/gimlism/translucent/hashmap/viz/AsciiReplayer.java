package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.MapEvent;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

/** Steps a recorded event stream frame-by-frame (each event's snapshot is a full frame). */
public final class AsciiReplayer {
    private final List<MapEvent> events;
    private final AsciiRenderer renderer;

    public AsciiReplayer(List<MapEvent> events, AsciiRenderer renderer) {
        this.events = events;
        this.renderer = renderer;
    }

    /** Interactive: Enter = next, {@code b} = back, {@code q} = quit; next past the end ends. */
    public void run(InputStream in, PrintStream out) {
        if (events.isEmpty()) {
            out.println("(no events to replay)");
            return;
        }
        Scanner scanner = new Scanner(in, StandardCharsets.UTF_8);
        int i = 0;
        while (true) {
            printFrame(out, i);
            out.print("[Enter=next, b=back, q=quit] ");
            out.flush(); // no trailing newline -> PrintStream won't auto-flush; make the prompt visible
            if (!scanner.hasNextLine()) break;
            String cmd = scanner.nextLine().trim();
            if (cmd.equals("q")) break;
            if (cmd.equals("b")) {
                i = Math.max(0, i - 1);
            } else {
                if (i == events.size() - 1) break; // next past the last frame ends
                i++;
            }
        }
    }

    /** Non-interactive: render every frame in order, sleeping {@code delayMillis} between them. */
    public void autoPlay(PrintStream out, long delayMillis) {
        for (int i = 0; i < events.size(); i++) {
            printFrame(out, i);
            if (delayMillis > 0 && i < events.size() - 1) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void printFrame(PrintStream out, int i) {
        out.println("── frame " + (i + 1) + "/" + events.size() + " ──");
        out.println(renderer.renderEvent(events.get(i)));
    }
}
