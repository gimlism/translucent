package com.gimlism.translucent.trie.viz;

import com.gimlism.translucent.substrate.viz.EventRenderer;
import com.gimlism.translucent.trie.events.CreateNode;
import com.gimlism.translucent.trie.events.Descend;
import com.gimlism.translucent.trie.events.MergeEdge;
import com.gimlism.translucent.trie.events.Prune;
import com.gimlism.translucent.trie.events.Put;
import com.gimlism.translucent.trie.events.Remove;
import com.gimlism.translucent.trie.events.SplitEdge;
import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieEvent;
import com.gimlism.translucent.trie.events.TrieEventFormatter;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;

/**
 * Pure renderer: turns immutable trie snapshots/events into a top-down indented ASCII tree.
 * No I/O, no colour. The N-ary, {@code String}-labelled shape (variable fan-out, edge labels,
 * a path-shaped highlight) is what the map's sideways binary tree could not express.
 */
public final class AsciiTrieRenderer implements EventRenderer<TrieEvent> {

    /**
     * Header plus the indented N-ary tree; the node at {@code highlightPath} (null for none) is
     * prefixed {@code > }. A path that matches no live node simply highlights nothing.
     */
    public String renderTrie(TrieSnapshot snap, String highlightPath) {
        StringBuilder sb = new StringBuilder("trie: size=").append(snap.size());
        appendNode(sb, "", snap.root(), "", 0, highlightPath, true);
        return sb.toString();
    }

    private void appendNode(StringBuilder sb, String label, TrieNodeSnapshot node,
                            String pathSoFar, int depth, String highlightPath, boolean root) {
        boolean hi = highlightPath != null && pathSoFar.equals(highlightPath);
        sb.append('\n').append(hi ? "> " : "  ").append("  ".repeat(depth));
        sb.append(root ? "(root)" : "\"" + label + "\"");
        if (node.key()) sb.append(" ●=").append(node.value());
        for (TrieEdge e : node.children()) {
            appendNode(sb, e.label(), e.target(), pathSoFar + e.label(), depth + 1, highlightPath, false);
        }
    }

    /** The event's one-line label (from {@link TrieEventFormatter}) above the resulting tree. */
    public String renderEvent(TrieEvent e) {
        return TrieEventFormatter.format(e) + "\n" + renderTrie(e.after(), affectedPath(e));
    }

    /**
     * The path (root-to-node label concatenation) of the node to highlight for an event.
     * Usually the event's own {@code path}; for a {@link Prune} that node has just been deleted,
     * so we highlight its <em>parent</em> (the node that lost the child, which survives in
     * {@code after()}) — {@code path} minus the pruned edge label.
     */
    public static String affectedPath(TrieEvent e) {
        return switch (e) {
            case Descend d -> d.path();
            case CreateNode c -> c.path();
            case SplitEdge s -> s.path();
            case Put p -> p.path();
            case Remove r -> r.path();
            case MergeEdge m -> m.path();
            case Prune pr -> pr.path().substring(0, pr.path().length() - pr.label().length());
        };
    }
}
