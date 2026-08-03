package com.gimlism.translucent.treeset.demo;

import com.gimlism.translucent.treeset.consumer.ConsoleSetEventLogger;
import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import java.io.PrintStream;

/** Scripted demonstration of the teaching red-black-tree event stream. */
public class TreeSetDemo {
    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream out) {
        var set = new TeachingTreeSet<Integer>();
        set.addListener(new ConsoleSetEventLogger(out));

        out.println("== inserting an ascending run (watch it rotate and recolour to stay balanced) ==");
        for (int e : new int[]{10, 20, 30, 40, 50}) { // a right-leaning chain: forces both
            set.add(e);
        }

        out.println("== reads narrate the comparison walk (no structural change) ==");
        set.contains(25); // a miss: walks and stops
        set.contains(40); // a hit

        out.println("== removing (the element simply leaves) ==");
        set.remove(30);
    }
}
