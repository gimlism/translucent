# Standard (one-char-per-edge) trie + compression compare — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an uncompressed, one-char-per-edge `StandardTrie<V>` beside `RadixTrie`, emitting the shared `trie.events` subset, plus a consumer-side node-count metric and a compression-compare demo that quantifies how many nodes the radix compression saves.

**Architecture:** `StandardTrie<V> extends AbstractMap<String,V>` in `trie.core`, backed by `StandardTrieNode<V>` (one `char` per edge — the edge char is the child's key in its parent's `TreeMap<Character,child>`; no `edgeLabel` field). It reuses the existing `trie.events` sealed `TrieEvent` type and `TrieSnapshot`/`TrieNodeSnapshot`/`TrieEdge` records verbatim (labels are length-1 strings), emitting only the subset `Descend`/`CreateNode`/`Put`/`Remove`/`Prune` — never `SplitEdge`/`MergeEdge`. A new `trie.compare` package holds `TrieMetrics.nodeCount(TrieSnapshot)` and `CompressionCompareDemo`.

**Tech Stack:** Java 21, Maven, JUnit 5. Build: `mvn`.

## Global Constraints

- **Additive only.** `RadixTrie`, the whole `trie.events` package, `trie.consumer`, and the substrate stay **byte-unchanged**. This slice only creates new files (`trie.core.StandardTrie`, `trie.core.StandardTrieNode`, `trie.compare.*`) and their tests.
- **Contract-identical to `RadixTrie`.** Same `Map<String,V>` behaviour so compression is the *only* measured difference: null-key rejected (`NullPointerException`), empty-string key at root, `get`/`containsKey`/`remove` tolerate non-String/null args (return null/false, no throw), immutable `entrySet` snapshots, `modCount` fail-fast CME iterator, `TreeMap`-ordered children (lexicographic), value-replace is non-structural (no `size`/`modCount`/`CreateNode`).
- **Per-char granularity.** `put`/`remove` narrate one character at a time: one `Descend` per matched char, one `CreateNode` per new char, one `Prune` per node that becomes childless-and-non-key on the way up.
- **Silent no-op removes.** Buffer the walk; narrate `Descend*` only when the removal actually proceeds. Absent-key and non-key-node removes emit nothing.
- **Snapshot-before-settled honoured trivially.** Each event's `after()` reflects the state *after* that step's mutation (mutate → `snapshot()` → `emit`), exactly as `RadixTrie` does.
- **Run the full suite** (`mvn -q test`) at the end of every task; every task ends green. Package name for all Java files: `com.gimlism.translucent.trie.*`.
- **Commit** ending with the repo's `Co-Authored-By:` / `Claude-Session:` trailers (copy from a recent commit).

---

## File structure

- `src/main/java/.../trie/core/StandardTrieNode.java` — package-private node: `isKey`, `value`, `TreeMap<Character,StandardTrieNode<V>> children`. No edge-label field.
- `src/main/java/.../trie/core/StandardTrie.java` — the public `AbstractMap<String,V>`; put/get/containsKey/remove/size/entrySet/keysWithPrefix + `snapshot()` + dispatcher plumbing.
- `src/main/java/.../trie/compare/TrieMetrics.java` — `public static int nodeCount(TrieSnapshot)`.
- `src/main/java/.../trie/compare/CompressionCompareDemo.java` — builds both tries from a key set, returns node counts.
- `src/main/java/.../trie/compare/package-info.java` — package doc.
- `src/test/java/.../trie/core/StandardTrieInsertTest.java`
- `src/test/java/.../trie/core/StandardTrieRemoveTest.java`
- `src/test/java/.../trie/core/StandardTrieInvariants.java` — test-only invariant checker + adversarial + no-Split/Merge grammar pin.
- `src/test/java/.../trie/compare/TrieMetricsTest.java`
- `src/test/java/.../trie/compare/CompressionCompareDemoTest.java`

Base path prefix `src/{main,test}/java/com/gimlism/translucent/` is abbreviated `.../` below.

---

### Task 1: `StandardTrie` — insert & read

**Files:**
- Create: `.../trie/core/StandardTrieNode.java`
- Create: `.../trie/core/StandardTrie.java`
- Test: `.../trie/core/StandardTrieInsertTest.java`

