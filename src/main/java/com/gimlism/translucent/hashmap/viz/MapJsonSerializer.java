package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.BucketSnapshot;
import com.gimlism.translucent.hashmap.events.ChainSnapshot;
import com.gimlism.translucent.hashmap.events.Collision;
import com.gimlism.translucent.hashmap.events.EmptyBucket;
import com.gimlism.translucent.hashmap.events.EntrySnapshot;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import com.gimlism.translucent.hashmap.events.Put;
import com.gimlism.translucent.hashmap.events.Recolor;
import com.gimlism.translucent.hashmap.events.Remove;
import com.gimlism.translucent.hashmap.events.Resize;
import com.gimlism.translucent.hashmap.events.Rotation;
import com.gimlism.translucent.hashmap.events.TreeNodeSnapshot;
import com.gimlism.translucent.hashmap.events.TreeSnapshot;
import com.gimlism.translucent.hashmap.events.Treeify;
import com.gimlism.translucent.hashmap.events.Untreeify;
import com.gimlism.translucent.substrate.viz.JsonWriter;
import java.util.List;

/**
 * The per-structure half of the web-viz seam: maps a recorded {@code MapEvent} stream to the
 * JSON the browser replay consumes. The web analogue of {@code AsciiMapRenderer} — it holds all
 * the branching/recursion (bucket classification, red-black tree walk, event captions) so the
 * front-end can stay a dumb renderer. Each frame carries the event's own {@code after()}
 * snapshot, so the JSON shows exactly the settled state the core emitted.
 */
public final class MapJsonSerializer {

    private MapJsonSerializer() {}

    public static String toJson(List<MapEvent> events) {
        JsonWriter w = new JsonWriter();
        w.beginObject().name("frames").beginArray();
        for (MapEvent e : events) writeFrame(w, e);
        w.endArray().endObject();
        return w.toString();
    }

    private static void writeFrame(JsonWriter w, MapEvent e) {
        w.beginObject();
        w.name("event");
        writeEvent(w, e);
        w.name("map");
        writeMap(w, e.after());
        w.endObject();
    }

    private static void writeEvent(JsonWriter w, MapEvent e) {
        w.beginObject();
        w.name("type").value(e.getClass().getSimpleName());
        w.name("label").value(label(e));
        Integer bucket = bucketOf(e);
        if (bucket != null) w.name("bucket").value((long) bucket);
        String hl = highlightKey(e);
        if (hl != null) w.name("highlightKey").value(hl);
        w.endObject();
    }

    private static String label(MapEvent e) {
        return switch (e) {
            case Put p -> p.newEntry()
                    ? "put " + str(p.key()) + " → bucket " + p.bucketIndex()
                    : "set " + str(p.key()) + " = " + str(p.value());
            case Remove r -> "remove " + str(r.key());
            case Collision c -> "collision @ bucket " + c.bucketIndex()
                    + " (chain " + c.chainLengthBefore() + "→" + c.chainLengthAfter() + ")";
            case Resize z -> "resize " + z.oldCapacity() + " → " + z.newCapacity();
            case Treeify t -> "treeify bucket " + t.bucketIndex();
            case Untreeify u -> "untreeify bucket " + u.bucketIndex();
            case Rotation ro -> "rotate " + ro.direction() + " @ " + str(ro.pivotKey());
            case Recolor rc -> "recolor " + str(rc.nodeKey()) + " " + rc.oldColor() + "→" + rc.newColor();
        };
    }

    private static Integer bucketOf(MapEvent e) {
        return switch (e) {
            case Put p -> p.bucketIndex();
            case Remove r -> r.bucketIndex();
            case Collision c -> c.bucketIndex();
            case Treeify t -> t.bucketIndex();
            case Untreeify u -> u.bucketIndex();
            case Rotation ro -> ro.bucketIndex();
            case Recolor rc -> rc.bucketIndex();
            case Resize z -> null;
        };
    }

    private static String highlightKey(MapEvent e) {
        return switch (e) {
            case Put p -> str(p.key());
            case Remove r -> str(r.key());
            case Collision c -> str(c.key());
            case Rotation ro -> str(ro.pivotKey());
            case Recolor rc -> str(rc.nodeKey());
            case Treeify t -> null;
            case Untreeify u -> null;
            case Resize z -> null;
        };
    }

    private static void writeMap(JsonWriter w, MapSnapshot m) {
        w.beginObject();
        w.name("capacity").value((long) m.capacity());
        w.name("size").value((long) m.size());
        w.name("threshold").value((long) m.threshold());
        w.name("buckets").beginArray();
        for (BucketSnapshot b : m.buckets()) writeBucket(w, b);
        w.endArray();
        w.endObject();
    }

    private static void writeBucket(JsonWriter w, BucketSnapshot b) {
        w.beginObject();
        switch (b) {
            case ChainSnapshot c -> {
                w.name("kind").value("chain");
                w.name("entries").beginArray();
                for (EntrySnapshot en : c.entries()) {
                    w.beginObject();
                    w.name("key").value(str(en.key()));
                    w.name("value").value(str(en.value()));
                    w.name("hash").value((long) en.hash());
                    w.endObject();
                }
                w.endArray();
            }
            case TreeSnapshot t -> {
                w.name("kind").value("tree");
                w.name("root");
                writeNode(w, t.root());
            }
            case EmptyBucket e -> w.name("kind").value("empty");
        }
        w.endObject();
    }

    private static void writeNode(JsonWriter w, TreeNodeSnapshot n) {
        if (n == null) {
            w.nullValue();
            return;
        }
        w.beginObject();
        w.name("key").value(str(n.key()));
        w.name("value").value(str(n.value()));
        w.name("color").value(n.color().name());
        w.name("left");
        writeNode(w, n.left());
        w.name("right");
        writeNode(w, n.right());
        w.endObject();
    }

    /** null → JSON null (via {@link JsonWriter#value(String)}); otherwise the value's String form. */
    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
