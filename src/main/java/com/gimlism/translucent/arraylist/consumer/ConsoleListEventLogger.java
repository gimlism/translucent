package com.gimlism.translucent.arraylist.consumer;

import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListEventFormatter;
import com.gimlism.translucent.arraylist.events.ListEventListener;
import java.io.PrintStream;

/** Prints a human-readable line per event (formatting only; no invariant checking). */
public class ConsoleListEventLogger implements ListEventListener {
    private final PrintStream out;

    public ConsoleListEventLogger() {
        this(System.out);
    }

    public ConsoleListEventLogger(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onEvent(ListEvent event) {
        out.println(ListEventFormatter.format(event));
    }
}
