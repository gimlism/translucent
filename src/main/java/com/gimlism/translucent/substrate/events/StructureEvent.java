package com.gimlism.translucent.substrate.events;

/**
 * Marker supertype of every structure's immutable event. Each event carries a
 * whole-state {@link StructureSnapshot} taken at emission. Concrete event types
 * (the sealed {@code MapEvent}/{@code ListEvent} vocabularies) narrow {@link #after()}
 * covariantly to their own snapshot type.
 *
 * <p>An event is not required to record a <em>structural</em> change: a structure may also
 * emit <b>traversal (narration) events</b> whose {@code after()} snapshot is unchanged from
 * the previous frame but whose "where" pointer advances — e.g. the trie's {@code Descend},
 * which walks a shared prefix. These give a renderer the path-shaped focus to animate the
 * walk. What each vocabulary emits is documented on that vocabulary.
 */
public interface StructureEvent {
    StructureSnapshot after();
}
