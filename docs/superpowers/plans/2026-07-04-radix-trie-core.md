# Teaching Radix Trie — Core — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A complete instrumented radix (PATRICIA) trie `RadixTrie<V> extends AbstractMap<String,V>` — insert with edge-splitting, get, remove with prune/merge, sorted `entrySet` + `keysWithPrefix` — emitting a sealed `TrieEvent` stream with a new N-ary `String`-labelled `TrieSnapshot`, plugged into the generic substrate. First real consumer of `com.gimlism.translucent.substrate`. Viz deferred to the next slice.

**Architecture:** `trie.events` holds the sealed `TrieEvent` (`Descend`/`CreateNode`/`SplitEdge`/`Put`/`Remove`/`MergeEdge`/`Prune`), the `TrieSnapshot`/`TrieNodeSnapshot`/`TrieEdge` records, the `TrieEventListener` alias and `TrieEventFormatter`. `trie.core.RadixTrie` + package-private `TrieNode` implement the algorithm and emit through `StructureEventListener<TrieEvent>`. `trie.consumer` has the console logger + a recorder alias; `trie.demo` a scripted demo.

**Tech Stack:** Java 21, Maven, JUnit 5. Depends on `substrate`.

## Global Constraints

- Java 21, Maven, JDK 26. Package `com.gimlism.translucent.trie.{core,events,consumer,demo}`; depends on `substrate`, not on `hashmap.*`/`arraylist.*`.
- `TrieEvent extends StructureEvent` (covariant `TrieSnapshot after()`); `TrieSnapshot implements StructureSnapshot`. `TrieEventListener extends StructureEventListener<TrieEvent>`; `TrieRecordingListener extends substrate RecordingListener<TrieEvent>`.
- **Radix invariants** (asserted after every insert AND delete): every non-root node has a non-empty `edgeLabel`; a node's children have distinct first characters; every non-key non-root node has ≥ 2 children.
- **Grammar** — insert: `Descend* → [SplitEdge] → [CreateNode] → Put`; remove: `Remove → [Prune] → [MergeEdge]` (or `Remove → MergeEdge`). The `Put`/`Remove` marker for the logical op; structural events precede/follow per the grammar.
- Null values permitted (`isKey` flag distinguishes). `null` keys rejected. Re-entrancy guard + `modCount` as in the other structures.
- Snapshot per event; `Shift`-style mid-op transients are acceptable (invariants asserted at rest only).

## File structure

- `trie/events/`: `TrieEvent`, `Descend`, `CreateNode`, `SplitEdge`, `Put`, `Remove`, `MergeEdge`, `Prune`, `TrieSnapshot`, `TrieNodeSnapshot`, `TrieEdge`, `TrieEventListener`, `TrieEventFormatter`, `package-info`.
- `trie/consumer/`: `ConsoleTrieEventLogger`, `TrieRecordingListener`.
- `trie/core/`: `RadixTrie`, `TrieNode`.
- `trie/demo/`: `TrieDemo`.
- Tests + a package-private `RadixTrieInvariants` test helper under `src/test/.../trie/core/`.

---

### Task 1: Event + snapshot model, formatter, consumers

**Files:** create all `trie/events/*` and `trie/consumer/*`; tests `trie/events/TrieSnapshotTest.java`, `trie/events/TrieEventFormatterTest.java`.

**Interfaces:** sealed `TrieEvent extends StructureEvent` (7 records, each `TrieSnapshot after()`); `TrieSnapshot(TrieNodeSnapshot root, int size) implements StructureSnapshot`; `TrieNodeSnapshot(boolean key, Object value, List<TrieEdge> children)`; `TrieEdge(String label, TrieNodeSnapshot target)`; `TrieEventListener`; `TrieEventFormatter.format`; `ConsoleTrieEventLogger`; `TrieRecordingListener`.

- [ ] **Step 1: Write the failing tests**

