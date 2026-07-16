package com.gimlism.translucent.treeset.events;

import com.gimlism.translucent.substrate.events.StructureEvent;

/**
 * An immutable record of a single {@code TeachingTreeSet} step. The vocabulary blends
 * the trie's node-link narration with the map's red-black rebalance events:
 * <ul>
 *   <li><b>add:</b> {@code Compare* -> Add -> (Rotation|Recolor)*} — the comparison walk,
 *       then the new element (linked before rebalancing), then any rebalance steps.</li>
 *   <li><b>remove:</b> {@code Compare* -> (Rotation|Recolor)* -> Remove} — the walk to the
 *       node, the rebalance, then the terminal removal marker.</li>
 *   <li><b>contains / navigation:</b> {@code Compare*} only (traversal narration; no
 *       structural change).</li>
 * </ul>
 * Unlike the trie there is no {@code CreateNode}: every node <em>is</em> an element,
 * so {@link Add} is the node creation.
 */
public sealed interface SetEvent extends StructureEvent
        permits Compare, Add, Remove, Rotation, Recolor {
    /** Whole-set snapshot at emission (covariant narrowing of {@link StructureEvent#after()}). */
    @Override
    SetSnapshot after();
}
