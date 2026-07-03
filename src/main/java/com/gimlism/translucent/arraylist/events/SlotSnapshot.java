package com.gimlism.translucent.arraylist.events;

/** One backing-array slot at snapshot time: filled with an element, or unused capacity. */
public sealed interface SlotSnapshot permits FilledSlot, EmptySlot {}