`trie/events/TrieSnapshotTest.java`
```java
package com.gimlism.translucent.trie.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.gimlism.translucent.substrate.events.StructureEvent;
import com.gimlism.translucent.substrate.events.StructureSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrieSnapshotTest {
    private static TrieSnapshot leafKey(String label, Object v) {
        return new TrieSnapshot(
            new TrieNodeSnapshot(false, null,
                List.of(new TrieEdge(label, new TrieNodeSnapshot(true, v, List.of())))), 1);
    }

    @Test
    void snapshotAndEventAreStructureTypes() {
        TrieSnapshot snap = leafKey("hi", 7);
        assertInstanceOf(StructureSnapshot.class, snap);
        StructureEvent e = new Put("hi", 7, null, true, snap);
        assertInstanceOf(StructureEvent.class, e);
        assertEquals(snap, e.after());
    }

    @Test
    void childrenListIsImmutable() {
        var kids = new java.util.ArrayList<TrieEdge>();
        var node = new TrieNodeSnapshot(false, null, kids);
        kids.add(new TrieEdge("x", new TrieNodeSnapshot(true, 1, List.of())));
        assertEquals(0, node.children().size());               // defensive copy
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
            () -> node.children().add(null));
    }
}
```

`trie/events/TrieEventFormatterTest.java`
```java
package com.gimlism.translucent.trie.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TrieEventFormatterTest {
    private static final TrieSnapshot ANY = new TrieSnapshot(new TrieNodeSnapshot(false, null, List.of()), 0);

    @Test
    void labels() {
        assertEquals("DESCEND \"sh\" -> \"sh\"", TrieEventFormatter.format(new Descend("sh", "sh", ANY)));
        assertEquals("CREATE \"ore\" -> \"shore\"", TrieEventFormatter.format(new CreateNode("ore", "shore", ANY)));
        assertEquals("SPLIT \"ore\" @ \"o\" -> \"sho\"", TrieEventFormatter.format(new SplitEdge("ore", "o", "sho", ANY)));
        assertEquals("PUT \"shore\"=2 (new)", TrieEventFormatter.format(new Put("shore", 2, null, true, ANY)));
        assertEquals("PUT \"she\"=9 (replaced 8)", TrieEventFormatter.format(new Put("she", 9, 8, false, ANY)));
        assertEquals("REMOVE \"she\" (was 8)", TrieEventFormatter.format(new Remove("she", 8, ANY)));
        assertEquals("MERGE -> \"shore\"", TrieEventFormatter.format(new MergeEdge("shore", "shore", ANY)));
        assertEquals("PRUNE \"e\" <- \"she\"", TrieEventFormatter.format(new Prune("e", "she", ANY)));
    }
}
```

- [ ] **Step 2: Run — FAIL** (`trie.events` absent).

