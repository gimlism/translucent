package com.gimlism.translucent.hashmap.core;

/** A single chained entry. {@code hash} is the raw key hashCode (no spreading). */
class ChainNode<K, V> implements Node<K, V> {
    final int hash;
    final K key;
    V value;
    Node<K, V> next;

    ChainNode(int hash, K key, V value, Node<K, V> next) {
        this.hash = hash;
        this.key = key;
        this.value = value;
        this.next = next;
    }

    @Override public int hash() { return hash; }
    @Override public K getKey() { return key; }
    @Override public V getValue() { return value; }
    @Override public Node<K, V> next() { return next; }
    @Override public void setNext(Node<K, V> next) { this.next = next; }

    @Override
    public V setValue(V newValue) {
        V old = value;
        value = newValue;
        return old;
    }

    @Override public boolean equals(Object o) { return Entries.equals(this, o); }
    @Override public int hashCode() { return Entries.hashCode(this); }
    @Override public String toString() { return Entries.toString(this); }
}
