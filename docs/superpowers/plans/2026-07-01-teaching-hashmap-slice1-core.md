# Teaching HashMap — Slice 1: Working Core (chains + events) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a working, event-emitting teaching `HashMap` with separate chaining, load-factor resize, immutable per-event snapshots, and console + recording consumers — a complete, demonstrable tool with no trees yet.

**Architecture:** `TeachingHashMap<K,V>` extends `java.util.AbstractMap<K,V>` and stores entries in a power-of-two `Node[]` table using separate chaining (tail-append to preserve insertion order). Every mutation emits an immutable `MapEvent` carrying a full whole-map `MapSnapshot` to a synchronous listener list. Trees are out of scope for this slice; the tree-related event records and the `TreeSnapshot` bucket type are *defined* so the sealed hierarchies are stable, but never emitted/produced yet.

**Tech Stack:** Java 21, Maven, JUnit 5 (Jupiter).

## Global Constraints

- Java language level and bytecode: **21** (`maven.compiler.release=21`).
- Build tool: **Maven**.
- Group id: **`com.gimlism`**. Java package root: **`com.gimlism.translucent.hashmap`**.
- Sub-packages: `.core` (data structure), `.events` (events + snapshots), `.consumer` (listeners), `.demo` (runnable main).
- `MapEvent` is **non-generic**; event records carry `Object` keys/values.
- Hash: `index = (n - 1) & (key == null ? 0 : key.hashCode())` — **no bit-spreading**.
- Teaching-default, constructor-configurable constants: `initialCapacity=8`, `loadFactor=0.75f`, `treeifyThreshold=4`, `untreeifyThreshold=2`, `minTreeifyCapacity=8`. (Only capacity/loadFactor are exercised this slice; the treeify triple is stored for Slice 2.)
- Chains use **tail-append** so chain order equals insertion order (easier for learners to reason about).
- Events carry a full whole-map snapshot taken *after* the operation settles; `Resize` carries before + after.
- TDD throughout: failing test first, minimal implementation, commit per task.

---

