package com.gimlism.translucent.treeset.events;

import com.gimlism.translucent.substrate.events.StructureEventListener;

/**
 * Synchronous consumer of a {@code TeachingTreeSet}'s event stream.
 *
 * <p><b>Reads narrate.</b> Unlike the other structures, a TreeSet emits
 * {@link Compare} frames during read operations ({@code contains}, the relative
 * navigators). The no-mutation-during-dispatch rule therefore applies to
 * read-narration events too: a listener must not mutate the set from within
 * {@code onEvent}, even one fired by a read — doing so would corrupt the in-progress
 * comparison walk (reads are not wrapped in the re-entrancy guard, so it would not be
 * caught). Read, record, or render — do not mutate, and do not throw.
 */
public interface SetEventListener extends StructureEventListener<SetEvent> {}
