package com.gimlism.translucent.substrate.viz;

import com.gimlism.translucent.substrate.events.StructureEvent;
import com.gimlism.translucent.substrate.events.StructureEventListener;
import java.io.PrintStream;

/** Live consumer that prints an ASCII frame to a stream on every event. */
public class Visualizer<E extends StructureEvent> implements StructureEventListener<E> {
    private final PrintStream out;
    private final EventRenderer<E> renderer;

    public Visualizer(PrintStream out, EventRenderer<E> renderer) {
        this.out = out;
        this.renderer = renderer;
    }

    @Override
    public void onEvent(E event) {
        out.println(renderer.renderEvent(event));
        out.println();
    }
}
