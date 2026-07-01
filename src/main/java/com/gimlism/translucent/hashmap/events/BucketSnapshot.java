package com.gimlism.translucent.hashmap.events;

/** A single table slot at snapshot time: empty, a chain, or a tree. */
public sealed interface BucketSnapshot
        permits EmptyBucket, ChainSnapshot, TreeSnapshot {}