- [ ] **Step 3: Implement.** The records (all `implements TrieEvent`, `after()` returns `TrieSnapshot`):
```java
// TrieEvent.java
package com.gimlism.translucent.trie.events;
import com.gimlism.translucent.substrate.events.StructureEvent;
public sealed interface TrieEvent extends StructureEvent
        permits Descend, CreateNode, SplitEdge, Put, Remove, MergeEdge, Prune {
    TrieSnapshot after();
}
```
```java
// records (one per file)
public record Descend(String label, String path, TrieSnapshot after) implements TrieEvent {}
public record CreateNode(String label, String path, TrieSnapshot after) implements TrieEvent {}
public record SplitEdge(String originalLabel, String commonPrefix, String path, TrieSnapshot after) implements TrieEvent {}
public record Put(String key, Object value, Object previousValue, boolean newKey, TrieSnapshot after) implements TrieEvent {}
public record Remove(String key, Object removedValue, TrieSnapshot after) implements TrieEvent {}
public record MergeEdge(String mergedLabel, String path, TrieSnapshot after) implements TrieEvent {}
public record Prune(String label, String path, TrieSnapshot after) implements TrieEvent {}
```
```java
// TrieSnapshot.java
package com.gimlism.translucent.trie.events;
import com.gimlism.translucent.substrate.events.StructureSnapshot;
public record TrieSnapshot(TrieNodeSnapshot root, int size) implements StructureSnapshot {}
```
```java
// TrieNodeSnapshot.java
package com.gimlism.translucent.trie.events;
import java.util.List;
public record TrieNodeSnapshot(boolean key, Object value, List<TrieEdge> children) {
    public TrieNodeSnapshot { children = List.copyOf(children); }
}
```
```java
// TrieEdge.java
public record TrieEdge(String label, TrieNodeSnapshot target) {}
```
```java
// TrieEventListener.java (named alias, @FunctionalInterface, extends StructureEventListener<TrieEvent>)
// TrieEventFormatter.java — switch over the sealed TrieEvent producing the labels above.
```
`TrieEventFormatter.format`:
```java
public static String format(TrieEvent e) {
    return switch (e) {
        case Descend d -> "DESCEND \"" + d.label() + "\" -> \"" + d.path() + "\"";
        case CreateNode c -> "CREATE \"" + c.label() + "\" -> \"" + c.path() + "\"";
        case SplitEdge s -> "SPLIT \"" + s.originalLabel() + "\" @ \"" + s.commonPrefix() + "\" -> \"" + s.path() + "\"";
        case Put p -> p.newKey()
            ? "PUT \"" + p.key() + "\"=" + p.value() + " (new)"
            : "PUT \"" + p.key() + "\"=" + p.value() + " (replaced " + p.previousValue() + ")";
        case Remove r -> "REMOVE \"" + r.key() + "\" (was " + r.removedValue() + ")";
        case MergeEdge m -> "MERGE -> \"" + m.mergedLabel() + "\"";
        case Prune pr -> "PRUNE \"" + pr.label() + "\" <- \"" + pr.path() + "\"";
    };
}
```
`ConsoleTrieEventLogger` mirrors `ConsoleEventLogger` (prints `TrieEventFormatter.format`). `TrieRecordingListener extends com.gimlism.translucent.substrate.events.RecordingListener<TrieEvent>` (empty).

- [ ] **Step 4: Run — PASS.** **Step 5: Commit** `feat(trie): TrieEvent vocabulary, snapshot model, consumers`.

---

### Task 2: `RadixTrie` insert (with edge split) + reads

**Files:** create `trie/core/RadixTrie.java`, `trie/core/TrieNode.java`; test `trie/core/RadixTrieInsertTest.java`; test helper `trie/core/RadixTrieInvariants.java`.

**Interfaces:** `RadixTrie<V> extends AbstractMap<String,V>` with `put`/`get`/`containsKey`/`size`, `addListener`/`removeListener`, package-private `snapshot()`; `TrieNode<V>` (fields per spec). `RadixTrieInvariants.assertValid(TrieSnapshot, expectedKeys)`.

- [ ] **Step 1: Write the failing test + invariant helper**

`trie/core/RadixTrieInvariants.java` (test helper)
```java
package com.gimlism.translucent.trie.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;
import java.util.List;
import java.util.TreeSet;

final class RadixTrieInvariants {
    private RadixTrieInvariants() {}

    /** Assert radix invariants and that the key set equals {@code expected}; returns the key set. */
    static TreeSet<String> assertValid(TrieSnapshot snap, java.util.Set<String> expected) {
        TreeSet<String> keys = new TreeSet<>();
        walk(snap.root(), "", true, keys);
        assertEquals(new TreeSet<>(expected), keys, "key set");
        assertEquals(expected.size(), snap.size(), "size field");
        return keys;
    }

    private static void walk(TrieNodeSnapshot n, String prefix, boolean root, TreeSet<String> keys) {
        if (n.key()) keys.add(prefix);
        List<TrieEdge> kids = n.children();
        var firstChars = new TreeSet<Character>();
        for (TrieEdge e : kids) {
            assertFalse(e.label().isEmpty(), "empty edge label at \"" + prefix + "\"");
            assertTrue(firstChars.add(e.label().charAt(0)), "duplicate child first-char at \"" + prefix + "\"");
        }
        if (!root && !n.key()) {
            assertTrue(kids.size() >= 2, "non-key internal node must have >=2 children at \"" + prefix + "\"");
        }
        for (TrieEdge e : kids) walk(e.target(), prefix + e.label(), false, keys);
    }
}
```