**Interfaces:**
- Consumes: `trie.events.{Descend,CreateNode,Put,TrieEvent,TrieSnapshot,TrieNodeSnapshot,TrieEdge}`, `substrate.events.{EventDispatcher,StructureEventListener}` (all pre-existing, unchanged).
- Produces:
  - `public class StandardTrie<V> extends AbstractMap<String,V>` with `public V put(String,V)`, `public V get(Object)`, `public boolean containsKey(Object)`, `public int size()`, `public Set<Map.Entry<String,V>> entrySet()`, `public List<String> keysWithPrefix(String)`, `public void addListener(StructureEventListener<TrieEvent>)`, `public void removeListener(...)`, package-private `TrieSnapshot snapshot()`, package-private `int modCount`.
  - `class StandardTrieNode<V>` (package-private) with fields `boolean isKey; V value; final TreeMap<Character,StandardTrieNode<V>> children`.

- [ ] **Step 1: Write the node class**

Create `.../trie/core/StandardTrieNode.java`:

```java
package com.gimlism.translucent.trie.core;

import java.util.TreeMap;

/**
 * A standard-trie node: one character per edge, so a node carries no edge label of its own
 * (its incoming character is the key under which its parent holds it). Contrast with
 * {@code TrieNode}, whose {@code edgeLabel} compresses a whole single-child chain into one edge.
 */
class StandardTrieNode<V> {
    boolean isKey;
    V value;                          // meaningful iff isKey
    final TreeMap<Character, StandardTrieNode<V>> children = new TreeMap<>();
}
```

- [ ] **Step 2: Write the failing insert test**

Create `.../trie/core/StandardTrieInsertTest.java`:

```java
package com.gimlism.translucent.trie.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.events.CreateNode;
import com.gimlism.translucent.trie.events.Descend;
import com.gimlism.translucent.trie.events.Put;
import com.gimlism.translucent.trie.events.TrieEvent;
import java.util.ConcurrentModificationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class StandardTrieInsertTest {
    private static String tag(TrieEvent e) {
        return switch (e) {
            case Descend d -> "DESC:" + d.label();
            case CreateNode c -> "CREATE:" + c.label();
            case Put p -> "PUT:" + p.key();
            default -> e.getClass().getSimpleName();
        };
    }

    @Test
    void putGetRoundTripIncludingNullValueAndEmptyKey() {
        var t = new StandardTrie<Integer>();
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
    void insertGrowsOneNodePerCharThenDescendsSharedChars() {
        var t = new StandardTrie<Integer>();
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        t.put("she", 1);   // CREATE s,h,e -> PUT
        assertEquals(List.of("CREATE:s", "CREATE:h", "CREATE:e", "PUT:she"),
            rec.events().stream().map(StandardTrieInsertTest::tag).toList());
        rec.clear();
        t.put("shell", 2); // DESC s,h,e -> CREATE l,l -> PUT
        assertEquals(List.of("DESC:s", "DESC:h", "DESC:e", "CREATE:l", "CREATE:l", "PUT:shell"),
            rec.events().stream().map(StandardTrieInsertTest::tag).toList());
        assertEquals(1, t.get("she"));
        assertEquals(2, t.get("shell"));
    }

    @Test
    void emptyKeyPutIsSilentWalkJustPut() {
        var t = new StandardTrie<Integer>();
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        t.put("", 7);
        assertEquals(List.of("PUT:"), rec.events().stream().map(StandardTrieInsertTest::tag).toList());
    }

    @Test
    void putEventCarriesPathEqualToKey() {
        var t = new StandardTrie<Integer>();
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        t.put("shore", 1);
        Put p = (Put) rec.events().get(rec.events().size() - 1);
        assertEquals("shore", p.key());
        assertEquals("shore", p.path());
    }

    @Test
    void keysWithPrefixReturnsLexicographicSnapshot() {
        var t = new StandardTrie<Integer>();
        t.put("she", 1); t.put("shell", 2); t.put("shore", 3); t.put("shy", 4); t.put("other", 5);
        assertEquals(List.of("she", "shell", "shore", "shy"), t.keysWithPrefix("sh"));
        assertEquals(List.of(), t.keysWithPrefix("zzz"));
        assertThrows(NullPointerException.class, () -> t.keysWithPrefix(null));
    }

    @Test
    void nonStringAndNullArgumentsAreTolerated() {
        var t = new StandardTrie<Integer>();
        t.put("she", 1);
        assertNull(t.get(42));
        assertNull(t.get(null));
        assertFalse(t.containsKey(42));
        assertFalse(t.containsKey(null));
        assertEquals(1, t.size());
    }

    @Test
    void valueReplaceIsNonStructuralAndDoesNotInvalidateIterators() {
        var t = new StandardTrie<Integer>();
        t.put("a", 0);
        t.put("b", 1);
        var it = t.entrySet().iterator();
        it.next();
        t.put("a", 99);                 // value replace: non-structural, no modCount bump
        assertEquals(99, t.get("a"));
        assertEquals(2, t.size());
        assertDoesNotThrow(it::next);
    }

    @Test
    void reentrantMutationFromListenerRejected() {
        var t = new StandardTrie<Integer>();
        t.addListener(e -> t.put("x", 0));
        assertThrows(ConcurrentModificationException.class, () -> t.put("a", 1));
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `mvn -q test-compile`
Expected: FAIL — `StandardTrie` does not exist / cannot find symbol.

- [ ] **Step 4: Write `StandardTrie` (insert & read)**

Create `.../trie/core/StandardTrie.java`:

```java
package com.gimlism.translucent.trie.core;

