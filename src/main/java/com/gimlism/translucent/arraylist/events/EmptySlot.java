package com.gimlism.translucent.arraylist.events;

/** An unused capacity slot — distinct from a {@link FilledSlot} holding {@code null}. */
public record EmptySlot() implements SlotSnapshot {}
