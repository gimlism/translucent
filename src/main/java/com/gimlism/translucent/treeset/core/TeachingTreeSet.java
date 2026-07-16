package com.gimlism.translucent.treeset.core;

import com.gimlism.translucent.substrate.events.EventDispatcher;
import com.gimlism.translucent.substrate.events.StructureEventListener;
import com.gimlism.translucent.substrate.rbtree.Color;
import com.gimlism.translucent.substrate.rbtree.Direction;
import com.gimlism.translucent.substrate.rbtree.RbEventSink;
import com.gimlism.translucent.substrate.rbtree.RedBlackTree;
import com.gimlism.translucent.treeset.events.Add;
import com.gimlism.translucent.treeset.events.Compare;
import com.gimlism.translucent.treeset.events.Recolor;
import com.gimlism.translucent.treeset.events.Remove;
import com.gimlism.translucent.treeset.events.Rotation;
import com.gimlism.translucent.treeset.events.SetEvent;
import com.gimlism.translucent.treeset.events.SetNodeSnapshot;
import com.gimlism.translucent.treeset.events.SetSnapshot;
import java.util.AbstractSet;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.SortedSet;

/**
 * A teaching {@link NavigableSet} backed by a red-black tree, ordered by natural
 * ordering or a supplied {@link Comparator}. Every mutation and every comparison is
 * observable through an immutable {@link SetEvent} stream. Rebalancing is delegated to
 * the shared {@link RedBlackTree} kernel; this class owns ordering, the comparison
 * walk, node identity, and event translation.
 */
public class TeachingTreeSet<E> extends AbstractSet<E> implements NavigableSet<E> {

    private SetNode<E> root;
    private int size;
    int modCount;
    private final Comparator<? super E> comparator;

    private final EventDispatcher<SetEvent> dispatcher = new EventDispatcher<>("set");

    /** Translates the kernel's neutral rotate/recolor callbacks into SetEvents, snapshotting the live tree. */
    private final RbEventSink<SetNode<E>> sink = new RbEventSink<>() {
        @Override public void rotated(Direction dir, SetNode<E> pivot) {
            emit(new Rotation(dir, pivot.element, snapshotFrom(pivot)));
        }
        @Override public void recolored(SetNode<E> node, Color oldColor, Color newColor) {
            emit(new Recolor(node.element, oldColor, newColor, snapshotFrom(node)));
        }
    };

    public TeachingTreeSet() {
        this.comparator = null;
    }

    public TeachingTreeSet(Comparator<? super E> comparator) {
        this.comparator = comparator;
    }

    @SuppressWarnings("unchecked")
    private int compare(E a, E b) {
        return comparator != null ? comparator.compare(a, b) : ((Comparable<? super E>) a).compareTo(b);
    }