`trie/core/RadixTrieInsertTest.java`
```java
package com.gimlism.translucent.trie.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.events.CreateNode;
import com.gimlism.translucent.trie.events.Descend;
import com.gimlism.translucent.trie.events.Put;
import com.gimlism.translucent.trie.events.SplitEdge;
import com.gimlism.translucent.trie.events.TrieEvent;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentModificationException;
import org.junit.jupiter.api.Test;

class RadixTrieInsertTest {
    private static String tag(TrieEvent e) {
        return switch (e) {
            case Descend d -> "DESC:" + d.label();
            case CreateNode c -> "CREATE:" + c.label();
            case SplitEdge s -> "SPLIT:" + s.originalLabel() + "@" + s.commonPrefix();
            case Put p -> "PUT:" + p.key();
            default -> e.getClass().getSimpleName();
        };
    }

    @Test
    void putGetRoundTripIncludingNullValueAndEmptyKey() {
        var t = new RadixTrie<Integer>();
        assertNull(t.put("she", 1));
        assertEquals(1, t.put("she", 2));      // replace returns old
        assertEquals(2, t.get("she"));
        t.put("", 0);                          // empty key -> root is a key
        assertEquals(0, t.get(""));
        t.put("shore", null);                  // null value permitted
        assertTrue(t.containsKey("shore"));
        assertNull(t.get("shore"));
        assertEquals(3, t.size());
        assertThrows(NullPointerException.class, () -> t.put(null, 1));
    }

    @Test
    void createLeafThenDescendSharesPrefix() {
        var t = new RadixTrie<Integer>();
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        t.put("she", 1);   // CREATE "she" -> PUT
        rec.clear();
        t.put("shell", 2); // DESC "she" -> CREATE "ll" -> PUT
        assertEquals(List.of("DESC:she", "CREATE:ll", "PUT:shell"),
            rec.events().stream().map(RadixTrieInsertTest::tag).toList());
    }

    @Test
    void splitWhenKeyEndsInsideEdge() {
        var t = new RadixTrie<Integer>();
        t.put("shore", 1);
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        t.put("sh", 2); // SPLIT "shore"@"sh" -> PUT (key ends at split node)
        assertEquals(List.of("SPLIT:shore@sh", "PUT:sh"),
            rec.events().stream().map(RadixTrieInsertTest::tag).toList());
        assertEquals(1, t.get("shore"));
        assertEquals(2, t.get("sh"));
    }

    @Test
    void splitWhenKeysDivergeMidEdge() {
        var t = new RadixTrie<Integer>();
        t.put("shore", 1);
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        t.put("shell", 2); // SPLIT "shore"@"sh" -> CREATE "ll" -> PUT
        assertEquals(List.of("SPLIT:shore@sh", "CREATE:ll", "PUT:shell"),
            rec.events().stream().map(RadixTrieInsertTest::tag).toList());
    }

    @Test
    void adversarialInsertKeepsRadixInvariants() {
        String[] keys = {"she","shell","shore","short","shrew","s","sh","romane","romanus","rom","a","ab","abc",""};
        var t = new RadixTrie<Integer>();
        var present = new java.util.HashSet<String>();
        for (int i = 0; i < keys.length; i++) {
            t.put(keys[i], i);
            present.add(keys[i]);
            RadixTrieInvariants.assertValid(t.snapshot(), present);
        }
        for (String k : keys) assertEquals(java.util.Arrays.asList(keys).indexOf(k), t.get(k));
    }

    @Test
    void reentrantMutationFromListenerRejected() {
        var t = new RadixTrie<Integer>();
        t.addListener(e -> t.put("x", 0));
        assertThrows(ConcurrentModificationException.class, () -> t.put("a", 1));
    }
}
```

