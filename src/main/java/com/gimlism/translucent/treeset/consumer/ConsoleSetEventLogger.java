package com.gimlism.translucent.treeset.consumer;

import com.gimlism.translucent.treeset.events.SetEvent;
import com.gimlism.translucent.treeset.events.SetEventFormatter;
import com.gimlism.translucent.treeset.events.SetEventListener;
import java.io.PrintStream;

/** Prints each {@code TeachingTreeSet} event as a formatted one-line caption. */
public final class ConsoleSetEventLogger implements SetEventListener {
    private final PrintStream out;

    public ConsoleSetEventLogger() {
        this(System.out);
    }

    public ConsoleSetEventLogger(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onEvent(SetEvent event) {
        out.println(SetEventFormatter.format(event));
    }
}
