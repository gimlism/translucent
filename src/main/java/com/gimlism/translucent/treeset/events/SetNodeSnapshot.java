package com.gimlism.translucent.treeset.events;

/**
 * Immutable snapshot of one binary red-black tree node: its {@code element}, its
 * colour, and its two children (null when absent). The binary, colour-carrying shape
 * is the counterpart of the trie's N-ary {@code TrieNodeSnapshot}.
 */
public record SetNodeSnapshot(Object element, boolean red, SetNodeSnapshot left, SetNodeSnapshot right) {}
