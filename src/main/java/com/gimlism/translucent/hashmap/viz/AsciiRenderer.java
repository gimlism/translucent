package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.BucketSnapshot;
import com.gimlism.translucent.hashmap.events.ChainSnapshot;
import com.gimlism.translucent.hashmap.events.Collision;
import com.gimlism.translucent.hashmap.events.EmptyBucket;
import com.gimlism.translucent.hashmap.events.EntrySnapshot;
import com.gimlism.translucent.hashmap.events.EventFormatter;
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
import com.gimlism.translucent.substrate.viz.EventRenderer;
import java.util.List;
import java.util.StringJoiner;

/** Pure renderer: turns immutable snapshots/events into ASCII text. No I/O. */
public final class AsciiRenderer implements EventRenderer<MapEvent> {
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

    /** Whole-map diagram; {@code highlightBucket} (−1 for none) is marked with {@code >}. */
    public String renderMap(MapSnapshot snap, int highlightBucket) {
        StringBuilder sb = new StringBuilder();
        sb.append("map: cap=").append(snap.capacity())
          .append(" size=").append(snap.size())
          .append(" threshold=").append(snap.threshold());
        List<BucketSnapshot> buckets = snap.buckets();
        for (int i = 0; i < buckets.size(); i++) {
            sb.append('\n').append(i == highlightBucket ? "> " : "  ")
              .append('[').append(i).append("] ").append(renderBucket(buckets.get(i)));
        }
        return sb.toString();
    }

    private String renderBucket(BucketSnapshot bucket) {
        return switch (bucket) {
            case EmptyBucket e -> "·";
            case ChainSnapshot c -> {
                StringJoiner sj = new StringJoiner(" -> ");
                for (EntrySnapshot en : c.entries()) sj.add(en.key() + "=" + en.value());
                yield sj.toString();
            }
            case TreeSnapshot t -> {
                StringBuilder sb = new StringBuilder("tree:");
                for (String line : renderTree(t.root()).split("\n", -1)) {
                    sb.append("\n      ").append(line);
                }
                yield sb.toString();
            }
        };
    }

    /** The event's one-line label (from {@link EventFormatter}) above the resulting map. */
    public String renderEvent(MapEvent e) {
        return EventFormatter.format(e) + "\n" + renderMap(e.after(), affectedBucket(e));
    }

    /** The bucket an event touched, or −1 for a whole-table Resize. */
    public static int affectedBucket(MapEvent e) {
        return switch (e) {
            case Put p -> p.bucketIndex();
            case Remove r -> r.bucketIndex();
            case Collision c -> c.bucketIndex();
            case Treeify t -> t.bucketIndex();
            case Untreeify u -> u.bucketIndex();
            case Rotation ro -> ro.bucketIndex();
            case Recolor rc -> rc.bucketIndex();
            case Resize rs -> -1;
        };
    }
}
