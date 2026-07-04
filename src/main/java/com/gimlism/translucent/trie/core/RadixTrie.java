package com.gimlism.translucent.trie.core;

import com.gimlism.translucent.substrate.events.StructureEventListener;
import com.gimlism.translucent.trie.events.CreateNode;
import com.gimlism.translucent.trie.events.Descend;
import com.gimlism.translucent.trie.events.Put;
import com.gimlism.translucent.trie.events.SplitEdge;
import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieEvent;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A teaching radix (PATRICIA) trie: a {@link Map} by shared prefix, with compressed
 * {@code String}-labelled edges (single-child chains collapse into one multi-character
 * edge). Every mutation is observable through an immutable {@link TrieEvent} stream.
 * First consumer of the generic instrumentation substrate.
 */
public class RadixTrie<V> extends AbstractMap<String, V> {

    private final TrieNode<V> root = new TrieNode<>("");
    private int size;
    int modCount;

    private final List<StructureEventListener<TrieEvent>> listeners = new ArrayList<>();
    private boolean mutating;

    @Override
    public int size() {
        return size;
    }

    @Override
    public V get(Object key) {
        if (!(key instanceof String s)) return null;
        TrieNode<V> n = find(s);
        return (n != null && n.isKey) ? n.value : null;
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof String s)) return false;
        TrieNode<V> n = find(s);
        return n != null && n.isKey;
    }

    /** The node exactly at {@code key} (all edge labels consumed), or null if the path breaks. */
    private TrieNode<V> find(String key) {
        TrieNode<V> node = root;
        String s = key;
        while (!s.isEmpty()) {
            TrieNode<V> child = node.children.get(s.charAt(0));
            if (child == null || !s.startsWith(child.edgeLabel)) return null;
            node = child;
            s = s.substring(child.edgeLabel.length());
        }
        return node;
    }

    @Override
    public V put(String key, V value) {
        Objects.requireNonNull(key, "null keys not supported");
        beginMutation();
        try {
            TrieNode<V> node = root;
            String s = key;
            String path = "";
            while (true) {
                if (s.isEmpty()) {
                    V old = node.isKey ? node.value : null;
                    boolean newKey = !node.isKey;
                    node.isKey = true;
                    node.value = value;
                    if (newKey) size++;
                    modCount++;
                    emit(new Put(key, value, old, newKey, snapshot()));
                    return old;
                }
                char c = s.charAt(0);
                TrieNode<V> child = node.children.get(c);
                if (child == null) {
                    TrieNode<V> leaf = new TrieNode<>(s);
                    node.children.put(c, leaf);
                    node = leaf;
                    path += s;
                    emit(new CreateNode(s, path, snapshot()));
                    s = "";
                    continue;                            // -> Put on leaf
                }
                String label = child.edgeLabel;
                int p = commonPrefixLength(s, label);
                if (p == label.length()) {
                    node = child;
                    path += label;
                    s = s.substring(p);
                    emit(new Descend(label, path, snapshot()));
                    continue;
                }
                // split at p (1 <= p < label.length())
                String common = label.substring(0, p);
                TrieNode<V> mid = new TrieNode<>(common);
                child.edgeLabel = label.substring(p);
                node.children.put(c, mid);
                mid.children.put(child.edgeLabel.charAt(0), child);
                path += common;
                emit(new SplitEdge(label, common, path, snapshot()));
                if (p == s.length()) {
                    node = mid;
                    s = "";
                    continue;                            // -> Put on mid (key ends here)
                }
                String rest = s.substring(p);
                TrieNode<V> leaf = new TrieNode<>(rest);
                mid.children.put(rest.charAt(0), leaf);
                node = leaf;
                path += rest;
                emit(new CreateNode(rest, path, snapshot()));
                s = "";
                continue;                                // -> Put on leaf
            }
        } finally {
            mutating = false;
        }
    }

    private static int commonPrefixLength(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    // --- snapshot + dispatch ----------------------------------------------------

    /** Immutable whole-trie snapshot. */
    TrieSnapshot snapshot() {
        return new TrieSnapshot(snap(root), size);
    }

    private TrieNodeSnapshot snap(TrieNode<V> n) {
        List<TrieEdge> kids = new ArrayList<>(n.children.size());
        for (TrieNode<V> c : n.children.values()) kids.add(new TrieEdge(c.edgeLabel, snap(c)));
        return new TrieNodeSnapshot(n.isKey, n.isKey ? n.value : null, kids);
    }

    public void addListener(StructureEventListener<TrieEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(StructureEventListener<TrieEvent> listener) {
        listeners.remove(listener);
    }

    private void emit(TrieEvent event) {
        for (StructureEventListener<TrieEvent> listener : List.copyOf(listeners)) listener.onEvent(event);
    }

    private void beginMutation() {
        if (mutating) {
            throw new ConcurrentModificationException(
                "trie mutated from within an event listener; listeners may read the trie "
                + "but must not put/remove during event dispatch");
        }
        mutating = true;
    }

    @Override
    public Set<Map.Entry<String, V>> entrySet() {
        throw new UnsupportedOperationException("entrySet arrives in Task 3");
    }
}
