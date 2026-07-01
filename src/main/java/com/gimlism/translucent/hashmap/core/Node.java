package com.gimlism.translucent.hashmap.core;

import java.util.Map;
import java.util.Objects;

/** A single chained entry. {@code hash} is the raw key hashCode (no spreading). */
class Node<K, V> implements Map.Entry<K, V> {
    final int hash;
    final K key;
    V value;
    Node<K, V> next;

    Node(int hash, K key, V value, Node<K, V> next) {
        this.hash = hash;
        this.key = key;
        this.value = value;
        this.next = next;
    }

    @Override public K getKey() { return key; }
    @Override public V getValue() { return value; }

    @Override
    public V setValue(V newValue) {
        V old = value;
        value = newValue;
        return old;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Map.Entry<?, ?> e)) return false;
        return Objects.equals(key, e.getKey()) && Objects.equals(value, e.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(key) ^ Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return key + "=" + value;
    }
}
