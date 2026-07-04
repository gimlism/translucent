package com.gimlism.translucent.arraylist.events;

/** An unused capacity slot — distinct from a {@link FilledSlot} holding {@code null}. */
public record EmptySlot() implements SlotSnapshot {
    /** Shared instance — the record is a stateless value, so snapshots reuse one rather than reallocating. */
    public static final EmptySlot INSTANCE = new EmptySlot();
}
