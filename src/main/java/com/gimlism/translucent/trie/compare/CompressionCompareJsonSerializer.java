package com.gimlism.translucent.trie.compare;

import com.gimlism.translucent.substrate.viz.JsonWriter;
import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;

/**
 * Pure {@code Comparison -> JSON} for the static web side-by-side page: both trees plus the savings
 * metrics, each node carrying a baked {@code absorbed} flag (from {@link TrieMetrics#isAbsorbed}) so
 * the browser stays a dumb renderer — the same "bake the decision in Java" choice
 * {@code TrieJsonSerializer} makes for {@code highlightPath}. {@code absorbed} is only ever true in
 * the standard tree; the radix trie keeps no single-child non-key node.
 */
public final class CompressionCompareJsonSerializer {

    private CompressionCompareJsonSerializer() {}

    public static String toJson(CompressionCompareDemo.Comparison c) {
        JsonWriter w = new JsonWriter();
        w.beginObject();
        w.name("keys").beginArray();
        for (String k : c.keys()) w.value(k);
        w.endArray();
        w.name("standard");
        writeTree(w, c.standardNodes(), c.standardSnapshot());
        w.name("radix");
        writeTree(w, c.radixNodes(), c.radixSnapshot());
        w.name("saved").value((long) c.saved());
        w.name("pct").value(c.savedPct());
        w.endObject();
        return w.toString();
    }

    private static void writeTree(JsonWriter w, int nodes, TrieSnapshot snap) {
        w.beginObject();
        w.name("nodes").value((long) nodes);
        w.name("root");
        writeNode(w, snap.root(), true);
        w.endObject();
    }

    private static void writeNode(JsonWriter w, TrieNodeSnapshot node, boolean root) {
        w.beginObject();
        w.name("key").value(node.key());
        w.name("value").value(node.value() == null ? null : String.valueOf(node.value()));
        w.name("absorbed").value(TrieMetrics.isAbsorbed(node, root));
        w.name("children").beginArray();
        for (TrieEdge edge : node.children()) {
            w.beginObject();
            w.name("label").value(edge.label());
            w.name("target");
            writeNode(w, edge.target(), false);
            w.endObject();
        }
        w.endArray();
        w.endObject();
    }
}
