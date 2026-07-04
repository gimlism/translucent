package com.gimlism.translucent.arraylist.viz;

import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListEventListener;
import java.io.PrintStream;

/** A live consumer that prints an ASCII frame to a stream on every list event. */
public final class AsciiListVisualizer implements ListEventListener {
    private final PrintStream out;
    private final AsciiListRenderer renderer;

    public AsciiListVisualizer(PrintStream out, AsciiListRenderer renderer) {
        this.out = out;
        this.renderer = renderer;
    }

    public AsciiListVisualizer(PrintStream out) {
        this(out, new AsciiListRenderer());
    }

    @Override
    public void onEvent(ListEvent event) {
        out.println(renderer.renderEvent(event));
        out.println();
    }
}
