/**
 * The shared instrumentation substrate: structure-agnostic event model
 * ({@link com.gimlism.translucent.substrate.events.StructureEvent} /
 * {@link com.gimlism.translucent.substrate.events.StructureSnapshot}) and transport
 * ({@link com.gimlism.translucent.substrate.events.StructureEventListener},
 * {@link com.gimlism.translucent.substrate.events.RecordingListener}). Each teaching
 * structure keeps its own concrete, sealed event vocabulary and simply plugs into this.
 */
package com.gimlism.translucent.substrate.events;
