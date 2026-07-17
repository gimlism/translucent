package com.gimlism.translucent.treeset.viz;

import com.gimlism.translucent.substrate.viz.JsonWriter;
import com.gimlism.translucent.treeset.events.SetEvent;
import com.gimlism.translucent.treeset.events.SetEventFormatter;
import com.gimlism.translucent.treeset.events.SetNodeSnapshot;
import com.gimlism.translucent.treeset.events.SetSnapshot;
import java.util.List;

/**
 * The per-structure half of the TreeSet web-viz seam: maps a recorded {@code SetEvent} stream to the
 * JSON the browser replay consumes. The web analogue of {@link AsciiSetRenderer} — it holds the
 * recursive binary-node serialization so the front-end stays a dumb renderer. Each frame carries the
 * event's own {@code after()} snapshot; captions come from {@link SetEventFormatter}.
 *
 * <p>The per-node highlight is resolved <em>here, in Java</em>: the node whose element equals
 * {@link AsciiSetRenderer#affectedElement} is marked {@code hl:true} (Remove's null target marks
 * none). The browser reads a boolean rather than value-matching an {@code Object} across the JSON
 * boundary — so a number-vs-string mismatch can never silently drop the highlight.
 */
public final class TreeSetJsonSerializer {

    private TreeSetJsonSerializer() {}

    public static String toJson(List<SetEvent> events) {
        JsonWriter w = new JsonWriter();
        w.beginObject().name("frames").beginArray();
        for (SetEvent e : events) writeFrame(w, e);
        w.endArray().endObject();
        return w.toString();
    }

    /** One event → the JSON for a single {@code { "event":…, "set":… }} frame (no {@code frames} wrapper). */
    public static String toFrame(SetEvent e) {
        JsonWriter w = new JsonWriter();
        writeFrame(w, e);
        return w.toString();
    }

    private static void writeFrame(JsonWriter w, SetEvent e) {
        w.beginObject();
        w.name("event");
        writeEvent(w, e);
        w.name("set");
        writeSet(w, e.after(), AsciiSetRenderer.affectedElement(e));
        w.endObject();
    }

    private static void writeEvent(JsonWriter w, SetEvent e) {
        w.beginObject();
        w.name("type").value(e.getClass().getSimpleName());
        w.name("label").value(SetEventFormatter.format(e));
        w.endObject();
    }

    private static void writeSet(JsonWriter w, SetSnapshot s, Object highlight) {
        w.beginObject();
        w.name("size").value((long) s.size());
        w.name("root");
        writeNode(w, s.root(), highlight);
        w.endObject();
    }

    private static void writeNode(JsonWriter w, SetNodeSnapshot node, Object highlight) {
        if (node == null) {
            w.value((String) null);
            return;
        }
        w.beginObject();
        w.name("element").value(String.valueOf(node.element()));
        w.name("red").value(node.red());
        w.name("hl").value(highlight != null && highlight.equals(node.element()));
        w.name("left");
        writeNode(w, node.left(), highlight);
        w.name("right");
        writeNode(w, node.right(), highlight);
        w.endObject();
    }
}