- [ ] **Step 2: Run — FAIL** (`RadixTrie` absent).

- [ ] **Step 3: Implement `TrieNode` + `RadixTrie` insert/reads.**

`TrieNode.java`
```java
package com.gimlism.translucent.trie.core;

import java.util.TreeMap;

/** A radix-trie node: a (compressed) incoming edge label, an optional value, and children by first char. */
class TrieNode<V> {
    String edgeLabel;                 // label on the edge from the parent ("" for the root)
    boolean isKey;
    V value;                          // meaningful iff isKey
    final TreeMap<Character, TrieNode<V>> children = new TreeMap<>();

    TrieNode(String edgeLabel) { this.edgeLabel = edgeLabel; }
}
```

`RadixTrie.java` — core (insert/reads); iteration in Task 3, remove in Task 4:
```java
package com.gimlism.translucent.trie.core;

import com.gimlism.translucent.substrate.events.StructureEventListener;
import com.gimlism.translucent.trie.events.CreateNode;
import com.gimlism.translucent.trie.events.Descend;
import com.gimlism.translucent.trie.events.Put;
import com.gimlism.translucent.trie.events.SplitEdge;
import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieEvent;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A teaching radix (PATRICIA) trie: a Map by shared prefix, with compressed String-labelled edges. */
public class RadixTrie<V> extends AbstractMap<String, V> {

    private final TrieNode<V> root = new TrieNode<>("");
    private int size;
    int modCount;

    private final List<StructureEventListener<TrieEvent>> listeners = new ArrayList<>();
    private boolean mutating;

    @Override public int size() { return size; }

    @Override
    @SuppressWarnings("unchecked")
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

    /** Node exactly at {@code key} (full edge labels consumed), or null if the path breaks. */
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
            String s = key, path = "";
            while (true) {
                if (s.isEmpty()) {
                    V old = node.isKey ? node.value : null;
                    boolean newKey = !node.isKey;
                    node.isKey = true;
                    node.value = value;
                    if (newKey) size++;
                    modCount++;
                    emit(new Put(key, value, old, newKey, snapshot()));
                    return old;
                }
                char c = s.charAt(0);
                TrieNode<V> child = node.children.get(c);
                if (child == null) {
                    TrieNode<V> leaf = new TrieNode<>(s);
                    node.children.put(c, leaf);
                    node = leaf; path += s;
                    emit(new CreateNode(s, path, snapshot()));
                    s = "";
                    continue;                       // -> Put on leaf
                }
                String L = child.edgeLabel;
                int p = commonPrefixLength(s, L);
                if (p == L.length()) {
                    node = child; path += L; s = s.substring(p);
                    emit(new Descend(L, path, snapshot()));
                    continue;
                }
                // split at p (1 <= p < L.length())
                String common = L.substring(0, p);
                TrieNode<V> mid = new TrieNode<>(common);
                child.edgeLabel = L.substring(p);
                node.children.put(c, mid);
                mid.children.put(child.edgeLabel.charAt(0), child);
                path += common;
                emit(new SplitEdge(L, common, path, snapshot()));
                if (p == s.length()) {
                    node = mid; s = "";
                    continue;                       // -> Put on mid (key ends here)
                }
                String rest = s.substring(p);
                TrieNode<V> leaf = new TrieNode<>(rest);
                mid.children.put(rest.charAt(0), leaf);
                node = leaf; path += rest;
                emit(new CreateNode(rest, path, snapshot()));
                s = "";
                continue;                           // -> Put on leaf
            }
        } finally {
            mutating = false;
        }
    }

    private static int commonPrefixLength(String a, String b) {
        int n = Math.min(a.length(), b.length()), i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    // --- snapshot + dispatch ---
    TrieSnapshot snapshot() { return new TrieSnapshot(snap(root), size); }

    private TrieNodeSnapshot snap(TrieNode<V> n) {
        List<TrieEdge> kids = new ArrayList<>(n.children.size());
        for (TrieNode<V> c : n.children.values()) kids.add(new TrieEdge(c.edgeLabel, snap(c)));
        return new TrieNodeSnapshot(n.isKey, n.isKey ? n.value : null, kids);
    }

    public void addListener(StructureEventListener<TrieEvent> l) { listeners.add(l); }
    public void removeListener(StructureEventListener<TrieEvent> l) { listeners.remove(l); }

    private void emit(TrieEvent e) {
        for (StructureEventListener<TrieEvent> l : List.copyOf(listeners)) l.onEvent(e);
    }

    private void beginMutation() {
        if (mutating) {
            throw new ConcurrentModificationException(
                "trie mutated from within an event listener; listeners may read but not mutate during dispatch");
        }
        mutating = true;
    }

    @Override
    public Set<Map.Entry<String, V>> entrySet() {
        throw new UnsupportedOperationException("entrySet arrives in Task 3");
    }
}
```

