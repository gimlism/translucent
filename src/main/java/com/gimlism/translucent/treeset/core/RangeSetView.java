package com.gimlism.translucent.treeset.core;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.SortedSet;

/**
 * A bounded, write-through view over a {@link TeachingTreeSet}. Reads filter the
 * backing iteration to the range; writes delegate through, rejecting an out-of-range
 * {@code add} with {@link IllegalArgumentException} (JDK semantics). Either bound may
 * be absent ({@code hasLo}/{@code hasHi} false) for head/tail views.
 */
final class RangeSetView<E> extends AbstractSet<E> implements NavigableSet<E> {

    private final TeachingTreeSet<E> base;
    private final E lo;
    private final boolean hasLo;
    private final boolean loInc;
    private final E hi;
    private final boolean hasHi;
    private final boolean hiInc;

    RangeSetView(TeachingTreeSet<E> base, E lo, boolean hasLo, boolean loInc,
                 E hi, boolean hasHi, boolean hiInc) {
        this.base = base;
        this.lo = lo; this.hasLo = hasLo; this.loInc = loInc;
        this.hi = hi; this.hasHi = hasHi; this.hiInc = hiInc;
    }

    private boolean tooLow(E e) {
        if (!hasLo) return false;
        int c = base.compareElements(e, lo);
        return loInc ? c < 0 : c <= 0;
    }

    private boolean tooHigh(E e) {
        if (!hasHi) return false;
        int c = base.compareElements(e, hi);
        return hiInc ? c > 0 : c >= 0;
    }

    private boolean inRange(E e) {
        return !tooLow(e) && !tooHigh(e);
    }

    private List<E> elementsInRange() {
        List<E> out = new ArrayList<>();
        for (E e : base) {
            if (tooLow(e)) continue;
            if (tooHigh(e)) break; // ascending: nothing further qualifies
            out.add(e);
        }
        return out;
    }

    @Override public Iterator<E> iterator() {
        List<E> snapshot = elementsInRange();
        return new Iterator<>() {
            private final Iterator<E> it = snapshot.iterator();
            private E lastReturned;
            @Override public boolean hasNext() { return it.hasNext(); }
            @Override public E next() { lastReturned = it.next(); return lastReturned; }
            @Override public void remove() {
                if (lastReturned == null) throw new IllegalStateException();
                base.remove(lastReturned);
                lastReturned = null;
            }
        };
    }

    @Override public int size() { return elementsInRange().size(); }
    @Override public boolean isEmpty() { return elementsInRange().isEmpty(); }

    @Override @SuppressWarnings("unchecked")
    public boolean contains(Object o) {
        return inRange((E) o) && base.contains(o);
    }

    @Override public boolean add(E e) {
        if (!inRange(e)) {
            throw new IllegalArgumentException("element " + e + " out of range");
        }
        return base.add(e);
    }

    @Override @SuppressWarnings("unchecked")
    public boolean remove(Object o) {
        return inRange((E) o) && base.remove(o);
    }

    @Override public Comparator<? super E> comparator() { return base.comparator(); }

    @Override public E first() {
        List<E> es = elementsInRange();
        if (es.isEmpty()) throw new NoSuchElementException();
        return es.get(0);
    }

    @Override public E last() {
        List<E> es = elementsInRange();
        if (es.isEmpty()) throw new NoSuchElementException();
        return es.get(es.size() - 1);
    }

    @Override public E lower(E e) { return highestBelow(e, false); }
    @Override public E floor(E e) { return highestBelow(e, true); }
    @Override public E ceiling(E e) { return lowestAbove(e, true); }
    @Override public E higher(E e) { return lowestAbove(e, false); }

    private E lowestAbove(E e, boolean inclusive) {
        for (E x : elementsInRange()) {
            int c = base.compareElements(x, e);
            if (c > 0 || (inclusive && c == 0)) return x;
        }
        return null;
    }

    private E highestBelow(E e, boolean inclusive) {
        E best = null;
        for (E x : elementsInRange()) {
            int c = base.compareElements(x, e);
            if (c < 0 || (inclusive && c == 0)) best = x; else break;
        }
        return best;
    }

    @Override public E pollFirst() {
        List<E> es = elementsInRange();
        if (es.isEmpty()) return null;
        E e = es.get(0);
        base.remove(e);
        return e;
    }

    @Override public E pollLast() {
        List<E> es = elementsInRange();
        if (es.isEmpty()) return null;
        E e = es.get(es.size() - 1);
        base.remove(e);
        return e;
    }

    @Override public Iterator<E> descendingIterator() {
        List<E> es = elementsInRange();
        return new Iterator<>() {
            private int i = es.size() - 1;
            @Override public boolean hasNext() { return i >= 0; }
            @Override public E next() { return es.get(i--); }
        };
    }

    @Override public NavigableSet<E> descendingSet() { return new DescendingSetView<>(materialize()); }

    /** A standalone copy of this view's elements, for descendingSet (read-only convenience). */
    private TeachingTreeSet<E> materialize() {
        TeachingTreeSet<E> copy = base.comparator() == null
                ? new TeachingTreeSet<>() : new TeachingTreeSet<>(base.comparator());
        for (E e : elementsInRange()) copy.add(e);
        return copy;
    }

    @Override public NavigableSet<E> subSet(E from, boolean fromInc, E to, boolean toInc) {
        if (base.compareElements(from, to) > 0) {
            throw new IllegalArgumentException("fromElement (" + from + ") > toElement (" + to + ")");
        }
        requireInRange(from);
        requireInRange(to);
        return new RangeSetView<>(base, from, true, fromInc, to, true, toInc);
    }

    @Override public NavigableSet<E> headSet(E to, boolean inclusive) {
        requireInRange(to);
        return new RangeSetView<>(base, lo, hasLo, loInc, to, true, inclusive);
    }

    @Override public NavigableSet<E> tailSet(E from, boolean inclusive) {
        requireInRange(from);
        return new RangeSetView<>(base, from, true, inclusive, hi, hasHi, hiInc);
    }

    @Override public SortedSet<E> subSet(E from, E to) { return subSet(from, true, to, false); }
    @Override public SortedSet<E> headSet(E to) { return headSet(to, false); }
    @Override public SortedSet<E> tailSet(E from) { return tailSet(from, true); }

    private void requireInRange(E e) {
        if (!inRange(e)) throw new IllegalArgumentException("bound " + e + " out of range");
    }
}
