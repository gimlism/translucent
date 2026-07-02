package com.gimlism.translucent.hashmap.events;

/** Immutable copy of a red-black tree node, including colour and children. */
public record TreeNodeSnapshot(
        Object key, Object value, Color color,
        TreeNodeSnapshot left, TreeNodeSnapshot right) {}
