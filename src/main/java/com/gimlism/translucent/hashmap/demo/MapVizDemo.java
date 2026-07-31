package com.gimlism.translucent.hashmap.demo;

import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.viz.AsciiMapVisualizer;
import java.io.PrintStream;

/**
 * Same scripted story as {@link MapDemo}, but rendered as live ASCII frames instead of a text log:
 * every mutation prints a highlighted whole-map diagram (sideways red-black trees once a bin
 * treeifies). The default {@code exec:java} entry point.
 */
public class MapVizDemo {
    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream out) {
        var map = new TeachingHashMap<Integer, String>();
        // Palette.auto(): ANSI colour on a real terminal, plain text when piped or captured.
        map.addListener(new AsciiMapVisualizer(out));

        out.println("== inserting keys that collide in bucket 0 until it treeifies (then resizes) ==");
        for (int k : new int[]{0, 8, 16, 24, 32, 40, 48, 56}) {
            map.put(k, "v" + k);
        }

        out.println("== removing colliding keys from bucket 0 until it untreeifies back to a chain ==");
        for (int k : new int[]{0, 16, 32}) {
            map.remove(k);
        }
    }
}
