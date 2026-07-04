package com.gimlism.translucent.hashmap.core;

import com.gimlism.translucent.hashmap.events.BucketSnapshot;
import com.gimlism.translucent.hashmap.events.ChainSnapshot;
import com.gimlism.translucent.hashmap.events.Collision;
import com.gimlism.translucent.hashmap.events.Color;
import com.gimlism.translucent.hashmap.events.Direction;
import com.gimlism.translucent.hashmap.events.EmptyBucket;
import com.gimlism.translucent.hashmap.events.EntrySnapshot;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import com.gimlism.translucent.hashmap.events.Put;
import com.gimlism.translucent.hashmap.events.Recolor;
import com.gimlism.translucent.hashmap.events.Remove;
import com.gimlism.translucent.hashmap.events.Resize;
import com.gimlism.translucent.hashmap.events.Rotation;
import com.gimlism.translucent.hashmap.events.Treeify;
import com.gimlism.translucent.hashmap.events.TreeNodeSnapshot;
import com.gimlism.translucent.hashmap.events.TreeSnapshot;
import com.gimlism.translucent.hashmap.events.Untreeify;
import com.gimlism.translucent.substrate.events.StructureEventListener;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * A teaching HashMap: power-of-two table, separate chaining (tail-append),
 * load-factor resize. Chains that reach the treeify threshold (and whose table
 * is at least the min treeify capacity) are converted into red-black trees.
 *
 * <p><b>Known limitation (this slice):</b> mutating a value via
 * {@link Map.Entry#setValue(Object)} on an entry obtained from {@link #entrySet()},
 * and the inherited {@link #replaceAll(java.util.function.BiFunction)} (which uses
 * {@code setValue} internally), currently do NOT emit events. Moreover, treeify and
 * untreeify <em>copy</em> a bucket's nodes into fresh {@code TreeNode}/{@code Node}
 * instances, so an entry captured before such a conversion aliases a now-detached
 * node: a later {@code setValue} on it is silently <em>lost</em> (the live map is
 * unchanged and no exception is thrown). Iterate-then-mutate in a single pass, or
 * write through {@link #put}, to be safe. This will be addressed in a later slice
 * when entry views are wrapped to route through the map's mutators.
 */
public class TeachingHashMap<K, V> extends AbstractMap<K, V> {

    static final int DEFAULT_INITIAL_CAPACITY = 8;
    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    static final int DEFAULT_TREEIFY_THRESHOLD = 4;
    static final int DEFAULT_UNTREEIFY_THRESHOLD = 2;
    static final int DEFAULT_MIN_TREEIFY_CAPACITY = 8;

    /** Hard upper bound on table capacity (mirrors {@code java.util.HashMap}). */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    final float loadFactor;
    final int treeifyThreshold;
    final int untreeifyThreshold;
    final int minTreeifyCapacity;

    Node<K, V>[] table;
    int size;
    int threshold;
    int modCount;
    long nextSeq;

    private final List<StructureEventListener<MapEvent>> listeners = new ArrayList<>();

    /**
     * True while a public structural mutation ({@link #put}/{@link #remove}) is in
     * progress. Events are dispatched synchronously, sometimes mid-operation (e.g.
     * while a bin is being treeified or a red-black delete is rebalancing), so a
     * listener that mutates the map would observe — and corrupt — a half-built
     * structure. This flag blocks such re-entrant mutation; reads are always safe.
     */
    private boolean mutating;

    public TeachingHashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR,
             DEFAULT_TREEIFY_THRESHOLD, DEFAULT_UNTREEIFY_THRESHOLD,
             DEFAULT_MIN_TREEIFY_CAPACITY);
    }

    @SuppressWarnings("unchecked")
    public TeachingHashMap(int initialCapacity, float loadFactor,
                           int treeifyThreshold, int untreeifyThreshold,
                           int minTreeifyCapacity) {
        if (initialCapacity < 1) throw new IllegalArgumentException("initialCapacity < 1");
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("loadFactor <= 0");
        // A "tree" needs at least two nodes; treeifyThreshold == 1 would treeify every
        // put into an empty bucket, making a degenerate 1-node tree per entry.
        if (treeifyThreshold < 2)
            throw new IllegalArgumentException("treeifyThreshold must be >= 2");
        if (untreeifyThreshold < 0)
            throw new IllegalArgumentException("untreeifyThreshold must be >= 0");
        if (untreeifyThreshold >= treeifyThreshold)
            throw new IllegalArgumentException("untreeifyThreshold must be < treeifyThreshold");
        int cap = tableSizeFor(initialCapacity);
        this.loadFactor = loadFactor;
        this.treeifyThreshold = treeifyThreshold;
        this.untreeifyThreshold = untreeifyThreshold;
        this.minTreeifyCapacity = minTreeifyCapacity;
        this.table = (Node<K, V>[]) new Node[cap];
        this.threshold = (int) (cap * loadFactor);
    }

    /** Smallest power of two >= c, clamped to {@link #MAXIMUM_CAPACITY} (min 1). */
    static int tableSizeFor(int c) {
        if (c >= MAXIMUM_CAPACITY) return MAXIMUM_CAPACITY;
        int n = 1;
        while (n < c) n <<= 1;
        return n;
    }

    static int hash(Object key) {
        return key == null ? 0 : key.hashCode();
    }

    static int indexFor(int hash, int capacity) {
        return (capacity - 1) & hash;
    }

    /** Current table length (power of two). */
    int capacity() {
        return table.length;
    }

    /** True if bucket {@code index} is a treeified bin. (Test/inspection hook.) */
    boolean isTreeBin(int index) {
        return table[index] instanceof TreeNode;
    }

    /** A sink that turns tree structural changes into events for bucket {@code i}. */
    private TreeEventSink sinkFor(int i) {
        return new TreeEventSink() {
            @Override public void rotated(Direction dir, Object pivotKey) {
                emit(new Rotation(i, dir, pivotKey, snapshot()));
            }
            @Override public void recolored(Object nodeKey, Color oldColor, Color newColor) {
                emit(new Recolor(i, nodeKey, oldColor, newColor, snapshot()));
            }
        };
    }

    public void addListener(StructureEventListener<MapEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(StructureEventListener<MapEvent> listener) {
        listeners.remove(listener);
    }

    private void emit(MapEvent event) {
        // Iterate a copy so a listener may add/remove listeners during dispatch
        // without triggering a ConcurrentModificationException.
        for (StructureEventListener<MapEvent> listener : List.copyOf(listeners)) listener.onEvent(event);
    }

    /**
     * Marks the start of a public structural mutation, rejecting a re-entrant one.
     * A listener invoked from {@link #emit} that calls back into {@link #put} or
     * {@link #remove} would mutate a map that is only partway through an operation
     * (mid-treeify, mid-rebalance), so we fail fast instead of corrupting it.
     */
    private void beginMutation() {
        if (mutating) {
            throw new ConcurrentModificationException(
                "map mutated from within an event listener; listeners may read the map "
                + "but must not put/remove during event dispatch");
        }
        mutating = true;
    }

    /** Test hook: force a rehash to the next capacity. */
    void forceResize() {
        resize();
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        Node<K, V>[] oldTab = table;
        int oldCap = oldTab.length;
        if (oldCap >= MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE; // at the cap: never resize again
            return;
        }
        MapSnapshot before = snapshot();
        int newCap = oldCap << 1;
        Node<K, V>[] newTab = (Node<K, V>[]) new Node[newCap];
        for (int j = 0; j < oldCap; j++) {
            Node<K, V> head = oldTab[j];
            if (head == null) continue;
            if (head instanceof TreeNode) {
                TreeNode<K, V> t = (TreeNode<K, V>) head;
                splitTreeBin(newTab, j, t, oldCap);
            } else {
                Node<K, V> e = head;
                while (e != null) {
                    Node<K, V> next = e.next;
                    int idx = indexFor(e.hash, newCap);
                    e.next = null;
                    if (newTab[idx] == null) {
                        newTab[idx] = e;
                    } else {
                        Node<K, V> tail = newTab[idx];
                        while (tail.next != null) tail = tail.next;
                        tail.next = e;
                    }
                    e = next;
                }
            }
        }
        table = newTab;
        threshold = (int) (newCap * loadFactor);
        modCount++; // a rehash relocates every entry: fail-fast any live iterator
        emit(new Resize(oldCap, newCap, before, snapshot()));
    }

    /**
     * Split a tree bin during resize: partition its nodes (walked in insertion
     * order via next) into the low bucket {@code j} and high bucket
     * {@code j + oldCap}, then rebuild each non-empty half's red-black tree.
     * Rebuild is silent (TreeEventSink.NONE) because the table is mid-swap here;
     * the Resize before/after snapshots convey the change. A half with
     * {@code <= untreeifyThreshold} nodes is untreeified into a plain chain;
     * larger halves are rebuilt as trees.
     */
    private void splitTreeBin(Node<K, V>[] newTab, int j, TreeNode<K, V> head, int oldCap) {
        TreeNode<K, V> loHead = null, loTail = null, hiHead = null, hiTail = null;
        for (Node<K, V> e = head; e != null; ) {
            @SuppressWarnings("unchecked")
            TreeNode<K, V> t = (TreeNode<K, V>) e;
            Node<K, V> next = e.next;
            t.parent = null;
            t.left = null;
            t.right = null;
            t.next = null;
            t.prev = null;
            if ((t.hash & oldCap) == 0) {
                if (loTail == null) { loHead = t; } else { loTail.next = t; t.prev = loTail; }
                loTail = t;
            } else {
                if (hiTail == null) { hiHead = t; } else { hiTail.next = t; t.prev = hiTail; }
                hiTail = t;
            }
            e = next;
        }
        if (loHead != null) {
            if (countAtMost(loHead, untreeifyThreshold)) {
                newTab[j] = untreeify(loHead);
            } else {
                TreeNode.build(loHead, TreeEventSink.NONE);
                newTab[j] = loHead;
            }
        }
        if (hiHead != null) {
            if (countAtMost(hiHead, untreeifyThreshold)) {
                newTab[j + oldCap] = untreeify(hiHead);
            } else {
                TreeNode.build(hiHead, TreeEventSink.NONE);
                newTab[j + oldCap] = hiHead;
            }
        }
    }

    @Override
    public V get(Object key) {
        Node<K, V> e = findNode(key);
        return e == null ? null : e.value;
    }

    @Override
    public boolean containsKey(Object key) {
        return findNode(key) != null;
    }

    private Node<K, V> findNode(Object key) {
        int h = hash(key);
        int i = indexFor(h, table.length);
        Node<K, V> head = table[i];
        if (head instanceof TreeNode) {
            @SuppressWarnings("unchecked")
            TreeNode<K, V> t = (TreeNode<K, V>) head;
            return TreeNode.find(t.root(), h, key);
        }
        for (Node<K, V> e = head; e != null; e = e.next) {
            if (e.hash == h && Objects.equals(e.key, key)) return e;
        }
        return null;
    }

    @Override
    public V put(K key, V value) {
        beginMutation();
        try {
            return doPut(key, value);
        } finally {
            mutating = false;
        }
    }

    private V doPut(K key, V value) {
        int h = hash(key);
        int i = indexFor(h, table.length);
        Node<K, V> head = table[i];

        // --- tree bin ---
        if (head instanceof TreeNode) {
            @SuppressWarnings("unchecked")
            TreeNode<K, V> root = ((TreeNode<K, V>) head).root();
            TreeNode<K, V> found = TreeNode.find(root, h, key);
            if (found != null) {
                V old = found.value;
                found.value = value;
                emit(new Put(key, value, old, i, false, snapshot()));
                return old;
            }
            TreeNode<K, V> node = new TreeNode<>(h, key, value, null, nextSeq++);
            @SuppressWarnings("unchecked")
            TreeNode<K, V> tail = (TreeNode<K, V>) head;
            while (tail.next != null) tail = (TreeNode<K, V>) tail.next;
            tail.next = node;
            node.prev = tail;
            size++;
            modCount++;
            TreeNode.insert(root, node, sinkFor(i)); // balancing events now see the true size
            emit(new Put(key, value, null, i, true, snapshot())); // no Collision for tree bins
            if (size > threshold) resize();
            return null;
        }

        // --- chain bin (Slice 1 behaviour) ---
        for (Node<K, V> e = head; e != null; e = e.next) {
            if (e.hash == h && Objects.equals(e.key, key)) {
                V old = e.value;
                e.value = value;
                emit(new Put(key, value, old, i, false, snapshot()));
                return old;
            }
        }
        int chainBefore = 0;
        Node<K, V> created = new Node<>(h, key, value, null);
        if (head == null) {
            table[i] = created;
        } else {
            Node<K, V> tail = head;
            chainBefore = 1;
            while (tail.next != null) { tail = tail.next; chainBefore++; }
            tail.next = created;
        }
        size++;
        modCount++;
        emit(new Put(key, value, null, i, true, snapshot()));
        if (chainBefore > 0) {
            emit(new Collision(key, i, chainBefore, chainBefore + 1, snapshot()));
        }
        if (chainBefore + 1 >= treeifyThreshold) {
            treeifyBin(i);
        }
        if (size > threshold) resize();
        return null;
    }

    /** Convert the chain at bucket {@code i} into a red-black tree. */
    private void treeifyBin(int i) {
        if (table.length < minTreeifyCapacity) {
            resize(); // grow instead of treeifying a small table (mirrors the JDK)
            return;
        }
        emit(new Treeify(i, snapshot())); // announce: bucket i is still the chain here
        // convert chain Nodes to TreeNodes, preserving order via the next thread
        TreeNode<K, V> first = null;
        TreeNode<K, V> prev = null;
        for (Node<K, V> e = table[i]; e != null; e = e.next) {
            TreeNode<K, V> t = new TreeNode<>(e.hash, e.key, e.value, null, nextSeq++);
            t.prev = prev;
            if (prev == null) first = t; else prev.next = t;
            prev = t;
        }
        table[i] = first;
        TreeNode.build(first, sinkFor(i)); // assembles the tree, emitting Rotation/Recolor
    }

    /** True if the chain/list from {@code head} has at most {@code max} nodes. */
    private boolean countAtMost(Node<K, V> head, int max) {
        int c = 0;
        for (Node<K, V> e = head; e != null; e = e.next) {
            if (++c > max) return false;
        }
        return true;
    }

    /** Convert a tree bin's surviving nodes (walked via next) into a plain-Node chain. */
    private Node<K, V> untreeify(TreeNode<K, V> first) {
        Node<K, V> head = null, tail = null;
        for (TreeNode<K, V> t = first; t != null; ) {
            @SuppressWarnings("unchecked")
            TreeNode<K, V> next = (TreeNode<K, V>) t.next;
            Node<K, V> plain = new Node<>(t.hash, t.key, t.value, null);
            if (tail == null) head = plain; else tail.next = plain;
            tail = plain;
            t = next;
        }
        return head;
    }

    @Override
    public V remove(Object key) {
        beginMutation();
        try {
            return doRemove(key);
        } finally {
            mutating = false;
        }
    }

    private V doRemove(Object key) {
        int h = hash(key);
        int i = indexFor(h, table.length);
        Node<K, V> head = table[i];
        if (head instanceof TreeNode) {
            @SuppressWarnings("unchecked")
            TreeNode<K, V> treeHead = (TreeNode<K, V>) head;
            TreeNode<K, V> p = TreeNode.find(treeHead.root(), h, key);
            if (p == null) return null;
            V old = p.value;
            size--;
            modCount++;
            // unlink p from the doubly-linked list (O(1) via prev)
            @SuppressWarnings("unchecked")
            TreeNode<K, V> pNext = (TreeNode<K, V>) p.next;
            TreeNode<K, V> pPrev = p.prev;
            if (pPrev != null) pPrev.next = pNext;
            if (pNext != null) pNext.prev = pPrev;
            TreeNode<K, V> newHead = (pPrev == null) ? pNext : treeHead;
            if (newHead == null) {
                table[i] = null; // bin now empty
            } else if (countAtMost(newHead, untreeifyThreshold)) {
                table[i] = untreeify(newHead); // small: convert survivors to a chain
                emit(new Untreeify(i, snapshot()));
            } else {
                table[i] = newHead; // set the survivor head first so fixup snapshots read a survivor's root
                TreeNode.deleteFromTree(p.root(), p, sinkFor(i));
            }
            p.next = null;
            p.prev = null;
            emit(new Remove(key, old, i, snapshot()));
            return old;
        }
        Node<K, V> prev = null;
        for (Node<K, V> e = head; e != null; prev = e, e = e.next) {
            if (e.hash == h && Objects.equals(e.key, key)) {
                if (prev == null) table[i] = e.next;
                else prev.next = e.next;
                size--;
                modCount++;
                V old = e.value;
                emit(new Remove(key, old, i, snapshot()));
                return old;
            }
        }
        return null;
    }

    @Override
    public int size() {
        return size;
    }

    /** Immutable snapshot of the whole map. Chains render as {@link ChainSnapshot},
     * tree bins as {@link TreeSnapshot}, and empty buckets as {@link EmptyBucket}. */
    MapSnapshot snapshot() {
        List<BucketSnapshot> buckets = new ArrayList<>(table.length);
        for (Node<K, V> head : table) {
            if (head == null) {
                buckets.add(EmptyBucket.INSTANCE);
            } else if (head instanceof TreeNode) {
                @SuppressWarnings("unchecked")
                TreeNode<K, V> t = (TreeNode<K, V>) head;
                buckets.add(new TreeSnapshot(treeSnapshot(t.root())));
            } else {
                List<EntrySnapshot> entries = new ArrayList<>();
                for (Node<K, V> e = head; e != null; e = e.next) {
                    entries.add(new EntrySnapshot(e.key, e.value, e.hash));
                }
                buckets.add(new ChainSnapshot(entries));
            }
        }
        return new MapSnapshot(table.length, size, threshold, buckets);
    }

    private TreeNodeSnapshot treeSnapshot(TreeNode<K, V> n) {
        if (n == null) return null;
        return new TreeNodeSnapshot(
            n.key, n.value,
            n.red ? Color.RED : Color.BLACK,
            treeSnapshot(n.left),
            treeSnapshot(n.right));
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size() { return size; }
            @Override public Iterator<Map.Entry<K, V>> iterator() { return new EntryIterator(); }
        };
    }

    private final class EntryIterator implements Iterator<Map.Entry<K, V>> {
        private int slot = 0;
        private Node<K, V> nextNode;
        private Node<K, V> lastReturned;
        private int expectedModCount = modCount;

        EntryIterator() {
            nextNode = advanceToFirst();
        }

        private Node<K, V> advanceToFirst() {
            while (slot < table.length && table[slot] == null) slot++;
            return slot < table.length ? table[slot] : null;
        }

        @Override
        public boolean hasNext() {
            return nextNode != null;
        }

        @Override
        public Map.Entry<K, V> next() {
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            if (nextNode == null) throw new NoSuchElementException();
            lastReturned = nextNode;
            if (nextNode.next != null) {
                nextNode = nextNode.next;
            } else {
                slot++;
                nextNode = advanceToFirst();
            }
            return lastReturned;
        }

        @Override
        public void remove() {
            if (lastReturned == null) throw new IllegalStateException();
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            TeachingHashMap.this.remove(lastReturned.key);
            expectedModCount = modCount;
            lastReturned = null;
        }
    }
}
