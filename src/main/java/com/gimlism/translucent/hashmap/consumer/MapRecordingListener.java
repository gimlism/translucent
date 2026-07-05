package com.gimlism.translucent.hashmap.consumer;

import com.gimlism.translucent.hashmap.events.MapEvent;

/**
 * Records {@link MapEvent}s — the map-typed
 * {@link com.gimlism.translucent.substrate.events.RecordingListener}. Kept as a named
 * subclass so existing {@code new MapRecordingListener()} call sites read naturally.
 */
public class MapRecordingListener extends com.gimlism.translucent.substrate.events.RecordingListener<MapEvent> {}
