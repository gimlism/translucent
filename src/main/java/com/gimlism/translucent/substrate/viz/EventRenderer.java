package com.gimlism.translucent.substrate.viz;

import com.gimlism.translucent.substrate.events.StructureEvent;

/** The one seam a structure supplies to the substrate: an event → ASCII frame. */
@FunctionalInterface
public interface EventRenderer<E extends StructureEvent> {
    String renderEvent(E event);
}