import com.gimlism.translucent.substrate.events.EventDispatcher;
import com.gimlism.translucent.substrate.events.StructureEventListener;
import com.gimlism.translucent.trie.events.CreateNode;
import com.gimlism.translucent.trie.events.Descend;
import com.gimlism.translucent.trie.events.Put;
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
```

Note: `remove(...)` is referenced by `EntryIterator.remove` and is implemented in Task 2. Until then it resolves to `AbstractMap.remove`, which throws `UnsupportedOperationException` (this slice's entrySet is read-only until Task 2). That is fine — no Task-1 test calls iterator `remove`.

- [ ] **Step 5: Run the insert test to verify it passes**

Run: `mvn -q -Dtest=StandardTrieInsertTest test`
Expected: PASS (7 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/core/StandardTrieNode.java \
        src/main/java/com/gimlism/translucent/trie/core/StandardTrie.java \
        src/test/java/com/gimlism/translucent/trie/core/StandardTrieInsertTest.java
git commit -m "feat(trie): StandardTrie one-char-per-edge insert & read"
```

---

### Task 2: `StandardTrie` — remove (prune cascade) & iterator remove

**Files:**
- Modify: `.../trie/core/StandardTrie.java` (add `remove(Object)`)
- Test: `.../trie/core/StandardTrieRemoveTest.java`

**Interfaces:**
- Consumes: Task 1's `StandardTrie`/`StandardTrieNode`; `trie.events.{Remove,Prune,Descend}`.
- Produces: `public V remove(Object key)` on `StandardTrie` — walks char-by-char; silent no-op for a path break or non-key node; on a real removal narrates buffered `Descend*`, then `Remove`, then cascades `Prune` up the childless-and-non-key chain (root never pruned). Never emits `MergeEdge`/`SplitEdge`.

- [ ] **Step 1: Write the failing remove test**

Create `.../trie/core/StandardTrieRemoveTest.java`:

