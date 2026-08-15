package com.gimlism.translucent.trie.events;

import com.gimlism.translucent.substrate.events.StructureEvent;

/**
 * An immutable, self-contained record of a single trie step — the event vocabulary shared by
 * both {@link com.gimlism.translucent.trie.core.RadixTrie} and
 * {@link com.gimlism.translucent.trie.core.StandardTrie}.
 *
 * <p><b>Event grammar per operation.</b> The logical {@link Put}/{@link Remove} marker
 * anchors the operation; {@link Descend} <em>narrates</em> the walk down the shared prefix
 * (a traversal event — its {@code after()} is unchanged, its {@code path} advances), and the
 * remaining fine-grained events record structural changes. The two implementations share this
 * vocabulary but not this grammar: a radix trie splits and merges compressed edges, so its
 * {@link SplitEdge}/{@link MergeEdge} fire at most once per operation; a standard trie stores
 * one character per node, so it grows or unwinds a whole chain instead, and never emits
 * {@code SplitEdge} or {@code MergeEdge} at all.
 * <ul>
 *   <li><b>insert, radix trie:</b> {@code Descend* → [SplitEdge] → [CreateNode] → Put} —
 *       {@code Put} is the terminal event, trailing the walk/split/create burst.</li>
 *   <li><b>insert, standard trie:</b> {@code Descend* → CreateNode* → Put} — the walk down
 *       existing nodes is followed by growing a fresh chain of one node per remaining
 *       character; no edge is ever split.</li>
 *   <li><b>remove, radix trie:</b> {@code Descend* → Remove → [Prune] → [MergeEdge]} — the
 *       walk is narrated (only for a removal that proceeds; an absent-key remove is silent),
 *       then the {@code Remove} marker, then the compression cleanup.</li>
 *   <li><b>remove, standard trie:</b> {@code Descend* → Remove → Prune*} — the walk is
 *       narrated the same way, then {@code Remove}, then a prune cascade of zero or more
 *       nodes climbing back toward the root as each newly-childless, non-key node is
 *       unhooked; there is no merge step to follow it.</li>
 * </ul>
 */
public sealed interface TrieEvent extends StructureEvent
        permits Descend, CreateNode, SplitEdge, Put, Remove, MergeEdge, Prune {
    /** Whole-trie snapshot at emission (covariant narrowing of {@link StructureEvent#after()}). */
    TrieSnapshot after();
}
