package com.gimlism.translucent.hashmap.core;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * A teaching HashMap: power-of-two table, separate chaining (tail-append),
 * load-factor resize. Trees are added in later slices.
 */
public class TeachingHashMap<K, V> extends AbstractMap<K, V> {

    static final int DEFAULT_INITIAL_CAPACITY = 8;
    static final float DEFAULT_LOAD_FACTOR = 0.75f;
    static final int DEFAULT_TREEIFY_THRESHOLD = 4;
    static final int DEFAULT_UNTREEIFY_THRESHOLD = 2;
    static final int DEFAULT_MIN_TREEIFY_CAPACITY = 8;

    final float loadFactor;
    final int treeifyThreshold;
    final int untreeifyThreshold;
    final int minTreeifyCapacity;

    Node<K, V>[] table;
    int size;
    int threshold;
    int modCount;

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

    /** Smallest power of two >= c (min 1). */
    static int tableSizeFor(int c) {
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
        for (Node<K, V> e = table[i]; e != null; e = e.next) {
            if (e.hash == h && Objects.equals(e.key, key)) return e;
        }
        return null;
    }

    @Override
    public V put(K key, V value) {
        int h = hash(key);
        int i = indexFor(h, table.length);
        Node<K, V> head = table[i];
        for (Node<K, V> e = head; e != null; e = e.next) {
            if (e.hash == h && Objects.equals(e.key, key)) {
                V old = e.value;
                e.value = value;
                return old;
            }
        }
        Node<K, V> created = new Node<>(h, key, value, null);
        if (head == null) {
            table[i] = created;
        } else {
            Node<K, V> tail = head;
            while (tail.next != null) tail = tail.next;
            tail.next = created;
        }
        size++;
        modCount++;
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(Object key) {
        int h = hash(key);
        int i = indexFor(h, table.length);
        Node<K, V> prev = null;
        for (Node<K, V> e = table[i]; e != null; prev = e, e = e.next) {
            if (e.hash == h && Objects.equals(e.key, key)) {
                if (prev == null) table[i] = e.next;
                else prev.next = e.next;
                size--;
                modCount++;
                return e.value;
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void clear() {
        if (size == 0) return;
        table = (Node<K, V>[]) new Node[table.length];
        size = 0;
        modCount++;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size() { return size; }
            @Override public Iterator<Map.Entry<K, V>> iterator() { return new EntryIterator(); }
        };
    }

    /** Forward iteration across table slots and chains. (remove/fail-fast added in Task 10.) */
    private final class EntryIterator implements Iterator<Map.Entry<K, V>> {
        private int slot = 0;
        private Node<K, V> nextNode = advanceToFirst();

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
            if (nextNode == null) throw new NoSuchElementException();
            Node<K, V> current = nextNode;
            if (current.next != null) {
                nextNode = current.next;
            } else {
                slot++;
                nextNode = advanceToFirst();
            }
            return current;
        }
    }
}
