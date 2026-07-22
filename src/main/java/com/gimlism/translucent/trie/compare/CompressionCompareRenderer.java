package com.gimlism.translucent.trie.compare;

import com.gimlism.translucent.trie.events.TrieSnapshot;
import com.gimlism.translucent.trie.viz.AsciiTrieRenderer;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders the two tries from a {@link CompressionCompareDemo.Comparison} side by side (fat
 * {@code StandardTrie} left, compressed {@code RadixTrie} right) under a savings banner, so the
 * nodes the radix compression removes are visible. Pure: no I/O, no colour. Reuses
 * {@link AsciiTrieRenderer} verbatim (its API is not widened); a per-tree panel is that renderer's
 * output with its {@code "trie: size=N"} header line swapped for a {@code standard (N)} / {@code radix (N)}
 * label. Columns when they fit within {@code maxWidth}; stacked otherwise.
 * Column widths are measured in {@code char} count, so the {@code ●} key-marker may render double-width
 * in some terminals and shift marked rows by one cell.
 */
public final class CompressionCompareRenderer {
    private static final int DEFAULT_MAX_WIDTH = 100;
    private static final int GAP = 3;
    private final AsciiTrieRenderer trees = new AsciiTrieRenderer();

    /** Render with the default width guard (~100 columns). */
    public String render(CompressionCompareDemo.Comparison c) {
        return render(c, DEFAULT_MAX_WIDTH);
    }

    /** Render, falling back from columns to stacked when the two panels would exceed {@code maxWidth}. */
    public String render(CompressionCompareDemo.Comparison c, int maxWidth) {
        List<String> left = panel("standard (" + c.standardNodes() + ")", c.standardSnapshot());
        List<String> right = panel("radix (" + c.radixNodes() + ")", c.radixSnapshot());
        String body = gutter(left) + width(right) <= maxWidth ? columns(left, right) : stacked(left, right);
        return banner(c) + "\n\n" + body;
    }

    private String banner(CompressionCompareDemo.Comparison c) {
        long pct = Math.round(100.0 * c.saved() / c.standardNodes());
        return "compression compare: {" + String.join(", ", c.keys()) + "}\n"
            + "  standard = " + c.standardNodes()
            + "   radix = " + c.radixNodes()
            + "   saved = " + c.saved() + " (" + pct + "%)";
    }

    /** A header label followed by the tree body, i.e. {@code renderTrie} minus its "trie: size=" line. */
    private List<String> panel(String header, TrieSnapshot snap) {
        String full = trees.renderTrie(snap, null);
        List<String> lines = new ArrayList<>();
        lines.add(header);
        // full = "trie: size=N\n<line>\n<line>..." ; drop the header line, keep the tree verbatim.
        for (String line : full.substring(full.indexOf('\n') + 1).split("\n", -1)) {
            lines.add(line);
        }
        return lines;
    }

    private int gutter(List<String> left) {
        return width(left) + GAP;
    }

    private int width(List<String> lines) {
        int w = 0;
        for (String l : lines) w = Math.max(w, l.length());
        return w;
    }

    private String columns(List<String> left, List<String> right) {
        int gutter = gutter(left);
        int n = Math.max(left.size(), right.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            String l = i < left.size() ? left.get(i) : "";
            String r = i < right.size() ? right.get(i) : "";
            if (r.isEmpty()) {
                sb.append(l);                                   // right exhausted: no trailing padding
            } else {
                sb.append(l).append(" ".repeat(gutter - l.length())).append(r);
            }
            if (i < n - 1) sb.append('\n');
        }
        return sb.toString();
    }

    private String stacked(List<String> left, List<String> right) {
        return withColonHeader(left) + "\n\n" + withColonHeader(right);
    }

    private String withColonHeader(List<String> panel) {
        StringBuilder sb = new StringBuilder(panel.get(0)).append(':');   // "standard (10):"
        for (int i = 1; i < panel.size(); i++) sb.append('\n').append(panel.get(i));
        return sb.toString();
    }
}
