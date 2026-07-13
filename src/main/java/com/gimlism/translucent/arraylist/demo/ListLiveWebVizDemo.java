package com.gimlism.translucent.arraylist.demo;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.viz.ListLiveVisualizer;
import com.gimlism.translucent.arraylist.viz.ListWebExporter;
import com.gimlism.translucent.substrate.viz.BrowserLauncher;
import com.gimlism.translucent.substrate.viz.DemoLifecycle;
import com.gimlism.translucent.substrate.viz.LiveServer;
import java.io.IOException;

/**
 * The student sandbox: a running {@link TeachingArrayList} whose every mutation renders live in the
 * browser. Write your own add/remove/set calls in the marked block, then run:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListLiveWebVizDemo}
 * and watch the structure change as your code runs. The server keeps running after your code
 * finishes so the final state stays live and scrubbable — stop it with Ctrl-C.
 */
public class ListLiveWebVizDemo {

    private static final int DEFAULT_PORT = 7070;

    public static void main(String[] args) throws IOException {
        LiveServer server = new LiveServer(ListWebExporter.liveHtml(), "127.0.0.1", DEFAULT_PORT);
        server.start();
        String url = "http://localhost:" + server.port();

        var list = new TeachingArrayList<String>(4);
        list.addListener(new ListLiveVisualizer(server::broadcast));

        BrowserLauncher.open(url);
        System.out.println("Serving live at " + url + " — Ctrl-C to stop.");

        // ---------------------------------------------------------------
        // TODO: your mutations here — each renders live in the browser.
        for (String s : new String[] {"a", "b", "c", "d", "e"}) { // 5th append grows 4 -> 6
            list.add(s);
        }
        list.add(2, "x"); // insert in the middle: a shift burst then place
        list.remove(1);   // remove in the middle: shift survivors left
        // ---------------------------------------------------------------

        DemoLifecycle.awaitShutdown(server);
    }
}
