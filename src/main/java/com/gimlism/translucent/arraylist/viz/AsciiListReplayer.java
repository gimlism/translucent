package com.gimlism.translucent.arraylist.viz;

import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.substrate.viz.Replayer;
import java.util.List;

/** Step-through ASCII replayer for a recorded list stream — a {@link Replayer} + {@link AsciiListRenderer}. */
public final class AsciiListReplayer extends Replayer<ListEvent> {
    public AsciiListReplayer(List<ListEvent> events, AsciiListRenderer renderer) {
        super(events, renderer);
    }
}
