package com.gimlism.translucent.trie.core;

import java.util.List;
import java.util.Map;

/**
 * The one capability a trie offers beyond {@link Map}: listing every key under a prefix. Both tries
 * already declare exactly this method, so implementing it moves no code — the interface simply names
 * what {@code TrieCommandInterpreter} needs, so the interpreter can drive either trie without naming
 * one of them.
 *
 * <p><b>This is deliberately NOT a shared-implementation extract.</b> PR #41's whole-branch review
 * ruled the {@code AbstractTrie} transport extract not actionable: the ~70% plumbing parallel between
 * {@link RadixTrie} and {@link StandardTrie} is justified duplication for a teaching library, because
 * each structure must read standalone and their node types differ fundamentally. That ruling stands
 * and both tries keep every line they have. Without this note, a reader (or a Copilot pass) sees an
 * interface over two tries and reasonably concludes the ruled-out extract was started and abandoned.
 *
 * <p>Note also that this is the repo's first <i>structure-capability</i> interface — every other
 * interface here ({@code TrieEventListener}, {@code StructureEventListener}, {@code EventRenderer})
 * is an observer interface, and the other three command interpreters take a concrete structure type.
 * The deviation is justified by tries being the only structure with two implementations.
 */
public interface PrefixMap<V> extends Map<String, V> {

    /** Every key beginning with {@code prefix}, or every key when {@code prefix} is empty. */
    List<String> keysWithPrefix(String prefix);
}