    @Override
    public Comparator<? super E> comparator() {
        return comparator;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean contains(Object o) {
        @SuppressWarnings("unchecked")
        E e = (E) o;
        SetNode<E> node = root;
        while (node != null) {
            int c = compare(e, node.element);
            if (c == 0) {
                emitRead(new Compare(node.element, null, true, snapshot()));
                return true;
            }
            Direction went = c < 0 ? Direction.LEFT : Direction.RIGHT;
            emitRead(new Compare(node.element, went, false, snapshot()));
            node = c < 0 ? node.left : node.right;
        }
        return false;
    }

    @Override
    public boolean add(E e) {
        beginMutation();
        try {
            if (root == null) {
                root = new SetNode<>(e);
                root.red = false;      // black root, committed before the frame
                size++;
                modCount++;
                emit(new Add(e, snapshot()));
                return true;
            }
            SetNode<E> node = root;
            SetNode<E> parent = null;
            int dir = 0;
            while (node != null) {
                int c = compare(e, node.element);
                if (c == 0) {
                    emit(new Compare(node.element, null, true, snapshot()));
                    return false;      // already present -> no Add
                }
                Direction went = c < 0 ? Direction.LEFT : Direction.RIGHT;
                emit(new Compare(node.element, went, false, snapshot()));
                parent = node;
                dir = c;
                node = c < 0 ? node.left : node.right;
            }
            SetNode<E> x = new SetNode<>(e);
            x.parent = parent;
            x.red = true;
            if (dir < 0) parent.left = x; else parent.right = x;
            size++;
            modCount++;
            emit(new Add(e, snapshot()));          // committed link + size before the frame
            root = RedBlackTree.insertFixup(root, x, sink);
            return true;
        } finally {
            dispatcher.endMutation();
        }
    }

    @Override
    public boolean remove(Object o) {
        beginMutation();
        try {
            @SuppressWarnings("unchecked")
            E e = (E) o;
            SetNode<E> node = root;
            while (node != null) {
                int c = compare(e, node.element);
                if (c == 0) {
                    emit(new Compare(node.element, null, true, snapshot()));
                    break;
                }
                Direction went = c < 0 ? Direction.LEFT : Direction.RIGHT;
                emit(new Compare(node.element, went, false, snapshot()));
                node = c < 0 ? node.left : node.right;
            }
            if (node == null) return false;          // absent: the failed walk was narrated, no marker
            unlink(node);
            return true;
        } finally {
            dispatcher.endMutation();
        }
    }

    /** Remove an already-located node: commit size first, rebalance, then mark. */
    private void unlink(SetNode<E> node) {
        Object element = node.element;
        size--;
        modCount++;
        root = RedBlackTree.deleteFromTree(root, node, sink); // mid-fixup frames see final size
        emit(new Remove(element, snapshot()));
    }

    /** Iterator entry point: delete an already-held node with full event + guard semantics. */
    void removeNode(SetNode<E> node) {
        beginMutation();
        try {
            unlink(node);
        } finally {
            dispatcher.endMutation();
        }
    }

    /**
     * Reset to empty in O(1). Guarded like every other mutator (a listener may not
     * clear the set mid-dispatch). Emits no event by design — the vocabulary has no
     * bulk-clear event; a visualizer sees the next frame as the empty tree.
     */
    @Override
    public void clear() {
        beginMutation();
        try {
            root = null;
            size = 0;
            modCount++;
        } finally {
            dispatcher.endMutation();
        }
    }

    @Override
    public Iterator<E> iterator() {
        return new AscendingIterator();
    }

    /** Leftmost (minimum) node, or null when empty. */
    private SetNode<E> firstNode() {
        SetNode<E> n = root;
        if (n == null) return null;
        while (n.left != null) n = n.left;
        return n;
    }

    /** In-order successor of {@code n}. */
    private SetNode<E> successor(SetNode<E> n) {
        if (n.right != null) {
            SetNode<E> s = n.right;
            while (s.left != null) s = s.left;
            return s;
        }
        SetNode<E> p = n.parent;
        SetNode<E> c = n;
        while (p != null && c == p.right) { c = p; p = p.parent; }
        return p;
    }

    /** Rightmost (maximum) node, or null when empty. */
    private SetNode<E> lastNode() {
        SetNode<E> n = root;
        if (n == null) return null;
        while (n.right != null) n = n.right;
        return n;
    }

    private E elementOrNull(SetNode<E> n) {
        return n == null ? null : n.element;
    }

    /**
     * Narrated comparison walk for the relative navigators. Returns the node that is
     * the ceiling ({@code up=true}) or the floor ({@code up=false}) of {@code e};
     * {@code inclusive} decides whether an exact match qualifies. Emits a Compare at
     * each visited node.
     */
    private SetNode<E> bound(E e, boolean up, boolean inclusive) {
        SetNode<E> node = root;
        SetNode<E> best = null;
        while (node != null) {
            int c = compare(e, node.element);
            if (c == 0) {
                emitRead(new Compare(node.element, null, true, snapshot()));
                if (inclusive) return node;
                // exclusive: step to the neighbour on the requested side
                return up ? successor(node) : predecessor(node);
            }
            Direction went = c < 0 ? Direction.LEFT : Direction.RIGHT;
            emitRead(new Compare(node.element, went, false, snapshot()));
            if (up) {                       // ceiling/higher: smallest element > (or >=) e
                if (c < 0) { best = node; node = node.left; }
                else { node = node.right; }
            } else {                        // floor/lower: largest element < (or <=) e
                if (c > 0) { best = node; node = node.right; }
                else { node = node.left; }
            }
        }
        return best;
    }

    /** In-order predecessor of {@code n}. */
    private SetNode<E> predecessor(SetNode<E> n) {
        if (n.left != null) {
            SetNode<E> p = n.left;
            while (p.right != null) p = p.right;
            return p;
        }
        SetNode<E> p = n.parent;
        SetNode<E> c = n;
        while (p != null && c == p.left) { c = p; p = p.parent; }
        return p;
    }

    private final class AscendingIterator implements Iterator<E> {
        private SetNode<E> next = firstNode();
        private SetNode<E> lastReturned;
        private int expectedModCount = modCount;

        @Override public boolean hasNext() { return next != null; }

        @Override public E next() {
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            if (next == null) throw new NoSuchElementException();
            lastReturned = next;
            next = successor(next);
            return lastReturned.element;
        }

        @Override public void remove() {
            if (lastReturned == null) throw new IllegalStateException();
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            // The kernel's delete is pointer-based (identity-preserving): when `lastReturned`
            // has two children its successor NODE is relocated into `lastReturned`'s slot and
            // stays live, so `next` (= that successor) remains correctly positioned. No re-seat
            // is needed — unlike JDK TreeMap, which copies the successor's value and would.
            TeachingTreeSet.this.removeNode(lastReturned);
            expectedModCount = modCount;
            lastReturned = null;
        }
    }

    private final class DescendingIterator implements Iterator<E> {
        private SetNode<E> next = lastNode();
        private SetNode<E> lastReturned;
        private int expectedModCount = modCount;

        @Override public boolean hasNext() { return next != null; }

        @Override public E next() {
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            if (next == null) throw new NoSuchElementException();
            lastReturned = next;
            next = predecessor(next);
            return lastReturned.element;
        }

        @Override public void remove() {
            if (lastReturned == null) throw new IllegalStateException();
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            // Identity-preserving delete keeps `next` (the predecessor) live and positioned; no re-seat.
            TeachingTreeSet.this.removeNode(lastReturned);
            expectedModCount = modCount;
            lastReturned = null;
        }
    }

    // --- snapshot + dispatch ----------------------------------------------------

    /** Immutable whole-set snapshot from the cached root (valid outside a fixup). */
    SetSnapshot snapshot() {
        return new SetSnapshot(snap(root), size);
    }

    /** Snapshot built by climbing to the true root from {@code anyNode} (valid mid-fixup). */
    private SetSnapshot snapshotFrom(SetNode<E> anyNode) {
        SetNode<E> r = anyNode;
        while (r.parent != null) r = r.parent;
        return new SetSnapshot(snap(r), size);
    }

    private SetNodeSnapshot snap(SetNode<E> n) {
        if (n == null) return null;
        return new SetNodeSnapshot(n.element, n.red, snap(n.left), snap(n.right));
    }

    public void addListener(StructureEventListener<SetEvent> listener) {
        dispatcher.addListener(listener);
    }

    public void removeListener(StructureEventListener<SetEvent> listener) {
        dispatcher.removeListener(listener);
    }

    private void emit(SetEvent event) {
        dispatcher.emit(event);
    }

    /** Emit a read-narration event (a Compare during contains/navigation), guarding the walk against a mutating listener. */
    private void emitRead(SetEvent event) {
        dispatcher.emitRead(event);
    }

    private void beginMutation() {
        dispatcher.beginMutation();
    }

    /** Test hook: the tree root (package-visible for invariant checkers). */
    SetNode<E> rootForTest() {
        return root;
    }

    // --- NavigableSet methods filled in by later tasks --------------------------

    @Override
    public E first() {
        SetNode<E> n = firstNode();
        if (n == null) throw new NoSuchElementException();
        return n.element;
    }

    @Override
    public E last() {
        SetNode<E> n = lastNode();
        if (n == null) throw new NoSuchElementException();
        return n.element;
    }

    @Override
    public E lower(E e) {
        return elementOrNull(bound(e, false, false));
    }

    @Override
    public E floor(E e) {
        return elementOrNull(bound(e, false, true));
    }

    @Override
    public E ceiling(E e) {
        return elementOrNull(bound(e, true, true));
    }

    @Override
    public E higher(E e) {
        return elementOrNull(bound(e, true, false));
    }

    @Override
    public E pollFirst() {
        SetNode<E> n = firstNode();
        if (n == null) return null;
        E element = n.element;
        removeNode(n);
        return element;
    }

    @Override
    public E pollLast() {
        SetNode<E> n = lastNode();
        if (n == null) return null;
        E element = n.element;
        removeNode(n);
        return element;
    }

    @Override
    public Iterator<E> descendingIterator() {
        return new DescendingIterator();
    }

    @Override
    public NavigableSet<E> descendingSet() {
        return new DescendingSetView<>(this);
    }

    @Override
    public NavigableSet<E> subSet(E from, boolean fromInc, E to, boolean toInc) {
        if (compare(from, to) > 0) {
            throw new IllegalArgumentException("fromElement (" + from + ") > toElement (" + to + ")");
        }
        return new RangeSetView<>(this, from, true, fromInc, to, true, toInc);
    }

    @Override
    public NavigableSet<E> headSet(E to, boolean inclusive) {
        return new RangeSetView<>(this, null, false, false, to, true, inclusive);
    }

    @Override
    public NavigableSet<E> tailSet(E from, boolean inclusive) {
        return new RangeSetView<>(this, from, true, inclusive, null, false, false);
    }

    @Override
    public SortedSet<E> subSet(E from, E to) {
        return subSet(from, true, to, false);
    }

    @Override
    public SortedSet<E> headSet(E to) {
        return headSet(to, false);
    }

    @Override
    public SortedSet<E> tailSet(E from) {
        return tailSet(from, true);
    }

    /** Ordering bridge for range views (uses this set's comparator or natural order). */
    int compareElements(E a, E b) {
        return compare(a, b);
    }
}
