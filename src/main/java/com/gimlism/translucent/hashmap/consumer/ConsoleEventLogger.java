package com.gimlism.translucent.hashmap.consumer;

import com.gimlism.translucent.hashmap.events.EventFormatter;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.MapEventListener;
import java.io.PrintStream;

/** Prints a human-readable line per event. Validates the stream end to end. */
public class ConsoleEventLogger implements MapEventListener {
    private final PrintStream out;

    public ConsoleEventLogger() {
        this(System.out);
    }

    public ConsoleEventLogger(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onEvent(MapEvent event) {
        out.println(format(event));
    }

    /** The human-readable line for an event. Delegates to {@link EventFormatter}. */
    public static String format(MapEvent event) {
        return EventFormatter.format(event);
    }
}
