package com.gimlism.translucent.hashmap.events;

/** A table slot holding a red-black tree, referenced by its root. */
public record TreeSnapshot(TreeNodeSnapshot root) implements BucketSnapshot {}
