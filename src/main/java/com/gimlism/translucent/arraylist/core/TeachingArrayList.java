package com.gimlism.translucent.arraylist.core;

import com.gimlism.translucent.arraylist.events.Append;
import com.gimlism.translucent.arraylist.events.EmptySlot;
import com.gimlism.translucent.arraylist.events.FilledSlot;
import com.gimlism.translucent.arraylist.events.Grow;
import com.gimlism.translucent.arraylist.events.Insert;
import com.gimlism.translucent.arraylist.events.ListEvent;
import com.gimlism.translucent.arraylist.events.ListSnapshot;
import com.gimlism.translucent.arraylist.events.RemoveAt;
import com.gimlism.translucent.arraylist.events.Set;
import com.gimlism.translucent.arraylist.events.Shift;
import com.gimlism.translucent.arraylist.events.SlotSnapshot;
import com.gimlism.translucent.substrate.events.StructureEventListener;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * A teaching ArrayList: a backing array with a capacity distinct from size, 1.5×
 * amortized growth, and O(n) shift on insert/remove — every mutation observable
 * through an immutable event stream. Sibling of {@code TeachingHashMap}; shares no
 * types with it.
 */
public class TeachingArrayList<E> extends AbstractList<E> implements RandomAccess {

    /** JDK-faithful default capacity the first add jumps to from the lazy sentinel. */
    static final int DEFAULT_CAPACITY = 10;

