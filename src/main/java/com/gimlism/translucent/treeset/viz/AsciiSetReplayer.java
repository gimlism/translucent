package com.gimlism.translucent.treeset.viz;

import com.gimlism.translucent.substrate.viz.Replayer;
import com.gimlism.translucent.treeset.events.SetEvent;
import java.util.List;

/** Step-through ASCII replayer for a recorded set stream — a {@link Replayer} + {@link AsciiSetRenderer}. */
public final class AsciiSetReplayer extends Replayer<SetEvent> {
    public AsciiSetReplayer(List<SetEvent> events, AsciiSetRenderer renderer) {
        super(events, renderer);
    }
}
