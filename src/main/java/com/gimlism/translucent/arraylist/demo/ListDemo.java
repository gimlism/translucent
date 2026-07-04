package com.gimlism.translucent.arraylist.demo;

import com.gimlism.translucent.arraylist.consumer.ConsoleListEventLogger;
import com.gimlism.translucent.arraylist.core.TeachingArrayList;
import java.io.PrintStream;

/** Scripted demonstration of the teaching ArrayList event stream. */
public class ListDemo {
    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream out) {
        // Small eager capacity so an ordinary 1.5x grow fires quickly.
        var list = new TeachingArrayList<String>(4);
        list.addListener(new ConsoleListEventLogger(out));

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
