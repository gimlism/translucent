package com.gimlism.translucent.arraylist.demo;

import com.gimlism.translucent.arraylist.consumer.ListRecordingListener;
import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.viz.ListJsonSerializer;
import com.gimlism.translucent.arraylist.viz.ListWebExporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Records a small story that exercises every {@code ListEvent} — append past the initial capacity
 * (forcing a grow), an insert-in-the-middle (a shift burst), a set, and a remove (another shift) —
 * and writes it out as a self-contained HTML web replay. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.arraylist.demo.ListWebVizDemo}
 * (optionally {@code -Dexec.args="path/to/out.html"}). The whole-document build is factored into
 * {@link #buildHtml()} so it can be unit-tested without touching the filesystem.
 */
public class ListWebVizDemo {

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "target/arraylist-web-viz.html");
        Path parent = out.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(out, buildHtml());
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    /** The self-contained HTML replay for the standard story (package-private test seam — no filesystem). */
    static String buildHtml() {
        var list = new TeachingArrayList<String>(4);
        var rec = new ListRecordingListener();
        list.addListener(rec);

        // fill the initial capacity, then one more append to force a grow (4 -> 6)
        for (String s : new String[]{"a", "b", "c", "d", "e"}) {
            list.add(s);
        }
        list.add(2, "x"); // insert in the middle: shift the tail right, then place
        list.set(0, "A");  // in-place set, no structural change
        list.remove(1);    // remove in the middle: shift survivors left

        return ListWebExporter.toHtml(ListJsonSerializer.toJson(rec.events()));
    }
}
