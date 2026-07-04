package com.gimlism.translucent.arraylist.viz;

import com.gimlism.translucent.arraylist.events.Append;
import com.gimlism.translucent.arraylist.events.EmptySlot;
import com.gimlism.translucent.arraylist.events.FilledSlot;
import com.gimlism.translucent.arraylist.events.Grow;
import com.gimlism.translucent.arraylist.events.Insert;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListEventFormatter;
import com.gimlism.translucent.arraylist.events.ListSnapshot;
import com.gimlism.translucent.arraylist.events.RemoveAt;
import com.gimlism.translucent.arraylist.events.Set;
import com.gimlism.translucent.arraylist.events.Shift;
import com.gimlism.translucent.arraylist.events.SlotSnapshot;
import java.util.List;
import java.util.StringJoiner;

/** Pure renderer: turns immutable list snapshots/events into ASCII text. No I/O, no colour. */
public final class AsciiListRenderer {

    /** Header line plus a horizontal cells row; {@code highlightIndex} (−1 for none) is wrapped {@code >x<}. */
    public String renderList(ListSnapshot snap, int highlightIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append("list: cap=").append(snap.capacity()).append(" size=").append(snap.size());
        StringJoiner cells = new StringJoiner("|", "[", "]");
        List<SlotSnapshot> slots = snap.slots();
        for (int i = 0; i < slots.size(); i++) {
            String content = switch (slots.get(i)) {
                case FilledSlot f -> String.valueOf(f.element());
                case EmptySlot e -> "·";
            };
            cells.add(i == highlightIndex ? ">" + content + "<" : " " + content + " ");
        }
        return sb.append('\n').append(cells).toString();
    }

    /** The event's one-line label (from {@link ListEventFormatter}) above the resulting row. */
    public String renderEvent(ListEvent e) {
        return ListEventFormatter.format(e) + "\n" + renderList(e.after(), affectedIndex(e));
    }

    /** The slot an event touched, or −1 for a whole-array {@link Grow}. */
    public static int affectedIndex(ListEvent e) {
        return switch (e) {
            case Append a -> a.index();
            case Insert in -> in.index();
            case Set s -> s.index();
            case RemoveAt r -> r.index();
            case Shift sh -> sh.toIndex();   // where the element landed
            case Grow g -> -1;
        };
    }
}
