package com.gimlism.translucent.hashmap.events;

import com.gimlism.translucent.substrate.events.StructureEventListener;

/**
 * Consumer of the map's event stream — a {@link StructureEventListener} named for the
 * map's concrete {@link MapEvent} vocabulary. Invoked synchronously during a mutation
 * (sometimes mid-operation, e.g. between the rotations of a treeify or a red-black
 * delete).
 *
 * <p>A listener may freely <em>read</em> the map and register/unregister listeners,
 * but must <strong>not</strong> structurally mutate it (put/remove) from within
 * {@link #onEvent}: the map may be partway through an operation, so a re-entrant
 * mutation is rejected with a {@link java.util.ConcurrentModificationException}.
 */
@FunctionalInterface
public interface MapEventListener extends StructureEventListener<MapEvent> {}