```java
package com.gimlism.translucent.trie.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.events.CreateNode;
import com.gimlism.translucent.trie.events.Descend;
import com.gimlism.translucent.trie.events.Prune;
import com.gimlism.translucent.trie.events.Put;
import com.gimlism.translucent.trie.events.Remove;
import com.gimlism.translucent.trie.events.TrieEvent;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StandardTrieRemoveTest {
    private static String tag(TrieEvent e) {
        return switch (e) {
            case Descend d -> "DESC:" + d.label();
            case CreateNode c -> "CREATE:" + c.label();
            case Put p -> "PUT:" + p.key();
            case Remove r -> "REMOVE:" + r.key();
            case Prune pr -> "PRUNE:" + pr.label();
            default -> e.getClass().getSimpleName();
        };
    }

    private static StandardTrie<Integer> of(String... keys) {
        var t = new StandardTrie<Integer>();
        for (int i = 0; i < keys.length; i++) t.put(keys[i], i);
        return t;
    }

    @Test
    void removeAbsentReturnsNullNoEvents() {
        var t = of("she", "shore");
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        assertNull(t.remove("xyz"));   // path breaks
        assertNull(t.remove("sh"));    // node exists but isn't a key
        assertNull(t.remove(42));      // non-String
        assertNull(t.remove(null));    // null
        assertEquals(0, rec.events().size());
        assertEquals(2, t.size());
    }

    @Test
    void removeLeafPrunesCascadingChainUpToAKeyNode() {
        // she, shell share s-h-e; e is a key (she), then l-l (shell).
        // remove shell: prune the two l nodes; stop at e (a key).
        var t = of("she", "shell");
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        assertEquals(1, t.remove("shell"));
        assertEquals(List.of("DESC:s", "DESC:h", "DESC:e", "DESC:l", "DESC:l",
                "REMOVE:shell", "PRUNE:l", "PRUNE:l"),
            rec.events().stream().map(StandardTrieRemoveTest::tag).toList());
        assertEquals(0, t.get("she"));
        assertFalse(t.containsKey("shell"));
        assertEquals(1, t.size());
    }

    @Test
    void removeLeafCascadesPastNonKeyNodesToABranch() {
        // shell, shore branch at h (non-key, children e/o). remove shell:
        // prune l,l,e; stop at h (still has child o) -> shore survives, no merge event.
        var t = of("shell", "shore");
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        assertEquals(0, t.remove("shell"));
        assertEquals(List.of("DESC:s", "DESC:h", "DESC:e", "DESC:l", "DESC:l",
                "REMOVE:shell", "PRUNE:l", "PRUNE:l", "PRUNE:e"),
            rec.events().stream().map(StandardTrieRemoveTest::tag).toList());
        assertEquals(1, t.get("shore"));
        assertEquals(1, t.size());
    }

    @Test
    void removeBranchKeyUnmarksWithoutPruning() {
        // sh is a key AND a branch (children e, o). remove sh: unmark only, no prune.
        var t = of("she", "shore", "sh");
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        assertEquals(2, t.remove("sh"));
        assertEquals(List.of("DESC:s", "DESC:h", "REMOVE:sh"),
            rec.events().stream().map(StandardTrieRemoveTest::tag).toList());
        assertFalse(t.containsKey("sh"));
        assertEquals(0, t.get("she"));
        assertEquals(1, t.get("shore"));
    }

    @Test
    void removeEmptyKeyKeepsRoot() {
        var t = of("", "a");
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        assertEquals(0, t.remove(""));
        assertEquals(List.of("REMOVE:"), rec.events().stream().map(StandardTrieRemoveTest::tag).toList());
        assertFalse(t.containsKey(""));
        assertEquals(1, t.get("a"));
        assertEquals(1, t.size());
    }

    @Test
    void removeEventCarriesPathEqualToKey() {
        var t = of("she", "shore");
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        t.remove("she");
        Remove r = rec.events().stream().filter(e -> e instanceof Remove)
            .map(e -> (Remove) e).findFirst().orElseThrow();
        assertEquals("she", r.key());
        assertEquals("she", r.path());
    }

    @Test
    void iteratorRemoveDeletesAndContinues() {
        var t = of("a", "ab", "b", "c");
        Iterator<Map.Entry<String, Integer>> it = t.entrySet().iterator();
        it.next();     // "a"
        it.remove();   // deletes "a"; modCount resynced
        var rest = new ArrayList<String>();
        while (it.hasNext()) rest.add(it.next().getKey());
        assertEquals(List.of("ab", "b", "c"), rest);
        assertFalse(t.containsKey("a"));
        assertEquals(3, t.size());
    }

    @Test
    void clearEmptiesTheTrie() {
        var t = of("a", "ab", "abc", "b", "");
        t.clear();
        assertTrue(t.isEmpty());
        assertEquals(0, t.size());
        assertFalse(t.containsKey("abc"));
        assertNull(t.get(""));
    }

    @Test
    void reentrantRemoveFromListenerRejected() {
        var t = of("a", "b");
        t.addListener(e -> t.remove("a"));
        assertThrows(ConcurrentModificationException.class, () -> t.remove("b"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=StandardTrieRemoveTest test`
Expected: FAIL — `removeLeafPrunesCascadingChainUpToAKeyNode` etc. fail (inherited `AbstractMap.remove` throws `UnsupportedOperationException`).