    /**
     * Largest array length to attempt (mirrors {@code java.util.ArrayList}). Some VMs
     * reserve header words in an array, so {@code Integer.MAX_VALUE} can fail; growth
     * clamps here and reports {@link OutOfMemoryError} beyond it rather than overflowing
     * to a negative capacity.
     */
    static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /** Shared empty array for no-arg (default-capacity) lists — first add jumps to DEFAULT_CAPACITY. */
    private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};
    /** Shared empty array for explicit zero-capacity lists — grows by the 1.5x formula. */
    private static final Object[] EMPTY_ELEMENTDATA = {};

    Object[] elementData;
    private int size;

    private final List<StructureEventListener<ListEvent>> listeners = new ArrayList<>();
    private boolean mutating;

    /** Lazy: allocates nothing until the first add, which jumps to {@link #DEFAULT_CAPACITY}. */
    public TeachingArrayList() {
        this.elementData = DEFAULTCAPACITY_EMPTY_ELEMENTDATA;
    }

    public TeachingArrayList(int initialCapacity) {
        if (initialCapacity < 0) throw new IllegalArgumentException("initialCapacity < 0");
        this.elementData = (initialCapacity == 0) ? EMPTY_ELEMENTDATA : new Object[initialCapacity];
    }

    @Override
    public int size() {
        return size;
    }

    @SuppressWarnings("unchecked")
    private E elementAt(int i) {
        return (E) elementData[i];
    }

    @Override
    public E get(int index) {
        Objects.checkIndex(index, size);
        return elementAt(index);
    }

    @Override
    public E set(int index, E element) {
        Objects.checkIndex(index, size);
        beginMutation();
        try {
            E old = elementAt(index);
            elementData[index] = element;          // non-structural: no modCount bump
            emit(new Set(index, old, element, snapshot()));
            return old;
        } finally {
            mutating = false;
        }
    }

    @Override
    public boolean add(E element) {
        beginMutation();
        try {
            appendInternal(element);
            return true;
        } finally {
            mutating = false;
        }
    }

    @Override
    public void add(int index, E element) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
        beginMutation();
        try {
            if (index == size) {
                appendInternal(element);                 // no shift -> Append
            } else {
                insertInternal(index, element);          // Shift* -> Insert
            }
        } finally {
            mutating = false;
        }
    }

    @Override
    public E remove(int index) {
        Objects.checkIndex(index, size);
        beginMutation();
        try {
            E old = elementAt(index);
            modCount++;
            for (int i = index + 1; i < size; i++) {     // low -> high, slide survivors left
                elementData[i - 1] = elementData[i];
                emit(new Shift(i, i - 1, elementData[i - 1], snapshot()));
            }
            elementData[--size] = null;                  // drop size, clear the vacated tail
            emit(new RemoveAt(index, old, snapshot()));
            return old;
        } finally {
            mutating = false;
        }
    }

    /** Insert at index &lt; size: grow if needed, slide [index,size) right one slot, place. */
    private void insertInternal(int index, E element) {
        ensureCapacity(size + 1);                        // may emit Grow (size still old)
        size++;                                          // open one slot at the tail
        modCount++;
        for (int i = size - 2; i >= index; i--) {        // high -> low, avoid overwrite
            elementData[i + 1] = elementData[i];
            emit(new Shift(i, i + 1, elementData[i + 1], snapshot()));
        }
        elementData[index] = element;
        emit(new Insert(element, index, snapshot()));
    }

    /** Append at the tail (caller holds the mutation guard). May grow first. */
    private void appendInternal(E element) {
        ensureCapacity(size + 1);                  // may emit Grow (size still old)
        int index = size;
        elementData[index] = element;
        size++;
        modCount++;
        emit(new Append(element, index, snapshot()));
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity - elementData.length > 0) grow(minCapacity);
    }

    /** Grow the backing array to hold at least {@code minCapacity}, emitting {@link Grow}. */
    private void grow(int minCapacity) {
        ListSnapshot before = snapshot();
        int oldCapacity = elementData.length;
        int newCapacity;
        if (elementData == DEFAULTCAPACITY_EMPTY_ELEMENTDATA) {
            newCapacity = Math.max(DEFAULT_CAPACITY, minCapacity);   // 0 -> 10 jump (fresh allocation)
            elementData = new Object[newCapacity];
        } else {
            newCapacity = newCapacity(oldCapacity, minCapacity);
            elementData = Arrays.copyOf(elementData, newCapacity);
        }
        emit(new Grow(oldCapacity, newCapacity, before, snapshot()));
    }

    /**
     * The 1.5× grown capacity (JDK {@code newLength} floor), clamped near the maximum
     * array length like {@code java.util.ArrayList}. Uses {@link #MAX_ARRAY_SIZE}
     * rather than the HashMap's power-of-two {@code MAXIMUM_CAPACITY}, because a list
     * capacity is not a power of two. Package-private (with {@link #hugeCapacity}) so
     * the clamp is unit-testable without a billion-element allocation.
     */
    static int newCapacity(int oldCapacity, int minCapacity) {
        int newCapacity = oldCapacity + Math.max(minCapacity - oldCapacity, oldCapacity >> 1);
        if (newCapacity < 0 || newCapacity - MAX_ARRAY_SIZE > 0) { // int-overflow-safe
            return hugeCapacity(minCapacity);
        }
        return newCapacity;
    }

    static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0) throw new OutOfMemoryError(); // the requested size itself overflowed int
        return (minCapacity > MAX_ARRAY_SIZE) ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
    }

    // --- event dispatch ---------------------------------------------------------

    public void addListener(StructureEventListener<ListEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(StructureEventListener<ListEvent> listener) {
        listeners.remove(listener);
    }

    private void emit(ListEvent event) {
        // Copy so a listener may add/remove listeners during dispatch.
        for (StructureEventListener<ListEvent> listener : List.copyOf(listeners)) listener.onEvent(event);
    }

    /** Marks the start of a mutation, rejecting a re-entrant one from a listener. */
    private void beginMutation() {
        if (mutating) {
            throw new ConcurrentModificationException(
                "list mutated from within an event listener; listeners may read the list "
                + "but must not add/set/remove during event dispatch");
        }
        mutating = true;
    }

    /** Immutable whole-list snapshot: filled slots [0,size), empty slots [size,capacity). */
    ListSnapshot snapshot() {
        List<SlotSnapshot> slots = new ArrayList<>(elementData.length);
        for (int i = 0; i < elementData.length; i++) {
            slots.add(i < size ? new FilledSlot(elementData[i]) : EmptySlot.INSTANCE);
        }
        return new ListSnapshot(elementData.length, size, slots);
    }
}
