package com.gimlism.translucent.trie.core;

import com.gimlism.translucent.substrate.events.StructureEventListener;
import com.gimlism.translucent.trie.events.CreateNode;
import com.gimlism.translucent.trie.events.Descend;
import com.gimlism.translucent.trie.events.MergeEdge;
import com.gimlism.translucent.trie.events.Prune;
import com.gimlism.translucent.trie.events.Put;
import com.gimlism.translucent.trie.events.Remove;
import com.gimlism.translucent.trie.events.SplitEdge;
import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieEvent;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A teaching radix (PATRICIA) trie: a {@link Map} by shared prefix, with compressed
 * {@code String}-labelled edges (single-child chains collapse into one multi-character
 * edge). Every mutation is observable through an immutable {@link TrieEvent} stream.
 * First consumer of the generic instrumentation substrate.
 *
 * <p><b>Known limitation (this slice):</b> entries returned by {@link #entrySet()} are
 * immutable snapshots, so {@link java.util.Map.Entry#setValue(Object)} throws
 * {@link UnsupportedOperationException} rather than writing through. Update via
 * {@link #put(String, Object)} instead. (This fails fast rather than silently losing a
 * write; a later slice may route entry updates through the mutators to emit events.)
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
                    if (newKey) {           // a value-replace is non-structural: don't invalidate iterators
                        size++;
                        modCount++;
                    }
                    emit(new Put(key, value, old, newKey, key, snapshot()));
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

    @Override
    public V remove(Object key) {
        if (!(key instanceof String k)) return null;
        beginMutation();
        try {
            TrieNode<V> parent = null;
            TrieNode<V> node = root;
            String s = k;
            while (!s.isEmpty()) {
                TrieNode<V> child = node.children.get(s.charAt(0));
                if (child == null || !s.startsWith(child.edgeLabel)) return null; // path breaks
                parent = node;
                node = child;
                s = s.substring(child.edgeLabel.length());
            }
            if (!node.isKey) return null;                 // node exists but isn't a key
            V old = node.value;
            node.isKey = false;
            node.value = null;
            size--;
            modCount++;
            emit(new Remove(k, old, k, snapshot()));

            if (node == root) return old;                 // removed the "" key; the root stays
            if (node.children.size() >= 2) return old;    // still a branch
            if (node.children.size() == 1) {              // non-key with one child -> absorb it
                String start = k.substring(0, k.length() - node.edgeLabel.length());
                mergeWithChild(node, start);
                return old;
            }
            // leaf: prune from its parent
            parent.children.remove(node.edgeLabel.charAt(0));
            emit(new Prune(node.edgeLabel, k, snapshot()));
            if (parent != root && !parent.isKey && parent.children.size() == 1) {
                String parentEnd = k.substring(0, k.length() - node.edgeLabel.length());
                String parentStart = parentEnd.substring(0, parentEnd.length() - parent.edgeLabel.length());
                mergeWithChild(parent, parentStart);
            }
            return old;
        } finally {
            mutating = false;
        }
    }

    /** Absorb {@code node}'s sole remaining child into it (concatenate labels). {@code start} = path to node's start. */
    private void mergeWithChild(TrieNode<V> node, String start) {
        TrieNode<V> child = node.children.firstEntry().getValue();
        node.children.clear();
        node.edgeLabel = node.edgeLabel + child.edgeLabel;
        node.isKey = child.isKey;
        node.value = child.value;
        node.children.putAll(child.children);
        emit(new MergeEdge(node.edgeLabel, start + node.edgeLabel, snapshot()));
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
        return new AbstractSet<>() {
            @Override public int size() { return size; }
            @Override public Iterator<Map.Entry<String, V>> iterator() { return new EntryIterator(); }
        };
    }

    /** All key entries in lexicographic order (sorted DFS; children are sorted by first char). */
    private List<Map.Entry<String, V>> collect() {
        List<Map.Entry<String, V>> out = new ArrayList<>();
        collect(root, "", out);
        return out;
    }

    private void collect(TrieNode<V> n, String prefix, List<Map.Entry<String, V>> out) {
        if (n.isKey) out.add(new AbstractMap.SimpleImmutableEntry<>(prefix, n.value));
        for (TrieNode<V> c : n.children.values()) collect(c, prefix + c.edgeLabel, out);
    }

    /** Keys with the given prefix, lexicographically (an unmodifiable snapshot). */
    public List<String> keysWithPrefix(String prefix) {
        Objects.requireNonNull(prefix, "null prefix");
        TrieNode<V> node = root;
        String s = prefix;
        String at = "";
        while (!s.isEmpty()) {
            TrieNode<V> child = node.children.get(s.charAt(0));
            if (child == null) return List.of();
            String label = child.edgeLabel;
            int p = commonPrefixLength(s, label);
            if (p == s.length()) {          // prefix ends inside/at this edge
                node = child;
                at += label;
                break;
            }
            if (p < label.length()) return List.of();   // diverges -> no matches
            node = child;
            at += label;
            s = s.substring(label.length());
        }
        List<String> keys = new ArrayList<>();
        collectKeys(node, at, keys);
        return Collections.unmodifiableList(keys);
    }

    private void collectKeys(TrieNode<V> n, String prefix, List<String> out) {
        if (n.isKey) out.add(prefix);
        for (TrieNode<V> c : n.children.values()) collectKeys(c, prefix + c.edgeLabel, out);
    }

    private final class EntryIterator implements Iterator<Map.Entry<String, V>> {
        private final Iterator<Map.Entry<String, V>> it = collect().iterator();
        private int expectedModCount = modCount;
        private Map.Entry<String, V> last;

        @Override public boolean hasNext() { return it.hasNext(); }

        @Override public Map.Entry<String, V> next() {
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            last = it.next();
            return last;
        }

        @Override public void remove() {
            if (last == null) throw new IllegalStateException();
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            RadixTrie.this.remove(last.getKey());
            expectedModCount = modCount;
            last = null;
        }
    }
}
