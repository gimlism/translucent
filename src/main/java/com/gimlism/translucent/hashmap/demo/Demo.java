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
        for (int k : new int[]{0, 8, 16, 24, 32, 40, 48, 56}) { // 4th key hits treeifyThreshold; 8 keys → resize at 7th
            map.put(k, "v" + k);
        }

        out.println("== removing colliding keys from bucket 0 until it untreeifies back to a chain ==");
        for (int k : new int[]{0, 16, 32}) { // 4->3->2: untreeify fires on removing 16 (<= untreeifyThreshold); 32 then hits the chain
            map.remove(k);
        }
    }
}