- [ ] **Step 4: Run — PASS** (insert cases + adversarial invariants + reentrancy green). **Step 5: Commit** `feat(trie): radix insert with edge splitting + reads`.

---

### Task 3: Iteration (`entrySet`, sorted DFS, fail-fast) + `keysWithPrefix`

**Files:** modify `RadixTrie.java`; test `trie/core/RadixTrieIterationTest.java`.

**Interfaces:** replace the `entrySet` stub with a real sorted-DFS `AbstractSet` view whose iterator is `modCount` fail-fast and whose `remove()` delegates to `remove(key)` (works once Task 4 lands; test `Iterator.remove` there). Add `keysWithPrefix(String)`.

- [ ] **Step 1: Write the failing test**
```java
// RadixTrieIterationTest.java
@Test void iteratesKeysInLexicographicOrder() { /* put she,shell,shore,short,a,ab; assert keySet()/entrySet order */ }
@Test void keysWithPrefixReturnsSortedMatches() { /* keysWithPrefix("sh") == [she,shell,shore,short]; "" == all; "zzz" == [] */ }
@Test void failFastOnStructuralChangeDuringIteration() { /* iterator then put -> ConcurrentModificationException */ }
```
(Full assertions: exact `List.copyOf(t.keySet())` equals the sorted expected list; `t.keysWithPrefix("sh")` equals the sorted sublist; a `put` mid-iteration throws `ConcurrentModificationException` from `it.next()`.)

- [ ] **Step 2: Run — FAIL** (entrySet throws UOE).

