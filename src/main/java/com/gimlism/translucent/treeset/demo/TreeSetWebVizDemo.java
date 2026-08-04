package com.gimlism.translucent.treeset.demo;

import com.gimlism.translucent.treeset.consumer.SetRecordingListener;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.viz.TreeSetJsonSerializer;
import com.gimlism.translucent.treeset.viz.TreeSetWebExporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Records a small story that exercises the tree's rebalancing — inserting ascending keys (watch
 * rotations and recolours keep the tree balanced), then removing one — and writes it out as a
 * self-contained HTML web replay. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.treeset.demo.TreeSetWebVizDemo}
 * (optionally {@code -Dexec.args="path/to/out.html"}). The whole-document build is factored into
 * {@link #buildHtml()} so it can be unit-tested without touching the filesystem.
 */
public class TreeSetWebVizDemo {

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "target/treeset-web-viz.html");
        Path parent = out.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(out, buildHtml());
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    /**
     * The self-contained HTML replay for the standard story, built without touching the filesystem.
     * Public because {@code RegenerateDocs} bakes it into {@code docs/viz/} and {@code DocsPagesGoldenTest}
     * pins the committed bytes to it — the seam has a production consumer now, not just a test.
     */
    public static String buildHtml() {
        var set = new TeachingTreeSet<Integer>();
        var rec = new SetRecordingListener();
        set.addListener(rec);
        for (int e : new int[] {10, 20, 30, 40, 50}) {
            set.add(e);   // ascending inserts force rotations + recolours
        }
        set.remove(30);
        return TreeSetWebExporter.toHtml(TreeSetJsonSerializer.toJson(rec.events()));
    }
}
