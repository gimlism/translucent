package com.gimlism.translucent.trie.core;

import com.gimlism.translucent.substrate.events.EventDispatcher;
import com.gimlism.translucent.substrate.events.StructureEventListener;
import com.gimlism.translucent.trie.events.CreateNode;
import com.gimlism.translucent.trie.events.Descend;
import com.gimlism.translucent.trie.events.Prune;
import com.gimlism.translucent.trie.events.Put;
import com.gimlism.translucent.trie.events.Remove;
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
 * A teaching <em>standard</em> trie: a {@link Map} keyed by shared prefix with exactly one
 * character per edge (no compression). It exists to be measured against {@link RadixTrie} on
 * the same keys — every single-child chain that the radix trie collapses into one edge is,
 * here, a chain of one-character nodes. Every mutation is observable through the shared
 * immutable {@link TrieEvent} stream, emitting only the compression-free subset
 * ({@link Descend}, {@link CreateNode}, {@link Put}, and — on remove — {@code Remove}/{@code Prune});
 * it never emits {@code SplitEdge}/{@code MergeEdge}.
 *
 * <p><b>Known limitation (this slice):</b> entries returned by {@link #entrySet()} are immutable
 * snapshots, so {@link java.util.Map.Entry#setValue(Object)} throws
 * {@link UnsupportedOperationException}; update via {@link #put(String, Object)} instead.
 */
public class StandardTrie<V> extends AbstractMap<String, V> {

    private final StandardTrieNode<V> root = new StandardTrieNode<>();
    private int size;
    int modCount;

    private final EventDispatcher<TrieEvent> dispatcher = new EventDispatcher<>("standard-trie");

    @Override
    public int size() {
        return size;
    }

    @Override
    public V get(Object key) {
        if (!(key instanceof String s)) return null;
        StandardTrieNode<V> n = find(s);
        return (n != null && n.isKey) ? n.value : null;
    }

    @Override
    public boolean containsKey(Object key) {
        if (!(key instanceof String s)) return false;
        StandardTrieNode<V> n = find(s);
        return n != null && n.isKey;
    }

    /** The node exactly at {@code key} (every character consumed), or null if the path breaks. */
    private StandardTrieNode<V> find(String key) {
        StandardTrieNode<V> node = root;
        for (int i = 0; i < key.length(); i++) {
            node = node.children.get(key.charAt(i));
            if (node == null) return null;
        }
        return node;
    }

    @Override
    public V put(String key, V value) {
        Objects.requireNonNull(key, "null keys not supported");
        beginMutation();
        try {
            StandardTrieNode<V> node = root;
            String path = "";
            int i = 0;
            while (i < key.length()) {
                char c = key.charAt(i);
                StandardTrieNode<V> child = node.children.get(c);
                if (child == null) {
                    while (i < key.length()) {              // grow a one-node-per-char chain
                        char cc = key.charAt(i);
                        StandardTrieNode<V> leaf = new StandardTrieNode<>();
                        node.children.put(cc, leaf);
                        node = leaf;
                        path += cc;
                        emit(new CreateNode(String.valueOf(cc), path, snapshot()));
                        i++;
                    }
                    break;
                }
                node = child;
                path += c;
                emit(new Descend(String.valueOf(c), path, snapshot()));
                i++;
            }
            V old = node.isKey ? node.value : null;
            boolean newKey = !node.isKey;
            node.isKey = true;
            node.value = value;
            if (newKey) {           // value-replace is non-structural: don't invalidate iterators
                size++;
                modCount++;
            }
            emit(new Put(key, value, old, newKey, key, snapshot()));
            return old;
        } finally {
            dispatcher.endMutation();
        }
    }

    @Override
    public V remove(Object key) {
        if (!(key instanceof String k)) return null;
        beginMutation();
        try {
            record Step(String label, String path) {}
            List<StandardTrieNode<V>> chain = new ArrayList<>();
            chain.add(root);                              // chain[i] is the node reached after i chars
            List<Step> walk = new ArrayList<>();
            StandardTrieNode<V> node = root;
            String path = "";
            for (int i = 0; i < k.length(); i++) {
                char c = k.charAt(i);
                StandardTrieNode<V> child = node.children.get(c);
                if (child == null) return null;          // path breaks -> silent no-op
                node = child;
                path += c;
                chain.add(node);
                walk.add(new Step(String.valueOf(c), path)); // buffered; narrated only if remove proceeds
            }
            if (!node.isKey) return null;                // node exists but isn't a key -> silent no-op
            TrieSnapshot walked = snapshot();
            for (Step step : walk) emit(new Descend(step.label(), step.path(), walked));
            V old = node.value;
            node.isKey = false;
            node.value = null;
            size--;
            modCount++;
            emit(new Remove(k, old, k, snapshot()));
            // Prune cascade: from the removed node upward, while it is childless, non-key, and not the root.
            String prunePath = k;                         // path to chain[idx]
            for (int idx = chain.size() - 1; idx >= 1; idx--) {
                StandardTrieNode<V> cur = chain.get(idx);
                if (cur.isKey || !cur.children.isEmpty()) break;
                char c = k.charAt(idx - 1);               // the edge char into cur
                chain.get(idx - 1).children.remove(c);
                emit(new Prune(String.valueOf(c), prunePath, snapshot()));
                prunePath = prunePath.substring(0, prunePath.length() - 1);
            }
            return old;
        } finally {
            dispatcher.endMutation();
        }
    }

    // --- snapshot + dispatch ----------------------------------------------------

    /** Immutable whole-trie snapshot. */
    TrieSnapshot snapshot() {
        return new TrieSnapshot(snap(root), size);
    }

    private TrieNodeSnapshot snap(StandardTrieNode<V> n) {
        List<TrieEdge> kids = new ArrayList<>(n.children.size());
        for (Map.Entry<Character, StandardTrieNode<V>> e : n.children.entrySet()) {
            kids.add(new TrieEdge(String.valueOf(e.getKey()), snap(e.getValue())));
        }
        return new TrieNodeSnapshot(n.isKey, n.isKey ? n.value : null, kids);
    }

    public void addListener(StructureEventListener<TrieEvent> listener) {
        dispatcher.addListener(listener);
    }

    public void removeListener(StructureEventListener<TrieEvent> listener) {
        dispatcher.removeListener(listener);
    }

    private void emit(TrieEvent event) {
        dispatcher.emit(event);
    }

    private void beginMutation() {
        dispatcher.beginMutation();
    }

    @Override
    public Set<Map.Entry<String, V>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size() { return size; }
            @Override public Iterator<Map.Entry<String, V>> iterator() { return new EntryIterator(); }
        };
    }

    /** All key entries in lexicographic order (sorted DFS; children ordered by char). */
    private List<Map.Entry<String, V>> collect() {
        List<Map.Entry<String, V>> out = new ArrayList<>();
        collect(root, "", out);
        return out;
    }

    private void collect(StandardTrieNode<V> n, String prefix, List<Map.Entry<String, V>> out) {
        if (n.isKey) out.add(new AbstractMap.SimpleImmutableEntry<>(prefix, n.value));
        for (Map.Entry<Character, StandardTrieNode<V>> e : n.children.entrySet()) {
            collect(e.getValue(), prefix + e.getKey(), out);
        }
    }

    /** Keys with the given prefix, lexicographically (an unmodifiable snapshot). */
    public List<String> keysWithPrefix(String prefix) {
        Objects.requireNonNull(prefix, "null prefix");
        StandardTrieNode<V> node = find(prefix);
        if (node == null) return List.of();
        List<String> keys = new ArrayList<>();
        collectKeys(node, prefix, keys);
        return Collections.unmodifiableList(keys);
    }

    private void collectKeys(StandardTrieNode<V> n, String prefix, List<String> out) {
        if (n.isKey) out.add(prefix);
        for (Map.Entry<Character, StandardTrieNode<V>> e : n.children.entrySet()) {
            collectKeys(e.getValue(), prefix + e.getKey(), out);
        }
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
            StandardTrie.this.remove(last.getKey());
            expectedModCount = modCount;
            last = null;
        }
    }
}
