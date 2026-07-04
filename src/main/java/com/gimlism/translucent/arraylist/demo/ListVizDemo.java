package com.gimlism.translucent.arraylist.demo;

import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import com.gimlism.translucent.arraylist.viz.AsciiListVisualizer;
import java.io.PrintStream;

/**
 * Same scripted story as {@link ListDemo}, rendered as live ASCII frames instead of a text log:
 * every mutation prints the contiguous cells row with the affected slot highlighted.
 */
public class ListVizDemo {
    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream out) {
        var list = new TeachingArrayList<String>(4);
        list.addListener(new AsciiListVisualizer(out));

        out.println("== appending past the initial capacity to force a grow ==");
        for (String s : new String[]{"a", "b", "c", "d", "e"}) { // 5th append: grow 4 -> 6
            list.add(s);
        }

        out.println("== inserting in the middle (shifts the tail right) ==");
        list.add(1, "X"); // [a,b,c,d,e] -> [a,X,b,c,d,e]

        out.println("== removing from the middle (shifts survivors left) ==");
        list.remove(2);   // remove "b"
    }
}
