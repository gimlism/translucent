/**
 * Consumer-side tools for comparing trie implementations. {@link TrieMetrics} counts nodes from a
 * public {@code TrieSnapshot} (touching neither data structure), and
 * {@link CompressionCompareDemo} quantifies how many nodes the radix trie's edge compression saves
 * over the standard one-char-per-edge trie on the same keys.
 */
package com.gimlism.translucent.trie.compare;
