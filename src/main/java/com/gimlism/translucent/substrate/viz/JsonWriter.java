package com.gimlism.translucent.substrate.viz;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A minimal, hand-rolled JSON writer — the generic *syntax* half of the web-viz seam
 * (escaping, comma placement, object/array bracketing). It imposes no structure-specific
 * knowledge, so each structure's per-structure serializer reuses it. Not a general-purpose
 * library: it writes exactly the value kinds the visualizers need.
 *
 * <p>Fluent and stateful: interleave {@link #beginObject}/{@link #name}/{@code value}/
 * {@link #endObject} (and the array forms); {@link #toString} returns the accumulated JSON.
 */
public final class JsonWriter {
    private final StringBuilder out = new StringBuilder();
    /** Element count of each open container (top = innermost); drives comma placement. */
    private final Deque<Integer> counts = new ArrayDeque<>();
    /** True immediately after {@link #name}: the next value fills that member, so it emits no comma. */
    private boolean expectingValue = false;

    /** U+2028 LINE SEPARATOR — valid unescaped in JSON, but a line terminator in pre-ES2019 JS. */
    private static final char LINE_SEPARATOR = ' ';
    /** U+2029 PARAGRAPH SEPARATOR — same hazard as {@link #LINE_SEPARATOR} for inline-script embedding. */
    private static final char PARAGRAPH_SEPARATOR = ' ';

    public JsonWriter beginObject() { pre(); out.append('{'); counts.push(0); return this; }
    public JsonWriter endObject()   { out.append('}'); counts.pop(); post(); return this; }
    public JsonWriter beginArray()  { pre(); out.append('['); counts.push(0); return this; }
    public JsonWriter endArray()    { out.append(']'); counts.pop(); post(); return this; }

    public JsonWriter name(String name) {
        if (!counts.isEmpty() && counts.peek() > 0) out.append(',');
        writeString(name);
        out.append(':');
        expectingValue = true;
        return this;
    }

    public JsonWriter value(String s) { pre(); if (s == null) out.append("null"); else writeString(s); post(); return this; }
    public JsonWriter value(long n)   { pre(); out.append(n); post(); return this; }
    public JsonWriter value(boolean b){ pre(); out.append(b); post(); return this; }
    public JsonWriter nullValue()     { pre(); out.append("null"); post(); return this; }

    /** Separator before a value/opener: a name already emitted the comma; array elements need one. */
    private void pre() {
        if (expectingValue) { expectingValue = false; return; }
        if (!counts.isEmpty() && counts.peek() > 0) out.append(',');
    }

    /** Count the just-written value in its enclosing container. */
    private void post() {
        if (!counts.isEmpty()) counts.push(counts.pop() + 1);
    }

    private void writeString(String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                // '<' can't form </script> and terminate the inline <script> the JSON is embedded in.
                case '<'  -> out.append("\\u003c");
                default -> {
                    // Control chars, plus U+2028/U+2029 which are JS (pre-ES2019) line terminators.
                    if (c < 0x20 || c == LINE_SEPARATOR || c == PARAGRAPH_SEPARATOR) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
