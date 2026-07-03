package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.TreeNodeSnapshot;

/** Pure renderer: turns immutable snapshots/events into ASCII text. No I/O. */
public final class AsciiRenderer {
    private final Palette palette;

    public AsciiRenderer(Palette palette) {
        this.palette = palette;
    }

    /** Sideways red-black tree: right-subtree above, node, left-subtree below. */
    public String renderTree(TreeNodeSnapshot root) {
        StringBuilder sb = new StringBuilder();
        appendTree(sb, root, 0, "");
        // drop the trailing newline appendTree leaves
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private void appendTree(StringBuilder sb, TreeNodeSnapshot n, int depth, String connector) {
        if (n == null) return;
        appendTree(sb, n.right(), depth + 1, "┌─ ");
        sb.append("    ".repeat(depth)).append(connector)
          .append(palette.node(n.key(), n.color())).append('\n');
        appendTree(sb, n.left(), depth + 1, "└─ ");
    }
}
