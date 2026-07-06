# HashMap Web Visualizer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render a recorded `TeachingHashMap` event stream as an interactive, self-contained HTML replay (SVG buckets, chains, red-black tree bins).

**Architecture:** A new seam parallel to the ASCII track: a generic hand-rolled `JsonWriter` (substrate) writes JSON *syntax*; a per-structure `MapJsonSerializer` (hashmap/viz) maps `List<MapEvent>` → a `frames` array (each frame = an event descriptor + that event's whole-map `after()` snapshot); `MapWebExporter` injects the JSON into an HTML template resource; a `WebVizDemo` records the standard story and writes the `.html`. The vanilla-JS/SVG front-end is a dumb renderer of pre-computed frames — all intelligence lives in the tested Java serializer.

**Tech Stack:** Java 21 (compiled on JDK 26 via `maven.compiler.release=21`), Maven, JUnit 5 (Jupiter), vanilla JavaScript + inline SVG (no framework, no CDN, no build step).

## Global Constraints

- Target Java 21: `maven.compiler.release=21`. No newer-than-21 APIs.
- **Zero new runtime dependencies.** JUnit is the only dependency (test-scope). No JSON library, no JS framework/CDN, no JS build toolchain.
- The exported page is a **single self-contained `.html`**: inline `<style>`, `<script>`, and SVG; no network requests.
- Hand-rolled `JsonWriter` lives in `substrate/viz` (generic syntax, reusable later). The snapshot/event→JSON **mapping** is per-structure: `MapJsonSerializer` in `hashmap/viz`. **Do NOT** add a speculative substrate "JsonSerializer" interface.
- The JS front-end is a **dumb renderer**; all recursive/branching logic is in the Java serializer. The front-end is **not unit-tested** (verified by inspection + the demo).
- Transition fidelity: **discrete frames + a light cross-fade.** No positional interpolation between snapshots.
- Each frame's `map` comes from **that event's own `after()` snapshot** — not the map's live end state.
- Serialization rules: `event.type` = the `MapEvent` subtype simple name; `color` = `Color` enum name (`RED`/`BLACK`); a `null` key or value → JSON `null`; other keys/values → their `String.valueOf` form; `hash` is the raw int.
- Template resource path: `/web/map-viz.html`. Injection token (verbatim, inside a `<script>`): `/*__FRAMES__*/`.
- Run a single test class: `mvn -q -Dtest=ClassName test`. Full suite: `mvn -q test`.

### Reference: exact existing types the serializer consumes (do not redefine)

```
// package com.gimlism.translucent.hashmap.events
sealed interface MapEvent extends StructureEvent permits Put, Remove, Collision, Resize, Treeify, Untreeify, Rotation, Recolor { MapSnapshot after(); }
record Put(Object key, Object value, Object previousValue, int bucketIndex, boolean newEntry, MapSnapshot after)
record Remove(Object key, Object removedValue, int bucketIndex, MapSnapshot after)
record Collision(Object key, int bucketIndex, int chainLengthBefore, int chainLengthAfter, MapSnapshot after)
record Resize(int oldCapacity, int newCapacity, MapSnapshot before, MapSnapshot after)
record Treeify(int bucketIndex, MapSnapshot after)
record Untreeify(int bucketIndex, MapSnapshot after)
record Rotation(int bucketIndex, Direction direction, Object pivotKey, MapSnapshot after)   // Direction { LEFT, RIGHT }
record Recolor(int bucketIndex, Object nodeKey, Color oldColor, Color newColor, MapSnapshot after)  // Color { RED, BLACK }
record MapSnapshot(int capacity, int size, int threshold, List<BucketSnapshot> buckets)
sealed interface BucketSnapshot permits ChainSnapshot, EmptyBucket, TreeSnapshot
record ChainSnapshot(List<EntrySnapshot> entries)
record EmptyBucket()                              // EmptyBucket.INSTANCE exists
record TreeSnapshot(TreeNodeSnapshot root)
record EntrySnapshot(Object key, Object value, int hash)
record TreeNodeSnapshot(Object key, Object value, Color color, TreeNodeSnapshot left, TreeNodeSnapshot right)

// package com.gimlism.translucent.hashmap.consumer
class MapRecordingListener extends RecordingListener<MapEvent>   // .events() -> List<MapEvent>
```

---

### Task 1: `JsonWriter` — generic JSON syntax primitive

**Files:**
- Create: `src/main/java/com/gimlism/translucent/substrate/viz/JsonWriter.java`
- Test: `src/test/java/com/gimlism/translucent/substrate/viz/JsonWriterTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `JsonWriter` — a fluent, stateful writer. Methods (each returns `this` except `toString`):
  `beginObject()`, `endObject()`, `beginArray()`, `endArray()`, `name(String)`, `value(String)` (null → JSON `null`), `value(long)`, `value(boolean)`, `nullValue()`, and `String toString()`. Commas and quoting are handled internally; string values and names are escaped.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/substrate/viz/JsonWriterTest.java`:

```java
package com.gimlism.translucent.substrate.viz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JsonWriterTest {

    @Test
    void writesNestedObjectWithCommasAndTypes() {
        String json = new JsonWriter()
                .beginObject()
                .name("n").value(42L)
                .name("ok").value(true)
                .name("items").beginArray()
                    .value("a").value("b")
                .endArray()
                .name("child").beginObject()
                    .name("x").nullValue()
                .endObject()
                .endObject()
                .toString();
        assertEquals("{\"n\":42,\"ok\":true,\"items\":[\"a\",\"b\"],\"child\":{\"x\":null}}", json);
    }

    @Test
    void escapesStringsAndNullValue() {
        String json = new JsonWriter()
                .beginObject()
                .name("s").value("he\"ll\\o\n\t")
                .name("gone").value((String) null)
                .endObject()
                .toString();
        assertEquals("{\"s\":\"he\\\"ll\\\\o\\n\\t\",\"gone\":null}", json);
    }

    @Test
    void escapesControlCharactersAsUnicode() {
        String json = new JsonWriter().value("").toString();
        assertEquals("\"\\u0001\"", json);
    }

    @Test
    void emptyContainers() {
        assertEquals("{}", new JsonWriter().beginObject().endObject().toString());
        assertEquals("[]", new JsonWriter().beginArray().endArray().toString());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=JsonWriterTest test`
Expected: FAIL — compilation error, `JsonWriter` does not exist.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/gimlism/translucent/substrate/viz/JsonWriter.java`:

```java
package com.gimlism.translucent.substrate.viz;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A minimal, hand-rolled JSON writer — the generic *syntax* half of the web-viz seam
 * (escaping, comma placement, object/array bracketing). It imposes no structure-specific
 * knowledge, so each structure's per-structure serializer reuses it. Not a general-purpose
 * library: it writes exactly the value kinds the visualizers need.
 *
 * <p>Fluent and stateful: interleave {@link #beginObject}/{@link #name}/{@code value}/
 * {@link #endObject} (and the array forms); {@link #toString} returns the accumulated JSON.
 */
public final class JsonWriter {
    private final StringBuilder out = new StringBuilder();
    /** Element count of each open container (top = innermost); drives comma placement. */
    private final Deque<Integer> counts = new ArrayDeque<>();
    /** True immediately after {@link #name}: the next value fills that member, so it emits no comma. */
    private boolean expectingValue = false;

    public JsonWriter beginObject() { pre(); out.append('{'); counts.push(0); return this; }
    public JsonWriter endObject()   { out.append('}'); counts.pop(); post(); return this; }
    public JsonWriter beginArray()  { pre(); out.append('['); counts.push(0); return this; }
    public JsonWriter endArray()    { out.append(']'); counts.pop(); post(); return this; }

    public JsonWriter name(String name) {
        if (!counts.isEmpty() && counts.peek() > 0) out.append(',');
        writeString(name);
        out.append(':');
        expectingValue = true;
        return this;
    }

    public JsonWriter value(String s) { pre(); if (s == null) out.append("null"); else writeString(s); post(); return this; }
    public JsonWriter value(long n)   { pre(); out.append(n); post(); return this; }
    public JsonWriter value(boolean b){ pre(); out.append(b); post(); return this; }
    public JsonWriter nullValue()     { pre(); out.append("null"); post(); return this; }

    /** Separator before a value/opener: a name already emitted the comma; array elements need one. */
    private void pre() {
        if (expectingValue) { expectingValue = false; return; }
        if (!counts.isEmpty() && counts.peek() > 0) out.append(',');
    }

    /** Count the just-written value in its enclosing container. */
    private void post() {
        if (!counts.isEmpty()) counts.push(counts.pop() + 1);
    }

    private void writeString(String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=JsonWriterTest test`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/substrate/viz/JsonWriter.java \
        src/test/java/com/gimlism/translucent/substrate/viz/JsonWriterTest.java
git commit -m "feat(substrate-viz): hand-rolled JsonWriter — the generic web-viz syntax seam

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01GRkjPtkQNVBT3Mk9HtQEdF"
```

---

### Task 2: `MapJsonSerializer` — `List<MapEvent>` → frames JSON

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/viz/MapJsonSerializer.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/MapJsonSerializerTest.java`

(The test lives in package `...hashmap.core` so it can drive a real `TeachingHashMap` end-to-end via a `MapRecordingListener`, exactly as the other core tests do.)

**Interfaces:**
- Consumes: `JsonWriter` (Task 1); the event/snapshot types in the Global Constraints reference block.
- Produces: `MapJsonSerializer` with `public static String toJson(java.util.List<MapEvent> events)` — returns `{"frames":[ {"event":{…},"map":{…}}, … ]}`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/hashmap/core/MapJsonSerializerTest.java`:

```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.consumer.MapRecordingListener;
import com.gimlism.translucent.hashmap.viz.MapJsonSerializer;
import org.junit.jupiter.api.Test;

class MapJsonSerializerTest {

    private static long count(String haystack, String needle) {
        long n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) n++;
        return n;
    }

    @Test
    void frameCountEqualsEventCountAndTypesAppear() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.put(1, "a");
        map.put(2, "b");

        String json = MapJsonSerializer.toJson(rec.events());
        assertEquals(rec.events().size(), count(json, "\"event\":"));
        assertTrue(json.contains("\"type\":\"Put\""), json);
        assertTrue(json.startsWith("{\"frames\":["), json);
    }

    @Test
    void chainBucketListsEntriesWithHash() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.put(1, "a");

        String json = MapJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"kind\":\"chain\""), json);
        assertTrue(json.contains("\"key\":\"1\""), json);
        assertTrue(json.contains("\"value\":\"a\""), json);
        assertTrue(json.contains("\"hash\":1"), json);
    }

    @Test
    void treeifiedBinSerializesNestedColouredNodes() {
        var map = new TeachingHashMap<Integer, String>(); // cap 8, treeify at 4
        var rec = new MapRecordingListener();
        map.addListener(rec);
        for (int k : new int[]{0, 8, 16, 24}) map.put(k, "v" + k); // all bucket 0 -> treeify

        String json = MapJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"kind\":\"tree\""), json);
        assertTrue(json.contains("\"color\":\"BLACK\""), json);
        assertTrue(json.contains("\"color\":\"RED\""), json);
        assertTrue(json.contains("\"left\":"), json);
        assertTrue(json.contains("\"right\":"), json);
    }

    @Test
    void resizeFrameCarriesNewCapacity() {
        var map = new TeachingHashMap<Integer, String>(); // threshold = 6
        var rec = new MapRecordingListener();
        map.addListener(rec);
        for (int k = 0; k < 7; k++) map.put(k, "v" + k); // size 7 > 6 -> resize to 16

        String json = MapJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"type\":\"Resize\""), json);
        assertTrue(json.contains("\"capacity\":16"), json);
    }

    @Test
    void nullKeyAndValueSerializeAsJsonNull() {
        var map = new TeachingHashMap<Object, Object>();
        var rec = new MapRecordingListener();
        map.addListener(rec);
        map.put(null, null);

        String json = MapJsonSerializer.toJson(rec.events());
        assertTrue(json.contains("\"key\":null"), json);
        assertTrue(json.contains("\"value\":null"), json);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=MapJsonSerializerTest test`
Expected: FAIL — compilation error, `MapJsonSerializer` does not exist.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/gimlism/translucent/hashmap/viz/MapJsonSerializer.java`:

```java
package com.gimlism.translucent.hashmap.viz;

import com.gimlism.translucent.hashmap.events.BucketSnapshot;
import com.gimlism.translucent.hashmap.events.ChainSnapshot;
import com.gimlism.translucent.hashmap.events.Collision;
import com.gimlism.translucent.hashmap.events.EntrySnapshot;
import com.gimlism.translucent.hashmap.events.MapEvent;
import com.gimlism.translucent.hashmap.events.MapSnapshot;
import com.gimlism.translucent.hashmap.events.Put;
import com.gimlism.translucent.hashmap.events.Recolor;
import com.gimlism.translucent.hashmap.events.Remove;
import com.gimlism.translucent.hashmap.events.Resize;
import com.gimlism.translucent.hashmap.events.Rotation;
import com.gimlism.translucent.hashmap.events.TreeNodeSnapshot;
import com.gimlism.translucent.hashmap.events.TreeSnapshot;
import com.gimlism.translucent.hashmap.events.Treeify;
import com.gimlism.translucent.hashmap.events.Untreeify;
import com.gimlism.translucent.substrate.viz.JsonWriter;
import java.util.List;

/**
 * The per-structure half of the web-viz seam: maps a recorded {@code MapEvent} stream to the
 * JSON the browser replay consumes. The web analogue of {@code AsciiMapRenderer} — it holds all
 * the branching/recursion (bucket classification, red-black tree walk, event captions) so the
 * front-end can stay a dumb renderer. Each frame carries the event's own {@code after()}
 * snapshot, so the JSON shows exactly the settled state the core emitted.
 */
public final class MapJsonSerializer {

    private MapJsonSerializer() {}

    public static String toJson(List<MapEvent> events) {
        JsonWriter w = new JsonWriter();
        w.beginObject().name("frames").beginArray();
        for (MapEvent e : events) writeFrame(w, e);
        w.endArray().endObject();
        return w.toString();
    }

    private static void writeFrame(JsonWriter w, MapEvent e) {
        w.beginObject();
        w.name("event");
        writeEvent(w, e);
        w.name("map");
        writeMap(w, e.after());
        w.endObject();
    }

    private static void writeEvent(JsonWriter w, MapEvent e) {
        w.beginObject();
        w.name("type").value(e.getClass().getSimpleName());
        w.name("label").value(label(e));
        Integer bucket = bucketOf(e);
        if (bucket != null) w.name("bucket").value((long) bucket);
        String hl = highlightKey(e);
        if (hl != null) w.name("highlightKey").value(hl);
        w.endObject();
    }

    private static String label(MapEvent e) {
        return switch (e) {
            case Put p -> p.newEntry()
                    ? "put " + str(p.key()) + " → bucket " + p.bucketIndex()
                    : "set " + str(p.key()) + " = " + str(p.value());
            case Remove r -> "remove " + str(r.key());
            case Collision c -> "collision @ bucket " + c.bucketIndex()
                    + " (chain " + c.chainLengthBefore() + "→" + c.chainLengthAfter() + ")";
            case Resize z -> "resize " + z.oldCapacity() + " → " + z.newCapacity();
            case Treeify t -> "treeify bucket " + t.bucketIndex();
            case Untreeify u -> "untreeify bucket " + u.bucketIndex();
            case Rotation ro -> "rotate " + ro.direction() + " @ " + str(ro.pivotKey());
            case Recolor rc -> "recolor " + str(rc.nodeKey()) + " " + rc.oldColor() + "→" + rc.newColor();
        };
    }

    private static Integer bucketOf(MapEvent e) {
        return switch (e) {
            case Put p -> p.bucketIndex();
            case Remove r -> r.bucketIndex();
            case Collision c -> c.bucketIndex();
            case Treeify t -> t.bucketIndex();
            case Untreeify u -> u.bucketIndex();
            case Rotation ro -> ro.bucketIndex();
            case Recolor rc -> rc.bucketIndex();
            case Resize z -> null;
        };
    }

    private static String highlightKey(MapEvent e) {
        return switch (e) {
            case Put p -> str(p.key());
            case Remove r -> str(r.key());
            case Collision c -> str(c.key());
            case Rotation ro -> str(ro.pivotKey());
            case Recolor rc -> str(rc.nodeKey());
            case Treeify t -> null;
            case Untreeify u -> null;
            case Resize z -> null;
        };
    }

    private static void writeMap(JsonWriter w, MapSnapshot m) {
        w.beginObject();
        w.name("capacity").value((long) m.capacity());
        w.name("size").value((long) m.size());
        w.name("threshold").value((long) m.threshold());
        w.name("buckets").beginArray();
        for (BucketSnapshot b : m.buckets()) writeBucket(w, b);
        w.endArray();
        w.endObject();
    }

    private static void writeBucket(JsonWriter w, BucketSnapshot b) {
        w.beginObject();
        if (b instanceof ChainSnapshot c) {
            w.name("kind").value("chain");
            w.name("entries").beginArray();
            for (EntrySnapshot en : c.entries()) {
                w.beginObject();
                w.name("key").value(str(en.key()));
                w.name("value").value(str(en.value()));
                w.name("hash").value((long) en.hash());
                w.endObject();
            }
            w.endArray();
        } else if (b instanceof TreeSnapshot t) {
            w.name("kind").value("tree");
            w.name("root");
            writeNode(w, t.root());
        } else { // EmptyBucket
            w.name("kind").value("empty");
        }
        w.endObject();
    }

    private static void writeNode(JsonWriter w, TreeNodeSnapshot n) {
        if (n == null) {
            w.nullValue();
            return;
        }
        w.beginObject();
        w.name("key").value(str(n.key()));
        w.name("value").value(str(n.value()));
        w.name("color").value(n.color().name());
        w.name("left");
        writeNode(w, n.left());
        w.name("right");
        writeNode(w, n.right());
        w.endObject();
    }

    /** null → JSON null (via {@link JsonWriter#value(String)}); otherwise the value's String form. */
    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=MapJsonSerializerTest test`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/viz/MapJsonSerializer.java \
        src/test/java/com/gimlism/translucent/hashmap/core/MapJsonSerializerTest.java
git commit -m "feat(hashmap-viz): MapJsonSerializer — event stream to web-replay frames JSON

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01GRkjPtkQNVBT3Mk9HtQEdF"
```

---

### Task 3: `map-viz.html` template + `MapWebExporter`

**Files:**
- Create: `src/main/resources/web/map-viz.html`
- Create: `src/main/java/com/gimlism/translucent/hashmap/viz/MapWebExporter.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/MapWebExporterTest.java`

**Interfaces:**
- Consumes: the template resource `/web/map-viz.html` with token `/*__FRAMES__*/`.
- Produces: `MapWebExporter` with `public static String toHtml(String framesJson)` (returns the self-contained document) and `public static void writeHtml(String framesJson, java.nio.file.Path out)` (writes it).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/hashmap/core/MapWebExporterTest.java`:

```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.viz.MapWebExporter;
import org.junit.jupiter.api.Test;

class MapWebExporterTest {

    @Test
    void injectsJsonIntoASelfContainedDocument() {
        String html = MapWebExporter.toHtml("{\"frames\":[]}");
        assertTrue(html.toLowerCase().contains("<!doctype html"), "must be a full document");
        assertTrue(html.contains("</html>"), "must be a full document");
        assertTrue(html.contains("{\"frames\":[]}"), "must contain the injected JSON");
    }

    @Test
    void consumesTheInjectionToken() {
        String html = MapWebExporter.toHtml("{\"frames\":[]}");
        assertFalse(html.contains("/*__FRAMES__*/"), "the token must be replaced, not left behind");
    }

    @Test
    void isSelfContained_noExternalUrls() {
        String html = MapWebExporter.toHtml("{\"frames\":[]}");
        assertFalse(html.contains("http://"), "no external URLs");
        assertFalse(html.contains("https://"), "no external URLs");
        assertFalse(html.contains("src=\""), "no external script/img src");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=MapWebExporterTest test`
Expected: FAIL — compilation error, `MapWebExporter` does not exist.

- [ ] **Step 3: Create the HTML template**

Create `src/main/resources/web/map-viz.html` (complete, self-contained; the token appears once, inside the script):

```html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>TeachingHashMap — replay</title>
<style>
  :root { color-scheme: light dark; --bg:#f7f7f8; --fg:#1b1b1d; --muted:#777; --card:#fff;
          --edge:#8a8a8a; --cell:#eceef2; --cellb:#c8ccd4; --red:#d64545; --black:#333; --hl:#f5a623; }
  @media (prefers-color-scheme: dark) {
    :root { --bg:#16171a; --fg:#e8e8ea; --muted:#999; --card:#1f2126; --edge:#8a8a8a;
            --cell:#2a2d33; --cellb:#41454d; --red:#e05a5a; --black:#c9ccd2; --hl:#f5a623; }
  }
  * { box-sizing: border-box; }
  body { margin:0; background:var(--bg); color:var(--fg); font:15px system-ui,sans-serif; }
  #app { max-width: 960px; margin: 0 auto; padding: 18px; }
  h1 { font-size: 16px; font-weight:600; margin: 0 0 12px; }
  #bar { display:flex; align-items:center; gap:12px; flex-wrap:wrap; margin-bottom:10px; }
  button { font:inherit; padding:6px 12px; border:1px solid var(--cellb); border-radius:8px;
           background:var(--card); color:var(--fg); cursor:pointer; }
  button:disabled { opacity:.4; cursor:default; }
  #counter { color:var(--muted); font-variant-numeric: tabular-nums; }
  #caption { font-weight:600; min-height:1.4em; margin-bottom:8px; }
  #stage { background:var(--card); border:1px solid var(--cellb); border-radius:12px; padding:10px;
           transition: opacity .18s ease; overflow:auto; }
  svg text { fill: var(--fg); font: 13px system-ui, sans-serif; }
  .idx { fill: var(--muted); font-weight:600; }
  .cell rect { fill: var(--cell); stroke: var(--cellb); }
  .cell.hl rect, .node.hl circle { stroke: var(--hl); stroke-width: 3; }
  .edge { stroke: var(--edge); stroke-width: 2; }
  .node circle { stroke: #0006; stroke-width: 1.5; }
  .node.red circle { fill: var(--red); }
  .node.black circle { fill: var(--black); }
  .node text, .cell text { fill: #fff; }
  .cell text { fill: var(--fg); }
</style>
</head>
<body>
<div id="app">
  <h1>TeachingHashMap — web replay</h1>
  <div id="caption"></div>
  <div id="bar">
    <button id="prev">◀ prev</button>
    <button id="play">▶ play</button>
    <button id="next">next ▶</button>
    <span id="counter"></span>
  </div>
  <div id="stage"><svg id="svg" xmlns="http://www.w3.org/2000/svg"></svg></div>
</div>
<script>
"use strict";
const DATA = /*__FRAMES__*/;
const frames = (DATA && DATA.frames) || [];
const svg = document.getElementById("svg");
const stage = document.getElementById("stage");
const caption = document.getElementById("caption");
const counter = document.getElementById("counter");
const prevBtn = document.getElementById("prev");
const nextBtn = document.getElementById("next");
const playBtn = document.getElementById("play");

const PAD = 14, IDX_W = 40, ROW_GAP = 12, CELL_W = 66, CELL_H = 30, ARROW = 20,
      NODE_R = 16, H_GAP = 42, V_GAP = 54;
let idx = 0, timer = null;

function esc(s) { return String(s).replace(/[&<>]/g, c => ({ "&":"&amp;","<":"&lt;",">":"&gt;" }[c])); }
function txt(s) { return s === null ? "∅" : String(s); }

// Lay out a tree bin: in-order slot (x) + depth (y) per node, then parent->child edges.
function buildTree(root) {
  const nodes = [], edges = [], pos = new Map();
  let slot = 0, maxDepth = 0;
  (function assign(n, depth) {            // in-order: left, self, right -> slots read left-to-right
    if (!n) return;
    assign(n.left, depth + 1);
    const rec = { key: n.key, color: n.color, s: slot++, depth };
    maxDepth = Math.max(maxDepth, depth);
    nodes.push(rec);
    pos.set(n, rec);
    assign(n.right, depth + 1);
  })(root, 0);
  (function link(n) {                     // one edge from each node to each present child
    if (!n) return;
    const p = pos.get(n);
    if (n.left)  { edges.push([p, pos.get(n.left)]);  link(n.left); }
    if (n.right) { edges.push([p, pos.get(n.right)]); link(n.right); }
  })(root);
  return { nodes, edges, slots: slot, depth: maxDepth };
}

function renderBucket(b, x0, y0, hl) {
  // returns { markup, width, height }
  if (b.kind === "empty") {
    return { markup: `<text x="${x0}" y="${y0 + CELL_H/2 + 4}" fill="var(--muted)">·</text>`,
             width: CELL_W, height: CELL_H };
  }
  if (b.kind === "chain") {
    let m = "", x = x0;
    b.entries.forEach((e, i) => {
      if (i > 0) m += `<line class="edge" x1="${x-ARROW}" y1="${y0+CELL_H/2}" x2="${x}" y2="${y0+CELL_H/2}"/>`;
      const on = String(e.key) === hl ? " hl" : "";
      m += `<g class="cell${on}"><rect x="${x}" y="${y0}" width="${CELL_W}" height="${CELL_H}" rx="6"/>`
         + `<text x="${x+CELL_W/2}" y="${y0+CELL_H/2+4}" text-anchor="middle">${esc(txt(e.key))}=${esc(txt(e.value))}</text></g>`;
      x += CELL_W + ARROW;
    });
    return { markup: m, width: b.entries.length * (CELL_W + ARROW), height: CELL_H };
  }
  // tree
  const t = buildTree(b.root);
  const cx = s => x0 + s * H_GAP + NODE_R;
  const cy = d => y0 + d * V_GAP + NODE_R;
  let m = "";
  t.edges.forEach(([p, c]) => {
    m += `<line class="edge" x1="${cx(p.s)}" y1="${cy(p.depth)}" x2="${cx(c.s)}" y2="${cy(c.depth)}"/>`;
  });
  t.nodes.forEach(n => {
    const on = String(n.key) === hl ? " hl" : "";
    const cls = n.color === "RED" ? "red" : "black";
    m += `<g class="node ${cls}${on}"><circle cx="${cx(n.s)}" cy="${cy(n.depth)}" r="${NODE_R}"/>`
       + `<text x="${cx(n.s)}" y="${cy(n.depth)+4}" text-anchor="middle">${esc(txt(n.key))}</text></g>`;
  });
  return { markup: m, width: Math.max(1, t.slots) * H_GAP, height: (t.depth + 1) * V_GAP };
}

function renderFrame(f) {
  const hl = (f.event && f.event.highlightKey != null) ? String(f.event.highlightKey) : null;
  const buckets = f.map.buckets;
  let y = PAD, maxW = 0, body = "";
  buckets.forEach((b, i) => {
    const contentX = PAD + IDX_W;
    const r = renderBucket(b, contentX, y, hl);
    body += `<text class="idx" x="${PAD}" y="${y + CELL_H/2 + 4}">${i}</text>`;
    body += r.markup;
    maxW = Math.max(maxW, contentX + r.width + PAD);
    y += r.height + ROW_GAP;
  });
  const width = Math.max(320, maxW), height = y + PAD;
  svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
  svg.setAttribute("width", width);
  svg.setAttribute("height", height);
  svg.innerHTML = body;
}

function render() {
  const f = frames[idx];
  caption.textContent = f ? f.event.label : "(no events to replay)";
  counter.textContent = frames.length ? `frame ${idx + 1} / ${frames.length}` : "";
  prevBtn.disabled = idx <= 0;
  nextBtn.disabled = idx >= frames.length - 1;
  if (f) renderFrame(f); else svg.innerHTML = "";
  // light cross-fade
  stage.style.opacity = "0";
  requestAnimationFrame(() => requestAnimationFrame(() => { stage.style.opacity = "1"; }));
}

function go(n) { idx = Math.max(0, Math.min(frames.length - 1, n)); render(); }
function stop() { if (timer) { clearInterval(timer); timer = null; playBtn.textContent = "▶ play"; } }
prevBtn.onclick = () => { stop(); go(idx - 1); };
nextBtn.onclick = () => { stop(); go(idx + 1); };
playBtn.onclick = () => {
  if (timer) { stop(); return; }
  if (idx >= frames.length - 1) idx = 0;
  playBtn.textContent = "⏸ pause";
  timer = setInterval(() => { if (idx >= frames.length - 1) { stop(); } else { go(idx + 1); } }, 900);
};
document.addEventListener("keydown", e => {
  if (e.key === "ArrowRight") { stop(); go(idx + 1); }
  else if (e.key === "ArrowLeft") { stop(); go(idx - 1); }
});
render();
</script>
</body>
</html>
```

- [ ] **Step 4: Write the exporter**

Create `src/main/java/com/gimlism/translucent/hashmap/viz/MapWebExporter.java`:

```java
package com.gimlism.translucent.hashmap.viz;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Assembles the self-contained HTML replay by injecting a serialized {@code frames} JSON blob
 * (from {@link MapJsonSerializer}) into the {@code /web/map-viz.html} template at its single
 * {@code /*__FRAMES__*}{@code /} token. The template carries the whole vanilla-JS/SVG renderer;
 * this class only substitutes the data, so the output is one shareable file with no external
 * assets.
 */
public final class MapWebExporter {

    private static final String TEMPLATE_RESOURCE = "/web/map-viz.html";
    private static final String TOKEN = "/*__FRAMES__*/";

    private MapWebExporter() {}

    /** The self-contained HTML document with {@code framesJson} injected at the template token. */
    public static String toHtml(String framesJson) {
        String template = readTemplate();
        if (!template.contains(TOKEN)) {
            throw new IllegalStateException("template " + TEMPLATE_RESOURCE + " is missing token " + TOKEN);
        }
        return template.replace(TOKEN, framesJson);
    }

    /** Write {@link #toHtml(String)} to {@code out} (UTF-8). */
    public static void writeHtml(String framesJson, Path out) throws IOException {
        Files.writeString(out, toHtml(framesJson));
    }

    private static String readTemplate() {
        try (InputStream in = MapWebExporter.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("template resource not found on classpath: " + TEMPLATE_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn -q -Dtest=MapWebExporterTest test`
Expected: PASS (3 tests). (If it fails with "template resource not found", confirm the file is at `src/main/resources/web/map-viz.html` so Maven puts it on the classpath.)

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/web/map-viz.html \
        src/main/java/com/gimlism/translucent/hashmap/viz/MapWebExporter.java \
        src/test/java/com/gimlism/translucent/hashmap/core/MapWebExporterTest.java
git commit -m "feat(hashmap-viz): self-contained HTML template + MapWebExporter injection

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01GRkjPtkQNVBT3Mk9HtQEdF"
```

---

### Task 4: `WebVizDemo` — end-to-end runnable

**Files:**
- Create: `src/main/java/com/gimlism/translucent/hashmap/demo/WebVizDemo.java`
- Test: `src/test/java/com/gimlism/translucent/hashmap/core/WebVizDemoTest.java`

**Interfaces:**
- Consumes: `TeachingHashMap`, `MapRecordingListener`, `MapJsonSerializer`, `MapWebExporter`.
- Produces: `WebVizDemo` with `public static void main(String[] args)` (writes the `.html`; optional `args[0]` = output path, default `target/hashmap-web-viz.html`) and a test seam `static String buildHtml()` returning the document for the standard story.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/gimlism/translucent/hashmap/core/WebVizDemoTest.java`:

```java
package com.gimlism.translucent.hashmap.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimlism.translucent.hashmap.demo.WebVizDemo;
import org.junit.jupiter.api.Test;

class WebVizDemoTest {

    @Test
    void buildsASelfContainedPageForTheStandardStory() {
        String html = WebVizDemo.buildHtml();
        assertTrue(html.toLowerCase().contains("<!doctype html"), "full document");
        assertTrue(html.contains("</html>"), "full document");
        assertTrue(html.contains("\"frames\":["), "carries serialized frames");
        // the story collides in one bucket until it treeifies, so at least one tree bin appears
        assertTrue(html.contains("\"kind\":\"tree\""), "the story should treeify a bin");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=WebVizDemoTest test`
Expected: FAIL — compilation error, `WebVizDemo` does not exist.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/gimlism/translucent/hashmap/demo/WebVizDemo.java`:

```java
package com.gimlism.translucent.hashmap.demo;

import com.gimlism.translucent.hashmap.consumer.MapRecordingListener;
import com.gimlism.translucent.hashmap.core.TeachingHashMap;
import com.gimlism.translucent.hashmap.viz.MapJsonSerializer;
import com.gimlism.translucent.hashmap.viz.MapWebExporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Records the standard collide → treeify → resize → untreeify story and writes it out as a
 * self-contained HTML web replay. Run with:
 * {@code mvn exec:java -Dexec.mainClass=com.gimlism.translucent.hashmap.demo.WebVizDemo}
 * (optionally {@code -Dexec.args="path/to/out.html"}). The whole-document build is factored into
 * {@link #buildHtml()} so it can be unit-tested without touching the filesystem.
 */
public class WebVizDemo {

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "target/hashmap-web-viz.html");
        Path parent = out.getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(out, buildHtml());
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    /** The self-contained HTML replay for the standard story (test seam — no filesystem). */
    static String buildHtml() {
        var map = new TeachingHashMap<Integer, String>();
        var rec = new MapRecordingListener();
        map.addListener(rec);

        // collide in bucket 0 until it treeifies, then grows past the load factor and resizes
        for (int k : new int[]{0, 8, 16, 24, 32, 40, 48, 56}) {
            map.put(k, "v" + k);
        }
        // remove colliding keys until the bin untreeifies back to a chain
        for (int k : new int[]{0, 16, 32}) {
            map.remove(k);
        }
        return MapWebExporter.toHtml(MapJsonSerializer.toJson(rec.events()));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=WebVizDemoTest test`
Expected: PASS (1 test).

- [ ] **Step 5: Manually generate and eyeball the page**

Run: `mvn -q exec:java -Dexec.mainClass=com.gimlism.translucent.hashmap.demo.WebVizDemo`
Expected: prints `Wrote …/target/hashmap-web-viz.html`. Open that file in a browser: prev/next/play step through frames; a bucket fills, treeifies into a red/black tree, the table resizes, and the bin untreeifies. (Inspection check — the front-end is not unit-tested.)

- [ ] **Step 6: Run the full suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/gimlism/translucent/hashmap/demo/WebVizDemo.java \
        src/test/java/com/gimlism/translucent/hashmap/core/WebVizDemoTest.java
git commit -m "feat(hashmap-demo): WebVizDemo — record the story and export the HTML replay

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01GRkjPtkQNVBT3Mk9HtQEdF"
```

---

## Self-Review

**Spec coverage:**
- New seam (generic `JsonWriter` + per-structure `MapJsonSerializer`, no substrate interface) → Tasks 1, 2. ✓
- JSON frame model (event descriptor + per-event `after()` snapshot; chain/tree/empty; recursive coloured nodes; null key/value) → Task 2 (`writeMap`/`writeBucket`/`writeNode`) + tests. ✓
- Self-contained single `.html`, inline everything, no CDN → Task 3 template + `isSelfContained` test. ✓
- Template-injection exporter with `/*__FRAMES__*/` token → Task 3. ✓
- Scrubber mirrors `Replayer` (step/back/play-pause + counter + caption) → Task 3 template controls. ✓
- Discrete frames + light cross-fade, no positional interpolation → Task 3 (`render()` opacity fade; `innerHTML` swap, no tweening). ✓
- Front-end untested, intelligence in Java → serializer fully tested (Task 2); template inspection-only (Task 3/4 Step 5). ✓
- `WebVizDemo` runnable via `-Dexec.mainClass`, standard story → Task 4. ✓
- Serialization rules (type = simple name; color enum name; hash int; String form) → Task 2 + tests. ✓

**Placeholder scan:** No TBD/TODO. Every code step shows complete code; the template's tree layout uses a single `buildTree` function.

**Type consistency:** `MapJsonSerializer.toJson(List<MapEvent>) -> String` used identically in Task 2 (def) and Task 4 (call). `MapWebExporter.toHtml(String) -> String` used in Task 3 (def), Task 4 (call), and both exporter/demo tests. Template token `/*__FRAMES__*/` identical in the template (Task 3), the exporter constant (Task 3), and the exporter test (Task 3). `buildHtml()` defined and called consistently in Task 4. JSON field names (`frames`, `event`/`type`/`label`/`bucket`/`highlightKey`, `map`/`capacity`/`size`/`threshold`/`buckets`, `kind`/`entries`/`key`/`value`/`hash`, `root`/`color`/`left`/`right`) match between the serializer (Task 2) and the front-end renderer (Task 3). ✓
```
