package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.substrate.viz.Visualizer;
import java.io.PrintStream;

/** Live ASCII visualizer for the map — a {@link Visualizer} wired with an {@link AsciiRenderer}. */
public final class AsciiVisualizer extends Visualizer<MapEvent> {
    public AsciiVisualizer(PrintStream out, AsciiRenderer renderer) {
        super(out, renderer);
    }

    public AsciiVisualizer(PrintStream out) {
        this(out, new AsciiRenderer(Palette.auto()));
    }
}