- [ ] **Step 3: Add `remove(Object)` to `StandardTrie`**

Add these imports to `StandardTrie.java` (alphabetical, alongside the existing event imports):

```java
import com.gimlism.translucent.trie.events.Prune;
import com.gimlism.translucent.trie.events.Remove;
```

Insert this method immediately after `put(...)` (before the `// --- snapshot + dispatch ---` divider):

```java
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
```

- [ ] **Step 4: Run the remove test to verify it passes**

Run: `mvn -q -Dtest=StandardTrieRemoveTest test`
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/core/StandardTrie.java \
        src/test/java/com/gimlism/translucent/trie/core/StandardTrieRemoveTest.java
git commit -m "feat(trie): StandardTrie remove with prune cascade"
```

---

### Task 3: Invariants, adversarial round-trip, and the no-compression grammar pin

**Files:**
- Test: `.../trie/core/StandardTrieInvariants.java`
- Test: `.../trie/core/StandardTrieGrammarTest.java`

**Interfaces:**
- Consumes: Task 1/2 `StandardTrie.snapshot()` (package-private, so the checker lives in `trie.core`); `trie.events.{TrieSnapshot,TrieNodeSnapshot,TrieEdge}`.
- Produces: `StandardTrieInvariants.assertValid(TrieSnapshot, Set<String>)` — standard-trie invariants (every edge label is exactly one char, unique child first-chars, key set matches expected, size field matches). Unlike radix, a non-key node with a single child is **legal** (that is the whole uncompressed point), so there is no `>=2 children` rule.

- [ ] **Step 1: Write the invariant checker**

Create `.../trie/core/StandardTrieInvariants.java`:

```java
package com.gimlism.translucent.trie.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Adversarial standard-trie invariant checker for tests (one char per edge, no compression). */
final class StandardTrieInvariants {
    private StandardTrieInvariants() {}

    /** Assert one-char-per-edge invariants and that the key set equals {@code expected}. */
    static TreeSet<String> assertValid(TrieSnapshot snap, Set<String> expected) {
        TreeSet<String> keys = new TreeSet<>();
        walk(snap.root(), "", keys);
        assertEquals(new TreeSet<>(expected), keys, "key set");
        assertEquals(expected.size(), snap.size(), "size field");
        return keys;
    }

    private static void walk(TrieNodeSnapshot n, String prefix, TreeSet<String> keys) {
        if (n.key()) keys.add(prefix);
        List<TrieEdge> kids = n.children();
        var firstChars = new TreeSet<Character>();
        for (TrieEdge e : kids) {
            assertEquals(1, e.label().length(), "edge label must be exactly one char at \"" + prefix + "\"");
            assertTrue(firstChars.add(e.label().charAt(0)), "duplicate child char at \"" + prefix + "\"");
        }
        for (TrieEdge e : kids) walk(e.target(), prefix + e.label(), keys);
    }
}
```

- [ ] **Step 2: Write the failing grammar + adversarial test**

Create `.../trie/core/StandardTrieGrammarTest.java`:

```java
package com.gimlism.translucent.trie.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.events.MergeEdge;
import com.gimlism.translucent.trie.events.SplitEdge;
import com.gimlism.translucent.trie.events.TrieEvent;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class StandardTrieGrammarTest {
    private static final String[] KEYS = {
        "she", "shell", "shore", "short", "shrew", "s", "sh",
        "romane", "romanus", "rom", "a", "ab", "abc", ""
    };

    @Test
    void adversarialInsertThenDeleteKeepsInvariants() {
        int[] order = {6, 0, 13, 8, 2, 10, 4, 1, 11, 9, 3, 12, 5, 7};
        var t = new StandardTrie<Integer>();
        var present = new HashSet<String>();
        for (int i = 0; i < KEYS.length; i++) {
            t.put(KEYS[i], i);
            present.add(KEYS[i]);
            StandardTrieInvariants.assertValid(t.snapshot(), present);
        }
        for (int i = 0; i < KEYS.length; i++) assertEquals(i, t.get(KEYS[i]));
        for (int idx : order) {
            assertEquals(idx, t.remove(KEYS[idx]));
            present.remove(KEYS[idx]);
            StandardTrieInvariants.assertValid(t.snapshot(), present);
        }
        assertTrue(t.isEmpty());
    }

    @Test
    void neverEmitsSplitOrMergeEvents() {
        // The uncompressed trie has no edge compression, so these radix-only events must never fire,
        // across a full adversarial insert+delete workload.
        var t = new StandardTrie<Integer>();
        var rec = new TrieRecordingListener();
        t.addListener(rec);
        for (int i = 0; i < KEYS.length; i++) t.put(KEYS[i], i);
        for (String k : KEYS) t.remove(k);
        for (TrieEvent e : rec.events()) {
            assertTrue(!(e instanceof SplitEdge) && !(e instanceof MergeEdge),
                "unexpected compression event: " + e.getClass().getSimpleName());
        }
        assertTrue(t.isEmpty());
    }
}
```

- [ ] **Step 3: Run the test to verify it passes**

Run: `mvn -q -Dtest=StandardTrieGrammarTest test`
Expected: PASS (2 tests). (No production change this task — the invariant checker and grammar pin should pass against Task 1/2 code. If `adversarialInsertThenDeleteKeepsInvariants` fails, there is a real remove bug to fix in `StandardTrie.remove` before proceeding.)

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/gimlism/translucent/trie/core/StandardTrieInvariants.java \
        src/test/java/com/gimlism/translucent/trie/core/StandardTrieGrammarTest.java
git commit -m "test(trie): StandardTrie invariants, adversarial round-trip, no-Split/Merge pin"
```

