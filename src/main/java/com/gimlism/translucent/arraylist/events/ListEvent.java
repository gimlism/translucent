package com.gimlism.translucent.arraylist.events;

/**
 * An immutable, self-contained record of a single list state change.
 *
 * <p><b>Event grammar per operation.</b> A single call can emit several events; the
 * logical marker ({@link Append}/{@link Insert}/{@link RemoveAt}) is the <em>terminal</em>
 * event, trailing its mechanical burst:
 * <ul>
 *   <li><b>append:</b> [{@code Grow →}] {@code Append}.</li>
 *   <li><b>set:</b> {@code Set}.</li>
 *   <li><b>insert (i &lt; size):</b> [{@code Grow →}] {@code Shift}* (high→low) {@code → Insert}.</li>
 *   <li><b>insert (i == size):</b> same as append.</li>
 *   <li><b>remove:</b> {@code Shift}* (low→high) {@code → RemoveAt}.</li>
 * </ul>
 */
public sealed interface ListEvent
        permits Append, Insert, Set, RemoveAt, Shift, Grow {
    /**
     * Whole-list snapshot at the moment this event was emitted. For most events this
     * is the settled state after the step; a {@link Shift} captures the array
     * <em>mid-slide</em> and a {@link Grow}'s {@code after} shows the new capacity
     * with the pending element not yet placed.
     */
    ListSnapshot after();
}
