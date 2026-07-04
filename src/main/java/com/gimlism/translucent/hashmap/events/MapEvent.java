package com.gimlism.translucent.hashmap.events;

import com.gimlism.translucent.substrate.events.StructureEvent;

/**
 * An immutable, self-contained record of a single map state change.
 *
 * <p><b>Event grammar per operation.</b> A single {@code put}/{@code remove}
 * call can emit several events. The {@link Put}/{@link Remove} marks where the
 * logical operation lands, but it does <em>not</em> always come first:
 * <ul>
 *   <li><b>chain put:</b> {@code Put} [{@code → Collision}] [{@code → Treeify →}
 *       ({@code Rotation}|{@code Recolor})*] — {@code Put} <em>leads</em>.</li>
 *   <li><b>tree-bin put:</b> ({@code Rotation}|{@code Recolor})* {@code → Put} —
 *       {@code Put} is the <em>terminal</em> event, trailing its own rebalancing
 *       burst (emitting it first would show a key already driving rotations,
 *       while the snapshot already counts it in {@code size}).</li>
 *   <li><b>chain remove:</b> {@code Remove}.</li>
 *   <li><b>tree-bin remove:</b> [{@code Untreeify} | ({@code Rotation}|{@code Recolor})*]
 *       {@code → Remove} — {@code Remove} is the <em>terminal</em> event.</li>
 *   <li><b>resize:</b> a single {@link Resize} carrying both before and after.</li>
 * </ul>
 */
public sealed interface MapEvent extends StructureEvent
        permits Put, Remove, Collision, Resize, Treeify, Untreeify, Rotation, Recolor {
    /**
     * Whole-map snapshot at the moment this event was emitted.
     *
     * <p>For most events this is the map after the step settled. Two exceptions
     * follow the announce/settle semantics documented on their types:
     * {@link Treeify} is emitted <em>before</em> conversion (its {@code after()}
     * still shows the bucket as a chain), and the balancing events of a treeify
     * or tree-bin put/remove capture the tree <em>mid-assembly</em>.
     */
    MapSnapshot after();
}
