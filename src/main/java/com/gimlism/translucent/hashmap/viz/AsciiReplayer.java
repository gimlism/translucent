package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.substrate.viz.Replayer;
import java.util.List;

/** Step-through ASCII replayer for a recorded map stream — a {@link Replayer} + {@link AsciiRenderer}. */
public final class AsciiReplayer extends Replayer<MapEvent> {
    public AsciiReplayer(List<MapEvent> events, AsciiRenderer renderer) {
        super(events, renderer);
    }
}
