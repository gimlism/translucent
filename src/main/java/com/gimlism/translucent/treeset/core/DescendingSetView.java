package com.gimlism.translucent.treeset.core;

import java.util.AbstractSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.SortedSet;

/**
 * A reversed view over a {@link TeachingTreeSet}. Reads reflect the backing set in
 * descending order; writes delegate through. Endpoint and relative navigators map to
 * the backing set's mirror ({@code first<->last}, {@code lower<->higher},
 * {@code floor<->ceiling}).
 */
final class DescendingSetView<E> extends AbstractSet<E> implements NavigableSet<E> {

    private final TeachingTreeSet<E> base;

    DescendingSetView(TeachingTreeSet<E> base) {
        this.base = base;
    }

    @Override public int size() { return base.size(); }
    @Override public boolean contains(Object o) { return base.contains(o); }
    @Override public boolean add(E e) { return base.add(e); }
    @Override public boolean remove(Object o) { return base.remove(o); }
    @Override public void clear() { base.clear(); }

    @Override public Iterator<E> iterator() { return base.descendingIterator(); }
    @Override public Iterator<E> descendingIterator() { return base.iterator(); }
    @Override public NavigableSet<E> descendingSet() { return base; }

    @Override public E first() { return base.last(); }
    @Override public E last() { return base.first(); }
    @Override public E lower(E e) { return base.higher(e); }
    @Override public E higher(E e) { return base.lower(e); }
    @Override public E floor(E e) { return base.ceiling(e); }
    @Override public E ceiling(E e) { return base.floor(e); }
    @Override public E pollFirst() { return base.pollLast(); }
    @Override public E pollLast() { return base.pollFirst(); }

    @Override
    public Comparator<? super E> comparator() {
        // Collections.reverseOrder(Comparator) has no Comparable bound (unlike
        // Comparator.reverseOrder()), and maps a null comparator to reverse-natural
        // ordering directly — matching java.util.TreeSet.descendingSet().comparator().
        return Collections.reverseOrder(base.comparator());
    }

    @Override public NavigableSet<E> subSet(E from, boolean fromInc, E to, boolean toInc) {
        return base.subSet(to, toInc, from, fromInc).descendingSet();
    }
    @Override public NavigableSet<E> headSet(E to, boolean inclusive) {
        return base.tailSet(to, inclusive).descendingSet();
    }
    @Override public NavigableSet<E> tailSet(E from, boolean inclusive) {
        return base.headSet(from, inclusive).descendingSet();
    }
    @Override public SortedSet<E> subSet(E from, E to) { return subSet(from, true, to, false); }
    @Override public SortedSet<E> headSet(E to) { return headSet(to, false); }
    @Override public SortedSet<E> tailSet(E from) { return tailSet(from, true); }
}