---

### Task 4: `TrieMetrics.nodeCount`

**Files:**
- Create: `.../trie/compare/package-info.java`
- Create: `.../trie/compare/TrieMetrics.java`
- Test: `.../trie/compare/TrieMetricsTest.java`

**Interfaces:**
- Consumes: `trie.events.{TrieSnapshot,TrieNodeSnapshot,TrieEdge}`; `trie.core.{StandardTrie,RadixTrie}`; `trie.consumer.TrieRecordingListener`.
- Produces: `public static int TrieMetrics.nodeCount(TrieSnapshot)` — total nodes in the trie the snapshot describes, **root included**.

- [ ] **Step 1: Write the package-info**

Create `.../trie/compare/package-info.java`:

```java
/**
 * Consumer-side tools for comparing trie implementations. {@link TrieMetrics} counts nodes from a
 * public {@code TrieSnapshot} (touching neither data structure), and
 * {@link CompressionCompareDemo} quantifies how many nodes the radix trie's edge compression saves
 * over the standard one-char-per-edge trie on the same keys.
 */
package com.gimlism.translucent.trie.compare;
```

- [ ] **Step 2: Write the failing metric test**

Create `.../trie/compare/TrieMetricsTest.java`:

```java
package com.gimlism.translucent.trie.compare;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrieMetricsTest {
    @Test
    void countsRootOnlyForEmptyTrie() {
        var snap = new TrieSnapshot(new TrieNodeSnapshot(false, null, List.of()), 0);
        assertEquals(1, TrieMetrics.nodeCount(snap));   // just the root
    }

    @Test
    void countsEveryNodeIncludingRoot() {
        // root -> a -> b (key), root -> c (key): 4 nodes total.
        var b = new TrieNodeSnapshot(true, 1, List.of());
        var a = new TrieNodeSnapshot(false, null, List.of(new TrieEdge("b", b)));
        var c = new TrieNodeSnapshot(true, 2, List.of());
        var root = new TrieNodeSnapshot(false, null, List.of(new TrieEdge("a", a), new TrieEdge("c", c)));
        assertEquals(4, TrieMetrics.nodeCount(new TrieSnapshot(root, 2)));
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `mvn -q test-compile`
Expected: FAIL — `TrieMetrics` does not exist.

- [ ] **Step 4: Write `TrieMetrics`**

Create `.../trie/compare/TrieMetrics.java`:

```java
package com.gimlism.translucent.trie.compare;

import com.gimlism.translucent.trie.events.TrieEdge;
import com.gimlism.translucent.trie.events.TrieNodeSnapshot;
import com.gimlism.translucent.trie.events.TrieSnapshot;

/** Consumer-side node-count metric over a whole-trie {@link TrieSnapshot} (root included). */
public final class TrieMetrics {
    private TrieMetrics() {}

