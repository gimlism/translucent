package com.gimlism.translucent.arraylist.viz;

import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.substrate.viz.Visualizer;
import java.io.PrintStream;

/** Live ASCII visualizer for the list — a {@link Visualizer} wired with an {@link AsciiListRenderer}. */
public final class AsciiListVisualizer extends Visualizer<ListEvent> {
    public AsciiListVisualizer(PrintStream out, AsciiListRenderer renderer) {
        super(out, renderer);
    }

    public AsciiListVisualizer(PrintStream out) {
        this(out, new AsciiListRenderer());
    }
}
