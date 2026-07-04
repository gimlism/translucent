package com.gimlism.translucent.substrate.events;

/**
 * Marker supertype of every structure's immutable event. Each event carries a
 * whole-state {@link StructureSnapshot} taken at emission. Concrete event types
 * (the sealed {@code MapEvent}/{@code ListEvent} vocabularies) narrow {@link #after()}
 * covariantly to their own snapshot type.
 */
public interface StructureEvent {
    StructureSnapshot after();
}