- [ ] **Step 3: Implement.** Collect entries by sorted DFS into a list; the `AbstractSet` iterator walks that list, checking `modCount` for fail-fast and delegating `remove()` to `RadixTrie.remove`:
```java
    @Override
    public Set<Map.Entry<String, V>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size() { return size; }
            @Override public Iterator<Map.Entry<String, V>> iterator() { return new EntryIterator(); }
        };
    }

    /** Lexicographic list of all key entries (sorted DFS; children already sorted by first char). */
    private List<Map.Entry<String, V>> collect() {
        List<Map.Entry<String, V>> out = new ArrayList<>();
        collect(root, "", out);
        return out;
    }
    private void collect(TrieNode<V> n, String prefix, List<Map.Entry<String, V>> out) {
        if (n.isKey) out.add(new AbstractMap.SimpleImmutableEntry<>(prefix, n.value));
        for (TrieNode<V> c : n.children.values()) collect(c, prefix + c.edgeLabel, out);
    }

    /** Keys with the given prefix, lexicographically. */
    public List<String> keysWithPrefix(String prefix) {
        // walk to the node covering `prefix` (may end mid-edge), then DFS-collect
        TrieNode<V> node = root; String s = prefix; String at = "";
        while (!s.isEmpty()) {
            TrieNode<V> child = node.children.get(s.charAt(0));
            if (child == null) return List.of();
            String L = child.edgeLabel;
            int p = commonPrefixLength(s, L);
            if (p == s.length()) { node = child; at += L; break; }   // prefix ends inside/at this edge
            if (p < L.length()) return List.of();                    // diverges -> no matches
            node = child; at += L; s = s.substring(L.length());
        }
        List<Map.Entry<String, V>> entries = new ArrayList<>();
        collect(node, at, entries);
        List<String> keys = new ArrayList<>(entries.size());
        for (var e : entries) keys.add(e.getKey());
        return keys;
    }

    private final class EntryIterator implements Iterator<Map.Entry<String, V>> {
        private final Iterator<Map.Entry<String, V>> it = collect().iterator();
        private int expectedModCount = modCount;
        private Map.Entry<String, V> last;
        @Override public boolean hasNext() { return it.hasNext(); }
        @Override public Map.Entry<String, V> next() {
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            return last = it.next();
        }
        @Override public void remove() {
            if (last == null) throw new IllegalStateException();
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            RadixTrie.this.remove(last.getKey());
            expectedModCount = modCount;
            last = null;
        }
    }
```
(Add imports: `AbstractSet`, `Iterator`.) `keySet()`/`values()`/`containsValue()` derive from `entrySet` via `AbstractMap`.

- [ ] **Step 4: Run — PASS** (iteration order + prefix + fail-fast; `Iterator.remove` covered in Task 4). **Step 5: Commit** `feat(trie): sorted entrySet iteration, fail-fast, keysWithPrefix`.

---

### Task 4: `remove` (prune + merge)

**Files:** modify `RadixTrie.java`; test `trie/core/RadixTrieRemoveTest.java`.

**Interfaces:** `remove(Object)` deletes a key, then restores invariants (`Prune` a leaf; `MergeEdge` a node/parent left with one child), emitting `Remove → [Prune] → [MergeEdge]`.

- [ ] **Step 1: Write the failing test**
```java
// RadixTrieRemoveTest.java — key scenarios:
@Test void removeBranchKeyEmitsOnlyRemove() { /* she,shell,shore then remove "sh"? build a branch key; only REMOVE */ }
@Test void removeLeafPrunesAndMergesParent() { /* she,shell -> remove shell: PRUNE "ll" then MERGE parent -> "she" */ }
@Test void removeKeyWithOneChildMerges() { /* sh,shore -> remove "sh": REMOVE then MERGE -> "shore" */ }
@Test void removeAbsentReturnsNull() { /* remove missing / non-key node returns null, no events */ }
@Test void adversarialDeleteKeepsRadixInvariants() {
    // build the Task-2 key set, delete in mixed order, assert invariants + exact remaining set after each delete
}
@Test void iteratorRemoveDeletes() { /* entrySet iterator.remove drops the entry, size--, fail-fast consistent */ }
@Test void eventOrderPerScenario() { /* RecordingListener asserts REMOVE -> PRUNE -> MERGE etc. exactly */ }
```

