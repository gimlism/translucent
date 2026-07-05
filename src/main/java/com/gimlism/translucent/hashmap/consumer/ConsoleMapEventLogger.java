package com.gimlism.translucent.hashmap.consumer;

import com.gimlism.translucent.hashmap.events.MapEventFormatter;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.MapEventListener;
import java.io.PrintStream;

/** Prints a human-readable line per event (formatting only; no invariant checking). */
public class ConsoleMapEventLogger implements MapEventListener {
    private final PrintStream out;

    public ConsoleMapEventLogger() {
        this(System.out);
    }

    public ConsoleMapEventLogger(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onEvent(MapEvent event) {
        out.println(format(event));
    }

    /** The human-readable line for an event. Delegates to {@link MapEventFormatter}. */
    public static String format(MapEvent event) {
        return MapEventFormatter.format(event);
    }
}