    /** Total number of nodes in the trie the snapshot describes, counting the root. */
    public static int nodeCount(TrieSnapshot snapshot) {
        return count(snapshot.root());
    }

    private static int count(TrieNodeSnapshot n) {
        int total = 1;
        for (TrieEdge e : n.children()) total += count(e.target());
        return total;
    }
}
```

- [ ] **Step 5: Run the metric test to verify it passes**

Run: `mvn -q -Dtest=TrieMetricsTest test`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/compare/package-info.java \
        src/main/java/com/gimlism/translucent/trie/compare/TrieMetrics.java \
        src/test/java/com/gimlism/translucent/trie/compare/TrieMetricsTest.java
git commit -m "feat(trie): TrieMetrics.nodeCount over a trie snapshot"
```

---

### Task 5: `CompressionCompareDemo` — the payload

**Files:**
- Create: `.../trie/compare/CompressionCompareDemo.java`
- Test: `.../trie/compare/CompressionCompareDemoTest.java`

**Interfaces:**
- Consumes: `trie.core.{StandardTrie,RadixTrie}`; `trie.consumer.TrieRecordingListener`; `trie.events.TrieSnapshot`; `TrieMetrics.nodeCount`.
- Produces:
  - `public record CompressionCompareDemo.Comparison(int standardNodes, int radixNodes, List<String> keys)` with `public int saved()`.
  - `public static Comparison CompressionCompareDemo.compare(List<String> keys)` — inserts the keys (as `Integer` values `0..n-1`) into a fresh `StandardTrie` and `RadixTrie`, obtains each final snapshot via the terminal event's `after()` (a `TrieRecordingListener`, so no core surface is widened), and returns the node counts.
  - `public static void main(String[])` — prints the canonical comparison.

- [ ] **Step 1: Write the failing compare test**

Create `.../trie/compare/CompressionCompareDemoTest.java`:

```java
package com.gimlism.translucent.trie.compare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.core.StandardTrie;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class CompressionCompareDemoTest {
    private static final List<String> CANON = List.of("she", "shell", "shore", "shy");

    @Test
    void bothTriesHoldTheSameEntriesSoTheCompareIsFair() {
        var std = new StandardTrie<Integer>();
        var rad = new RadixTrie<Integer>();
        for (int i = 0; i < CANON.size(); i++) {
            std.put(CANON.get(i), i);
            rad.put(CANON.get(i), i);
        }
        // Same Map contract -> identical entry sets (ordering-independent map equality).
        assertEquals(new TreeMap<>(rad), new TreeMap<>(std));
    }

    @Test
    void standardTrieHasStrictlyMoreNodesOnAPrefixSharingSet() {
        var c = CompressionCompareDemo.compare(CANON);
        assertTrue(c.standardNodes() > c.radixNodes(),
            "expected compression: standard " + c.standardNodes() + " vs radix " + c.radixNodes());
        assertEquals(c.standardNodes() - c.radixNodes(), c.saved());
    }

    @Test
    void pinnedNodeCountsForTheCanonicalSet() {
        // she,shell,shore,shy:
        //   standard: root,s,h,e,l,l,o,r,e,y            = 10 nodes
        //   radix:    root,sh,e("she"),ll,ore,y         =  6 nodes
        var c = CompressionCompareDemo.compare(CANON);
        assertEquals(10, c.standardNodes());
        assertEquals(6, c.radixNodes());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q test-compile`
Expected: FAIL — `CompressionCompareDemo` does not exist.

- [ ] **Step 3: Write `CompressionCompareDemo`**

Create `.../trie/compare/CompressionCompareDemo.java`:

