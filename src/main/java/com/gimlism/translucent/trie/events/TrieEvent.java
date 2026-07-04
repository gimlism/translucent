package com.gimlism.translucent.trie.events;

import com.gimlism.translucent.substrate.events.StructureEvent;

/**
 * An immutable, self-contained record of a single radix-trie state change.
 *
 * <p><b>Event grammar per operation.</b> The logical {@link Put}/{@link Remove} marker
 * anchors the operation; fine-grained structural events surround it:
 * <ul>
 *   <li><b>insert:</b> {@code Descend* → [SplitEdge] → [CreateNode] → Put} — {@code Put}
 *       is the terminal event, trailing the walk/split/create burst.</li>
 *   <li><b>remove:</b> {@code Remove → [Prune] → [MergeEdge]} — {@code Remove} leads,
 *       then the compression cleanup.</li>
 * </ul>
 */
public sealed interface TrieEvent extends StructureEvent
        permits Descend, CreateNode, SplitEdge, Put, Remove, MergeEdge, Prune {
    /** Whole-trie snapshot at emission (covariant narrowing of {@link StructureEvent#after()}). */
    TrieSnapshot after();
}
