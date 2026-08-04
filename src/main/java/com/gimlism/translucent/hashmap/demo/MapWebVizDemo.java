package com.gimlism.translucent.hashmap.demo;

import com.gimlism.translucent.hashmap.consumer.MapRecordingListener;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.viz.MapJsonSerializer;
import com.gimlism.translucent.hashmap.viz.MapWebExporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Records the standard collide → treeify → resize → untreeify story and writes it out as a
 * self-contained HTML web replay. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.hashmap.demo.MapWebVizDemo}
 * (optionally {@code -Dexec.args="path/to/out.html"}). The whole-document build is factored into
 * {@link #buildHtml()} so it can be unit-tested without touching the filesystem.
 */
public class MapWebVizDemo {

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "target/hashmap-web-viz.html");
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
        var map = new TeachingHashMap<Integer, String>();
        var rec = new MapRecordingListener();
        map.addListener(rec);

        // collide in bucket 0 until it treeifies, then grows past the load factor and resizes
        for (int k : new int[]{0, 8, 16, 24, 32, 40, 48, 56}) {
            map.put(k, "v" + k);
        }
        // remove colliding keys until the bin untreeifies back to a chain
        for (int k : new int[]{0, 16, 32}) {
            map.remove(k);
        }
        return MapWebExporter.toHtml(MapJsonSerializer.toJson(rec.events()));
    }
}
