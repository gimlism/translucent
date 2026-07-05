# HashMap `setValue` Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `Map.Entry.setValue(...)` on an `entrySet()` entry an observable, write-through mutation (emitting `Put`), which also fixes the inherited `replaceAll` and removes the lost-write hazard.

**Architecture:** `EntryIterator.next()` returns a wrapper `LiveEntry` instead of the raw `Node`. `LiveEntry.setValue` routes through a new map method `setValueThroughEntry`, which re-finds the live node by key (so it can't write to a node detached by treeify/untreeify/resize), assigns the value, and emits `Put(newEntry=false)` — mirroring `doPut`'s existing replace branch, guarded by `beginMutation`/`endMutation` and **without** bumping `modCount`.

**Tech Stack:** Java 21 (compiled on JDK 26 via `maven.compiler.release=21`), Maven, JUnit 5 (Jupiter).

## Global Constraints

- Target Java 21: `maven.compiler.release=21`. No newer-than-21 APIs.
- Event vocabulary is the sealed `MapEvent`; **reuse `Put`** for value replacement — do NOT add a new event type.
- A value replacement is **not** a structural change: `setValueThroughEntry` must **not** increment `modCount`.
- Every mid-operation event must see fully-committed state before `snapshot()` runs (the recurring event-frame rule).
- The re-entrancy guard (`EventDispatcher.beginMutation`) throws `java.util.ConcurrentModificationException`.
- Tests live in `src/test/java/com/gimlism/translucent/hashmap/core/` (package `com.gimlism.translucent.hashmap.core`, so package-private hooks like `isTreeBin(int)` are directly callable).
- Collision arithmetic (default config: capacity 8, mask 7, treeify threshold 4, min-treeify capacity 8): keys `1, 9, 17, 25` all satisfy `key & 7 == 1`, so they share bucket 1 and the 4th insertion treeifies it.
- Run a single test class: `mvn -q -Dtest=MapEntryAndBulkTest test`. Run the whole suite: `mvn -q test`.

---

### Task 1: Write-through `setValue` via a `LiveEntry` wrapper

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java` (add `setValueThroughEntry`, add the `LiveEntry` inner class, change `EntryIterator.next()`'s return, rewrite the class Javadoc caveat)
- Modify (test): `src/test/java/com/gimlism/translucent/hashmap/core/MapEntryAndBulkTest.java` (replace the stale "emits no event" pinning test; add ISE + treeify-regression tests)

**Interfaces:**
- Consumes (existing, already in `TeachingHashMap`): `findNode(Object) -> Node<K,V>`; `hash(Object) -> int`; `indexFor(int,int) -> int`; `emit(MapEvent)`; `snapshot() -> MapSnapshot`; `dispatcher.beginMutation()` / `dispatcher.endMutation()`; `Node` fields `key`, `value`; the `Put(Object key, Object value, Object previousValue, int bucketIndex, boolean newEntry, MapSnapshot after)` record.
- Produces (relied on by Task 2): `private V setValueThroughEntry(K key, V newValue)` — write-through emitting one `Put(newEntry=false)`, throwing `IllegalStateException` if the key is absent, bracketed by `beginMutation`/`endMutation`, no `modCount` bump; and `EntryIterator.next()` returning a `LiveEntry` whose `setValue` calls it.

- [ ] **Step 1: Replace the stale pinning test with the write-through/emits-`Put` test**

In `MapEntryAndBulkTest.java`, add these imports below the existing `assertEquals` import:

```java
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
```

Update the class Javadoc to `/** Pins {@code setValue} observability/write-through and the inherited bulk ops. */`.

Delete the existing `setValueOnAChainEntryWritesThroughButEmitsNoEvent` test entirely and replace it with:

```java
@Test
void setValueOnAChainEntryEmitsPutAndWritesThrough() {
    var map = new TeachingHashMap<Integer, String>();
    map.put(1, "a");
    var rec = new MapRecordingListener();
    map.addListener(rec);

    Map.Entry<Integer, String> e = map.entrySet().iterator().next();
    assertEquals("a", e.setValue("A"));    // returns the old value
    assertEquals("A", map.get(1));         // live node -> write-through
    assertEquals("A", e.getValue());       // wrapper's cached view is updated

    assertEquals(1, rec.events().size());
    Put p = assertInstanceOf(Put.class, rec.events().get(0));
    assertEquals(1, p.key());
    assertEquals("A", p.value());
    assertEquals("a", p.previousValue());
    assertFalse(p.newEntry());
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=MapEntryAndBulkTest#setValueOnAChainEntryEmitsPutAndWritesThrough test`
Expected: FAIL — the raw `Node.setValue` emits nothing, so `rec.events().size()` is `0`, not `1`.

- [ ] **Step 3: Add the `setValueThroughEntry` write-through method**

In `TeachingHashMap.java`, add this method (place it just after `doRemove`, before `size()`):

```java
/**
 * Write-through value replacement for an entry-view {@code setValue}. Re-finds the
 * live node by key (so a node detached by a prior treeify/untreeify/resize cannot be
 * silently written), assigns the value, and emits a replacement {@link Put}. A value
 * replacement is not structural, so {@code modCount} is left untouched — that is what
 * lets an inherited {@code replaceAll} iterate and set every entry without invalidating
 * its own iterator.
 *
 * @throws IllegalStateException if {@code key} is no longer present in the map
 */
private V setValueThroughEntry(K key, V newValue) {
    beginMutation();
    try {
        Node<K, V> live = findNode(key);
        if (live == null) {
            throw new IllegalStateException("entry no longer in map");
        }
        V old = live.value;
        live.value = newValue;
        int i = indexFor(hash(key), table.length);
        emit(new Put(key, newValue, old, i, false, snapshot()));
        return old;
    } finally {
        dispatcher.endMutation();
    }
}
```

- [ ] **Step 4: Add the `LiveEntry` wrapper inner class**

In `TeachingHashMap.java`, add this class immediately after the `EntryIterator` class (still inside `TeachingHashMap`):

```java
/**
 * The entry object handed out by {@link EntryIterator#next()}. Unlike the raw
 * {@link Node}, its {@link #setValue} routes through {@link #setValueThroughEntry}
 * so the write is observable and always lands on the live map. {@code getValue}
 * returns the value cached at iteration time; only {@code setValue} re-finds.
 */
private final class LiveEntry implements Map.Entry<K, V> {
    private final K key;
    private V cachedValue;

    LiveEntry(K key, V value) {
        this.key = key;
        this.cachedValue = value;
    }

    @Override public K getKey() { return key; }
    @Override public V getValue() { return cachedValue; }

    @Override
    public V setValue(V newValue) {
        V old = setValueThroughEntry(key, newValue);
        cachedValue = newValue;
        return old;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Map.Entry<?, ?> e)) return false;
        return Objects.equals(key, e.getKey()) && Objects.equals(cachedValue, e.getValue());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(key) ^ Objects.hashCode(cachedValue);
    }

    @Override
    public String toString() {
        return key + "=" + cachedValue;
    }
}
```

- [ ] **Step 5: Return the wrapper from `EntryIterator.next()`**

In `TeachingHashMap.java`, in `EntryIterator.next()`, change the final `return lastReturned;` line to:

```java
        return new LiveEntry(lastReturned.key, lastReturned.value);
```

Leave everything else in `next()` unchanged — `lastReturned` stays the raw `Node` so `EntryIterator.remove()` (which unlinks by `lastReturned.key`) is unaffected.

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -q -Dtest=MapEntryAndBulkTest#setValueOnAChainEntryEmitsPutAndWritesThrough test`
Expected: PASS.

- [ ] **Step 7: Add the vanished-key ISE test**

In `MapEntryAndBulkTest.java`, add:

```java
@Test
void setValueOnARemovedKeyThrowsIllegalState() {
    var map = new TeachingHashMap<Integer, String>();
    map.put(1, "a");
    Map.Entry<Integer, String> e = map.entrySet().iterator().next();
    map.remove(1);   // the captured entry's key is now gone
    assertThrows(IllegalStateException.class, () -> e.setValue("A"));
}
```

- [ ] **Step 8: Add the treeify-boundary regression test (the old lost-write repro)**

In `MapEntryAndBulkTest.java`, add:

```java
@Test
void setValueSurvivesTreeifyOfItsBucket() {
    var map = new TeachingHashMap<Integer, String>();      // cap 8, treeify at 4
    map.put(1, "a");
    Map.Entry<Integer, String> e = map.entrySet().iterator().next(); // captured pre-treeify
    assertEquals(1, e.getKey());

    map.put(9, "i");    // 1, 9, 17, 25 all share bucket 1 (key & 7 == 1)...
    map.put(17, "q");
    map.put(25, "y");   // ...and the 4th insertion treeifies the bucket
    assertTrue(map.isTreeBin(1));   // key-1's node was copied into a fresh TreeNode

    var rec = new MapRecordingListener();
    map.addListener(rec);
    assertEquals("a", e.setValue("A"));   // re-finds the LIVE tree node, not the orphan
    assertEquals("A", map.get(1));        // write is not lost
    long puts = rec.events().stream()
            .filter(ev -> ev instanceof Put && !((Put) ev).newEntry()).count();
    assertEquals(1, puts);
}
```

- [ ] **Step 9: Run the three new tests to verify they pass**

Run: `mvn -q -Dtest=MapEntryAndBulkTest test`
Expected: PASS (all tests in the class, including `putAllEmitsOnePutPerEntry`).

- [ ] **Step 10: Replace the class Javadoc caveat on `TeachingHashMap`**

In `TeachingHashMap.java`, replace the `<p><b>Known limitation (this slice):</b> ...` paragraph (the one describing silent `setValue`/`replaceAll` and the lost-write hazard) with:

```java
 * <p><b>Entry-view writes are observable.</b> {@link Map.Entry#setValue(Object)} on an
 * entry from {@link #entrySet()} (and the inherited
 * {@link #replaceAll(java.util.function.BiFunction)}, which uses it) routes through the
 * map: it re-finds the live node by key, so the write always lands on the live map, and
 * it emits a replacement {@link com.gimlism.translucent.hashmap.events.Put} event.
 * Calling {@code setValue} for a key no longer present throws {@link IllegalStateException}.
```

- [ ] **Step 11: Run the full suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java \
        src/test/java/com/gimlism/translucent/hashmap/core/MapEntryAndBulkTest.java
git commit -m "feat(hashmap): observable write-through setValue on entrySet entries

Wrap the entry returned by EntryIterator.next() in a LiveEntry whose
setValue re-finds the live node by key and emits Put(newEntry=false).
Fixes the silent mutation and the lost-write hazard when the backing
node was detached by treeify/untreeify/resize. Throws IllegalStateException
when the key is gone.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01GRkjPtkQNVBT3Mk9HtQEdF"
```

---

### Task 2: Interaction coverage — `replaceAll`, re-entrancy, iterator stability

**No production changes.** This task adds tests that pin how the Task-1 mechanism composes with inherited `replaceAll`, the event-dispatch re-entrancy guard, and open iterators. These behaviors are delivered by Task 1, so **all three tests are expected to pass on first run** — they are regression/interaction guards (e.g. guarding against a future `modCount++` slipping into the write-through), not TDD drivers. A separate reviewer gate over these interaction surfaces is the point.

**Files:**
- Modify (test): `src/test/java/com/gimlism/translucent/hashmap/core/MapEntryAndBulkTest.java`

**Interfaces:**
- Consumes: `setValueThroughEntry` and the `LiveEntry`-returning `EntryIterator.next()` from Task 1; the inherited `Map.replaceAll`; `EventDispatcher.beginMutation`'s `ConcurrentModificationException`.
- Produces: nothing (test-only).

- [ ] **Step 1: Add imports for the new tests**

In `MapEntryAndBulkTest.java`, add:

```java
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
```

- [ ] **Step 2: Add the `replaceAll` test**

```java
@Test
void replaceAllEmitsOnePutPerEntry() {
    var map = new TeachingHashMap<Integer, String>();
    map.put(1, "a");
    map.put(2, "b");
    map.put(3, "c");
    var rec = new MapRecordingListener();
    map.addListener(rec);

    map.replaceAll((k, v) -> v.toUpperCase());

    assertEquals("A", map.get(1));
    assertEquals("B", map.get(2));
    assertEquals("C", map.get(3));
    // replaceAll iterates entrySet and setValues each -> one replacement Put per entry
    long puts = rec.events().stream()
            .filter(ev -> ev instanceof Put && !((Put) ev).newEntry()).count();
    assertEquals(3, puts);
}
```

- [ ] **Step 3: Add the re-entrancy-rejection test**

```java
@Test
void setValueFromWithinAListenerIsRejected() {
    var map = new TeachingHashMap<Integer, String>();
    map.put(1, "a");
    Map.Entry<Integer, String> captured = map.entrySet().iterator().next();
    map.addListener(ev -> captured.setValue("X")); // re-entrant write during dispatch

    // the next put's Put event dispatches to the listener, whose setValue calls
    // beginMutation while the map is already mutating -> ConcurrentModificationException
    assertThrows(ConcurrentModificationException.class, () -> map.put(2, "b"));
    // the guard is cleared in finally, so the outer put still landed and the map is usable
    assertEquals("a", map.get(1));   // the rejected setValue never wrote
    assertEquals(2, map.size());
}
```

- [ ] **Step 4: Add the `modCount`/open-iterator guard test**

```java
@Test
void setValueDoesNotBumpModCountSoOpenIteratorsSurvive() {
    var map = new TeachingHashMap<Integer, String>();
    map.put(1, "a");
    map.put(2, "b");
    Iterator<Map.Entry<Integer, String>> it = map.entrySet().iterator();
    Map.Entry<Integer, String> first = it.next();   // iterator now open
    first.setValue("A");                            // value replacement: not structural

    // a structural mod would make this next() throw ConcurrentModificationException
    Map.Entry<Integer, String> second = it.next();
    assertNotNull(second);
    assertEquals("A", map.get(1));
}
```

- [ ] **Step 5: Run the class to verify all pass**

Run: `mvn -q -Dtest=MapEntryAndBulkTest test`
Expected: PASS (the three new interaction guards plus all Task-1 tests).

- [ ] **Step 6: Run the full suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 7: Commit**

```bash
git add src/test/java/com/gimlism/translucent/hashmap/core/MapEntryAndBulkTest.java
git commit -m "test(hashmap): pin setValue interactions — replaceAll, re-entrancy, iterator stability

Guard that inherited replaceAll emits one Put per entry, that a
re-entrant setValue from a listener is rejected, and that setValue
does not bump modCount (open iterators survive it).

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01GRkjPtkQNVBT3Mk9HtQEdF"
```

---

## Self-Review

**Spec coverage:**
- Component 1 (`LiveEntry`) → Task 1 Steps 4–5. ✓
- Component 2 (`setValueThroughEntry`, re-find/ISE/no-modCount/beginMutation) → Task 1 Step 3. ✓
- Component 3 (iterator returns wrapper, `lastReturned` stays `Node`) → Task 1 Step 5. ✓
- Reuse `Put` (no new event) → Task 1 Step 3 emits `Put(newEntry=false)`; Global Constraints. ✓
- ISE on vanished key → Task 1 Step 3 + test Step 7. ✓
- `replaceAll` free → Task 2 Step 2. ✓
- Docs caveat replaced → Task 1 Step 10. ✓
- Event-frame check (emit after value committed) → Task 1 Step 3 (assign before `emit`). ✓
- Test list: emits one Put ✓ (T1 S1), treeify regression ✓ (T1 S8), vanished-key ISE ✓ (T1 S7), replaceAll burst ✓ (T2 S2), modCount/iterator ✓ (T2 S4), re-entrancy ✓ (T2 S3).

**Placeholder scan:** No TBD/TODO; every code step shows complete code. ✓

**Type consistency:** `setValueThroughEntry(K, V) -> V` used identically in Task 1 Step 3 (definition) and Step 4 (`LiveEntry.setValue` call site). `Put(...)` positional args match the record in `events/Put.java`. `isTreeBin(int)`, `findNode(Object)`, `indexFor(int,int)`, `hash(Object)` match `TeachingHashMap`. ✓
