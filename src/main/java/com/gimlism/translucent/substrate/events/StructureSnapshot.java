package com.gimlism.translucent.substrate.events;

/**
 * Marker supertype of every structure's immutable whole-state snapshot
 * (e.g. {@code MapSnapshot}, {@code ListSnapshot}). Intentionally a pure marker —
 * no {@code capacity()}/{@code size()} accessors — so it imposes no array-shaped
 * assumption a future structure (e.g. a Trie) could not satisfy.
 */
public interface StructureSnapshot {}
