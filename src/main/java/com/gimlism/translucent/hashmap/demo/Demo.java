package com.gimlism.translucent.hashmap.demo;

import com.gimlism.translucent.hashmap.consumer.ConsoleEventLogger;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import java.io.PrintStream;

/** Scripted demonstration of the teaching HashMap event stream. */
public class Demo {
    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream out) {
        var map = new TeachingHashMap<Integer, String>();
        map.addListener(new ConsoleEventLogger(out));

        out.println("== inserting keys that collide in bucket 0 until it treeifies ==");
        for (int k : new int[]{0, 8, 16, 24}) { // 4th key hits treeifyThreshold
            map.put(k, "v" + k);
        }

        out.println("== inserting more keys until the table resizes ==");
        for (int k = 1; k <= 7; k++) {
            map.put(k, "v" + k);
        }
    }
}
