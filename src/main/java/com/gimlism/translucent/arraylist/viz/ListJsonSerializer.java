package com.gimlism.translucent.arraylist.viz;

import com.gimlism.translucent.arraylist.events.Append;
import com.gimlism.translucent.arraylist.events.EmptySlot;
import com.gimlism.translucent.arraylist.events.FilledSlot;
import com.gimlism.translucent.arraylist.events.Grow;
import com.gimlism.translucent.arraylist.events.Insert;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListEventFormatter;
import com.gimlism.translucent.arraylist.events.ListSnapshot;
import com.gimlism.translucent.arraylist.events.RemoveAt;
import com.gimlism.translucent.arraylist.events.Set;
import com.gimlism.translucent.arraylist.events.Shift;
import com.gimlism.translucent.arraylist.events.SlotSnapshot;
import com.gimlism.translucent.substrate.viz.JsonWriter;
import java.util.List;

/**
 * The per-structure half of the ArrayList web-viz seam: maps a recorded {@code ListEvent} stream to
 * the JSON the browser replay consumes. The web analogue of {@code AsciiListRenderer} — it holds the
 * event-shape branching (index/from-to/capacity fields, slot classification) so the front-end stays a
 * dumb renderer. Each frame carries the event's own {@code after()} snapshot, so the JSON shows exactly
 * the state the core emitted (including the deliberate mid-slide {@code Shift} and pre-placement
 * {@code Grow} snapshots). Captions come from {@link ListEventFormatter}, the one source of wording.
 */
public final class ListJsonSerializer {

    private ListJsonSerializer() {}

    public static String toJson(List<ListEvent> events) {
        JsonWriter w = new JsonWriter();
        w.beginObject().name("frames").beginArray();
        for (ListEvent e : events) writeFrame(w, e);
        w.endArray().endObject();
        return w.toString();
    }

    /** One event → the JSON for a single {@code { "event":…, "list":… }} frame (no {@code frames} wrapper). */
    public static String toFrame(ListEvent e) {
        JsonWriter w = new JsonWriter();
        writeFrame(w, e);
        return w.toString();
    }

    private static void writeFrame(JsonWriter w, ListEvent e) {
        w.beginObject();
        w.name("event");
        writeEvent(w, e);
        w.name("list");
        writeList(w, e.after());
        w.endObject();
    }

    private static void writeEvent(JsonWriter w, ListEvent e) {
        w.beginObject();
        w.name("type").value(e.getClass().getSimpleName());
        w.name("label").value(ListEventFormatter.format(e));
        switch (e) {
            case Append a -> w.name("index").value((long) a.index());
            case Insert in -> w.name("index").value((long) in.index());
            case Set s -> w.name("index").value((long) s.index());
            case RemoveAt r -> w.name("index").value((long) r.index());
            case Shift sh -> {
                w.name("index").value((long) sh.toIndex());
                w.name("fromIndex").value((long) sh.fromIndex());
                w.name("toIndex").value((long) sh.toIndex());
            }
            case Grow g -> {
                w.name("oldCapacity").value((long) g.oldCapacity());
                w.name("newCapacity").value((long) g.newCapacity());
            }
        }
        w.endObject();
    }

    private static void writeList(JsonWriter w, ListSnapshot s) {
        w.beginObject();
        w.name("capacity").value((long) s.capacity());
        w.name("size").value((long) s.size());
        w.name("slots").beginArray();
        for (SlotSnapshot slot : s.slots()) writeSlot(w, slot);
        w.endArray();
        w.endObject();
    }

    private static void writeSlot(JsonWriter w, SlotSnapshot slot) {
        w.beginObject();
        switch (slot) {
            case FilledSlot f -> {
                w.name("filled").value(true);
                w.name("element").value(f.element() == null ? null : String.valueOf(f.element()));
            }
            case EmptySlot e -> w.name("filled").value(false);
        }
        w.endObject();
    }
}
