package com.gimlism.translucent.trie.events;

import com.gimlism.translucent.substrate.events.StructureEvent;

/**
 * An immutable, self-contained record of a single radix-trie step.
 *
 * <p><b>Event grammar per operation.</b> The logical {@link Put}/{@link Remove} marker
 * anchors the operation; {@link Descend} <em>narrates</em> the walk down the shared prefix
 * (a traversal event — its {@code after()} is unchanged, its {@code path} advances), and the
 * remaining fine-grained events record structural changes:
 * <ul>
 *   <li><b>insert:</b> {@code Descend* → [SplitEdge] → [CreateNode] → Put} — {@code Put}
 *       is the terminal event, trailing the walk/split/create burst.</li>
 *   <li><b>remove:</b> {@code Descend* → Remove → [Prune] → [MergeEdge]} — the walk is
 *       narrated (only for a removal that proceeds; an absent-key remove is silent), then the
 *       {@code Remove} marker, then the compression cleanup.</li>
 * </ul>
 */
public sealed interface TrieEvent extends StructureEvent
        permits Descend, CreateNode, SplitEdge, Put, Remove, MergeEdge, Prune {
    /** Whole-trie snapshot at emission (covariant narrowing of {@link StructureEvent#after()}). */
    TrieSnapshot after();
}
