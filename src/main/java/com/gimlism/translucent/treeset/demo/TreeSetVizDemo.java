package com.gimlism.translucent.treeset.demo;

import com.gimlism.translucent.treeset.core.TeachingTreeSet;
import com.gimlism.translucent.treeset.viz.AsciiSetVisualizer;
import java.io.PrintStream;

/**
 * Same scripted story as {@link TreeSetDemo}, rendered as live ASCII trees instead of a text log:
 * inserts that force rotations and recolours, a couple of reads that narrate the comparison walk
 * (a moving cursor, no structural change), and a removal (the removed element
 * simply leaves — nothing is highlighted).
 */
public class TreeSetVizDemo {
    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream out) {
        var set = new TeachingTreeSet<Integer>();
        set.addListener(new AsciiSetVisualizer(out));

        out.println("== inserting (watch the tree rotate and recolour to stay balanced) ==");
        for (int e : new int[]{10, 20, 30, 40, 50}) {
            set.add(e);
        }

        out.println("== reads narrate the comparison walk (a moving cursor, no new frame state) ==");
        set.contains(25); // a miss: walks and stops
        set.contains(40); // a hit

        out.println("== removing (the removed element simply leaves; nothing is highlighted) ==");
        set.remove(30);
    }
}
