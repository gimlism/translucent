/**
 * The concrete, sealed event vocabulary shared by both trie implementations
 * ({@link com.gimlism.translucent.trie.core.RadixTrie} and
 * {@link com.gimlism.translucent.trie.core.StandardTrie}), and its N-ary, String-labelled
 * snapshot model. Plugs into {@link com.gimlism.translucent.substrate}: {@link
 * com.gimlism.translucent.trie.events.TrieEvent} extends
 * {@link com.gimlism.translucent.substrate.events.StructureEvent} and
 * {@link com.gimlism.translucent.trie.events.TrieSnapshot} implements
 * {@link com.gimlism.translucent.substrate.events.StructureSnapshot}.
 */
package com.gimlism.translucent.trie.events;
