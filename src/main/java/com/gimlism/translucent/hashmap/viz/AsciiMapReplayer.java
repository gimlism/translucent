package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.substrate.viz.Replayer;
import java.util.List;

/** Step-through ASCII replayer for a recorded map stream — a {@link Replayer} + {@link AsciiMapRenderer}. */
public final class AsciiMapReplayer extends Replayer<MapEvent> {
    public AsciiMapReplayer(List<MapEvent> events, AsciiMapRenderer renderer) {
        super(events, renderer);
    }
}