- [ ] **Step 2: Run — FAIL** (remove not overridden → AbstractMap uses entrySet remove which now works... so override to emit events + prune/merge; test asserts events/invariants that the default can't satisfy).

- [ ] **Step 3: Implement.**
```java
    @Override
    public V remove(Object key) {
        if (!(key instanceof String k)) return null;
        beginMutation();
        try {
            TrieNode<V> parent = null, node = root;
            String s = k;
            while (!s.isEmpty()) {
                TrieNode<V> child = node.children.get(s.charAt(0));
                if (child == null || !s.startsWith(child.edgeLabel)) return null; // absent path
                parent = node; node = child; s = s.substring(child.edgeLabel.length());
            }
            if (!node.isKey) return null;               // node exists but isn't a key
            V old = node.value;
            node.isKey = false; node.value = null;
            size--; modCount++;
            emit(new Remove(k, old, snapshot()));

            if (node == root) return old;               // removed the "" key; root stays
            if (node.children.size() >= 2) return old;  // still a branch
            if (node.children.size() == 1) {            // non-key 1-child -> absorb child
                String startPrefix = k.substring(0, k.length() - node.edgeLabel.length());
                mergeWithChild(node, startPrefix);
                return old;
            }
            // leaf: prune from parent
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

    /** Absorb {@code node}'s sole child into it (concatenate labels); {@code startPrefix} = path to node's start. */
    private void mergeWithChild(TrieNode<V> node, String startPrefix) {
        TrieNode<V> ch = node.children.firstEntry().getValue();
        node.children.clear();
        node.edgeLabel = node.edgeLabel + ch.edgeLabel;
        node.isKey = ch.isKey;
        node.value = ch.value;
        node.children.putAll(ch.children);
        emit(new MergeEdge(node.edgeLabel, startPrefix + node.edgeLabel, snapshot()));
    }
```

- [ ] **Step 4: Run — PASS** (prune/merge scenarios + adversarial delete invariants + iterator.remove + event order). **Step 5: Commit** `feat(trie): radix remove with prune and edge merge`.

---

### Task 5: Demo

**Files:** create `trie/demo/TrieDemo.java`; test `trie/demo/TrieDemoTest.java`.

**Interfaces:** `TrieDemo.run(PrintStream)` inserts keys with rich shared prefixes (forcing `Descend`/`CreateNode`/`SplitEdge`) then removes to force `Prune`/`MergeEdge`, driving `ConsoleTrieEventLogger`; output contains `DESCEND`, `CREATE`, `SPLIT`, `PUT`, `REMOVE`, `MERGE`, `PRUNE`.

- [ ] **Step 1–5:** Test asserts the output contains each label; implement `run` (e.g. put `shore, she, shell, short, sh`, then remove `shell, sh`); `main` calls `run(System.out)`. Commit `feat(trie): scripted radix-trie demo`.

---

## Self-Review

**Spec coverage:** radix insert with split (key-ends-inside / diverge) → Task 2; sorted iteration + keysWithPrefix + fail-fast → Task 3; remove with prune/merge → Task 4; adversarial radix-invariant gate after insert AND delete → Tasks 2/4; sealed `TrieEvent` extends `StructureEvent`, `TrieSnapshot implements StructureSnapshot`, substrate recorder/listener → Tasks 1–2; null values + empty key + null-key rejection → Task 2; re-entrancy guard → Task 2; demo → Task 5. ✓

**Placeholder scan:** Task 3/4/5 test bodies are sketched with exact intent; expand to full assertions when writing (the impl code is complete). No placeholders in production code.

**Type consistency:** `TrieEvent.after(): TrieSnapshot` (covariant); node fields `edgeLabel/isKey/value/children`; `snapshot()` package-private; events carry `path`/`label` strings as in the formatter. `commonPrefixLength` shared by `put`/`keysWithPrefix`.

## Notes for the reviewer / final review

- **`put` split + `remove` merge are the crux** — the adversarial invariant tests (after every insert and delete, across rich shared-prefix key sets incl. `""` and prefix-of-another keys) are the gate. Recommend the strong model for Tasks 2 and 4.
- **Path bookkeeping** in events is best-effort for the (next-slice) renderer; correctness never depends on it. The `MergeEdge`/`Prune` `path` derivations from `k` and edge labels are the fiddly bit — verify against the split cases.
- **Announce/settle transient:** `CreateNode` emits a snapshot with a not-yet-key leaf before the terminal `Put` (like the map's treeify-announce). Invariants are asserted at rest, not mid-burst.
- Deferred by design: the variable-fan-out **viz** (next slice), a **second implementation**, and prefix ops beyond `keysWithPrefix`.
```
