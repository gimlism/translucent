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

        out.println("== inserting keys that collide in bucket 0 ==");
        map.put(0, "zero");
        map.put(8, "eight");
        map.put(16, "sixteen");

        out.println("== inserting keys until the table resizes ==");
        for (int k = 1; k <= 7; k++) {
            if (k != 8 && k != 16) map.put(k, "v" + k);
        }
    }
}
