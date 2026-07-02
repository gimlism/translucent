package com.gimlism.translucent.hashmap.events;

/** Consumer of the map's event stream. Invoked synchronously after each mutation. */
@FunctionalInterface
public interface MapEventListener {
    void onEvent(MapEvent event);
}
