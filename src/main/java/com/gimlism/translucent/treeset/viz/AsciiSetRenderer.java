package com.gimlism.translucent.treeset.viz;

import com.gimlism.translucent.substrate.viz.ColorMode;
import com.gimlism.translucent.substrate.viz.EventRenderer;
import com.gimlism.translucent.treeset.events.Add;
import com.gimlism.translucent.treeset.events.Compare;
import com.gimlism.translucent.treeset.events.Recolor;
import com.gimlism.translucent.treeset.events.Remove;
import com.gimlism.translucent.treeset.events.Rotation;
import com.gimlism.translucent.treeset.events.SetEvent;
import com.gimlism.translucent.treeset.events.SetEventFormatter;
import com.gimlism.translucent.treeset.events.SetNodeSnapshot;
import com.gimlism.translucent.treeset.events.SetSnapshot;

/**
 * Pure renderer: turns immutable set snapshots/events into a sideways ASCII
 * red-black tree. No I/O. The binary, colour-carrying shape mirrors the map's
 * treeified-bin renderer applied to the whole set; a per-node highlight tracks
 * the element an event touched.
 */
public final class AsciiSetRenderer implements EventRenderer<SetEvent> {
    private static final String RED = "\u001b[31m";
    private static final String RESET = "\u001b[0m";

    private final ColorMode mode;

    public AsciiSetRenderer(ColorMode mode) {
        this.mode = mode;
    }

    /**
     * Header plus the sideways tree (right subtree above, node, left subtree below).
     * The node holding {@code highlight} (null / absent for none) is marked {@code > }.
     */
    public String renderSet(SetSnapshot snap, Object highlight) {
        StringBuilder sb = new StringBuilder("set: size=").append(snap.size());
        appendTree(sb, snap.root(), 0, "", highlight);
        return sb.toString();
    }

    private void appendTree(StringBuilder sb, SetNodeSnapshot n, int depth,
                            String connector, Object highlight) {
        if (n == null) return;
        appendTree(sb, n.right(), depth + 1, "┌─ ", highlight);
        boolean hi = highlight != null && highlight.equals(n.element());
        sb.append('\n').append(hi ? "> " : "  ")
          .append("    ".repeat(depth)).append(connector)
          .append(node(n.element(), n.red()));
        appendTree(sb, n.left(), depth + 1, "└─ ", highlight);
    }

    /** A node label: {@code elem(R)}/{@code elem(B)} in PLAIN, or ANSI-red elem in ANSI. */
    private String node(Object element, boolean red) {
        if (mode == ColorMode.PLAIN) {
            return element + (red ? "(R)" : "(B)");
        }
        return red ? RED + element + RESET : String.valueOf(element);
    }

    /** The event's one-line caption (from {@link SetEventFormatter}) above the resulting set. */
    @Override
    public String renderEvent(SetEvent e) {
        return SetEventFormatter.format(e) + "\n" + renderSet(e.after(), affectedElement(e));
    }

    /**
     * The element an event highlights, or {@code null} for none. Compare marks the
     * visited node (a moving cursor over live nodes); Add the new element; Rotation
     * the pivot; Recolor the recoloured node. Remove's element is gone from
     * {@code after()} and the event carries no successor, so a Remove frame
     * highlights nothing (the caption already names what left).
     */
    public static Object affectedElement(SetEvent e) {
        return switch (e) {
            case Compare c -> c.element();
            case Add a -> a.element();
            case Rotation ro -> ro.pivot();
            case Recolor rc -> rc.element();
            case Remove rm -> null;
        };
    }
}
