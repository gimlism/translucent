package com.gimlism.translucent.trie.viz;

import com.gimlism.translucent.substrate.viz.JsonWriter;
import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieEvent;
import com.gimlism.translucent.trie.events.TrieEventFormatter;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;
import java.util.List;

/**
 * The per-structure half of the RadixTrie web-viz seam: maps a recorded {@code TrieEvent} stream to
 * the JSON the browser replay consumes. The web analogue of {@link AsciiTrieRenderer} — it holds the
 * recursive node serialization so the front-end stays a dumb renderer. Each frame carries the event's
 * own {@code after()} snapshot. Captions come from {@link TrieEventFormatter} and the per-frame
 * highlight target from {@link AsciiTrieRenderer#affectedPath} (same package) — so the ASCII and web
 * renderers name and highlight the identical node for every event, including the {@code Prune} case
 * where the highlighted node is the surviving parent, not the deleted leaf.
 */
public final class TrieJsonSerializer {

    private TrieJsonSerializer() {}

    public static String toJson(List<TrieEvent> events) {
        JsonWriter w = new JsonWriter();
        w.beginObject().name("frames").beginArray();
        for (TrieEvent e : events) writeFrame(w, e);
        w.endArray().endObject();
        return w.toString();
    }

    /** One event → the JSON for a single {@code { "event":…, "trie":… }} frame (no {@code frames} wrapper). */
    public static String toFrame(TrieEvent e) {
        JsonWriter w = new JsonWriter();
        writeFrame(w, e);
        return w.toString();
    }

    private static void writeFrame(JsonWriter w, TrieEvent e) {
        w.beginObject();
        w.name("event");
        writeEvent(w, e);
        w.name("trie");
        writeTrie(w, e.after());
        w.endObject();
    }

    private static void writeEvent(JsonWriter w, TrieEvent e) {
        w.beginObject();
        w.name("type").value(e.getClass().getSimpleName());
        w.name("label").value(TrieEventFormatter.format(e));
        w.name("highlightPath").value(AsciiTrieRenderer.affectedPath(e));
        w.endObject();
    }

    private static void writeTrie(JsonWriter w, TrieSnapshot s) {
        w.beginObject();
        w.name("size").value((long) s.size());
        w.name("root");
        writeNode(w, s.root());
        w.endObject();
    }

    private static void writeNode(JsonWriter w, TrieNodeSnapshot node) {
        w.beginObject();
        w.name("key").value(node.key());
        w.name("value").value(node.value() == null ? null : String.valueOf(node.value()));
        w.name("children").beginArray();
        for (TrieEdge edge : node.children()) {
            w.beginObject();
            w.name("label").value(edge.label());
            w.name("target");
            writeNode(w, edge.target());
            w.endObject();
        }
        w.endArray();
        w.endObject();
    }
}
