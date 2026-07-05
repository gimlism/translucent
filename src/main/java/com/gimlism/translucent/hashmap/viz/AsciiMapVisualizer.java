package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.substrate.viz.Visualizer;
import java.io.PrintStream;

/** Live ASCII visualizer for the map — a {@link Visualizer} wired with an {@link AsciiMapRenderer}. */
public final class AsciiMapVisualizer extends Visualizer<MapEvent> {
    public AsciiMapVisualizer(PrintStream out, AsciiMapRenderer renderer) {
        super(out, renderer);
    }

    public AsciiMapVisualizer(PrintStream out) {
        this(out, new AsciiMapRenderer(Palette.auto()));
    }
}
