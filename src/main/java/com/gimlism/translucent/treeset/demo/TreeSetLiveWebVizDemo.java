package com.gimlism.translucent.treeset.demo;

import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
import com.gimlism.translucent.substrate.viz.LiveServer;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.viz.TreeSetLiveVisualizer;
import com.gimlism.translucent.treeset.viz.TreeSetWebExporter;
import java.io.IOException;

/**
 * The student sandbox: a running {@link TeachingTreeSet} whose every event renders live in the
 * browser. Write your own add/remove/contains calls in the marked block, then run:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.treeset.demo.TreeSetLiveWebVizDemo}
 * and watch the red-black tree rotate and recolour as your code runs — and, because the set narrates
 * reads, watch a {@code contains} walk animate the comparison cursor live. The server keeps running
 * after your code finishes so the final state stays live and scrubbable — stop it with Ctrl-C.
 */
public class TreeSetLiveWebVizDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        LiveServer server = new LiveServer(TreeSetWebExporter.liveHtml(), "127.0.0.1", DEFAULT_PORT);
        server.start();
        String url = "http://localhost:" + server.port();
        DemoLifecycle.announcePortFallback(server);

        var set = new TeachingTreeSet<Integer>();
        set.addListener(new TreeSetLiveVisualizer(server::broadcast));

        BrowserLauncher.open(url);
        System.out.println("Serving live at " + url + " — Ctrl-C to stop.");

        // ---------------------------------------------------------------
        // TODO: your mutations here — each renders live in the browser.
        for (int e : new int[] {10, 20, 30, 40, 50}) { // watch rotations + recolours
            set.add(e);
        }
        set.contains(25); // a read: the comparison walk animates live (reads narrate)
        set.remove(30);
        // ---------------------------------------------------------------

        DemoLifecycle.awaitShutdown(server);
    }
}