```java
package com.gimlism.translucent.trie.compare;

import com.gimlism.translucent.trie.consumer.TrieRecordingListener;
import com.gimlism.translucent.trie.core.RadixTrie;
import com.gimlism.translucent.trie.core.StandardTrie;
import com.gimlism.translucent.trie.events.TrieSnapshot;
import java.util.List;

/**
 * Quantifies the radix trie's edge compression: inserts the same keys into a
 * {@link StandardTrie} (one node per character) and a {@link RadixTrie} (single-child chains
 * collapsed), then reports each one's node count. The difference is exactly the nodes the
 * compression saves.
 */
public final class CompressionCompareDemo {
    private CompressionCompareDemo() {}

    /** Result of comparing the two tries on one key set. */
    public record Comparison(int standardNodes, int radixNodes, List<String> keys) {
        public Comparison {
            keys = List.copyOf(keys);
        }

        /** Nodes the radix compression saves over the standard trie. */
        public int saved() {
            return standardNodes - radixNodes;
        }
    }

    /** Insert {@code keys} (values {@code 0..n-1}) into both tries and count their nodes. */
    public static Comparison compare(List<String> keys) {
        var standard = new StandardTrie<Integer>();
        var radix = new RadixTrie<Integer>();
        var standardRec = new TrieRecordingListener();
        var radixRec = new TrieRecordingListener();
        standard.addListener(standardRec);
        radix.addListener(radixRec);
        for (int i = 0; i < keys.size(); i++) {
            standard.put(keys.get(i), i);
            radix.put(keys.get(i), i);
        }
        return new Comparison(
            TrieMetrics.nodeCount(lastSnapshot(standardRec)),
            TrieMetrics.nodeCount(lastSnapshot(radixRec)),
            keys);
    }

    /** The whole-trie snapshot carried by the most recent event (the final Put's {@code after()}). */
    private static TrieSnapshot lastSnapshot(TrieRecordingListener rec) {
        var events = rec.events();
        return events.get(events.size() - 1).after();
    }

    public static void main(String[] args) {
        Comparison c = compare(List.of("she", "shell", "shore", "shy"));
        System.out.printf("keys %s%n  standard = %d nodes%n  radix    = %d nodes%n  saved    = %d (%.0f%%)%n",
            c.keys(), c.standardNodes(), c.radixNodes(), c.saved(),
            100.0 * c.saved() / c.standardNodes());
    }
}
```

- [ ] **Step 4: Run the compare test to verify it passes**

Run: `mvn -q -Dtest=CompressionCompareDemoTest test`
Expected: PASS (3 tests). If `pinnedNodeCountsForTheCanonicalSet` fails, the printed counts reveal a real structural bug in one trie — investigate before adjusting the pin.

- [ ] **Step 5: Run the full suite**

Run: `mvn -q test`
Expected: PASS — full suite (479 baseline + the new tests), BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/gimlism/translucent/trie/compare/CompressionCompareDemo.java \
        src/test/java/com/gimlism/translucent/trie/compare/CompressionCompareDemoTest.java
git commit -m "feat(trie): CompressionCompareDemo quantifying radix node savings"
```

---

## Self-review

**Spec coverage:**
- Standard trie core + contract-identity → Task 1 (put/get/containsKey/size/entrySet/keysWithPrefix, null-key, empty key, non-String tolerance, value-replace non-structural) + Task 2 (remove, iterator remove, clear).
- Per-char event grammar (Descend/CreateNode/Put; Remove/Prune cascade; no Split/Merge) → Task 1 insert grammar test, Task 2 remove grammar tests, Task 3 `neverEmitsSplitOrMergeEvents`.
- Silent no-op removes → Task 2 `removeAbsentReturnsNullNoEvents`.
- Reuse of `trie.events`/snapshot/dispatcher unchanged → Tasks 1/2 import them; no event files modified.
- `TrieMetrics.nodeCount` consumer-side → Task 4.
- `CompressionCompareDemo` with the three assertions (identical entrySet, standard>radix, pinned exact) → Task 5.
- Snapshot access without widening core surface → Task 5 `lastSnapshot(rec)` via terminal event `after()`.
- Additive-only / cores byte-unchanged → no task modifies `RadixTrie`, `trie.events`, or substrate.

**Placeholder scan:** none — every step carries full code and exact commands.

**Type consistency:** `StandardTrie`/`StandardTrieNode` field/method names are consistent across Tasks 1–3; `snapshot()` is package-private and only reached from `trie.core` tests (Task 3) — the `trie.compare` code (Tasks 4/5) never calls it, using the event `after()` snapshot instead, matching the spec's stated preference. `Comparison.saved()` is defined in Task 5 and asserted there. Node-count convention (root included) is consistent between `TrieMetrics` (Task 4) and the pinned expectations (Task 5: standard 10, radix 6).

**Deferred (non-goals, per spec):** ASCII/web viz, any `RadixTrie`/events/substrate change.
