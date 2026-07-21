package com.gimlism.translucent.hashmap.events;

import com.gimlism.translucent.substrate.rbtree.Color;

/** Immutable copy of a red-black tree node, including colour and children. */
public record TreeNodeSnapshot(
        Object key, Object value, Color color,
        TreeNodeSnapshot left, TreeNodeSnapshot right) {}