### Task 1: Maven project scaffold

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/gimlism/translucent/hashmap/package-info.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/BuildSmokeTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: a compiling, test-running Maven project on Java 21.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/BuildSmokeTest.java`
```java
package com.gimlism.translucent.hashmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BuildSmokeTest {
    @Test
    void toolchainRunsTests() {
        assertEquals(21, Runtime.version().feature());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — build error, no `pom.xml` / cannot find JUnit.

- [ ] **Step 3: Write minimal implementation**

`pom.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.gimlism</groupId>
    <artifactId>translucent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.release>21</maven.compiler.release>
        <junit.version>5.10.2</junit.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
```

`src/main/java/com/gimlism/translucent/hashmap/package-info.java`
```java
/**
 * Teaching HashMap with event instrumentation.
 *
 * <p>An instrumented {@code HashMap} for teaching: it mirrors the structurally
 * important parts of {@code java.util.HashMap} and emits an immutable event on
 * every internal state change.
 */
package com.gimlism.translucent.hashmap;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — 1 test run, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/java/com/gimlism/translucent/hashmap/package-info.java src/test/java/com/gimlism/translucent/hashmap/BuildSmokeTest.java
git commit -m "chore: scaffold Maven project on Java 21 with JUnit 5"
```

---

### Task 2: Snapshot value types

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/Color.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/Direction.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/EntrySnapshot.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/TreeNodeSnapshot.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/BucketSnapshot.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/EmptyBucket.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/ChainSnapshot.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/TreeSnapshot.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/MapSnapshot.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/events/SnapshotTypesTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum Color { RED, BLACK }`
  - `enum Direction { LEFT, RIGHT }`
  - `record EntrySnapshot(Object key, Object value, int hash)`
  - `record TreeNodeSnapshot(Object key, Object value, Color color, TreeNodeSnapshot left, TreeNodeSnapshot right)`
  - `sealed interface BucketSnapshot permits EmptyBucket, ChainSnapshot, TreeSnapshot`
  - `record EmptyBucket() implements BucketSnapshot`
  - `record ChainSnapshot(List<EntrySnapshot> entries) implements BucketSnapshot`
  - `record TreeSnapshot(TreeNodeSnapshot root) implements BucketSnapshot`
  - `record MapSnapshot(int capacity, int size, int threshold, List<BucketSnapshot> buckets)`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/events/SnapshotTypesTest.java`
```java
package com.gimlism.translucent.hashmap.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotTypesTest {
    @Test
    void mapSnapshotHoldsBuckets() {
        var chain = new ChainSnapshot(List.of(new EntrySnapshot("a", 1, 97)));
        var snap = new MapSnapshot(8, 1, 6, List.of(new EmptyBucket(), chain));
        assertEquals(8, snap.capacity());
        assertEquals(1, snap.size());
        assertInstanceOf(EmptyBucket.class, snap.buckets().get(0));
        assertInstanceOf(ChainSnapshot.class, snap.buckets().get(1));
    }

    @Test
    void bucketSnapshotIsSealedOverThreeCases() {
        BucketSnapshot b = new TreeSnapshot(
            new TreeNodeSnapshot("k", "v", Color.BLACK, null, null));
        String kind = switch (b) {
            case EmptyBucket e -> "empty";
            case ChainSnapshot c -> "chain";
            case TreeSnapshot t -> "tree";
        };
        assertEquals("tree", kind);
    }

    @Test
    void chainSnapshotListIsUnmodifiable() {
        var chain = new ChainSnapshot(List.copyOf(new ArrayList<>(
            List.of(new EntrySnapshot("a", 1, 97)))));
        assertThrows(UnsupportedOperationException.class,
            () -> chain.entries().add(new EntrySnapshot("b", 2, 98)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbols `MapSnapshot`, `ChainSnapshot`, etc.

- [ ] **Step 3: Write minimal implementation**

`Color.java`
```java
package com.gimlism.translucent.hashmap.events;

/** Red-black tree node colour, surfaced in snapshots so balancing is visible. */
public enum Color { RED, BLACK }
```

`Direction.java`
```java
package com.gimlism.translucent.hashmap.events;

/** Rotation direction for red-black tree balancing events. */
public enum Direction { LEFT, RIGHT }
```

`EntrySnapshot.java`
```java
package com.gimlism.translucent.hashmap.events;

/** Immutable copy of a single map entry at snapshot time. */
public record EntrySnapshot(Object key, Object value, int hash) {}
```

`TreeNodeSnapshot.java`
```java
package com.gimlism.translucent.hashmap.events;

/** Immutable copy of a red-black tree node, including colour and children. */
public record TreeNodeSnapshot(
        Object key, Object value, Color color,
        TreeNodeSnapshot left, TreeNodeSnapshot right) {}
```

`BucketSnapshot.java`
```java
package com.gimlism.translucent.hashmap.events;

/** A single table slot at snapshot time: empty, a chain, or a tree. */
public sealed interface BucketSnapshot
        permits EmptyBucket, ChainSnapshot, TreeSnapshot {}
```

`EmptyBucket.java`
```java
package com.gimlism.translucent.hashmap.events;

/** A table slot with no entries. */
public record EmptyBucket() implements BucketSnapshot {}
```

`ChainSnapshot.java`
```java
package com.gimlism.translucent.hashmap.events;

import java.util.List;

/** A table slot holding a separate-chaining list, head first. */
public record ChainSnapshot(List<EntrySnapshot> entries) implements BucketSnapshot {
    public ChainSnapshot {
        entries = List.copyOf(entries);
    }
}
```

`TreeSnapshot.java`
```java
package com.gimlism.translucent.hashmap.events;

/** A table slot holding a red-black tree, referenced by its root. */
public record TreeSnapshot(TreeNodeSnapshot root) implements BucketSnapshot {}
```

`MapSnapshot.java`
```java
package com.gimlism.translucent.hashmap.events;

import java.util.List;

/** Immutable whole-map state at a point in time. */
public record MapSnapshot(int capacity, int size, int threshold,
                          List<BucketSnapshot> buckets) {
    public MapSnapshot {
        buckets = List.copyOf(buckets);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — all `SnapshotTypesTest` tests green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/events src/test/java/com/gimlism/translucent/hashmap/events/SnapshotTypesTest.java
git commit -m "feat(events): add immutable snapshot value types"
```

---

### Task 3: MapEvent sealed hierarchy + listener interface

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/MapEvent.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/Put.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/Remove.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/Collision.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/Resize.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/Treeify.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/Untreeify.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/Rotation.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/Recolor.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/events/MapEventListener.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/events/MapEventTest.java`

**Interfaces:**
- Consumes: `MapSnapshot`, `Color`, `Direction` (Task 2).
- Produces:
  - `sealed interface MapEvent permits Put, Remove, Collision, Resize, Treeify, Untreeify, Rotation, Recolor { MapSnapshot after(); }`
  - `record Put(Object key, Object value, Object previousValue, int bucketIndex, boolean newEntry, MapSnapshot after) implements MapEvent`
  - `record Remove(Object key, Object removedValue, int bucketIndex, MapSnapshot after) implements MapEvent`
  - `record Collision(Object key, int bucketIndex, int chainLengthBefore, int chainLengthAfter, MapSnapshot after) implements MapEvent`
  - `record Resize(int oldCapacity, int newCapacity, MapSnapshot before, MapSnapshot after) implements MapEvent`
  - `record Treeify(int bucketIndex, MapSnapshot after) implements MapEvent`
  - `record Untreeify(int bucketIndex, MapSnapshot after) implements MapEvent`
  - `record Rotation(int bucketIndex, Direction direction, Object pivotKey, MapSnapshot after) implements MapEvent`
  - `record Recolor(int bucketIndex, Object nodeKey, Color oldColor, Color newColor, MapSnapshot after) implements MapEvent`
  - `interface MapEventListener { void onEvent(MapEvent event); }`

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/events/MapEventTest.java`
```java
package com.gimlism.translucent.hashmap.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MapEventTest {
    private static MapSnapshot emptySnap() {
        return new MapSnapshot(8, 0, 6, List.of());
    }

    @Test
    void putExposesCommonAfterAccessor() {
        MapEvent e = new Put("k", 1, null, 3, true, emptySnap());
        assertEquals(8, e.after().capacity());
    }

    @Test
    void exhaustiveSwitchOverAllEventKinds() {
        List<MapEvent> events = List.of(
            new Put("k", 1, null, 0, true, emptySnap()),
            new Remove("k", 1, 0, emptySnap()),
            new Collision("k", 0, 1, 2, emptySnap()),
            new Resize(8, 16, emptySnap(), emptySnap()),
            new Treeify(0, emptySnap()),
            new Untreeify(0, emptySnap()),
            new Rotation(0, Direction.LEFT, "k", emptySnap()),
            new Recolor(0, "k", Color.RED, Color.BLACK, emptySnap()));
        for (MapEvent e : events) {
            String name = switch (e) {
                case Put p -> "put";
                case Remove r -> "remove";
                case Collision c -> "collision";
                case Resize rs -> "resize";
                case Treeify t -> "treeify";
                case Untreeify u -> "untreeify";
                case Rotation ro -> "rotation";
                case Recolor rc -> "recolor";
            };
            assertTrue(name.length() > 0);
        }
        assertEquals(8, events.size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `MapEvent`, `Put`, etc.

- [ ] **Step 3: Write minimal implementation**

`MapEvent.java`
```java
package com.gimlism.translucent.hashmap.events;

/** An immutable, self-contained record of a single map state change. */
public sealed interface MapEvent
        permits Put, Remove, Collision, Resize, Treeify, Untreeify, Rotation, Recolor {
    /** Whole-map snapshot after the operation settled. */
    MapSnapshot after();
}
```

`Put.java`
```java
package com.gimlism.translucent.hashmap.events;

/** A key/value was inserted ({@code newEntry}) or its value replaced. */
public record Put(Object key, Object value, Object previousValue,
                  int bucketIndex, boolean newEntry, MapSnapshot after)
        implements MapEvent {}
```

`Remove.java`
```java
package com.gimlism.translucent.hashmap.events;

/** A key was removed, carrying the value that had been mapped. */
public record Remove(Object key, Object removedValue, int bucketIndex, MapSnapshot after)
        implements MapEvent {}
```

`Collision.java`
```java
package com.gimlism.translucent.hashmap.events;

/** A new entry landed in an already-occupied bucket (chain grew). */
public record Collision(Object key, int bucketIndex,
                        int chainLengthBefore, int chainLengthAfter, MapSnapshot after)
        implements MapEvent {}
```

`Resize.java`
```java
package com.gimlism.translucent.hashmap.events;

/** The table doubled and all entries were rehashed. */
public record Resize(int oldCapacity, int newCapacity,
                     MapSnapshot before, MapSnapshot after)
        implements MapEvent {}
```

`Treeify.java`
```java
package com.gimlism.translucent.hashmap.events;

/** A chain reached the treeify threshold and became a red-black tree. */
public record Treeify(int bucketIndex, MapSnapshot after) implements MapEvent {}
```

`Untreeify.java`
```java
package com.gimlism.translucent.hashmap.events;

/** A tree bin shrank below the untreeify threshold and became a chain again. */
public record Untreeify(int bucketIndex, MapSnapshot after) implements MapEvent {}
```

`Rotation.java`
```java
package com.gimlism.translucent.hashmap.events;

/** A single red-black tree rotation about {@code pivotKey}. */
public record Rotation(int bucketIndex, Direction direction, Object pivotKey, MapSnapshot after)
        implements MapEvent {}
```

`Recolor.java`
```java
package com.gimlism.translucent.hashmap.events;

/** A single red-black tree node changed colour. */
public record Recolor(int bucketIndex, Object nodeKey, Color oldColor, Color newColor, MapSnapshot after)
        implements MapEvent {}
```

`MapEventListener.java`
```java
package com.gimlism.translucent.hashmap.events;

/** Consumer of the map's event stream. Invoked synchronously after each mutation. */
@FunctionalInterface
public interface MapEventListener {
    void onEvent(MapEvent event);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `MapEventTest` green (exhaustive switch compiles = sealed hierarchy complete).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/events src/test/java/com/gimlism/translucent/hashmap/events/MapEventTest.java
git commit -m "feat(events): add sealed MapEvent hierarchy and listener"
```

---

### Task 4: Core map — Node, put/get, chaining (no events, no resize)

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/core/Node.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/TeachingHashMapBasicsTest.java`

**Interfaces:**
- Consumes: nothing yet from events (added in Task 8).
- Produces:
  - `class Node<K,V> implements Map.Entry<K,V>` with fields `final int hash; final K key; V value; Node<K,V> next;`
  - `class TeachingHashMap<K,V> extends AbstractMap<K,V>` with:
    - `public TeachingHashMap()` (defaults) and
      `public TeachingHashMap(int initialCapacity, float loadFactor, int treeifyThreshold, int untreeifyThreshold, int minTreeifyCapacity)`
    - `public V put(K key, V value)`
    - `public V get(Object key)`
    - `public boolean containsKey(Object key)`
    - `public int size()`
    - `public Set<Map.Entry<K,V>> entrySet()`
    - package-private `Node<K,V>[] table`, `int size`, `int threshold`, `int modCount`, and the five config fields.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/TeachingHashMapBasicsTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TeachingHashMapBasicsTest {
    @Test
    void putGetRoundTrip() {
        var map = new TeachingHashMap<String, Integer>();
        assertNull(map.put("a", 1));
        assertEquals(1, map.get("a"));
        assertEquals(1, map.size());
        assertTrue(map.containsKey("a"));
        assertFalse(map.containsKey("z"));
    }

    @Test
    void putReplacesExistingValueAndReturnsOld() {
        var map = new TeachingHashMap<String, Integer>();
        map.put("a", 1);
        assertEquals(1, map.put("a", 2));
        assertEquals(2, map.get("a"));
        assertEquals(1, map.size());
    }

    @Test
    void collidingKeysCoexistInSameBucketOrder() {
        // capacity 8: keys hashing to 0, 8, 16 all land in bucket 0
        var map = new TeachingHashMap<Integer, String>();
        map.put(0, "zero");
        map.put(8, "eight");
        map.put(16, "sixteen");
        assertEquals(3, map.size());
        assertEquals("zero", map.get(0));
        assertEquals("eight", map.get(8));
        assertEquals("sixteen", map.get(16));
    }

    @Test
    void supportsNullKeyAndValue() {
        var map = new TeachingHashMap<String, String>();
        map.put(null, "nullkey");
        map.put("x", null);
        assertEquals("nullkey", map.get(null));
        assertNull(map.get("x"));
        assertTrue(map.containsKey("x"));
    }

    @Test
    void entrySetReflectsContents() {
        var map = new TeachingHashMap<String, Integer>();
        map.put("a", 1);
        map.put("b", 2);
        int sum = 0;
        for (var e : map.entrySet()) {
            sum += e.getValue();
        }
        assertEquals(3, sum);
        assertEquals(2, map.entrySet().size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `TeachingHashMap`.

- [ ] **Step 3: Write minimal implementation**

`Node.java`
```java
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
```

`TeachingHashMap.java`
```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `TeachingHashMapBasicsTest` green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core src/test/java/com/gimlism/translucent/hashmap/core/TeachingHashMapBasicsTest.java
git commit -m "feat(core): add Node and chaining put/get/entrySet"
```

---

### Task 5: remove and clear (chains)

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/TeachingHashMapRemoveTest.java`

**Interfaces:**
- Consumes: Task 4 map.
- Produces: `public V remove(Object key)` and `public void clear()` overrides.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/TeachingHashMapRemoveTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TeachingHashMapRemoveTest {
    @Test
    void removeReturnsOldValueAndShrinks() {
        var map = new TeachingHashMap<String, Integer>();
        map.put("a", 1);
        map.put("b", 2);
        assertEquals(1, map.remove("a"));
        assertNull(map.get("a"));
        assertEquals(1, map.size());
    }

    @Test
    void removeMiddleOfChainKeepsOthers() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(0, "zero");
        map.put(8, "eight");   // same bucket as 0
        map.put(16, "sixteen"); // same bucket as 0
        assertEquals("eight", map.remove(8));
        assertEquals("zero", map.get(0));
        assertEquals("sixteen", map.get(16));
        assertEquals(2, map.size());
    }

    @Test
    void removeMissingKeyReturnsNull() {
        var map = new TeachingHashMap<String, Integer>();
        assertNull(map.remove("nope"));
    }

    @Test
    void clearEmptiesMap() {
        var map = new TeachingHashMap<String, Integer>();
        map.put("a", 1);
        map.put("b", 2);
        map.clear();
        assertEquals(0, map.size());
        assertFalse(map.containsKey("a"));
        assertTrue(map.entrySet().isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — `remove` returns wrong result / `clear` uses unsupported iterator remove (UnsupportedOperationException or assertion failure).

- [ ] **Step 3: Write minimal implementation**

Add these methods to `TeachingHashMap` (after `put`):
```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `TeachingHashMapRemoveTest` green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java src/test/java/com/gimlism/translucent/hashmap/core/TeachingHashMapRemoveTest.java
git commit -m "feat(core): add remove and clear for chains"
```

---

### Task 6: Resize / rehash (chains)

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/TeachingHashMapResizeTest.java`

**Interfaces:**
- Consumes: Task 4/5 map.
- Produces: private `void resize()`; `put` now calls `resize()` when `size > threshold`. Package-private `int capacity()` helper for tests.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/TeachingHashMapResizeTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TeachingHashMapResizeTest {
    @Test
    void doublesCapacityWhenThresholdExceeded() {
        // capacity 8, loadFactor 0.75 -> threshold 6; 7th insert triggers resize to 16
        var map = new TeachingHashMap<Integer, Integer>();
        assertEquals(8, map.capacity());
        for (int k = 1; k <= 6; k++) map.put(k, k);
        assertEquals(8, map.capacity());
        map.put(7, 7);
        assertEquals(16, map.capacity());
    }

    @Test
    void resizePreservesAllEntries() {
        var map = new TeachingHashMap<Integer, Integer>();
        for (int k = 0; k < 50; k++) map.put(k, k * 10);
        assertEquals(50, map.size());
        for (int k = 0; k < 50; k++) assertEquals(k * 10, map.get(k));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `capacity()`, and capacity stays 8 (no resize).

- [ ] **Step 3: Write minimal implementation**

Add to `TeachingHashMap`:
```java
    /** Current table length (power of two). */
    int capacity() {
        return table.length;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        Node<K, V>[] oldTab = table;
        int oldCap = oldTab.length;
        int newCap = oldCap << 1;
        Node<K, V>[] newTab = (Node<K, V>[]) new Node[newCap];
        for (int j = 0; j < oldCap; j++) {
            Node<K, V> e = oldTab[j];
            while (e != null) {
                Node<K, V> next = e.next;
                int i = indexFor(e.hash, newCap);
                e.next = null;
                if (newTab[i] == null) {
                    newTab[i] = e;
                } else {
                    Node<K, V> tail = newTab[i];
                    while (tail.next != null) tail = tail.next;
                    tail.next = e;
                }
                e = next;
            }
        }
        table = newTab;
        threshold = (int) (newCap * loadFactor);
    }
```

In `put`, replace the final `return null;` block so the tail of the method reads:
```java
        size++;
        modCount++;
        if (size > threshold) resize();
        return null;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `TeachingHashMapResizeTest` green; all prior tests still green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java src/test/java/com/gimlism/translucent/hashmap/core/TeachingHashMapResizeTest.java
git commit -m "feat(core): add load-factor resize with rehash"
```

---

### Task 7: Whole-map snapshot capture

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/SnapshotCaptureTest.java`

**Interfaces:**
- Consumes: `MapSnapshot`, `BucketSnapshot`, `EmptyBucket`, `ChainSnapshot`, `EntrySnapshot` (Task 2).
- Produces: package-private `MapSnapshot snapshot()` returning current whole-map state (chains only; tree bins added in Slice 2).

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/SnapshotCaptureTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.gimlism.translucent.hashmap.events.ChainSnapshot;
import com.gimlism.translucent.hashmap.events.EmptyBucket;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import org.junit.jupiter.api.Test;

class SnapshotCaptureTest {
    @Test
    void snapshotReflectsCapacitySizeAndBuckets() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(0, "zero");
        map.put(8, "eight"); // bucket 0 chain of 2
        map.put(1, "one");   // bucket 1 chain of 1
        MapSnapshot snap = map.snapshot();
        assertEquals(8, snap.capacity());
        assertEquals(3, snap.size());
        assertEquals(8, snap.buckets().size());
        assertInstanceOf(ChainSnapshot.class, snap.buckets().get(0));
        assertInstanceOf(ChainSnapshot.class, snap.buckets().get(1));
        assertInstanceOf(EmptyBucket.class, snap.buckets().get(2));
        ChainSnapshot bucket0 = (ChainSnapshot) snap.buckets().get(0);
        assertEquals(2, bucket0.entries().size());
        assertEquals(0, bucket0.entries().get(0).key());
        assertEquals(8, bucket0.entries().get(1).key());
    }

    @Test
    void snapshotIsIndependentOfLaterMutations() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(0, "zero");
        MapSnapshot snap = map.snapshot();
        map.put(8, "eight");
        map.remove(0);
        // earlier snapshot unchanged
        ChainSnapshot bucket0 = (ChainSnapshot) snap.buckets().get(0);
        assertEquals(1, bucket0.entries().size());
        assertEquals("zero", bucket0.entries().get(0).value());
        assertEquals(1, snap.size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `snapshot()`.

- [ ] **Step 3: Write minimal implementation**

Add imports to `TeachingHashMap`:
```java
import com.gimlism.translucent.hashmap.events.BucketSnapshot;
import com.gimlism.translucent.hashmap.events.ChainSnapshot;
import com.gimlism.translucent.hashmap.events.EmptyBucket;
import com.gimlism.translucent.hashmap.events.EntrySnapshot;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import java.util.ArrayList;
import java.util.List;
```

Add method:
```java
    /** Immutable snapshot of the whole map. Chains only until Slice 2 adds trees. */
    MapSnapshot snapshot() {
        List<BucketSnapshot> buckets = new ArrayList<>(table.length);
        for (Node<K, V> head : table) {
            if (head == null) {
                buckets.add(new EmptyBucket());
            } else {
                List<EntrySnapshot> entries = new ArrayList<>();
                for (Node<K, V> e = head; e != null; e = e.next) {
                    entries.add(new EntrySnapshot(e.key, e.value, e.hash));
                }
                buckets.add(new ChainSnapshot(entries));
            }
        }
        return new MapSnapshot(table.length, size, threshold, buckets);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `SnapshotCaptureTest` green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java src/test/java/com/gimlism/translucent/hashmap/core/SnapshotCaptureTest.java
git commit -m "feat(core): capture immutable whole-map snapshots"
```

---

### Task 8: Event emission + RecordingListener

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`
- Create: `src/main/java/com/gimlism/translucent/hashmap/consumer/RecordingListener.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/EventEmissionTest.java`

**Interfaces:**
- Consumes: `MapEventListener`, `Put`, `Remove`, `Collision`, `Resize` (Task 3); `snapshot()` (Task 7).
- Produces:
  - `public void addListener(MapEventListener listener)` and `public void removeListener(MapEventListener listener)` on `TeachingHashMap`.
  - Emission from `put`/`remove`/`resize` in the canonical order below.
  - `class RecordingListener implements MapEventListener` with `List<MapEvent> events()` (unmodifiable view) and `void clear()`.

**Canonical event order for one `put` of a *new* entry:** `Put` (always) → `Collision` (only if the entry landed in an already-occupied bucket) → `Resize` (only if `size > threshold`). A value *replacement* emits only `Put` with `newEntry=false` and `previousValue` set. `remove` emits a single `Remove`. `Resize.before` is captured before rehashing; `Resize.after` after.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/EventEmissionTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.RecordingListener;
import com.gimlism.translucent.hashmap.events.Collision;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.Put;
import com.gimlism.translucent.hashmap.events.Remove;
import com.gimlism.translucent.hashmap.events.Resize;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventEmissionTest {
    @Test
    void newEntryEmitsPut() {
        var map = new TeachingHashMap<String, Integer>();
        var rec = new RecordingListener();
        map.addListener(rec);
        map.put("a", 1);
        assertEquals(1, rec.events().size());
        Put p = assertInstanceOf(Put.class, rec.events().get(0));
        assertEquals("a", p.key());
        assertEquals(1, p.value());
        assertTrue(p.newEntry());
        assertEquals(1, p.after().size());
    }

    @Test
    void replacementEmitsPutWithPreviousValue() {
        var map = new TeachingHashMap<String, Integer>();
        map.put("a", 1);
        var rec = new RecordingListener();
        map.addListener(rec);
        map.put("a", 2);
        assertEquals(1, rec.events().size());
        Put p = (Put) rec.events().get(0);
        assertFalse(p.newEntry());
        assertEquals(1, p.previousValue());
        assertEquals(2, p.value());
    }

    @Test
    void collisionEmitsPutThenCollision() {
        var map = new TeachingHashMap<Integer, String>();
        map.put(0, "zero");
        var rec = new RecordingListener();
        map.addListener(rec);
        map.put(8, "eight"); // bucket 0 already occupied
        assertEquals(2, rec.events().size());
        assertInstanceOf(Put.class, rec.events().get(0));
        Collision c = assertInstanceOf(Collision.class, rec.events().get(1));
        assertEquals(0, c.bucketIndex());
        assertEquals(1, c.chainLengthBefore());
        assertEquals(2, c.chainLengthAfter());
    }

    @Test
    void resizeEmittedAfterThresholdBreach() {
        var map = new TeachingHashMap<Integer, Integer>();
        var rec = new RecordingListener();
        map.addListener(rec);
        for (int k = 1; k <= 7; k++) map.put(k, k); // 7th breaches threshold 6
        List<MapEvent> events = rec.events();
        Resize resize = (Resize) events.stream()
            .filter(e -> e instanceof Resize).findFirst().orElseThrow();
        assertEquals(8, resize.oldCapacity());
        assertEquals(16, resize.newCapacity());
        assertEquals(8, resize.before().capacity());
        assertEquals(16, resize.after().capacity());
        // resize is the last event of the 7th put
        assertInstanceOf(Resize.class, events.get(events.size() - 1));
    }

    @Test
    void removeEmitsRemove() {
        var map = new TeachingHashMap<String, Integer>();
        map.put("a", 1);
        var rec = new RecordingListener();
        map.addListener(rec);
        assertEquals(1, map.remove("a"));
        assertEquals(1, rec.events().size());
        Remove r = assertInstanceOf(Remove.class, rec.events().get(0));
        assertEquals("a", r.key());
        assertEquals(1, r.removedValue());
    }

    @Test
    void removeMissingKeyEmitsNothing() {
        var map = new TeachingHashMap<String, Integer>();
        var rec = new RecordingListener();
        map.addListener(rec);
        map.remove("nope");
        assertTrue(rec.events().isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `addListener` / `RecordingListener`.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/gimlism/translucent/hashmap/consumer/RecordingListener.java`
```java
package com.gimlism.translucent.hashmap.consumer;

import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.MapEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects events in order. Backbone of sequence-based tests and future replay. */
public class RecordingListener implements MapEventListener {
    private final List<MapEvent> events = new ArrayList<>();

    @Override
    public void onEvent(MapEvent event) {
        events.add(event);
    }

    /** Events in emission order (unmodifiable view). */
    public List<MapEvent> events() {
        return Collections.unmodifiableList(events);
    }

    public void clear() {
        events.clear();
    }
}
```

In `TeachingHashMap`, add imports:
```java
import com.gimlism.translucent.hashmap.events.Collision;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.MapEventListener;
import com.gimlism.translucent.hashmap.events.Put;
import com.gimlism.translucent.hashmap.events.Remove;
import com.gimlism.translucent.hashmap.events.Resize;
```

Add a listener list field (near the other fields):
```java
    private final List<MapEventListener> listeners = new ArrayList<>();
```

Add listener management + emit helper:
```java
    public void addListener(MapEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MapEventListener listener) {
        listeners.remove(listener);
    }

    private void emit(MapEvent event) {
        for (MapEventListener listener : listeners) listener.onEvent(event);
    }
```

Replace the body of `put` (from the insert branch onward) with the event-emitting version:
```java
    @Override
    public V put(K key, V value) {
        int h = hash(key);
        int i = indexFor(h, table.length);
        Node<K, V> head = table[i];
        for (Node<K, V> e = head; e != null; e = e.next) {
            if (e.hash == h && Objects.equals(e.key, key)) {
                V old = e.value;
                e.value = value;
                emit(new Put(key, value, old, i, false, snapshot()));
                return old;
            }
        }
        int chainBefore = 0;
        Node<K, V> created = new Node<>(h, key, value, null);
        if (head == null) {
            table[i] = created;
        } else {
            Node<K, V> tail = head;
            chainBefore = 1;
            while (tail.next != null) { tail = tail.next; chainBefore++; }
            tail.next = created;
        }
        size++;
        modCount++;
        emit(new Put(key, value, null, i, true, snapshot()));
        if (chainBefore > 0) {
            emit(new Collision(key, i, chainBefore, chainBefore + 1, snapshot()));
        }
        if (size > threshold) resize();
        return null;
    }
```

Replace `remove` so it emits:
```java
    @Override
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
                V old = e.value;
                emit(new Remove(key, old, i, snapshot()));
                return old;
            }
        }
        return null;
    }
```

Update `resize` to emit (capture `before` first, emit `after` last):
```java
    @SuppressWarnings("unchecked")
    private void resize() {
        MapSnapshot before = snapshot();
        Node<K, V>[] oldTab = table;
        int oldCap = oldTab.length;
        int newCap = oldCap << 1;
        Node<K, V>[] newTab = (Node<K, V>[]) new Node[newCap];
        for (int j = 0; j < oldCap; j++) {
            Node<K, V> e = oldTab[j];
            while (e != null) {
                Node<K, V> next = e.next;
                int idx = indexFor(e.hash, newCap);
                e.next = null;
                if (newTab[idx] == null) {
                    newTab[idx] = e;
                } else {
                    Node<K, V> tail = newTab[idx];
                    while (tail.next != null) tail = tail.next;
                    tail.next = e;
                }
                e = next;
            }
        }
        table = newTab;
        threshold = (int) (newCap * loadFactor);
        emit(new Resize(oldCap, newCap, before, snapshot()));
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `EventEmissionTest` green; all prior tests still green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java src/main/java/com/gimlism/translucent/hashmap/consumer/RecordingListener.java src/test/java/com/gimlism/translucent/hashmap/core/EventEmissionTest.java
git commit -m "feat: emit Put/Remove/Collision/Resize events with snapshots"
```

---

### Task 9: ConsoleEventLogger

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/consumer/ConsoleEventLogger.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/consumer/ConsoleEventLoggerTest.java`

**Interfaces:**
- Consumes: `MapEvent` hierarchy (Task 3).
- Produces: `class ConsoleEventLogger implements MapEventListener` with:
  - `public ConsoleEventLogger()` → writes to `System.out`
  - `public ConsoleEventLogger(java.io.PrintStream out)` → writes to a supplied stream (testable)
  - `static String format(MapEvent event)` → the human-readable line for an event.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/consumer/ConsoleEventLoggerTest.java`
```java
package com.gimlism.translucent.hashmap.consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.events.Collision;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import com.gimlism.translucent.hashmap.events.Put;
import com.gimlism.translucent.hashmap.events.Resize;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsoleEventLoggerTest {
    private static MapSnapshot snap() {
        return new MapSnapshot(8, 1, 6, List.of());
    }

    @Test
    void formatsPutNewEntry() {
        String line = ConsoleEventLogger.format(new Put("a", 1, null, 3, true, snap()));
        assertEquals("PUT a=1 -> bucket 3 (new)", line);
    }

    @Test
    void formatsPutReplacement() {
        String line = ConsoleEventLogger.format(new Put("a", 2, 1, 3, false, snap()));
        assertEquals("PUT a=2 -> bucket 3 (replaced 1)", line);
    }

    @Test
    void formatsCollision() {
        String line = ConsoleEventLogger.format(new Collision("b", 0, 1, 2, snap()));
        assertEquals("COLLISION b -> bucket 0 (chain len 1 -> 2)", line);
    }

    @Test
    void formatsResize() {
        String line = ConsoleEventLogger.format(new Resize(8, 16, snap(), snap()));
        assertEquals("RESIZE 8 -> 16", line);
    }

    @Test
    void writesToSuppliedStream() {
        var buffer = new ByteArrayOutputStream();
        var logger = new ConsoleEventLogger(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        logger.onEvent(new Put("a", 1, null, 3, true, snap()));
        String printed = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("PUT a=1 -> bucket 3 (new)"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `ConsoleEventLogger`.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/gimlism/translucent/hashmap/consumer/ConsoleEventLogger.java`
```java
package com.gimlism.translucent.hashmap.consumer;

import com.gimlism.translucent.hashmap.events.Collision;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.MapEventListener;
import com.gimlism.translucent.hashmap.events.Put;
import com.gimlism.translucent.hashmap.events.Recolor;
import com.gimlism.translucent.hashmap.events.Remove;
import com.gimlism.translucent.hashmap.events.Resize;
import com.gimlism.translucent.hashmap.events.Rotation;
import com.gimlism.translucent.hashmap.events.Treeify;
import com.gimlism.translucent.hashmap.events.Untreeify;
import java.io.PrintStream;

/** Prints a human-readable line per event. Validates the stream end to end. */
public class ConsoleEventLogger implements MapEventListener {
    private final PrintStream out;

    public ConsoleEventLogger() {
        this(System.out);
    }

    public ConsoleEventLogger(PrintStream out) {
        this.out = out;
    }

    @Override
    public void onEvent(MapEvent event) {
        out.println(format(event));
    }

    /** The human-readable line for an event. */
    public static String format(MapEvent event) {
        return switch (event) {
            case Put p -> p.newEntry()
                ? "PUT " + p.key() + "=" + p.value() + " -> bucket " + p.bucketIndex() + " (new)"
                : "PUT " + p.key() + "=" + p.value() + " -> bucket " + p.bucketIndex()
                    + " (replaced " + p.previousValue() + ")";
            case Remove r -> "REMOVE " + r.key() + " -> bucket " + r.bucketIndex()
                + " (was " + r.removedValue() + ")";
            case Collision c -> "COLLISION " + c.key() + " -> bucket " + c.bucketIndex()
                + " (chain len " + c.chainLengthBefore() + " -> " + c.chainLengthAfter() + ")";
            case Resize rs -> "RESIZE " + rs.oldCapacity() + " -> " + rs.newCapacity();
            case Treeify t -> "TREEIFY bucket " + t.bucketIndex();
            case Untreeify u -> "UNTREEIFY bucket " + u.bucketIndex();
            case Rotation ro -> "ROTATE " + ro.direction() + " @ " + ro.pivotKey()
                + " (bucket " + ro.bucketIndex() + ")";
            case Recolor rc -> "RECOLOR " + rc.nodeKey() + " " + rc.oldColor()
                + " -> " + rc.newColor() + " (bucket " + rc.bucketIndex() + ")";
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `ConsoleEventLoggerTest` green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/consumer/ConsoleEventLogger.java src/test/java/com/gimlism/translucent/hashmap/consumer/ConsoleEventLoggerTest.java
git commit -m "feat(consumer): add ConsoleEventLogger"
```

---

### Task 10: Fail-fast iterator with remove

**Files:**
- Modify: `src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/IteratorTest.java`

**Interfaces:**
- Consumes: Task 4 `EntryIterator`.
- Produces: `EntryIterator` now supports `remove()` and throws `ConcurrentModificationException` when the map is structurally modified during iteration (via a tracked `expectedModCount`).

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/core/IteratorTest.java`
```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IteratorTest {
    @Test
    void iteratorRemoveDeletesEntry() {
        var map = new TeachingHashMap<Integer, Integer>();
        for (int k = 0; k < 5; k++) map.put(k, k);
        Iterator<Map.Entry<Integer, Integer>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getKey() % 2 == 0) it.remove();
        }
        assertEquals(2, map.size()); // 1 and 3 remain
        assertFalse(map.containsKey(0));
        assertEquals(1, map.get(1));
        assertEquals(3, map.get(3));
    }

    @Test
    void structuralModificationDuringIterationThrows() {
        var map = new TeachingHashMap<Integer, Integer>();
        for (int k = 0; k < 5; k++) map.put(k, k);
        Iterator<Map.Entry<Integer, Integer>> it = map.entrySet().iterator();
        it.next();
        map.put(99, 99); // structural change
        assertThrows(ConcurrentModificationException.class, it::next);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — `it.remove()` throws `UnsupportedOperationException`; no CME thrown.

- [ ] **Step 3: Write minimal implementation**

Add import to `TeachingHashMap`:
```java
import java.util.ConcurrentModificationException;
```

Replace the `EntryIterator` inner class with:
```java
    private final class EntryIterator implements Iterator<Map.Entry<K, V>> {
        private int slot = 0;
        private Node<K, V> nextNode;
        private Node<K, V> lastReturned;
        private int expectedModCount = modCount;

        EntryIterator() {
            nextNode = advanceToFirst();
        }

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
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            if (nextNode == null) throw new NoSuchElementException();
            lastReturned = nextNode;
            if (nextNode.next != null) {
                nextNode = nextNode.next;
            } else {
                slot++;
                nextNode = advanceToFirst();
            }
            return lastReturned;
        }

        @Override
        public void remove() {
            if (lastReturned == null) throw new IllegalStateException();
            if (modCount != expectedModCount) throw new ConcurrentModificationException();
            TeachingHashMap.this.remove(lastReturned.key);
            expectedModCount = modCount;
            lastReturned = null;
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `IteratorTest` green; all prior tests still green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/core/TeachingHashMap.java src/test/java/com/gimlism/translucent/hashmap/core/IteratorTest.java
git commit -m "feat(core): fail-fast iterator with remove support"
```

---

### Task 11: Demo runner

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/demo/Demo.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/demo/DemoTest.java`

**Interfaces:**
- Consumes: `TeachingHashMap`, `ConsoleEventLogger`.
- Produces: `class Demo` with `public static void main(String[] args)` and a testable `static void run(java.io.PrintStream out)` that runs a scripted sequence exercising collision + resize.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/gimlism/translucent/hashmap/demo/DemoTest.java`
```java
package com.gimlism.translucent.hashmap.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DemoTest {
    @Test
    void runEmitsCollisionAndResizeLines() {
        var buffer = new ByteArrayOutputStream();
        Demo.run(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("COLLISION"), "expected a collision line, got:\n" + out);
        assertTrue(out.contains("RESIZE"), "expected a resize line, got:\n" + out);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test`
Expected: FAIL — cannot find symbol `Demo`.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/com/gimlism/translucent/hashmap/demo/Demo.java`
```java
package com.gimlism.translucent.hashmap.demo;

import com.gimlism.translucent.hashmap.consumer.ConsoleEventLogger;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import java.io.PrintStream;

/** Scripted demonstration of the teaching HashMap event stream. */
public class Demo {
    public static void main(String[] args) {
        run(System.out);
    }

    static void run(PrintStream out) {
        var map = new TeachingHashMap<Integer, String>();
        map.addListener(new ConsoleEventLogger(out));

        out.println("== inserting keys that collide in bucket 0 ==");
        map.put(0, "zero");
        map.put(8, "eight");
        map.put(16, "sixteen");

        out.println("== inserting keys until the table resizes ==");
        for (int k = 1; k <= 7; k++) {
            if (k != 8 && k != 16) map.put(k, "v" + k);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test`
Expected: PASS — `DemoTest` green. Optionally run the demo: `mvn -q compile exec:java -Dexec.mainClass=com.gimlism.translucent.hashmap.demo.Demo` (only if the exec plugin is configured; otherwise run from an IDE).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/demo/Demo.java src/test/java/com/gimlism/translucent/hashmap/demo/DemoTest.java
git commit -m "feat(demo): scripted collision + resize demonstration"
```

---

## Self-Review

**Spec coverage (Slice 1 portions of the design):**
- Bucket array, power-of-two sizing → Task 4/6. ✓
- Separate chaining for collisions → Task 4. ✓
- Load factor and resize/rehash → Task 6. ✓
- Configurable, demo-tuned constants → Task 4 constructor. ✓
- No bit-spreading hash → Task 4 `hash`/`indexFor`. ✓
- Full `Map` compliance via `AbstractMap` + `entrySet` (chain scope) → Task 4/10. ✓
- Put/Remove/Collision/Resize events → Task 8. ✓
- Full immutable snapshots per event (before+after on Resize) → Task 7/8. ✓
- Sealed `MapEvent` hierarchy (all 8 records) + `MapEventListener` → Task 3. ✓
- Snapshot model incl. `TreeSnapshot`/`TreeNodeSnapshot` (defined, not yet produced) → Task 2. ✓
- ConsoleEventLogger + RecordingListener → Task 8/9. ✓
- Fail-fast iterator + `Iterator.remove` → Task 10. ✓
- Tests asserting event sequences → Task 8. ✓
- Demo runner → Task 11. ✓
- **Deferred to Slice 2/3 (out of scope here, intentionally):** treeify/untreeify, red-black tree, Rotation/Recolor/Treeify/Untreeify *emission*, tree-aware snapshot capture, tree-aware iteration.

**Placeholder scan:** No TBD/TODO; every code step contains complete code. ✓

**Type consistency:** `snapshot()`, `emit()`, `capacity()`, `addListener/removeListener`, `RecordingListener.events()`, `ConsoleEventLogger.format()` names are used identically across tasks. Event record component names (`key`, `value`, `previousValue`, `bucketIndex`, `newEntry`, `after`, `oldCapacity`, `newCapacity`, `before`, `chainLengthBefore`, `chainLengthAfter`, `removedValue`) match between Task 3 definitions and Task 8/9 usages. ✓

## Next slices (separate plans, written after this one lands)

- **Slice 2 — Treeify:** `TreeNode extends Node`, chain→tree conversion at `treeifyThreshold`/`minTreeifyCapacity`, red-black insert emitting `Rotation`/`Recolor`, `Treeify` event, tree-aware `snapshot()` (`TreeSnapshot`), tree-aware read iteration, tree-bin split on resize.
- **Slice 3 — Untreeify + delete:** red-black deletion, `Untreeify` below `untreeifyThreshold`, `Iterator.remove` from tree bins.

These are deliberately deferred so their code is written against the concrete types this slice produces, not speculative signatures.
