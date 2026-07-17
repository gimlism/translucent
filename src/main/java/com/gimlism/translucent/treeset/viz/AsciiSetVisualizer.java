package com.gimlism.translucent.treeset.viz;

import com.gimlism.translucent.substrate.viz.ColorMode;
import com.gimlism.translucent.substrate.viz.Visualizer;
import com.gimlism.translucent.treeset.events.SetEvent;
import java.io.PrintStream;

/** Live ASCII visualizer for the set — a {@link Visualizer} wired with an {@link AsciiSetRenderer}. */
public final class AsciiSetVisualizer extends Visualizer<SetEvent> {
    public AsciiSetVisualizer(PrintStream out, AsciiSetRenderer renderer) {
        super(out, renderer);
    }

    public AsciiSetVisualizer(PrintStream out) {
        this(out, new AsciiSetRenderer(ColorMode.detect()));
    }
}
