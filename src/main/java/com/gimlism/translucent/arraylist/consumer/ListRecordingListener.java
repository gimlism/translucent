package com.gimlism.translucent.arraylist.consumer;

import com.gimlism.translucent.arraylist.events.ListEvent;

/**
 * Records {@link ListEvent}s — the list-typed
 * {@link com.gimlism.translucent.substrate.events.RecordingListener}. Kept as a named
 * subclass so existing {@code new ListRecordingListener()} call sites read naturally.
 */
public class ListRecordingListener extends com.gimlism.translucent.substrate.events.RecordingListener<ListEvent> {}
