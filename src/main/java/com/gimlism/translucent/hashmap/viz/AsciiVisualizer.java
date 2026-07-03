package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.MapEventListener;
import java.io.PrintStream;

/** A live consumer that prints an ASCII frame to a stream on every map event. */
public final class AsciiVisualizer implements MapEventListener {
    private final PrintStream out;
    private final AsciiRenderer renderer;

    public AsciiVisualizer(PrintStream out, AsciiRenderer renderer) {
        this.out = out;
        this.renderer = renderer;
    }

    public AsciiVisualizer(PrintStream out) {
        this(out, new AsciiRenderer(Palette.auto()));
    }

    @Override
    public void onEvent(MapEvent event) {
        out.println(renderer.renderEvent(event));
        out.println();
    }
}
